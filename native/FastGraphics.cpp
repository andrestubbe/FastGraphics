#include <windows.h>
#include <d3d11.h>
#include <d3dcompiler.h>
#include <directxmath.h>
#include <map>
#include <vector>
#include <string>
#include <jni.h>
#include <d2d1_1.h>
#include <dwrite.h>

using namespace DirectX;

struct ConstantBuffer {
    XMMATRIX transform;
    float smoothness;
    float pad[3];
};

struct InstanceData {
    XMFLOAT2 pos;
    XMFLOAT2 size;
    XMFLOAT4 color;
    XMFLOAT4 params; // [p1, p2, type, p3]
};

static ID3D11Device* g_device = nullptr;
static ID3D11DeviceContext* g_context = nullptr;
static IDXGISwapChain* g_swapChain = nullptr;
static ID3D11RenderTargetView* g_rtv = nullptr;
static ID3D11Buffer* g_cb = nullptr;
static ID3D11Buffer* g_quadVB = nullptr;
static ID3D11Buffer* g_instanceVB = nullptr;
static ID3D11VertexShader* g_vs = nullptr;
static ID3D11PixelShader* g_ps = nullptr;
static ID3D11InputLayout* g_layout = nullptr;
static ID3D11BlendState* g_bsAlpha = nullptr;
static ID3D11RasterizerState* g_rs = nullptr;
static ID3D11SamplerState* g_samplerLinear = nullptr;
static ID3D11SamplerState* g_samplerPoint = nullptr;

static HWND g_hwnd = NULL;
static int g_renderWidth = 0;
static int g_renderHeight = 0;
static bool g_aaEnabled = false;

static ID2D1Factory1* g_d2dFactory = nullptr;
static IDWriteFactory* g_dwFactory = nullptr;
static ID2D1DeviceContext* g_d2dContext = nullptr;
static ID2D1SolidColorBrush* g_textBrush = nullptr;

static const int MAX_INSTANCES = 16384;

struct TextureEntry {
    ID3D11ShaderResourceView* srv;
    int w, h;
};
std::map<int, TextureEntry> g_textures;
int g_nextTextureId = 1;

const char* SHADER_SRC = R"(
struct VS_INPUT {
    float2 pos : POSITION;
    float2 quadPos : TEXCOORD;
};
struct INSTANCE {
    float2 pos : I_POS;
    float2 size : I_SIZE;
    float4 color : I_COLOR;
    float4 params : I_PARAMS;
};
struct PS_INPUT {
    float4 pos : SV_POSITION;
    float4 color : COLOR;
    float2 localPos : TEXCOORD0;
    float4 params : PARAMS;
    float2 pixelPos : TEXCOORD1;
    float2 p1 : P1;
    float2 p2 : P2;
    float2 p3 : P3;
};

cbuffer cb0 : register(b0) { 
    float4x4 transform; 
    float smoothness;
}
Texture2D tex : register(t0);
SamplerState smp : register(s0);

float sdRoundRect(float2 p, float2 b, float r) {
    float2 d = abs(p) - b + r;
    return min(max(d.x, d.y), 0.0) + length(max(d, 0.0)) - r;
}

float sdTriangle(float2 p, float2 p0, float2 p1, float2 p2) {
    float2 e0 = p1 - p0, e1 = p2 - p1, e2 = p0 - p2;
    float2 v0 = p - p0, v1 = p - p1, v2 = p - p2;
    float2 pq0 = v0 - e0 * clamp(dot(v0, e0) / dot(e0, e0), 0.0, 1.0);
    float2 pq1 = v1 - e1 * clamp(dot(v1, e1) / dot(e1, e1), 0.0, 1.0);
    float2 pq2 = v2 - e2 * clamp(dot(v2, e2) / dot(e2, e2), 0.0, 1.0);
    float s = sign(e0.x * e2.y - e0.y * e2.x);
    float2 d = min(min(float2(dot(pq0, pq0), s * (v0.x * e0.y - v0.y * e0.x)),
                       float2(dot(pq1, pq1), s * (v1.x * e1.y - v1.y * e1.x))),
                   float2(dot(pq2, pq2), s * (v2.x * e2.y - v2.y * e2.x)));
    return -sqrt(d.x) * sign(d.y);
}

PS_INPUT vs_main(VS_INPUT input, INSTANCE instance) {
    PS_INPUT output;
    float type = instance.params.z;
    float2 pixelPos;
    if (type == 6 || type == 7) { 
        float2 p1 = instance.pos, p2 = instance.size, p3 = instance.params.xy;
        float2 minP = min(min(p1, p2), p3), maxP = max(max(p1, p2), p3);
        pixelPos = minP + input.quadPos * (maxP - minP);
        output.p1 = p1; output.p2 = p2; output.p3 = p3;
    } else if (type == 8) { 
        float2 a = instance.pos, b = instance.size;
        float2 minP = min(a, b) - 5.0, maxP = max(a, b) + 5.0;
        pixelPos = minP + input.quadPos * (maxP - minP);
        output.p1 = a; output.p2 = b; output.p3 = 0;
    } else {
        pixelPos = instance.pos + input.quadPos * instance.size;
        output.p1 = 0; output.p2 = 0; output.p3 = 0;
    }
    output.pos = mul(float4(pixelPos, 0, 1), transform);
    output.color = instance.color;
    output.localPos = input.quadPos * 2.0 - 1.0;
    output.params = instance.params;
    output.pixelPos = pixelPos;
    return output;
}

float4 ps_main(PS_INPUT input) : SV_Target {
    float type = floor(input.params.z + 0.5);
    float p1 = input.params.x; 
    float p2 = input.params.y;
    float p3 = input.params.w;
    
    if (type == 9) {
        float2 uv = input.localPos * 0.5 + 0.5;
        return tex.Sample(smp, uv) * input.color;
    }

    float d = 0, th = 0;
    if (type == 0 || type == 2) { 
        d = sdRoundRect(input.localPos, float2(1,1), p1);
        th = (type == 2) ? p2 * 2.0 : 0;
    } else if (type == 1 || type == 3) { 
        d = sdRoundRect(input.localPos, float2(1,1), 1.0);
        th = (type == 3) ? p2 * 2.0 : 0;
    } else if (type == 4 || type == 5) {
        float2 lp = input.localPos;
        float dist = length(lp);
        
        // Use a more stable angle calculation for 0-360 range
        float angle = degrees(atan2(-lp.y, lp.x));
        if (angle < 0) angle += 360.0f;
        angle = fmod(angle, 360.0f);
        
        float start = p1, extent = p2;
        bool inside = (abs(extent) >= 360.0f);
        if (!inside) {
            float s = fmod(start, 360.0f); if (s < 0) s += 360.0f;
            float e = fmod(start + extent, 360.0f); if (e < 0) e += 360.0f;
            
            // For negative sweep, we are looking for the space BETWEEN e and s
            if (extent > 0) {
                if (s <= e) inside = (angle >= s && angle <= e);
                else inside = (angle >= s || angle <= e);
            } else {
                if (e <= s) inside = (angle >= e && angle <= s);
                else inside = (angle >= e || angle <= s);
            }
        }
        
        if (!inside) discard;
        d = dist - 1.0;
        th = (type == 5) ? p3 * 2.0f : 0.0f;
    } else if (type == 6 || type == 7) { 
        d = sdTriangle(input.pixelPos, input.p1, input.p2, input.p3);
        th = (type == 7) ? p3 : 0;
    } else if (type == 8) {
        float2 ba = input.p2 - input.p1;
        float2 pa = input.pixelPos - input.p1;
        float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
        d = length(pa - ba * h);
        if (d > p1 * 0.5) discard;
        return input.color;
    }
    
    float alpha = 1.0;
    if (smoothness > 0) {
        float fw = fwidth(d);
        float d_pixel = d / fw;
        float th_pixel = th / fw;
        if (th > 0) {
            alpha = saturate(0.5 - d_pixel) * saturate(0.5 + (d_pixel + th_pixel));
        } else {
            alpha = saturate(0.5 - d_pixel);
        }
    } else {
        if (th > 0) {
            if (d > 0 || d < -th) discard;
        } else {
            if (d > 0) discard;
        }
    }
    
    return float4(input.color.rgb, input.color.a * alpha);
}
)";

extern "C" {

JNIEXPORT jlong JNICALL Java_fastgraphics_FastGraphicsEngine_findWindow(JNIEnv* env, jclass, jstring title) {
    const char* str = env->GetStringUTFChars(title, NULL);
    HWND hwnd = FindWindowA(NULL, str);
    env->ReleaseStringUTFChars(title, str);
    return (jlong)hwnd;
}

JNIEXPORT jlong JNICALL Java_fastgraphics_FastGraphicsEngine_findCanvas(JNIEnv* env, jclass, jlong parent) {
    return (jlong)FindWindowExA((HWND)parent, NULL, "SunAwtCanvas", NULL);
}

JNIEXPORT jboolean JNICALL Java_fastgraphics_FastGraphicsEngine_init(JNIEnv* env, jobject, jlong hwnd) {
    g_hwnd = (HWND)hwnd;
    DXGI_SWAP_CHAIN_DESC sd = {};
    sd.BufferCount = 2;
    sd.BufferDesc.Format = DXGI_FORMAT_R8G8B8A8_UNORM;
    sd.BufferUsage = DXGI_USAGE_RENDER_TARGET_OUTPUT;
    sd.OutputWindow = g_hwnd;
    sd.SampleDesc.Count = 1;
    sd.Windowed = TRUE;
    sd.SwapEffect = DXGI_SWAP_EFFECT_DISCARD;

    UINT flags = D3D11_CREATE_DEVICE_BGRA_SUPPORT;
    HRESULT hr = D3D11CreateDeviceAndSwapChain(NULL, D3D_DRIVER_TYPE_HARDWARE, NULL, flags, NULL, 0, D3D11_SDK_VERSION, &sd, &g_swapChain, &g_device, NULL, &g_context);
    if (FAILED(hr)) { printf("D3D11 Error: 0x%08X\n", hr); return JNI_FALSE; }

    ID3D11Texture2D* bb = nullptr; 
    hr = g_swapChain->GetBuffer(0, __uuidof(ID3D11Texture2D), (void**)&bb);
    if (FAILED(hr)) { printf("GetBuffer Error: 0x%08X\n", hr); return JNI_FALSE; }
    
    hr = g_device->CreateRenderTargetView(bb, NULL, &g_rtv);
    if (FAILED(hr)) { bb->Release(); printf("CreateRTV Error: 0x%08X\n", hr); return JNI_FALSE; }

    ID3DBlob* vsBlob = nullptr, * psBlob = nullptr, * errorBlob = nullptr;
    HRESULT hr_vs = D3DCompile(SHADER_SRC, strlen(SHADER_SRC), NULL, NULL, NULL, "vs_main", "vs_4_0", 0, 0, &vsBlob, &errorBlob);
    if (FAILED(hr_vs)) {
        if (errorBlob) { printf("VS Error: %s\n", (char*)errorBlob->GetBufferPointer()); errorBlob->Release(); }
        bb->Release(); return JNI_FALSE;
    }
    hr = g_device->CreateVertexShader(vsBlob->GetBufferPointer(), vsBlob->GetBufferSize(), NULL, &g_vs);
    if (FAILED(hr)) { vsBlob->Release(); bb->Release(); return JNI_FALSE; }

    HRESULT hr_ps = D3DCompile(SHADER_SRC, strlen(SHADER_SRC), NULL, NULL, NULL, "ps_main", "ps_4_0", 0, 0, &psBlob, &errorBlob);
    if (FAILED(hr_ps)) {
        if (errorBlob) { printf("PS Error: %s\n", (char*)errorBlob->GetBufferPointer()); errorBlob->Release(); }
        vsBlob->Release(); bb->Release(); return JNI_FALSE;
    }
    hr = g_device->CreatePixelShader(psBlob->GetBufferPointer(), psBlob->GetBufferSize(), NULL, &g_ps);
    if (FAILED(hr)) { vsBlob->Release(); psBlob->Release(); bb->Release(); return JNI_FALSE; }

    D3D11_INPUT_ELEMENT_DESC ied[] = {
        {"POSITION", 0, DXGI_FORMAT_R32G32_FLOAT, 0, 0, D3D11_INPUT_PER_VERTEX_DATA, 0},
        {"TEXCOORD", 0, DXGI_FORMAT_R32G32_FLOAT, 0, 0, D3D11_INPUT_PER_VERTEX_DATA, 0},
        {"I_POS", 0, DXGI_FORMAT_R32G32_FLOAT, 1, 0, D3D11_INPUT_PER_INSTANCE_DATA, 1},
        {"I_SIZE", 0, DXGI_FORMAT_R32G32_FLOAT, 1, 8, D3D11_INPUT_PER_INSTANCE_DATA, 1},
        {"I_COLOR", 0, DXGI_FORMAT_R32G32B32A32_FLOAT, 1, 16, D3D11_INPUT_PER_INSTANCE_DATA, 1},
        {"I_PARAMS", 0, DXGI_FORMAT_R32G32B32A32_FLOAT, 1, 32, D3D11_INPUT_PER_INSTANCE_DATA, 1}
    };
    hr = g_device->CreateInputLayout(ied, 6, vsBlob->GetBufferPointer(), vsBlob->GetBufferSize(), &g_layout);
    vsBlob->Release(); psBlob->Release();
    if (FAILED(hr)) { bb->Release(); return JNI_FALSE; }

    float quad[] = { 0,0, 1,0, 0,1, 1,1 };
    D3D11_BUFFER_DESC qbd = { sizeof(quad), D3D11_USAGE_IMMUTABLE, D3D11_BIND_VERTEX_BUFFER };
    D3D11_SUBRESOURCE_DATA qsd = { quad };
    hr = g_device->CreateBuffer(&qbd, &qsd, &g_quadVB);
    if (FAILED(hr)) { bb->Release(); return JNI_FALSE; }

    D3D11_BUFFER_DESC ibd = { sizeof(InstanceData) * MAX_INSTANCES, D3D11_USAGE_DYNAMIC, D3D11_BIND_VERTEX_BUFFER, D3D11_CPU_ACCESS_WRITE };
    hr = g_device->CreateBuffer(&ibd, NULL, &g_instanceVB);
    if (FAILED(hr)) { bb->Release(); return JNI_FALSE; }

    D3D11_BUFFER_DESC cbd = { sizeof(ConstantBuffer), D3D11_USAGE_DYNAMIC, D3D11_BIND_CONSTANT_BUFFER, D3D11_CPU_ACCESS_WRITE };
    hr = g_device->CreateBuffer(&cbd, NULL, &g_cb);
    if (FAILED(hr)) { bb->Release(); return JNI_FALSE; }

    D3D11_BLEND_DESC bld = {};
    bld.RenderTarget[0].BlendEnable = TRUE;
    bld.RenderTarget[0].SrcBlend = D3D11_BLEND_SRC_ALPHA;
    bld.RenderTarget[0].DestBlend = D3D11_BLEND_INV_SRC_ALPHA;
    bld.RenderTarget[0].BlendOp = D3D11_BLEND_OP_ADD;
    bld.RenderTarget[0].SrcBlendAlpha = D3D11_BLEND_ONE;
    bld.RenderTarget[0].DestBlendAlpha = D3D11_BLEND_INV_SRC_ALPHA;
    bld.RenderTarget[0].BlendOpAlpha = D3D11_BLEND_OP_ADD;
    bld.RenderTarget[0].RenderTargetWriteMask = D3D11_COLOR_WRITE_ENABLE_ALL;
    hr = g_device->CreateBlendState(&bld, &g_bsAlpha);
    if (FAILED(hr)) { bb->Release(); return JNI_FALSE; }

    D3D11_RASTERIZER_DESC rd = {}; rd.FillMode = D3D11_FILL_SOLID; rd.CullMode = D3D11_CULL_NONE;
    hr = g_device->CreateRasterizerState(&rd, &g_rs);
    if (FAILED(hr)) { bb->Release(); return JNI_FALSE; }

    D3D11_SAMPLER_DESC sd_smp = {};
    sd_smp.Filter = D3D11_FILTER_MIN_MAG_MIP_LINEAR;
    sd_smp.AddressU = sd_smp.AddressV = sd_smp.AddressW = D3D11_TEXTURE_ADDRESS_CLAMP;
    sd_smp.ComparisonFunc = D3D11_COMPARISON_NEVER;
    sd_smp.MaxLOD = D3D11_FLOAT32_MAX;
    g_device->CreateSamplerState(&sd_smp, &g_samplerLinear);
    sd_smp.Filter = D3D11_FILTER_MIN_MAG_MIP_POINT;
    g_device->CreateSamplerState(&sd_smp, &g_samplerPoint);

    D2D1CreateFactory(D2D1_FACTORY_TYPE_SINGLE_THREADED, __uuidof(ID2D1Factory1), (void**)&g_d2dFactory);
    DWriteCreateFactory(DWRITE_FACTORY_TYPE_SHARED, __uuidof(IDWriteFactory), (IUnknown**)&g_dwFactory);

    IDXGIDevice* dxgiDevice;
    g_device->QueryInterface(__uuidof(IDXGIDevice), (void**)&dxgiDevice);
    ID2D1Device* d2dDevice;
    g_d2dFactory->CreateDevice(dxgiDevice, &d2dDevice);
    d2dDevice->CreateDeviceContext(D2D1_DEVICE_CONTEXT_OPTIONS_NONE, &g_d2dContext);
    g_d2dContext->CreateSolidColorBrush(D2D1::ColorF(D2D1::ColorF::White), &g_textBrush);

    IDXGISurface* surf;
    bb->QueryInterface(__uuidof(IDXGISurface), (void**)&surf);
    ID2D1Bitmap1* target;
    g_d2dContext->CreateBitmapFromDxgiSurface(surf, NULL, &target);
    g_d2dContext->SetTarget(target);

    target->Release(); surf->Release(); bb->Release();
    d2dDevice->Release(); dxgiDevice->Release();
    
    return JNI_TRUE;
}

JNIEXPORT void JNICALL Java_fastgraphics_FastGraphicsEngine_destroy(JNIEnv*, jobject) {
    if (g_textBrush) g_textBrush->Release();
    if (g_d2dContext) g_d2dContext->Release();
    if (g_dwFactory) g_dwFactory->Release();
    if (g_d2dFactory) g_d2dFactory->Release();
    if (g_samplerLinear) g_samplerLinear->Release();
    if (g_samplerPoint) g_samplerPoint->Release();
    if (g_bsAlpha) g_bsAlpha->Release();
    if (g_rs) g_rs->Release();
    if (g_layout) g_layout->Release();
    if (g_ps) g_ps->Release();
    if (g_vs) g_vs->Release();
    if (g_instanceVB) g_instanceVB->Release();
    if (g_quadVB) g_quadVB->Release();
    if (g_cb) g_cb->Release();
    if (g_rtv) g_rtv->Release();
    if (g_swapChain) g_swapChain->Release();
    if (g_context) g_context->Release();
    if (g_device) g_device->Release();
    
    g_textBrush = nullptr; g_d2dContext = nullptr; g_dwFactory = nullptr; g_d2dFactory = nullptr;
    g_samplerLinear = nullptr; g_samplerPoint = nullptr; g_bsAlpha = nullptr; g_rs = nullptr;
    g_layout = nullptr; g_ps = nullptr; g_vs = nullptr; g_instanceVB = nullptr;
    g_quadVB = nullptr; g_cb = nullptr; g_rtv = nullptr; g_swapChain = nullptr;
    g_context = nullptr; g_device = nullptr;
}

JNIEXPORT void JNICALL Java_fastgraphics_FastGraphicsEngine_clear(JNIEnv*, jobject, jfloat r, jfloat g, jfloat b, jfloat a) {
    float color[4] = { r, g, b, a };
    g_context->ClearRenderTargetView(g_rtv, color);
}

JNIEXPORT void JNICALL Java_fastgraphics_FastGraphicsEngine_setAntialiasing(JNIEnv*, jobject, jboolean enabled) {
    g_aaEnabled = (bool)enabled;
    if (g_d2dContext) {
        g_d2dContext->SetTextAntialiasMode(g_aaEnabled ? D2D1_TEXT_ANTIALIAS_MODE_GRAYSCALE : D2D1_TEXT_ANTIALIAS_MODE_ALIASED);
    }
}

JNIEXPORT void JNICALL Java_fastgraphics_FastGraphicsEngine_render(JNIEnv* env, jobject, jobject buffer, jint count, jfloatArray transform) {
    if (!g_context || count <= 0) return;
    RECT rc; GetClientRect(g_hwnd, &rc);
    int w = rc.right - rc.left; int h = rc.bottom - rc.top;
    if (w <= 0 || h <= 0) return;

    if (w != g_renderWidth || h != g_renderHeight) {
        if (g_d2dContext) g_d2dContext->SetTarget(nullptr);
        if (g_rtv) g_rtv->Release();
        g_swapChain->ResizeBuffers(0, w, h, DXGI_FORMAT_UNKNOWN, 0);
        ID3D11Texture2D* bb; g_swapChain->GetBuffer(0, __uuidof(ID3D11Texture2D), (void**)&bb);
        g_device->CreateRenderTargetView(bb, NULL, &g_rtv);
        IDXGISurface* surf; bb->QueryInterface(__uuidof(IDXGISurface), (void**)&surf);
        ID2D1Bitmap1* target; g_d2dContext->CreateBitmapFromDxgiSurface(surf, NULL, &target);
        g_d2dContext->SetTarget(target);
        target->Release(); surf->Release(); bb->Release();
        g_renderWidth = w; g_renderHeight = h;
    }

    jfloat* trans = env->GetFloatArrayElements(transform, NULL);
    XMMATRIX user = XMLoadFloat4x4((XMFLOAT4X4*)trans);
    XMMATRIX proj = XMMatrixOrthographicOffCenterLH(0, (float)w, (float)h, 0, 0, 1);
    env->ReleaseFloatArrayElements(transform, trans, JNI_ABORT);

    D3D11_MAPPED_SUBRESOURCE ms;
    g_context->Map(g_cb, 0, D3D11_MAP_WRITE_DISCARD, 0, &ms);
    ConstantBuffer* cb = (ConstantBuffer*)ms.pData;
    cb->transform = XMMatrixTranspose(user * proj);
    cb->smoothness = g_aaEnabled ? 1.0f : 0.0f;
    g_context->Unmap(g_cb, 0);

    g_context->OMSetRenderTargets(1, &g_rtv, NULL);
    D3D11_VIEWPORT vp = { 0, 0, (float)w, (float)h, 0, 1 };
    g_context->RSSetViewports(1, &vp);
    g_context->RSSetState(g_rs);
    g_context->OMSetBlendState(g_bsAlpha, NULL, 0xffffffff);
    g_context->IASetInputLayout(g_layout);
    g_context->IASetPrimitiveTopology(D3D11_PRIMITIVE_TOPOLOGY_TRIANGLESTRIP);
    UINT qstride = 8, stride = sizeof(InstanceData), offset = 0;
    g_context->IASetVertexBuffers(0, 1, &g_quadVB, &qstride, &offset);
    g_context->IASetVertexBuffers(1, 1, &g_instanceVB, &stride, &offset);
    g_context->VSSetShader(g_vs, NULL, 0);
    g_context->VSSetConstantBuffers(0, 1, &g_cb);
    g_context->PSSetShader(g_ps, NULL, 0);
    g_context->PSSetConstantBuffers(0, 1, &g_cb);
    ID3D11SamplerState* samplers[] = { g_aaEnabled ? g_samplerLinear : g_samplerPoint };
    g_context->PSSetSamplers(0, 1, samplers);
    void* data = env->GetDirectBufferAddress(buffer);
    g_context->Map(g_instanceVB, 0, D3D11_MAP_WRITE_DISCARD, 0, &ms);
    memcpy(ms.pData, data, count * sizeof(InstanceData));
    g_context->Unmap(g_instanceVB, 0);
    g_context->DrawInstanced(4, count, 0, 0);
}

JNIEXPORT void JNICALL Java_fastgraphics_FastGraphicsEngine_present(JNIEnv*, jobject) {
    g_swapChain->Present(0, 0);
}

JNIEXPORT void JNICALL Java_fastgraphics_FastGraphicsEngine_drawString(JNIEnv* env, jobject, jstring text, jfloat x, jfloat y, jfloat size, jfloat r, jfloat g, jfloat b, jfloat a) {
    const jchar* jstr = env->GetStringChars(text, NULL); jsize len = env->GetStringLength(text);
    IDWriteTextFormat* format; g_dwFactory->CreateTextFormat(L"Segoe UI", NULL, DWRITE_FONT_WEIGHT_NORMAL, DWRITE_FONT_STYLE_NORMAL, DWRITE_FONT_STRETCH_NORMAL, size, L"en-us", &format);
    
    // AWT drawString(x, y) uses y as the baseline.
    // D2D DrawText uses y as the top.
    // Approximate ascent as 80% of size for Segoe UI.
    float adjustedY = y - (size * 0.8f);

    g_d2dContext->BeginDraw();
    if (g_textBrush) g_textBrush->SetColor(D2D1::ColorF(r, g, b, a));
    g_d2dContext->DrawText((const WCHAR*)jstr, len, format, D2D1::RectF(x, adjustedY, x + 2000, adjustedY + 1000), g_textBrush);
    g_d2dContext->EndDraw();
    format->Release(); env->ReleaseStringChars(text, jstr);
}

JNIEXPORT jint JNICALL Java_fastgraphics_FastGraphicsEngine_loadTexture(JNIEnv* env, jobject, jintArray pixels, jint w, jint h) {
    jint* data = env->GetIntArrayElements(pixels, NULL);
    D3D11_TEXTURE2D_DESC td = { (UINT)w, (UINT)h, 1, 1, DXGI_FORMAT_B8G8R8A8_UNORM, {1, 0}, D3D11_USAGE_IMMUTABLE, D3D11_BIND_SHADER_RESOURCE };
    D3D11_SUBRESOURCE_DATA sd = { data, (UINT)(w * 4) };
    ID3D11Texture2D* tex; g_device->CreateTexture2D(&td, &sd, &tex);
    env->ReleaseIntArrayElements(pixels, data, JNI_ABORT);
    ID3D11ShaderResourceView* srv; g_device->CreateShaderResourceView(tex, NULL, &srv);
    tex->Release(); int id = g_nextTextureId++; g_textures[id] = { srv, w, h }; return id;
}

JNIEXPORT void JNICALL Java_fastgraphics_FastGraphicsEngine_destroyTexture(JNIEnv*, jobject, jint id) {
    if (g_textures.count(id)) { g_textures[id].srv->Release(); g_textures.erase(id); }
}

JNIEXPORT void JNICALL Java_fastgraphics_FastGraphicsEngine_drawImage(JNIEnv* env, jobject, jint textureId, jfloat x, jfloat y, jfloat w, jfloat h, jfloat alpha) {
    if (!g_textures.count(textureId)) return;
    TextureEntry& entry = g_textures[textureId];
    g_context->PSSetShaderResources(0, 1, &entry.srv);
    ID3D11SamplerState* samplers[] = { g_aaEnabled ? g_samplerLinear : g_samplerPoint };
    g_context->PSSetSamplers(0, 1, samplers);
    InstanceData inst = { {x, y}, {w, h}, {1,1,1,1}, {0, alpha, 9, 0} };
    D3D11_MAPPED_SUBRESOURCE ms; g_context->Map(g_instanceVB, 0, D3D11_MAP_WRITE_DISCARD, 0, &ms);
    memcpy(ms.pData, &inst, sizeof(InstanceData)); g_context->Unmap(g_instanceVB, 0);
    g_context->DrawInstanced(4, 1, 0, 0);
}

}
