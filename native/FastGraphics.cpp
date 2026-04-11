/**
 * FastGraphics - GPU-accelerated 2D Rendering for Java
 * DirectX 11 Implementation
 */

#include <jni.h>
#include <windows.h>
#include <d3d11.h>
#include <d3dcompiler.h>
#include <math.h>

#pragma comment(lib, "d3d11.lib")
#pragma comment(lib, "d3dcompiler.lib")
#pragma comment(lib, "user32.lib")

static ID3D11Device* g_device = nullptr;
static ID3D11DeviceContext* g_context = nullptr;
static IDXGISwapChain* g_swapChain = nullptr;
static ID3D11RenderTargetView* g_rtv = nullptr;
static ID3D11Buffer* g_vb = nullptr;
static ID3D11VertexShader* g_vs = nullptr;
static ID3D11PixelShader* g_ps = nullptr;
static ID3D11InputLayout* g_layout = nullptr;

// Texture management
static ID3D11VertexShader* g_vsTextured = nullptr;
static ID3D11PixelShader* g_psTextured = nullptr;
static ID3D11InputLayout* g_layoutTextured = nullptr;
static ID3D11Buffer* g_texturedVB = nullptr;
static ID3D11SamplerState* g_sampler = nullptr;

// Texture cache: ID -> {texture, srv}
struct TextureEntry {
    ID3D11Texture2D* texture;
    ID3D11ShaderResourceView* srv;
};
#include <map>
static std::map<int, TextureEntry> g_textureCache;
static int g_nextTextureId = 1;

// Standard vertex shader
const char* VS_SRC = R"(
    struct VS_INPUT {
        float2 pos : POSITION;
        float3 color : COLOR;
    };
    struct VS_OUTPUT {
        float4 pos : SV_POSITION;
        float3 color : COLOR;
    };
    VS_OUTPUT main(VS_INPUT input) {
        VS_OUTPUT output;
        output.pos = float4(input.pos, 0.0, 1.0);
        output.color = input.color;
        return output;
    }
)";

// Instanced vertex shader with pixel-perfect coordinates
const char* VS_INSTANCED_SRC = R"(
    struct VS_INPUT {
        float2 quadPos : POSITION;
    };
    struct VS_INSTANCE {
        float2 pos : INSTANCE_POS;
        float2 size : INSTANCE_SIZE;
        float3 color : INSTANCE_COLOR;
    };
    struct VS_OUTPUT {
        float4 pos : SV_POSITION;
        float3 color : COLOR;
    };
    cbuffer ScreenCB : register(b0) {
        float2 screenSize;
    };
    VS_OUTPUT main(VS_INPUT input, VS_INSTANCE instance) {
        VS_OUTPUT output;
        // Java2D-compatible coordinates: Top-Left origin, Y-down
        float2 pixelPos = instance.pos + input.quadPos * instance.size;
        
        // Convert pixel coordinates to clip space
        // Java2D: (0,0) = Top-Left, (width,height) = Bottom-Right
        // DirectX: (-1,-1) = Bottom-Left, (1,1) = Top-Right
        
        float2 clipPos;
        // X: [0, width] -> [-1, 1], pixel-center offset
        clipPos.x = ((pixelPos.x + 0.5) / screenSize.x) * 2.0 - 1.0;
        // Y: [0, height] -> [1, -1] (flip for Top-Left origin), pixel-center offset  
        clipPos.y = 1.0 - ((pixelPos.y + 0.5) / screenSize.y) * 2.0;
        
        output.pos = float4(clipPos, 0.0, 1.0);
        output.color = instance.color;
        return output;
    }
)";

const char* PS_SRC = R"(
    struct PS_INPUT {
        float4 pos : SV_POSITION;
        float3 color : COLOR;
    };
    float4 main(PS_INPUT input) : SV_TARGET {
        return float4(input.color, 1.0);
    }
)";

// Textured shaders for image rendering
const char* VS_TEXTURED_SRC = R"(
    struct VS_INPUT {
        float2 pos : POSITION;
        float2 uv : TEXCOORD;
    };
    struct VS_OUTPUT {
        float4 pos : SV_POSITION;
        float2 uv : TEXCOORD;
    };
    cbuffer ScreenCB : register(b0) {
        float2 screenSize;
    };
    VS_OUTPUT main(VS_INPUT input) {
        VS_OUTPUT output;
        float2 pixelPos = input.pos;
        float2 clipPos;
        clipPos.x = ((pixelPos.x + 0.5) / screenSize.x) * 2.0 - 1.0;
        clipPos.y = 1.0 - ((pixelPos.y + 0.5) / screenSize.y) * 2.0;
        output.pos = float4(clipPos, 0.0, 1.0);
        output.uv = input.uv;
        return output;
    }
)";

const char* PS_TEXTURED_SRC = R"(
    struct PS_INPUT {
        float4 pos : SV_POSITION;
        float2 uv : TEXCOORD;
    };
    Texture2D tex : register(t0);
    SamplerState samp : register(s0);
    float4 main(PS_INPUT input) : SV_TARGET {
        return tex.Sample(samp, input.uv);
    }
)";

bool CompileShader(const char* src, const char* entry, const char* target, ID3DBlob** blob) {
    ID3DBlob* err = nullptr;
    if (FAILED(D3DCompile(src, strlen(src), nullptr, nullptr, nullptr, entry, target, 0, 0, blob, &err))) {
        if (err) err->Release();
        return false;
    }
    return true;
}

float ToNDC_X(float x, float w) { return (x / w) * 2.0f - 1.0f; }
float ToNDC_Y(float y, float h) { return 1.0f - (y / h) * 2.0f; }

// Transformation state
static float g_translateX = 0.0f;
static float g_translateY = 0.0f;
static float g_scaleX = 1.0f;
static float g_scaleY = 1.0f;
static float g_rotation = 0.0f;

// Line width
static float g_lineWidth = 1.0f;

// Clipping state
static float g_clipX = 0.0f;
static float g_clipY = 0.0f;
static float g_clipW = 0.0f;
static float g_clipH = 0.0f;
static bool g_clipEnabled = false;

// Apply transformation to point
static void ApplyTransform(float* x, float* y) {
    *x *= g_scaleX;
    *y *= g_scaleY;
    
    if (g_rotation != 0.0f) {
        float rad = g_rotation * 3.14159265f / 180.0f;
        float cosr = cosf(rad);
        float sinr = sinf(rad);
        float newX = *x * cosr - *y * sinr;
        float newY = *x * sinr + *y * cosr;
        *x = newX;
        *y = newY;
    }
    
    *x += g_translateX;
    *y += g_translateY;
}

extern "C" {

JNIEXPORT void JNICALL Java_demo_DemoApp_init(JNIEnv*, jclass, jlong hwnd) {
    fprintf(stderr, "[FastGraphics] DemoApp_init: hwnd=%lld\n", hwnd);
    HWND h = (HWND)hwnd;
    fprintf(stderr, "[FastGraphics] DemoApp_init: calling GetClientRect...\n");
    RECT rc; 
    if (!GetClientRect(h, &rc)) {
        fprintf(stderr, "[FastGraphics] DemoApp_init: GetClientRect FAILED!\n");
        return;
    }
    int w = rc.right - rc.left, h2 = rc.bottom - rc.top;
    fprintf(stderr, "[FastGraphics] DemoApp_init: client size %dx%d\n", w, h2);
    
    DXGI_SWAP_CHAIN_DESC sd = {};
    sd.BufferCount = 1;
    sd.BufferDesc.Width = w; sd.BufferDesc.Height = h2;
    sd.BufferDesc.Format = DXGI_FORMAT_R8G8B8A8_UNORM;
    sd.BufferDesc.RefreshRate.Numerator = 60;
    sd.BufferDesc.RefreshRate.Denominator = 1;
    sd.BufferUsage = DXGI_USAGE_RENDER_TARGET_OUTPUT;
    sd.OutputWindow = h;
    sd.SampleDesc.Count = 1;
    sd.SampleDesc.Quality = 0;
    sd.Windowed = TRUE;
    sd.SwapEffect = DXGI_SWAP_EFFECT_DISCARD;
    
    fprintf(stderr, "[FastGraphics] DemoApp_init: creating D3D11 device...\n");
    auto fl = D3D_FEATURE_LEVEL_11_0;
    HRESULT hr = D3D11CreateDeviceAndSwapChain(
        nullptr, D3D_DRIVER_TYPE_HARDWARE, nullptr, 0,
        &fl, 1, D3D11_SDK_VERSION, &sd,
        &g_swapChain, &g_device, nullptr, &g_context);
    if (FAILED(hr)) {
        fprintf(stderr, "[FastGraphics] DemoApp_init: D3D11CreateDeviceAndSwapChain FAILED! hr=0x%08X\n", hr);
        return;
    }
    fprintf(stderr, "[FastGraphics] DemoApp_init: D3D11 device created OK\n");
    
    ID3D11Texture2D* backBuffer = nullptr;
    fprintf(stderr, "[FastGraphics] DemoApp_init: getting backbuffer...\n");
    HRESULT hr2 = g_swapChain->GetBuffer(0, __uuidof(ID3D11Texture2D), (void**)&backBuffer);
    if (FAILED(hr2) || !backBuffer) {
        fprintf(stderr, "[FastGraphics] DemoApp_init: GetBuffer FAILED! hr=0x%08X, backBuffer=%p\n", hr2, backBuffer);
        return;
    }
    fprintf(stderr, "[FastGraphics] DemoApp_init: got backbuffer OK\n");
    fprintf(stderr, "[FastGraphics] DemoApp_init: creating RTV...\n");
    if (!g_device) {
        fprintf(stderr, "[FastGraphics] DemoApp_init: ERROR g_device is null!\n");
        return;
    }
    HRESULT hr3 = g_device->CreateRenderTargetView(backBuffer, nullptr, &g_rtv);
    if (FAILED(hr3)) {
        fprintf(stderr, "[FastGraphics] DemoApp_init: CreateRenderTargetView FAILED! hr=0x%08X\n", hr3);
        backBuffer->Release();
        return;
    }
    fprintf(stderr, "[FastGraphics] DemoApp_init: RTV created OK\n");
    backBuffer->Release();
    
    fprintf(stderr, "[FastGraphics] DemoApp_init: setting viewport...\n");
    D3D11_VIEWPORT vp = {};
    vp.Width = (FLOAT)w;
    vp.Height = (FLOAT)h2;
    vp.MinDepth = 0.0f;
    vp.MaxDepth = 1.0f;
    vp.TopLeftX = 0;
    vp.TopLeftY = 0;
    g_context->RSSetViewports(1, &vp);
    fprintf(stderr, "[FastGraphics] DemoApp_init: viewport set OK\n");
    
    fprintf(stderr, "[FastGraphics] DemoApp_init: compiling shaders...\n");
    ID3D10Blob* vsBlob = nullptr, * psBlob = nullptr;
    if (!CompileShader(VS_SRC, "main", "vs_4_0", &vsBlob)) {
        fprintf(stderr, "[FastGraphics] DemoApp_init: VS compile FAILED!\n");
        return;
    }
    fprintf(stderr, "[FastGraphics] DemoApp_init: VS compiled OK\n");
    if (!CompileShader(PS_SRC, "main", "ps_4_0", &psBlob)) {
        fprintf(stderr, "[FastGraphics] DemoApp_init: PS compile FAILED!\n");
        vsBlob->Release();
        return;
    }
    fprintf(stderr, "[FastGraphics] DemoApp_init: PS compiled OK\n");
    g_device->CreateVertexShader(vsBlob->GetBufferPointer(), vsBlob->GetBufferSize(), nullptr, &g_vs);
    g_device->CreatePixelShader(psBlob->GetBufferPointer(), psBlob->GetBufferSize(), nullptr, &g_ps);
    
    // Input layout for standard shader (position + color)
    D3D11_INPUT_ELEMENT_DESC ied[] = {
        { "POSITION", 0, DXGI_FORMAT_R32G32_FLOAT, 0, 0, D3D11_INPUT_PER_VERTEX_DATA, 0 },
        { "COLOR", 0, DXGI_FORMAT_R32G32B32_FLOAT, 0, 8, D3D11_INPUT_PER_VERTEX_DATA, 0 }
    };
    g_device->CreateInputLayout(ied, 2, vsBlob->GetBufferPointer(), vsBlob->GetBufferSize(), &g_layout);
    
    vsBlob->Release();
    psBlob->Release();
    
    g_context->VSSetShader(g_vs, nullptr, 0);
    g_context->PSSetShader(g_ps, nullptr, 0);
    g_context->IASetInputLayout(g_layout);
    D3D11_VIEWPORT vp2 = { 0, 0, (float)w, (float)h2, 0, 1 };
    g_context->RSSetViewports(1, &vp2);
}

JNIEXPORT void JNICALL Java_demo_DemoApp_fillRect(JNIEnv*, jclass,
    jfloat x, jfloat y, jfloat width, jfloat height, jfloat r, jfloat g, jfloat b) {
    if (!g_device) return;
    
    D3D11_VIEWPORT vp; UINT num = 1; g_context->RSGetViewports(&num, &vp);
    g_context->OMSetRenderTargets(1, &g_rtv, nullptr);
    
    // Set shaders
    g_context->VSSetShader(g_vs, nullptr, 0);
    g_context->PSSetShader(g_ps, nullptr, 0);
    g_context->IASetInputLayout(g_layout);
    
    float x1 = ToNDC_X(x, vp.Width), y1 = ToNDC_Y(y, vp.Height);
    float x2 = ToNDC_X(x + width, vp.Width), y2 = ToNDC_Y(y + height, vp.Height);
    
    float vertices[] = {
        x1, y1, r, g, b,
        x2, y1, r, g, b,
        x1, y2, r, g, b,
        x1, y2, r, g, b,
        x2, y1, r, g, b,
        x2, y2, r, g, b
    };
    
    if (g_vb) g_vb->Release();
    D3D11_BUFFER_DESC bd = { sizeof(vertices), D3D11_USAGE_DEFAULT, D3D11_BIND_VERTEX_BUFFER };
    D3D11_SUBRESOURCE_DATA sd = { vertices };
    g_device->CreateBuffer(&bd, &sd, &g_vb);
    
    UINT stride = 20, offset = 0;
    g_context->IASetVertexBuffers(0, 1, &g_vb, &stride, &offset);
    g_context->IASetPrimitiveTopology(D3D11_PRIMITIVE_TOPOLOGY_TRIANGLELIST);
    g_context->Draw(6, 0);
}

JNIEXPORT void JNICALL Java_demo_DemoApp_clear(JNIEnv*, jclass, jfloat r, jfloat g, jfloat b) {
    if (g_context && g_rtv) {
        float clear[4] = { r, g, b, 1.0f };
        g_context->ClearRenderTargetView(g_rtv, clear);
        g_context->OMSetRenderTargets(1, &g_rtv, nullptr);
    }
}

JNIEXPORT void JNICALL Java_demo_DemoApp_present(JNIEnv*, jclass) {
    if (g_swapChain) g_swapChain->Present(0, 0);
}

// renderBatch implementation for FastGraphics2D
JNIEXPORT void JNICALL Java_fastgraphics_FastGraphics2D_renderBatch(JNIEnv* env, jclass, jfloatArray vertices, jint count) {
    if (!g_device || !g_context || !g_rtv) return;
    
    jfloat* verts = env->GetFloatArrayElements(vertices, nullptr);
    if (!verts) return;
    
    // Set render target
    g_context->OMSetRenderTargets(1, &g_rtv, nullptr);
    
    // Create/update vertex buffer
    if (g_vb) g_vb->Release();
    D3D11_BUFFER_DESC bd = {};
    bd.ByteWidth = count * 5 * sizeof(float); // count vertices * 5 floats (x,y,r,g,b)
    bd.Usage = D3D11_USAGE_DEFAULT;
    bd.BindFlags = D3D11_BIND_VERTEX_BUFFER;
    D3D11_SUBRESOURCE_DATA sd = { verts };
    g_device->CreateBuffer(&bd, &sd, &g_vb);
    
    env->ReleaseFloatArrayElements(vertices, verts, JNI_ABORT);
    
    // Setup input layout and shaders
    g_context->IASetInputLayout(g_layout);
    g_context->VSSetShader(g_vs, nullptr, 0);
    g_context->PSSetShader(g_ps, nullptr, 0);
    
    // Set vertex buffer
    UINT stride = 5 * sizeof(float);
    UINT offset = 0;
    g_context->IASetVertexBuffers(0, 1, &g_vb, &stride, &offset);
    g_context->IASetPrimitiveTopology(D3D11_PRIMITIVE_TOPOLOGY_TRIANGLELIST);
    
    // Draw
    g_context->Draw(count, 0);
}

JNIEXPORT jlong JNICALL Java_demo_DemoApp_findWindow(JNIEnv* env, jclass, jstring title) {
    const char* str = nullptr;
    if (title) str = env->GetStringUTFChars(title, nullptr);
    HWND hwnd = FindWindowA(nullptr, str);
    if (title && str) env->ReleaseStringUTFChars(title, str);
    return (jlong)hwnd;
}

// Batch rendering with instanced rendering

static ID3D11Buffer* g_batchVB = nullptr;
static size_t g_batchVBSize = 0;
static const size_t MAX_BATCH_RECTS = 100000;

// Vertex ring buffer (fallback)
static float g_vertexRingBuffer[MAX_BATCH_RECTS * 6 * 5];

// Instanced rendering
static ID3D11Buffer* g_quadVB = nullptr;
static ID3D11Buffer* g_instanceVB = nullptr;
static ID3D11Buffer* g_screenCB = nullptr;
static ID3D11VertexShader* g_vsInstanced = nullptr;
static ID3D11InputLayout* g_layoutInstanced = nullptr;
static size_t g_instanceVBSize = 0;

// Instance data: x, y, w, h, r, g, b (7 floats)
static float g_instanceData[MAX_BATCH_RECTS * 7];

// Static quad (0,0 to 1,1)
static float g_quadVertices[] = {
    0.0f, 0.0f,
    1.0f, 0.0f,
    0.0f, 1.0f,
    1.0f, 1.0f
};

// Instanced batch rendering

JNIEXPORT void JNICALL Java_demo_DemoApp_fillRects(JNIEnv* env, jclass, 
    jobject rectDataBuffer, jint count) {
    if (!g_device || count == 0) return;
    if (count > (int)MAX_BATCH_RECTS) count = (int)MAX_BATCH_RECTS;
    
    // Get direct buffer address
    float* rects = (float*)env->GetDirectBufferAddress(rectDataBuffer);
    if (!rects) return;
    
    D3D11_VIEWPORT vp; UINT num = 1;
    if (g_context) g_context->RSGetViewports(&num, &vp);
    float vw = (float)vp.Width;
    float vh = (float)vp.Height;
    
    // Instanced rendering setup (one-time)
    if (!g_quadVB) {
        // Create quad buffer (static)
        D3D11_BUFFER_DESC qbd = {};
        qbd.Usage = D3D11_USAGE_IMMUTABLE;
        qbd.ByteWidth = sizeof(g_quadVertices);
        qbd.BindFlags = D3D11_BIND_VERTEX_BUFFER;
        D3D11_SUBRESOURCE_DATA qsd = { g_quadVertices };
        g_device->CreateBuffer(&qbd, &qsd, &g_quadVB);
        
        // Compile instanced shader
        ID3DBlob* vsBlob = nullptr;
        if (CompileShader(VS_INSTANCED_SRC, "main", "vs_4_0", &vsBlob)) {
            g_device->CreateVertexShader(vsBlob->GetBufferPointer(), vsBlob->GetBufferSize(), nullptr, &g_vsInstanced);
            
            // Input layout for instanced rendering
            D3D11_INPUT_ELEMENT_DESC layout[] = {
                { "POSITION", 0, DXGI_FORMAT_R32G32_FLOAT, 0, 0, D3D11_INPUT_PER_VERTEX_DATA, 0 },
                { "INSTANCE_POS", 0, DXGI_FORMAT_R32G32_FLOAT, 1, 0, D3D11_INPUT_PER_INSTANCE_DATA, 1 },
                { "INSTANCE_SIZE", 0, DXGI_FORMAT_R32G32_FLOAT, 1, 8, D3D11_INPUT_PER_INSTANCE_DATA, 1 },
                { "INSTANCE_COLOR", 0, DXGI_FORMAT_R32G32B32_FLOAT, 1, 16, D3D11_INPUT_PER_INSTANCE_DATA, 1 }
            };
            g_device->CreateInputLayout(layout, 4, vsBlob->GetBufferPointer(), vsBlob->GetBufferSize(), &g_layoutInstanced);
            vsBlob->Release();
        }
        
        // Create screen constant buffer
        D3D11_BUFFER_DESC cbd = {};
        cbd.Usage = D3D11_USAGE_DYNAMIC;
        cbd.ByteWidth = 16;
        cbd.BindFlags = D3D11_BIND_CONSTANT_BUFFER;
        cbd.CPUAccessFlags = D3D11_CPU_ACCESS_WRITE;
        g_device->CreateBuffer(&cbd, nullptr, &g_screenCB);
    }
    
    // Prepare instance data
    size_t instanceDataSize = count * 7 * sizeof(float);
    memcpy(g_instanceData, rects, instanceDataSize);
    
    // Persistent instance buffer
    if (!g_instanceVB || instanceDataSize > g_instanceVBSize) {
        if (g_instanceVB) g_instanceVB->Release();
        g_instanceVBSize = instanceDataSize;
        
        D3D11_BUFFER_DESC ibd = {};
        ibd.Usage = D3D11_USAGE_DYNAMIC;
        ibd.ByteWidth = instanceDataSize;
        ibd.BindFlags = D3D11_BIND_VERTEX_BUFFER;
        ibd.CPUAccessFlags = D3D11_CPU_ACCESS_WRITE;
        g_device->CreateBuffer(&ibd, nullptr, &g_instanceVB);
    }
    
    // Map instance data
    D3D11_MAPPED_SUBRESOURCE mapped;
    if (SUCCEEDED(g_context->Map(g_instanceVB, 0, D3D11_MAP_WRITE_DISCARD, 0, &mapped))) {
        memcpy(mapped.pData, g_instanceData, instanceDataSize);
        g_context->Unmap(g_instanceVB, 0);
    }
    
    // Update screen constant buffer
    if (SUCCEEDED(g_context->Map(g_screenCB, 0, D3D11_MAP_WRITE_DISCARD, 0, &mapped))) {
        float* cbData = (float*)mapped.pData;
        cbData[0] = vw;
        cbData[1] = vh;
        g_context->Unmap(g_screenCB, 0);
    }
    
    // Instanced rendering
    g_context->OMSetRenderTargets(1, &g_rtv, nullptr);
    g_context->VSSetShader(g_vsInstanced, nullptr, 0);
    g_context->VSSetConstantBuffers(0, 1, &g_screenCB);
    g_context->PSSetShader(g_ps, nullptr, 0);
    g_context->IASetInputLayout(g_layoutInstanced);
    
    // Bind vertex buffers
    ID3D11Buffer* buffers[] = { g_quadVB, g_instanceVB };
    UINT strides[] = { 8, 28 };
    UINT offsets[] = { 0, 0 };
    g_context->IASetVertexBuffers(0, 2, buffers, strides, offsets);
    g_context->IASetPrimitiveTopology(D3D11_PRIMITIVE_TOPOLOGY_TRIANGLESTRIP);
    
    // Draw 4 vertices per instance
    g_context->DrawInstanced(4, count, 0, 0);
}

// Circle rendering

#ifndef D3D11_PRIMITIVE_TOPOLOGY_TRIANGLEFAN
#define D3D11_PRIMITIVE_TOPOLOGY_TRIANGLEFAN 5
#endif

#ifndef D3D11_PRIMITIVE_TOPOLOGY_LINESTRIP
#define D3D11_PRIMITIVE_TOPOLOGY_LINESTRIP 3
#endif

static ID3D11Buffer* g_circleVB = nullptr;

JNIEXPORT void JNICALL Java_demo_DemoApp_drawCircles(JNIEnv* env, jclass,
    jobject circleData, jint count) {
    if (!g_device || count == 0) return;
    
    // circleData: x, y, radius, r, g, b per circle (6 floats)
    float* circles = (float*)env->GetDirectBufferAddress(circleData);
    if (!circles) return;
    
    D3D11_VIEWPORT vp; UINT num = 1;
    if (g_context) g_context->RSGetViewports(&num, &vp);
    float vw = (float)vp.Width;
    float vh = (float)vp.Height;
    
    const int segments = 64;
    const int vertsPerCircle = segments + 1;
    float* vertices = new float[count * vertsPerCircle * 5]; // x, y, r, g, b
    
    int vIdx = 0;
    for (int i = 0; i < count; i++) {
        float cx = circles[i * 6 + 0];
        float cy = circles[i * 6 + 1];
        float radius = circles[i * 6 + 2];
        float cr = circles[i * 6 + 3];
        float cg = circles[i * 6 + 4];
        float cb = circles[i * 6 + 5];
        
        // Calculate circle edge points
        for (int s = 0; s <= segments; s++) {
            float angle = (float)s / segments * 3.14159265f * 2.0f;
            float x = cx + radius * cosf(angle);
            float y = cy + radius * sinf(angle);
            
            vertices[vIdx++] = ToNDC_X(x, vw);
            vertices[vIdx++] = ToNDC_Y(y, vh);
            vertices[vIdx++] = cr;
            vertices[vIdx++] = cg;
            vertices[vIdx++] = cb;
        }
    }
    
    // Create vertex buffer
    size_t bufferSize = count * vertsPerCircle * 5 * sizeof(float);
    if (g_circleVB) g_circleVB->Release();
    
    D3D11_BUFFER_DESC bd = {};
    bd.Usage = D3D11_USAGE_DEFAULT;
    bd.ByteWidth = bufferSize;
    bd.BindFlags = D3D11_BIND_VERTEX_BUFFER;
    D3D11_SUBRESOURCE_DATA sd = { vertices };
    g_device->CreateBuffer(&bd, &sd, &g_circleVB);
    delete[] vertices;
    
    // Use standard shader
    g_context->OMSetRenderTargets(1, &g_rtv, nullptr);
    g_context->VSSetShader(g_vs, nullptr, 0);
    g_context->PSSetShader(g_ps, nullptr, 0);
    g_context->IASetInputLayout(g_layout);
    
    UINT stride = 20;
    UINT offset = 0;
    g_context->IASetVertexBuffers(0, 1, &g_circleVB, &stride, &offset);
    
    g_context->IASetPrimitiveTopology((D3D11_PRIMITIVE_TOPOLOGY)D3D11_PRIMITIVE_TOPOLOGY_LINESTRIP);
    
    // Draw each circle individually (line strip)
    for (int i = 0; i < count; i++) {
        g_context->Draw(vertsPerCircle, i * vertsPerCircle);
    }
}

JNIEXPORT void JNICALL Java_fastgraphics_FastGraphics2D_clearNative(JNIEnv*, jclass, 
    jfloat r, jfloat g, jfloat b) {
    Java_demo_DemoApp_clear(nullptr, nullptr, r, g, b);
}

JNIEXPORT void JNICALL Java_fastgraphics_FastGraphics2D_presentNative(JNIEnv*, jclass) {
    Java_demo_DemoApp_present(nullptr, nullptr);
}

JNIEXPORT jlong JNICALL Java_fastgraphics_FastGraphics2D_findWindowNative(JNIEnv* env, jclass, jstring title) {
    return Java_demo_DemoApp_findWindow(env, nullptr, title);
}

JNIEXPORT void JNICALL Java_fastgraphics_FastGraphics2D_drawRectNative(JNIEnv*, jclass,
    jfloat x, jfloat y, jfloat width, jfloat height, jfloat r, jfloat g, jfloat b) {
    if (!g_device) return;

    D3D11_VIEWPORT vp; UINT num = 1; g_context->RSGetViewports(&num, &vp);
    g_context->OMSetRenderTargets(1, &g_rtv, nullptr);

    g_context->VSSetShader(g_vs, nullptr, 0);
    g_context->PSSetShader(g_ps, nullptr, 0);
    g_context->IASetInputLayout(g_layout);

    float x1 = ToNDC_X(x, vp.Width), y1 = ToNDC_Y(y, vp.Height);
    float x2 = ToNDC_X(x + width, vp.Width), y2 = ToNDC_Y(y + height, vp.Height);

    float vertices[] = {
        x1, y1, r, g, b,
        x2, y1, r, g, b,
        x2, y2, r, g, b,
        x1, y2, r, g, b,
        x1, y1, r, g, b
    };

    if (g_vb) g_vb->Release();
    D3D11_BUFFER_DESC bd = { sizeof(vertices), D3D11_USAGE_DEFAULT, D3D11_BIND_VERTEX_BUFFER };
    D3D11_SUBRESOURCE_DATA sd = { vertices };
    g_device->CreateBuffer(&bd, &sd, &g_vb);

    UINT stride = 20, offset = 0;
    g_context->IASetVertexBuffers(0, 1, &g_vb, &stride, &offset);
    g_context->IASetPrimitiveTopology((D3D11_PRIMITIVE_TOPOLOGY)D3D11_PRIMITIVE_TOPOLOGY_LINESTRIP);
    g_context->Draw(5, 0);
}

JNIEXPORT void JNICALL Java_fastgraphics_FastGraphics2D_fillRectNative(JNIEnv*, jclass,
    jfloat x, jfloat y, jfloat width, jfloat height, jfloat r, jfloat g, jfloat b) {
    if (!g_device) return;

    D3D11_VIEWPORT vp; UINT num = 1; g_context->RSGetViewports(&num, &vp);
    g_context->OMSetRenderTargets(1, &g_rtv, nullptr);

    g_context->VSSetShader(g_vs, nullptr, 0);
    g_context->PSSetShader(g_ps, nullptr, 0);
    g_context->IASetInputLayout(g_layout);

    // Apply transformation to corners
    float corners[] = { x, y, x + width, y, x, y + height, x + width, y + height };
    for (int i = 0; i < 8; i += 2) {
        ApplyTransform(&corners[i], &corners[i + 1]);
    }

    float x1 = ToNDC_X(corners[0], vp.Width), y1 = ToNDC_Y(corners[1], vp.Height);
    float x2 = ToNDC_X(corners[2], vp.Width), y2 = ToNDC_Y(corners[3], vp.Height);
    float x3 = ToNDC_X(corners[4], vp.Width), y3 = ToNDC_Y(corners[5], vp.Height);
    float x4 = ToNDC_X(corners[6], vp.Width), y4 = ToNDC_Y(corners[7], vp.Height);

    float vertices[] = {
        x1, y1, r, g, b,
        x2, y2, r, g, b,
        x3, y3, r, g, b,
        x3, y3, r, g, b,
        x2, y2, r, g, b,
        x4, y4, r, g, b
    };

    if (g_vb) g_vb->Release();
    D3D11_BUFFER_DESC bd = { sizeof(vertices), D3D11_USAGE_DEFAULT, D3D11_BIND_VERTEX_BUFFER };
    D3D11_SUBRESOURCE_DATA sd = { vertices };
    g_device->CreateBuffer(&bd, &sd, &g_vb);

    UINT stride = 20, offset = 0;
    g_context->IASetVertexBuffers(0, 1, &g_vb, &stride, &offset);
    g_context->IASetPrimitiveTopology(D3D11_PRIMITIVE_TOPOLOGY_TRIANGLELIST);
    g_context->Draw(6, 0);
}

JNIEXPORT void JNICALL Java_fastgraphics_FastGraphics2D_fillOvalNative(JNIEnv*, jclass,
    jfloat x, jfloat y, jfloat w, jfloat h, jfloat r, jfloat g, jfloat b) {
    if (!g_device) return;

    D3D11_VIEWPORT vp; UINT num = 1; g_context->RSGetViewports(&num, &vp);
    g_context->OMSetRenderTargets(1, &g_rtv, nullptr);

    g_context->VSSetShader(g_vs, nullptr, 0);
    g_context->PSSetShader(g_ps, nullptr, 0);
    g_context->IASetInputLayout(g_layout);

    float cx = x + w / 2.0f;
    float cy = y + h / 2.0f;
    float rx = w / 2.0f;
    float ry = h / 2.0f;

    const int segments = 64;
    float* vertices = new float[segments * 3 * 5];

    int idx = 0;
    for (int i = 0; i < segments; i++) {
        float angle1 = (float)i / segments * 3.14159265f * 2.0f;
        float angle2 = (float)(i + 1) / segments * 3.14159265f * 2.0f;

        // Center vertex
        vertices[idx++] = ToNDC_X(cx, vp.Width);
        vertices[idx++] = ToNDC_Y(cy, vp.Height);
        vertices[idx++] = r;
        vertices[idx++] = g;
        vertices[idx++] = b;

        // Edge vertex 1
        vertices[idx++] = ToNDC_X(cx + rx * cosf(angle1), vp.Width);
        vertices[idx++] = ToNDC_Y(cy + ry * sinf(angle1), vp.Height);
        vertices[idx++] = r;
        vertices[idx++] = g;
        vertices[idx++] = b;

        // Edge vertex 2
        vertices[idx++] = ToNDC_X(cx + rx * cosf(angle2), vp.Width);
        vertices[idx++] = ToNDC_Y(cy + ry * sinf(angle2), vp.Height);
        vertices[idx++] = r;
        vertices[idx++] = g;
        vertices[idx++] = b;
    }

    if (g_vb) g_vb->Release();
    D3D11_BUFFER_DESC bd = { segments * 3 * 5 * 4, D3D11_USAGE_DEFAULT, D3D11_BIND_VERTEX_BUFFER };
    D3D11_SUBRESOURCE_DATA sd = { vertices };
    g_device->CreateBuffer(&bd, &sd, &g_vb);
    delete[] vertices;

    UINT stride = 20, offset = 0;
    g_context->IASetVertexBuffers(0, 1, &g_vb, &stride, &offset);
    g_context->IASetPrimitiveTopology(D3D11_PRIMITIVE_TOPOLOGY_TRIANGLELIST);
    g_context->Draw(segments * 3, 0);
}

JNIEXPORT void JNICALL Java_fastgraphics_FastGraphics2D_drawOvalNative(JNIEnv*, jclass,
    jfloat x, jfloat y, jfloat w, jfloat h, jfloat r, jfloat g, jfloat b) {
    if (!g_device) return;

    D3D11_VIEWPORT vp; UINT num = 1; g_context->RSGetViewports(&num, &vp);
    g_context->OMSetRenderTargets(1, &g_rtv, nullptr);

    g_context->VSSetShader(g_vs, nullptr, 0);
    g_context->PSSetShader(g_ps, nullptr, 0);
    g_context->IASetInputLayout(g_layout);

    float cx = x + w / 2.0f;
    float cy = y + h / 2.0f;
    float rx = w / 2.0f;
    float ry = h / 2.0f;

    const int segments = 64;
    float* vertices = new float[(segments + 1) * 5];

    int idx = 0;
    for (int i = 0; i <= segments; i++) {
        float angle = (float)i / segments * 3.14159265f * 2.0f;
        float px = cx + rx * cosf(angle);
        float py = cy + ry * sinf(angle);
        vertices[idx++] = ToNDC_X(px, vp.Width);
        vertices[idx++] = ToNDC_Y(py, vp.Height);
        vertices[idx++] = r;
        vertices[idx++] = g;
        vertices[idx++] = b;
    }

    if (g_vb) g_vb->Release();
    D3D11_BUFFER_DESC bd = { (segments + 1) * 5 * 4, D3D11_USAGE_DEFAULT, D3D11_BIND_VERTEX_BUFFER };
    D3D11_SUBRESOURCE_DATA sd = { vertices };
    g_device->CreateBuffer(&bd, &sd, &g_vb);
    delete[] vertices;

    UINT stride = 20, offset = 0;
    g_context->IASetVertexBuffers(0, 1, &g_vb, &stride, &offset);
    g_context->IASetPrimitiveTopology((D3D11_PRIMITIVE_TOPOLOGY)D3D11_PRIMITIVE_TOPOLOGY_LINESTRIP);
    g_context->Draw(segments + 1, 0);
}

JNIEXPORT void JNICALL Java_fastgraphics_FastGraphics2D_drawLineNative(JNIEnv*, jclass,
    jfloat x1, jfloat y1, jfloat x2, jfloat y2, jfloat r, jfloat g, jfloat b) {
    if (!g_device) return;

    D3D11_VIEWPORT vp; UINT num = 1; g_context->RSGetViewports(&num, &vp);
    g_context->OMSetRenderTargets(1, &g_rtv, nullptr);

    g_context->VSSetShader(g_vs, nullptr, 0);
    g_context->PSSetShader(g_ps, nullptr, 0);
    g_context->IASetInputLayout(g_layout);

    float nx1 = ToNDC_X(x1, vp.Width), ny1 = ToNDC_Y(y1, vp.Height);
    float nx2 = ToNDC_X(x2, vp.Width), ny2 = ToNDC_Y(y2, vp.Height);

    float vertices[] = {
        nx1, ny1, r, g, b,
        nx2, ny2, r, g, b
    };

    if (g_vb) g_vb->Release();
    D3D11_BUFFER_DESC bd = { sizeof(vertices), D3D11_USAGE_DEFAULT, D3D11_BIND_VERTEX_BUFFER };
    D3D11_SUBRESOURCE_DATA sd = { vertices };
    g_device->CreateBuffer(&bd, &sd, &g_vb);

    UINT stride = 20, offset = 0;
    g_context->IASetVertexBuffers(0, 1, &g_vb, &stride, &offset);
    g_context->IASetPrimitiveTopology(D3D11_PRIMITIVE_TOPOLOGY_LINELIST);
    g_context->Draw(2, 0);
}

JNIEXPORT void JNICALL Java_fastgraphics_FastGraphics2D_drawPolygonNative(JNIEnv* env, jclass,
    jfloatArray xPoints, jfloatArray yPoints, jint nPoints, jfloat r, jfloat g, jfloat b) {
    if (!g_device || nPoints < 2) return;

    D3D11_VIEWPORT vp; UINT num = 1; g_context->RSGetViewports(&num, &vp);
    g_context->OMSetRenderTargets(1, &g_rtv, nullptr);

    g_context->VSSetShader(g_vs, nullptr, 0);
    g_context->PSSetShader(g_ps, nullptr, 0);
    g_context->IASetInputLayout(g_layout);

    jfloat* xArr = env->GetFloatArrayElements(xPoints, nullptr);
    jfloat* yArr = env->GetFloatArrayElements(yPoints, nullptr);

    // Line strip with closing vertex
    float* vertices = new float[(nPoints + 1) * 5];

    int idx = 0;
    for (int i = 0; i < nPoints; i++) {
        vertices[idx++] = ToNDC_X(xArr[i], vp.Width);
        vertices[idx++] = ToNDC_Y(yArr[i], vp.Height);
        vertices[idx++] = r;
        vertices[idx++] = g;
        vertices[idx++] = b;
    }
    vertices[idx++] = ToNDC_X(xArr[0], vp.Width);
    vertices[idx++] = ToNDC_Y(yArr[0], vp.Height);
    vertices[idx++] = r;
    vertices[idx++] = g;
    vertices[idx++] = b;

    env->ReleaseFloatArrayElements(xPoints, xArr, JNI_ABORT);
    env->ReleaseFloatArrayElements(yPoints, yArr, JNI_ABORT);

    if (g_vb) g_vb->Release();
    D3D11_BUFFER_DESC bd = { (nPoints + 1) * 5 * 4, D3D11_USAGE_DEFAULT, D3D11_BIND_VERTEX_BUFFER };
    D3D11_SUBRESOURCE_DATA sd = { vertices };
    g_device->CreateBuffer(&bd, &sd, &g_vb);
    delete[] vertices;

    UINT stride = 20, offset = 0;
    g_context->IASetVertexBuffers(0, 1, &g_vb, &stride, &offset);
    g_context->IASetPrimitiveTopology((D3D11_PRIMITIVE_TOPOLOGY)D3D11_PRIMITIVE_TOPOLOGY_LINESTRIP);
    g_context->Draw(nPoints + 1, 0);
}

JNIEXPORT void JNICALL Java_fastgraphics_FastGraphics2D_fillPolygonNative(JNIEnv* env, jclass,
    jfloatArray xPoints, jfloatArray yPoints, jint nPoints, jfloat r, jfloat g, jfloat b) {
    if (!g_device || nPoints < 3) return;

    D3D11_VIEWPORT vp; UINT num = 1; g_context->RSGetViewports(&num, &vp);
    g_context->OMSetRenderTargets(1, &g_rtv, nullptr);

    g_context->VSSetShader(g_vs, nullptr, 0);
    g_context->PSSetShader(g_ps, nullptr, 0);
    g_context->IASetInputLayout(g_layout);

    jfloat* xArr = env->GetFloatArrayElements(xPoints, nullptr);
    jfloat* yArr = env->GetFloatArrayElements(yPoints, nullptr);

    // Triangle list with fan triangulation
    int numTriangles = nPoints - 2;
    float* vertices = new float[numTriangles * 3 * 5];

    int idx = 0;
    for (int i = 1; i < nPoints - 1; i++) {
        vertices[idx++] = ToNDC_X(xArr[0], vp.Width);
        vertices[idx++] = ToNDC_Y(yArr[0], vp.Height);
        vertices[idx++] = r;
        vertices[idx++] = g;
        vertices[idx++] = b;

        // Punkt i
        vertices[idx++] = ToNDC_X(xArr[i], vp.Width);
        vertices[idx++] = ToNDC_Y(yArr[i], vp.Height);
        vertices[idx++] = r;
        vertices[idx++] = g;
        vertices[idx++] = b;

        vertices[idx++] = ToNDC_X(xArr[i + 1], vp.Width);
        vertices[idx++] = ToNDC_Y(yArr[i + 1], vp.Height);
        vertices[idx++] = r;
        vertices[idx++] = g;
        vertices[idx++] = b;
    }

    env->ReleaseFloatArrayElements(xPoints, xArr, JNI_ABORT);
    env->ReleaseFloatArrayElements(yPoints, yArr, JNI_ABORT);

    if (g_vb) g_vb->Release();
    D3D11_BUFFER_DESC bd = { numTriangles * 3 * 5 * 4, D3D11_USAGE_DEFAULT, D3D11_BIND_VERTEX_BUFFER };
    D3D11_SUBRESOURCE_DATA sd = { vertices };
    g_device->CreateBuffer(&bd, &sd, &g_vb);
    delete[] vertices;

    UINT stride = 20, offset = 0;
    g_context->IASetVertexBuffers(0, 1, &g_vb, &stride, &offset);
    g_context->IASetPrimitiveTopology(D3D11_PRIMITIVE_TOPOLOGY_TRIANGLELIST);
    g_context->Draw(numTriangles * 3, 0);
}

JNIEXPORT void JNICALL Java_fastgraphics_FastGraphics2D_drawArcNative(JNIEnv*, jclass,
    jfloat x, jfloat y, jfloat w, jfloat h, jfloat startAngle, jfloat arcAngle, jfloat r, jfloat g, jfloat b) {
    if (!g_device) return;

    D3D11_VIEWPORT vp; UINT num = 1; g_context->RSGetViewports(&num, &vp);
    g_context->OMSetRenderTargets(1, &g_rtv, nullptr);

    g_context->VSSetShader(g_vs, nullptr, 0);
    g_context->PSSetShader(g_ps, nullptr, 0);
    g_context->IASetInputLayout(g_layout);

    float cx = x + w / 2.0f;
    float cy = y + h / 2.0f;
    float rx = w / 2.0f;
    float ry = h / 2.0f;

    const int segments = 64;
    float startRad = startAngle * 3.14159265f / 180.0f;
    float endRad = (startAngle + arcAngle) * 3.14159265f / 180.0f;
    float* vertices = new float[segments * 5];

    int idx = 0;
    for (int i = 0; i < segments; i++) {
        float t = (float)i / (segments - 1);
        float angle = startRad + t * (endRad - startRad);
        float px = cx + rx * cosf(angle);
        float py = cy - ry * sinf(angle);
        vertices[idx++] = ToNDC_X(px, vp.Width);
        vertices[idx++] = ToNDC_Y(py, vp.Height);
        vertices[idx++] = r;
        vertices[idx++] = g;
        vertices[idx++] = b;
    }

    if (g_vb) g_vb->Release();
    D3D11_BUFFER_DESC bd = { segments * 5 * 4, D3D11_USAGE_DEFAULT, D3D11_BIND_VERTEX_BUFFER };
    D3D11_SUBRESOURCE_DATA sd = { vertices };
    g_device->CreateBuffer(&bd, &sd, &g_vb);
    delete[] vertices;

    UINT stride = 20, offset = 0;
    g_context->IASetVertexBuffers(0, 1, &g_vb, &stride, &offset);
    g_context->IASetPrimitiveTopology((D3D11_PRIMITIVE_TOPOLOGY)D3D11_PRIMITIVE_TOPOLOGY_LINESTRIP);
    g_context->Draw(segments, 0);
}

JNIEXPORT void JNICALL Java_fastgraphics_FastGraphics2D_fillArcNative(JNIEnv*, jclass,
    jfloat x, jfloat y, jfloat w, jfloat h, jfloat startAngle, jfloat arcAngle, jfloat r, jfloat g, jfloat b) {
    if (!g_device) return;

    D3D11_VIEWPORT vp; UINT num = 1; g_context->RSGetViewports(&num, &vp);
    g_context->OMSetRenderTargets(1, &g_rtv, nullptr);

    g_context->VSSetShader(g_vs, nullptr, 0);
    g_context->PSSetShader(g_ps, nullptr, 0);
    g_context->IASetInputLayout(g_layout);

    float cx = x + w / 2.0f;
    float cy = y + h / 2.0f;
    float rx = w / 2.0f;
    float ry = h / 2.0f;

    const int segments = 64;
    float startRad = startAngle * 3.14159265f / 180.0f;
    float endRad = (startAngle + arcAngle) * 3.14159265f / 180.0f;
    float* vertices = new float[segments * 3 * 5];

    int idx = 0;
    for (int i = 0; i < segments; i++) {
        float t1 = (float)i / (segments - 1);
        float t2 = (float)(i + 1) / (segments - 1);
        float angle1 = startRad - t1 * (endRad - startRad);
        float angle2 = startRad - t2 * (endRad - startRad);

        // Center vertex
        vertices[idx++] = ToNDC_X(cx, vp.Width);
        vertices[idx++] = ToNDC_Y(cy, vp.Height);
        vertices[idx++] = r;
        vertices[idx++] = g;
        vertices[idx++] = b;

        vertices[idx++] = ToNDC_X(cx + rx * cosf(angle1), vp.Width);
        vertices[idx++] = ToNDC_Y(cy - ry * sinf(angle1), vp.Height);
        vertices[idx++] = r;
        vertices[idx++] = g;
        vertices[idx++] = b;

        vertices[idx++] = ToNDC_X(cx + rx * cosf(angle2), vp.Width);
        vertices[idx++] = ToNDC_Y(cy - ry * sinf(angle2), vp.Height);
        vertices[idx++] = r;
        vertices[idx++] = g;
        vertices[idx++] = b;
    }

    if (g_vb) g_vb->Release();
    D3D11_BUFFER_DESC bd = { segments * 3 * 5 * 4, D3D11_USAGE_DEFAULT, D3D11_BIND_VERTEX_BUFFER };
    D3D11_SUBRESOURCE_DATA sd = { vertices };
    g_device->CreateBuffer(&bd, &sd, &g_vb);
    delete[] vertices;

    UINT stride = 20, offset = 0;
    g_context->IASetVertexBuffers(0, 1, &g_vb, &stride, &offset);
    g_context->IASetPrimitiveTopology(D3D11_PRIMITIVE_TOPOLOGY_TRIANGLELIST);
    g_context->Draw(segments * 3, 0);
}

JNIEXPORT void JNICALL Java_fastgraphics_FastGraphics2D_drawRoundRectNative(JNIEnv*, jclass,
    jfloat x, jfloat y, jfloat w, jfloat h, jfloat arcWidth, jfloat arcHeight, jfloat r, jfloat g, jfloat b) {
    // Stub: renders as rectangle (complex geometry not implemented)
    Java_fastgraphics_FastGraphics2D_drawRectNative(nullptr, nullptr, x, y, w, h, r, g, b);
}

JNIEXPORT void JNICALL Java_fastgraphics_FastGraphics2D_fillRoundRectNative(JNIEnv*, jclass,
    jfloat x, jfloat y, jfloat w, jfloat h, jfloat arcWidth, jfloat arcHeight, jfloat r, jfloat g, jfloat b) {
    // Stub: fills as rectangle (complex geometry not implemented)
    Java_fastgraphics_FastGraphics2D_fillRectNative(nullptr, nullptr, x, y, w, h, r, g, b);
}

JNIEXPORT void JNICALL Java_fastgraphics_FastGraphics2D_translateNative(JNIEnv*, jclass, jfloat tx, jfloat ty) {
    g_translateX += tx;
    g_translateY += ty;
}

JNIEXPORT void JNICALL Java_fastgraphics_FastGraphics2D_scaleNative(JNIEnv*, jclass, jfloat sx, jfloat sy) {
    g_scaleX *= sx;
    g_scaleY *= sy;
}

JNIEXPORT void JNICALL Java_fastgraphics_FastGraphics2D_rotateNative(JNIEnv*, jclass, jfloat angle) {
    g_rotation += angle;
}

JNIEXPORT void JNICALL Java_fastgraphics_FastGraphics2D_resetTransformNative(JNIEnv*, jclass) {
    g_translateX = 0.0f;
    g_translateY = 0.0f;
    g_scaleX = 1.0f;
    g_scaleY = 1.0f;
    g_rotation = 0.0f;
}

JNIEXPORT void JNICALL Java_fastgraphics_FastGraphics2D_setStrokeNative(JNIEnv*, jclass, jfloat lineWidth) {
    g_lineWidth = lineWidth;
}

JNIEXPORT void JNICALL Java_fastgraphics_FastGraphics2D_setAntiAliasingNative(JNIEnv*, jclass, jboolean enabled) {
    // Stub: anti-aliasing not supported (requires MSAA in swap chain)
}

JNIEXPORT void JNICALL Java_fastgraphics_FastGraphics2D_setClipNative(JNIEnv*, jclass, jfloat x, jfloat y, jfloat w, jfloat h) {
    // Stub: clipping not supported (requires scissor rects or stencil buffer)
    g_clipX = x;
    g_clipY = y;
    g_clipW = w;
    g_clipH = h;
    g_clipEnabled = true;
}

JNIEXPORT void JNICALL Java_fastgraphics_FastGraphics2D_resetClipNative(JNIEnv*, jclass) {
    g_clipEnabled = false;
}

JNIEXPORT void JNICALL Java_fastgraphics_FastGraphics2D_drawStringNative(JNIEnv* env, jclass, jstring str, jfloat x, jfloat y, jfloat r, jfloat g, jfloat b) {
    // Stub: text rendering not supported (requires textured shaders)
}

JNIEXPORT void JNICALL Java_fastgraphics_FastGraphics2D_drawImageNative(JNIEnv*, jclass, jfloat x, jfloat y, jfloat w, jfloat h) {
    // Stub: image rendering not supported (requires textured shaders)
}

// Initialize textured rendering resources (called on first texture operation)
static void InitTexturedRendering() {
    if (g_vsTextured) return; // Already initialized
    if (!g_device) {
        fprintf(stderr, "[FastGraphics] InitTexturedRendering: g_device is null!\n");
        return;
    }
    
    // Compile shaders
    ID3DBlob* vsBlob = nullptr;
    ID3DBlob* psBlob = nullptr;
    
    if (CompileShader(VS_TEXTURED_SRC, "main", "vs_4_0", &vsBlob)) {
        g_device->CreateVertexShader(vsBlob->GetBufferPointer(), vsBlob->GetBufferSize(), nullptr, &g_vsTextured);
        
        // Input layout for textured vertices (position + uv)
        D3D11_INPUT_ELEMENT_DESC layout[] = {
            { "POSITION", 0, DXGI_FORMAT_R32G32_FLOAT, 0, 0, D3D11_INPUT_PER_VERTEX_DATA, 0 },
            { "TEXCOORD", 0, DXGI_FORMAT_R32G32_FLOAT, 0, 8, D3D11_INPUT_PER_VERTEX_DATA, 0 }
        };
        g_device->CreateInputLayout(layout, 2, vsBlob->GetBufferPointer(), vsBlob->GetBufferSize(), &g_layoutTextured);
        vsBlob->Release();
    }
    
    if (CompileShader(PS_TEXTURED_SRC, "main", "ps_4_0", &psBlob)) {
        g_device->CreatePixelShader(psBlob->GetBufferPointer(), psBlob->GetBufferSize(), nullptr, &g_psTextured);
        psBlob->Release();
    }
    
    // Create sampler state
    D3D11_SAMPLER_DESC sampDesc = {};
    sampDesc.Filter = D3D11_FILTER_MIN_MAG_MIP_LINEAR;
    sampDesc.AddressU = D3D11_TEXTURE_ADDRESS_CLAMP;
    sampDesc.AddressV = D3D11_TEXTURE_ADDRESS_CLAMP;
    sampDesc.AddressW = D3D11_TEXTURE_ADDRESS_CLAMP;
    sampDesc.ComparisonFunc = D3D11_COMPARISON_NEVER;
    sampDesc.MinLOD = 0;
    sampDesc.MaxLOD = D3D11_FLOAT32_MAX;
    g_device->CreateSamplerState(&sampDesc, &g_sampler);
    
    // Create static quad vertex buffer (will be updated per draw)
    float quadVertices[] = {
        // Position    UV
        0.0f, 0.0f,   0.0f, 0.0f,
        1.0f, 0.0f,   1.0f, 0.0f,
        0.0f, 1.0f,   0.0f, 1.0f,
        1.0f, 1.0f,   1.0f, 1.0f
    };
    
    D3D11_BUFFER_DESC bd = {};
    bd.Usage = D3D11_USAGE_DYNAMIC;
    bd.ByteWidth = sizeof(quadVertices);
    bd.BindFlags = D3D11_BIND_VERTEX_BUFFER;
    bd.CPUAccessFlags = D3D11_CPU_ACCESS_WRITE;
    g_device->CreateBuffer(&bd, nullptr, &g_texturedVB);
}

JNIEXPORT jint JNICALL Java_fastgraphics_FastGraphics2D_loadTextureNative(JNIEnv* env, jclass, jintArray pixelData, jint width, jint height) {
    if (!g_device) {
        fprintf(stderr, "[FastGraphics] loadTextureNative: g_device is null!\n");
        return -1;
    }
    
    InitTexturedRendering();
    
    jint* pixels = env->GetIntArrayElements(pixelData, nullptr);
    if (!pixels) return -1;
    
    // Create texture
    D3D11_TEXTURE2D_DESC texDesc = {};
    texDesc.Width = width;
    texDesc.Height = height;
    texDesc.MipLevels = 1;
    texDesc.ArraySize = 1;
    texDesc.Format = DXGI_FORMAT_B8G8R8A8_UNORM;
    texDesc.SampleDesc.Count = 1;
    texDesc.Usage = D3D11_USAGE_IMMUTABLE;
    texDesc.BindFlags = D3D11_BIND_SHADER_RESOURCE;
    
    D3D11_SUBRESOURCE_DATA initData = {};
    initData.pSysMem = pixels;
    initData.SysMemPitch = width * 4;
    
    ID3D11Texture2D* texture = nullptr;
    ID3D11ShaderResourceView* srv = nullptr;
    
    if (FAILED(g_device->CreateTexture2D(&texDesc, &initData, &texture))) {
        env->ReleaseIntArrayElements(pixelData, pixels, JNI_ABORT);
        return -1;
    }
    
    if (FAILED(g_device->CreateShaderResourceView(texture, nullptr, &srv))) {
        texture->Release();
        env->ReleaseIntArrayElements(pixelData, pixels, JNI_ABORT);
        return -1;
    }
    
    env->ReleaseIntArrayElements(pixelData, pixels, JNI_ABORT);
    
    // Store in cache
    int id = g_nextTextureId++;
    g_textureCache[id] = { texture, srv };
    
    return id;
}

JNIEXPORT void JNICALL Java_fastgraphics_FastGraphics2D_unloadTextureNative(JNIEnv*, jclass, jint textureId) {
    auto it = g_textureCache.find(textureId);
    if (it != g_textureCache.end()) {
        if (it->second.srv) it->second.srv->Release();
        if (it->second.texture) it->second.texture->Release();
        g_textureCache.erase(it);
    }
}

JNIEXPORT void JNICALL Java_fastgraphics_FastGraphics2D_drawTexturedQuadNative(JNIEnv*, jclass, jint textureId, jfloat x, jfloat y, jfloat w, jfloat h) {
    if (!g_device || !g_context) return;
    
    auto it = g_textureCache.find(textureId);
    if (it == g_textureCache.end()) return;
    
    D3D11_VIEWPORT vp; UINT num = 1;
    g_context->RSGetViewports(&num, &vp);
    
    // Ensure screen constant buffer exists
    if (!g_screenCB) {
        D3D11_BUFFER_DESC cbd = {};
        cbd.Usage = D3D11_USAGE_DYNAMIC;
        cbd.ByteWidth = 16;
        cbd.BindFlags = D3D11_BIND_CONSTANT_BUFFER;
        cbd.CPUAccessFlags = D3D11_CPU_ACCESS_WRITE;
        g_device->CreateBuffer(&cbd, nullptr, &g_screenCB);
    }
    
    // Update vertex buffer with quad positions
    float vertices[] = {
        // Position      UV
        x,     y,       0.0f, 0.0f,
        x + w, y,       1.0f, 0.0f,
        x,     y + h,  0.0f, 1.0f,
        x + w, y + h,  1.0f, 1.0f
    };
    
    D3D11_MAPPED_SUBRESOURCE mapped;
    if (SUCCEEDED(g_context->Map(g_texturedVB, 0, D3D11_MAP_WRITE_DISCARD, 0, &mapped))) {
        memcpy(mapped.pData, vertices, sizeof(vertices));
        g_context->Unmap(g_texturedVB, 0);
    }
    
    // Update screen constant buffer
    D3D11_MAPPED_SUBRESOURCE cbMapped;
    if (SUCCEEDED(g_context->Map(g_screenCB, 0, D3D11_MAP_WRITE_DISCARD, 0, &cbMapped))) {
        float* cbData = (float*)cbMapped.pData;
        cbData[0] = (float)vp.Width;
        cbData[1] = (float)vp.Height;
        g_context->Unmap(g_screenCB, 0);
    }
    
    // Set render state
    g_context->OMSetRenderTargets(1, &g_rtv, nullptr);
    g_context->VSSetShader(g_vsTextured, nullptr, 0);
    g_context->VSSetConstantBuffers(0, 1, &g_screenCB);
    g_context->PSSetShader(g_psTextured, nullptr, 0);
    g_context->PSSetShaderResources(0, 1, &it->second.srv);
    g_context->PSSetSamplers(0, 1, &g_sampler);
    g_context->IASetInputLayout(g_layoutTextured);
    
    UINT stride = 16; // 4 floats per vertex
    UINT offset = 0;
    g_context->IASetVertexBuffers(0, 1, &g_texturedVB, &stride, &offset);
    g_context->IASetPrimitiveTopology(D3D11_PRIMITIVE_TOPOLOGY_TRIANGLESTRIP);
    
    // Draw
    g_context->Draw(4, 0);
    
    // Unbind texture to avoid conflicts with other shaders
    ID3D11ShaderResourceView* nullSrv = nullptr;
    g_context->PSSetShaderResources(0, 1, &nullSrv);
}

JNIEXPORT void JNICALL Java_fastgraphics_FastGraphics2D_init(JNIEnv*, jclass, jlong hwnd) {
    fprintf(stderr, "[FastGraphics] init called with hwnd=%lld\n", hwnd);
    Java_demo_DemoApp_init(nullptr, nullptr, hwnd);
    if (g_device) {
        fprintf(stderr, "[FastGraphics] g_device initialized OK\n");
    } else {
        fprintf(stderr, "[FastGraphics] ERROR: g_device is NULL!\n");
    }
}

JNIEXPORT void JNICALL Java_demo_BenchmarkApp_init(JNIEnv*, jclass, jlong hwnd) {
    Java_demo_DemoApp_init(nullptr, nullptr, hwnd);
}

JNIEXPORT void JNICALL Java_demo_BenchmarkApp_fillRect(JNIEnv*, jclass, float x, float y, float w, float h, float r, float g, float b) {
    Java_demo_DemoApp_fillRect(nullptr, nullptr, x, y, w, h, r, g, b);
}

JNIEXPORT void JNICALL Java_demo_BenchmarkApp_clear(JNIEnv*, jclass, float r, float g, float b) {
    Java_demo_DemoApp_clear(nullptr, nullptr, r, g, b);
}

JNIEXPORT void JNICALL Java_demo_BenchmarkApp_present(JNIEnv*, jclass) {
    Java_demo_DemoApp_present(nullptr, nullptr);
}

JNIEXPORT jlong JNICALL Java_demo_BenchmarkApp_findWindow(JNIEnv* env, jclass, jstring title) {
    return Java_demo_DemoApp_findWindow(env, nullptr, title);
}

JNIEXPORT void JNICALL Java_demo_Benchmark_init(JNIEnv*, jclass, jlong hwnd) {
    Java_demo_DemoApp_init(nullptr, nullptr, hwnd);
}

JNIEXPORT void JNICALL Java_demo_Benchmark_fillRects(JNIEnv* env, jclass, jobject data, jint count) {
    Java_demo_DemoApp_fillRects(env, nullptr, data, count);
}

JNIEXPORT void JNICALL Java_demo_Benchmark_clear(JNIEnv*, jclass, jfloat r, jfloat g, jfloat b) {
    Java_demo_DemoApp_clear(nullptr, nullptr, r, g, b);
}

JNIEXPORT void JNICALL Java_demo_Benchmark_present(JNIEnv*, jclass) {
    Java_demo_DemoApp_present(nullptr, nullptr);
}

JNIEXPORT jlong JNICALL Java_demo_Benchmark_findWindow(JNIEnv* env, jclass, jstring title) {
    return Java_demo_DemoApp_findWindow(env, nullptr, title);
}

JNIEXPORT void JNICALL Java_demo_Comparator_init(JNIEnv*, jclass, jlong hwnd) {
    Java_demo_DemoApp_init(nullptr, nullptr, hwnd);
}

JNIEXPORT void JNICALL Java_demo_Comparator_fillRects(JNIEnv* env, jclass, jobject data, jint count) {
    Java_demo_DemoApp_fillRects(env, nullptr, data, count);
}

JNIEXPORT void JNICALL Java_demo_Comparator_clear(JNIEnv*, jclass, jfloat r, jfloat g, jfloat b) {
    Java_demo_DemoApp_clear(nullptr, nullptr, r, g, b);
}

JNIEXPORT void JNICALL Java_demo_Comparator_present(JNIEnv*, jclass) {
    Java_demo_DemoApp_present(nullptr, nullptr);
}

JNIEXPORT jlong JNICALL Java_demo_Comparator_findWindow(JNIEnv* env, jclass, jstring title) {
    return Java_demo_DemoApp_findWindow(env, nullptr, title);
}

JNIEXPORT void JNICALL Java_demo_DrawRectTest_init(JNIEnv*, jclass, jlong hwnd) {
    Java_demo_DemoApp_init(nullptr, nullptr, hwnd);
}

JNIEXPORT void JNICALL Java_demo_DrawRectTest_drawRectNative(JNIEnv*, jclass,
    jfloat x, jfloat y, jfloat w, jfloat h, jfloat r, jfloat g, jfloat b) {
    Java_fastgraphics_FastGraphics2D_drawRectNative(nullptr, nullptr, x, y, w, h, r, g, b);
}

JNIEXPORT void JNICALL Java_demo_DrawRectTest_fillRectNative(JNIEnv*, jclass,
    jfloat x, jfloat y, jfloat w, jfloat h, jfloat r, jfloat g, jfloat b) {
    Java_fastgraphics_FastGraphics2D_fillRectNative(nullptr, nullptr, x, y, w, h, r, g, b);
}

JNIEXPORT void JNICALL Java_demo_DrawRectTest_clear(JNIEnv*, jclass, jfloat r, jfloat g, jfloat b) {
    Java_demo_DemoApp_clear(nullptr, nullptr, r, g, b);
}

JNIEXPORT void JNICALL Java_demo_DrawRectTest_present(JNIEnv*, jclass) {
    Java_demo_DemoApp_present(nullptr, nullptr);
}

JNIEXPORT jlong JNICALL Java_demo_DrawRectTest_findWindow(JNIEnv* env, jclass, jstring title) {
    return Java_demo_DemoApp_findWindow(env, nullptr, title);
}

JNIEXPORT void JNICALL Java_demo_DrawOvalTest_init(JNIEnv*, jclass, jlong hwnd) {
    Java_demo_DemoApp_init(nullptr, nullptr, hwnd);
}

JNIEXPORT void JNICALL Java_demo_DrawOvalTest_fillOvalNative(JNIEnv*, jclass,
    jfloat x, jfloat y, jfloat w, jfloat h, jfloat r, jfloat g, jfloat b) {
    Java_fastgraphics_FastGraphics2D_fillOvalNative(nullptr, nullptr, x, y, w, h, r, g, b);
}

JNIEXPORT void JNICALL Java_demo_DrawOvalTest_drawOvalNative(JNIEnv*, jclass,
    jfloat x, jfloat y, jfloat w, jfloat h, jfloat r, jfloat g, jfloat b) {
    Java_fastgraphics_FastGraphics2D_drawOvalNative(nullptr, nullptr, x, y, w, h, r, g, b);
}

JNIEXPORT void JNICALL Java_demo_DrawOvalTest_clear(JNIEnv*, jclass, jfloat r, jfloat g, jfloat b) {
    Java_demo_DemoApp_clear(nullptr, nullptr, r, g, b);
}

JNIEXPORT void JNICALL Java_demo_DrawOvalTest_present(JNIEnv*, jclass) {
    Java_demo_DemoApp_present(nullptr, nullptr);
}

JNIEXPORT jlong JNICALL Java_demo_DrawOvalTest_findWindow(JNIEnv* env, jclass, jstring title) {
    return Java_demo_DemoApp_findWindow(env, nullptr, title);
}

JNIEXPORT void JNICALL Java_demo_DrawLineTest_init(JNIEnv*, jclass, jlong hwnd) {
    Java_demo_DemoApp_init(nullptr, nullptr, hwnd);
}

JNIEXPORT void JNICALL Java_demo_DrawLineTest_drawLineNative(JNIEnv*, jclass,
    jfloat x1, jfloat y1, jfloat x2, jfloat y2, jfloat r, jfloat g, jfloat b) {
    Java_fastgraphics_FastGraphics2D_drawLineNative(nullptr, nullptr, x1, y1, x2, y2, r, g, b);
}

JNIEXPORT void JNICALL Java_demo_DrawLineTest_clear(JNIEnv*, jclass, jfloat r, jfloat g, jfloat b) {
    Java_demo_DemoApp_clear(nullptr, nullptr, r, g, b);
}

JNIEXPORT void JNICALL Java_demo_DrawLineTest_present(JNIEnv*, jclass) {
    Java_demo_DemoApp_present(nullptr, nullptr);
}

JNIEXPORT jlong JNICALL Java_demo_DrawLineTest_findWindow(JNIEnv* env, jclass, jstring title) {
    return Java_demo_DemoApp_findWindow(env, nullptr, title);
}

JNIEXPORT void JNICALL Java_demo_DrawPolygonTest_init(JNIEnv*, jclass, jlong hwnd) {
    Java_demo_DemoApp_init(nullptr, nullptr, hwnd);
}

JNIEXPORT void JNICALL Java_demo_DrawPolygonTest_drawPolygonNative(JNIEnv* env, jclass,
    jfloatArray xPoints, jfloatArray yPoints, jint nPoints, jfloat r, jfloat g, jfloat b) {
    Java_fastgraphics_FastGraphics2D_drawPolygonNative(env, nullptr, xPoints, yPoints, nPoints, r, g, b);
}

JNIEXPORT void JNICALL Java_demo_DrawPolygonTest_fillPolygonNative(JNIEnv* env, jclass,
    jfloatArray xPoints, jfloatArray yPoints, jint nPoints, jfloat r, jfloat g, jfloat b) {
    Java_fastgraphics_FastGraphics2D_fillPolygonNative(env, nullptr, xPoints, yPoints, nPoints, r, g, b);
}

JNIEXPORT void JNICALL Java_demo_DrawPolygonTest_clear(JNIEnv*, jclass, jfloat r, jfloat g, jfloat b) {
    Java_demo_DemoApp_clear(nullptr, nullptr, r, g, b);
}

JNIEXPORT void JNICALL Java_demo_DrawPolygonTest_present(JNIEnv*, jclass) {
    Java_demo_DemoApp_present(nullptr, nullptr);
}

JNIEXPORT jlong JNICALL Java_demo_DrawPolygonTest_findWindow(JNIEnv* env, jclass, jstring title) {
    return Java_demo_DemoApp_findWindow(env, nullptr, title);
}

JNIEXPORT void JNICALL Java_demo_DrawArcTest_init(JNIEnv*, jclass, jlong hwnd) {
    Java_demo_DemoApp_init(nullptr, nullptr, hwnd);
}

JNIEXPORT void JNICALL Java_demo_DrawArcTest_drawArcNative(JNIEnv*, jclass,
    jfloat x, jfloat y, jfloat w, jfloat h, jfloat startAngle, jfloat arcAngle, jfloat r, jfloat g, jfloat b) {
    Java_fastgraphics_FastGraphics2D_drawArcNative(nullptr, nullptr, x, y, w, h, startAngle, arcAngle, r, g, b);
}

JNIEXPORT void JNICALL Java_demo_DrawArcTest_fillArcNative(JNIEnv*, jclass,
    jfloat x, jfloat y, jfloat w, jfloat h, jfloat startAngle, jfloat arcAngle, jfloat r, jfloat g, jfloat b) {
    Java_fastgraphics_FastGraphics2D_fillArcNative(nullptr, nullptr, x, y, w, h, startAngle, arcAngle, r, g, b);
}

JNIEXPORT void JNICALL Java_demo_DrawArcTest_clear(JNIEnv*, jclass, jfloat r, jfloat g, jfloat b) {
    Java_demo_DemoApp_clear(nullptr, nullptr, r, g, b);
}

JNIEXPORT void JNICALL Java_demo_DrawArcTest_present(JNIEnv*, jclass) {
    Java_demo_DemoApp_present(nullptr, nullptr);
}

JNIEXPORT jlong JNICALL Java_demo_DrawArcTest_findWindow(JNIEnv* env, jclass, jstring title) {
    return Java_demo_DemoApp_findWindow(env, nullptr, title);
}

JNIEXPORT void JNICALL Java_demo_TransformTest_init(JNIEnv*, jclass, jlong hwnd) {
    Java_demo_DemoApp_init(nullptr, nullptr, hwnd);
}

JNIEXPORT void JNICALL Java_demo_TransformTest_fillRectNative(JNIEnv*, jclass,
    jfloat x, jfloat y, jfloat w, jfloat h, jfloat r, jfloat g, jfloat b) {
    Java_fastgraphics_FastGraphics2D_fillRectNative(nullptr, nullptr, x, y, w, h, r, g, b);
}

JNIEXPORT void JNICALL Java_demo_TransformTest_translateNative(JNIEnv*, jclass, jfloat tx, jfloat ty) {
    Java_fastgraphics_FastGraphics2D_translateNative(nullptr, nullptr, tx, ty);
}

JNIEXPORT void JNICALL Java_demo_TransformTest_scaleNative(JNIEnv*, jclass, jfloat sx, jfloat sy) {
    Java_fastgraphics_FastGraphics2D_scaleNative(nullptr, nullptr, sx, sy);
}

JNIEXPORT void JNICALL Java_demo_TransformTest_rotateNative(JNIEnv*, jclass, jfloat angle) {
    Java_fastgraphics_FastGraphics2D_rotateNative(nullptr, nullptr, angle);
}

JNIEXPORT void JNICALL Java_demo_TransformTest_resetTransformNative(JNIEnv*, jclass) {
    Java_fastgraphics_FastGraphics2D_resetTransformNative(nullptr, nullptr);
}

JNIEXPORT void JNICALL Java_demo_TransformTest_clear(JNIEnv*, jclass, jfloat r, jfloat g, jfloat b) {
    Java_demo_DemoApp_clear(nullptr, nullptr, r, g, b);
}

JNIEXPORT void JNICALL Java_demo_TransformTest_present(JNIEnv*, jclass) {
    Java_demo_DemoApp_present(nullptr, nullptr);
}

JNIEXPORT jlong JNICALL Java_demo_TransformTest_findWindow(JNIEnv* env, jclass, jstring title) {
    return Java_demo_DemoApp_findWindow(env, nullptr, title);
}

JNIEXPORT void JNICALL Java_demo_AntiAliasingTest_init(JNIEnv*, jclass, jlong hwnd) {
    Java_demo_DemoApp_init(nullptr, nullptr, hwnd);
}

JNIEXPORT void JNICALL Java_demo_AntiAliasingTest_drawOvalNative(JNIEnv*, jclass,
    jfloat x, jfloat y, jfloat w, jfloat h, jfloat r, jfloat g, jfloat b) {
    Java_fastgraphics_FastGraphics2D_drawOvalNative(nullptr, nullptr, x, y, w, h, r, g, b);
}

JNIEXPORT void JNICALL Java_demo_AntiAliasingTest_drawLineNative(JNIEnv*, jclass,
    jfloat x1, jfloat y1, jfloat x2, jfloat y2, jfloat r, jfloat g, jfloat b) {
    Java_fastgraphics_FastGraphics2D_drawLineNative(nullptr, nullptr, x1, y1, x2, y2, r, g, b);
}

JNIEXPORT void JNICALL Java_demo_AntiAliasingTest_setAntiAliasingNative(JNIEnv*, jclass, jboolean enabled) {
    Java_fastgraphics_FastGraphics2D_setAntiAliasingNative(nullptr, nullptr, enabled);
}

JNIEXPORT void JNICALL Java_demo_AntiAliasingTest_clear(JNIEnv*, jclass, jfloat r, jfloat g, jfloat b) {
    Java_demo_DemoApp_clear(nullptr, nullptr, r, g, b);
}

JNIEXPORT void JNICALL Java_demo_AntiAliasingTest_present(JNIEnv*, jclass) {
    Java_demo_DemoApp_present(nullptr, nullptr);
}

JNIEXPORT jlong JNICALL Java_demo_AntiAliasingTest_findWindow(JNIEnv* env, jclass, jstring title) {
    return Java_demo_DemoApp_findWindow(env, nullptr, title);
}

JNIEXPORT void JNICALL Java_demo_DrawRoundRectTest_init(JNIEnv*, jclass, jlong hwnd) {
    Java_demo_DemoApp_init(nullptr, nullptr, hwnd);
}

JNIEXPORT void JNICALL Java_demo_DrawRoundRectTest_drawRoundRectNative(JNIEnv*, jclass,
    jfloat x, jfloat y, jfloat w, jfloat h, jfloat arcWidth, jfloat arcHeight, jfloat r, jfloat g, jfloat b) {
    Java_fastgraphics_FastGraphics2D_drawRoundRectNative(nullptr, nullptr, x, y, w, h, arcWidth, arcHeight, r, g, b);
}

JNIEXPORT void JNICALL Java_demo_DrawRoundRectTest_fillRoundRectNative(JNIEnv*, jclass,
    jfloat x, jfloat y, jfloat w, jfloat h, jfloat arcWidth, jfloat arcHeight, jfloat r, jfloat g, jfloat b) {
    Java_fastgraphics_FastGraphics2D_fillRoundRectNative(nullptr, nullptr, x, y, w, h, arcWidth, arcHeight, r, g, b);
}

JNIEXPORT void JNICALL Java_demo_DrawRoundRectTest_clear(JNIEnv*, jclass, jfloat r, jfloat g, jfloat b) {
    Java_demo_DemoApp_clear(nullptr, nullptr, r, g, b);
}

JNIEXPORT void JNICALL Java_demo_DrawRoundRectTest_present(JNIEnv*, jclass) {
    Java_demo_DemoApp_present(nullptr, nullptr);
}

JNIEXPORT jlong JNICALL Java_demo_DrawRoundRectTest_findWindow(JNIEnv* env, jclass, jstring title) {
    return Java_demo_DemoApp_findWindow(env, nullptr, title);
}

JNIEXPORT void JNICALL Java_demo_TVTestPattern_init(JNIEnv*, jclass, jlong hwnd) {
    Java_demo_DemoApp_init(nullptr, nullptr, hwnd);
}

JNIEXPORT void JNICALL Java_demo_TVTestPattern_fillRects(JNIEnv* env, jclass, jobject data, jint count) {
    Java_demo_DemoApp_fillRects(env, nullptr, data, count);
}

JNIEXPORT void JNICALL Java_demo_TVTestPattern_drawCircles(JNIEnv* env, jclass, jobject data, jint count) {
    Java_demo_DemoApp_drawCircles(env, nullptr, data, count);
}

JNIEXPORT void JNICALL Java_demo_TVTestPattern_clear(JNIEnv*, jclass, jfloat r, jfloat g, jfloat b) {
    Java_demo_DemoApp_clear(nullptr, nullptr, r, g, b);
}

JNIEXPORT void JNICALL Java_demo_TVTestPattern_present(JNIEnv*, jclass) {
    Java_demo_DemoApp_present(nullptr, nullptr);
}

JNIEXPORT jlong JNICALL Java_demo_TVTestPattern_findWindow(JNIEnv* env, jclass, jstring title) {
    return Java_demo_DemoApp_findWindow(env, nullptr, title);
}

} // extern "C"
