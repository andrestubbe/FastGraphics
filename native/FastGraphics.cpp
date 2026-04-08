/**
 * FastGraphics - GPU-basiertes 2D Rendering für Java
 * DirectX 11 Implementation
 */

#include <jni.h>
#include <windows.h>
#include <d3d11.h>
#include <d3dcompiler.h>
#include <math.h>  // Für sinf, cosf

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

// Standard Shader (nicht-instanced)
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

// INSTANCED SHADER - Pixel-exakte Koordinaten!
const char* VS_INSTANCED_SRC = R"(
    struct VS_INPUT {
        float2 quadPos : POSITION;      // 0,0 bis 1,1 (das Quad)
    };
    struct VS_INSTANCE {
        float2 pos : INSTANCE_POS;      // Rechteck Position (Pixel)
        float2 size : INSTANCE_SIZE;    // Rechteck Größe (Pixel)
        float3 color : INSTANCE_COLOR;  // Rechteck Farbe
    };
    struct VS_OUTPUT {
        float4 pos : SV_POSITION;
        float3 color : COLOR;
    };
    cbuffer ScreenCB : register(b0) {
        float2 screenSize;  // z.B. 800, 600
    };
    VS_OUTPUT main(VS_INPUT input, VS_INSTANCE instance) {
        VS_OUTPUT output;
        // Java2D-Kompatible Koordinaten: Top-Left Origin, Y nach unten
        float2 pixelPos = instance.pos + input.quadPos * instance.size;
        
        // Java2D: (0,0) = Top-Left, (width,height) = Bottom-Right
        // DirectX: (-1,-1) = Bottom-Left, (1,1) = Top-Right
        // Transformation mit exaktem Pixel-Center
        
        float2 clipPos;
        // X: [0, width] -> [-1, 1], Pixel-Center bei +0.5
        clipPos.x = ((pixelPos.x + 0.5) / screenSize.x) * 2.0 - 1.0;
        // Y: [0, height] -> [1, -1] (flip für Top-Left Origin), Pixel-Center bei +0.5  
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

extern "C" {

JNIEXPORT void JNICALL Java_demo_DemoApp_init(JNIEnv*, jclass, jlong hwnd) {
    HWND h = (HWND)hwnd;
    RECT rc; GetClientRect(h, &rc);
    int w = rc.right - rc.left, h2 = rc.bottom - rc.top;
    
    DXGI_SWAP_CHAIN_DESC sd = {};
    sd.BufferCount = 1;
    sd.BufferDesc.Width = w; sd.BufferDesc.Height = h2;
    sd.BufferDesc.Format = DXGI_FORMAT_R8G8B8A8_UNORM;
    sd.BufferUsage = DXGI_USAGE_RENDER_TARGET_OUTPUT;
    sd.OutputWindow = h; sd.SampleDesc.Count = 1; sd.Windowed = TRUE;
    
    D3D11CreateDeviceAndSwapChain(nullptr, D3D_DRIVER_TYPE_HARDWARE, nullptr, 0,
        nullptr, 0, D3D11_SDK_VERSION, &sd, &g_swapChain, &g_device, nullptr, &g_context);
    
    ID3D11Texture2D* bb = nullptr;
    g_swapChain->GetBuffer(0, __uuidof(ID3D11Texture2D), (void**)&bb);
    g_device->CreateRenderTargetView(bb, nullptr, &g_rtv);
    if (bb) bb->Release();
    
    // Shader erstellen
    ID3DBlob *vsb = nullptr, *psb = nullptr;
    CompileShader(VS_SRC, "main", "vs_4_0", &vsb);
    CompileShader(PS_SRC, "main", "ps_4_0", &psb);
    
    g_device->CreateVertexShader(vsb->GetBufferPointer(), vsb->GetBufferSize(), nullptr, &g_vs);
    g_device->CreatePixelShader(psb->GetBufferPointer(), psb->GetBufferSize(), nullptr, &g_ps);
    
    D3D11_INPUT_ELEMENT_DESC layout[] = {
        { "POSITION", 0, DXGI_FORMAT_R32G32_FLOAT, 0, 0, D3D11_INPUT_PER_VERTEX_DATA, 0 },
        { "COLOR", 0, DXGI_FORMAT_R32G32B32_FLOAT, 0, 8, D3D11_INPUT_PER_VERTEX_DATA, 0 }
    };
    g_device->CreateInputLayout(layout, 2, vsb->GetBufferPointer(), vsb->GetBufferSize(), &g_layout);
    
    if (vsb) vsb->Release();
    if (psb) psb->Release();
    
    // Shader binden
    g_context->VSSetShader(g_vs, nullptr, 0);
    g_context->PSSetShader(g_ps, nullptr, 0);
    g_context->IASetInputLayout(g_layout);
    
    D3D11_VIEWPORT vp = { 0, 0, (float)w, (float)h2, 0, 1 };
    g_context->RSSetViewports(1, &vp);
}

JNIEXPORT void JNICALL Java_demo_DemoApp_fillRect(JNIEnv*, jclass,
    jfloat x, jfloat y, jfloat width, jfloat height, jfloat r, jfloat g, jfloat b) {
    if (!g_device) return;
    
    D3D11_VIEWPORT vp; UINT num = 1; g_context->RSGetViewports(&num, &vp);
    g_context->OMSetRenderTargets(1, &g_rtv, nullptr);
    
    // Shader binden (wichtig!)
    g_context->VSSetShader(g_vs, nullptr, 0);
    g_context->PSSetShader(g_ps, nullptr, 0);
    g_context->IASetInputLayout(g_layout);
    
    float x1 = ToNDC_X(x, vp.Width), y1 = ToNDC_Y(y, vp.Height);
    float x2 = ToNDC_X(x + width, vp.Width), y2 = ToNDC_Y(y + height, vp.Height);
    
    // 6 Vertices (2 Dreiecke), je 5 floats: x, y, r, g, b
    float vertices[] = {
        x1, y1, r, g, b,  // Top-Left
        x2, y1, r, g, b,  // Top-Right
        x1, y2, r, g, b,  // Bottom-Left
        x1, y2, r, g, b,  // Bottom-Left
        x2, y1, r, g, b,  // Top-Right
        x2, y2, r, g, b   // Bottom-Right
    };
    
    if (g_vb) g_vb->Release();
    D3D11_BUFFER_DESC bd = { sizeof(vertices), D3D11_USAGE_DEFAULT, D3D11_BIND_VERTEX_BUFFER };
    D3D11_SUBRESOURCE_DATA sd = { vertices };
    g_device->CreateBuffer(&bd, &sd, &g_vb);
    
    UINT stride = 20, offset = 0;  // 5 floats * 4 bytes = 20
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
    if (g_swapChain) g_swapChain->Present(0, 0);  // VSync OFF = max FPS
}

JNIEXPORT jlong JNICALL Java_demo_DemoApp_findWindow(JNIEnv* env, jclass, jstring title) {
    const char* str = nullptr;
    if (title) str = env->GetStringUTFChars(title, nullptr);
    HWND hwnd = FindWindowA(nullptr, str);
    if (title && str) env->ReleaseStringUTFChars(title, str);
    return (jlong)hwnd;
}

// ============================================================================
// BATCH RENDERING mit INSTANCED RENDERING - Das ist der Game-Changer!
// Statt 30 floats pro Rechteck nur noch 7 floats!
// ============================================================================

static ID3D11Buffer* g_batchVB = nullptr;
static size_t g_batchVBSize = 0;
static const size_t MAX_BATCH_RECTS = 100000;  // Max 100K Rechtecke

// Ring-Buffer für Vertices (Fallback)
static float g_vertexRingBuffer[MAX_BATCH_RECTS * 6 * 5];

// === INSTANCED RENDERING ===
static ID3D11Buffer* g_quadVB = nullptr;           // Statischer Quad (4 Vertices)
static ID3D11Buffer* g_instanceVB = nullptr;       // Dynamischer Instanz-Buffer
static ID3D11Buffer* g_screenCB = nullptr;         // Screen Size Constant Buffer
static ID3D11VertexShader* g_vsInstanced = nullptr; // Instanced VS
static ID3D11InputLayout* g_layoutInstanced = nullptr; // Instanced Layout
static size_t g_instanceVBSize = 0;

// Instanz-Daten: x, y, w, h, r, g, b (7 floats)
static float g_instanceData[MAX_BATCH_RECTS * 7];

// Statisches Quad (0,0 bis 1,1)
static float g_quadVertices[] = {
    0.0f, 0.0f,  // Top-Left
    1.0f, 0.0f,  // Top-Right
    0.0f, 1.0f,  // Bottom-Left
    1.0f, 1.0f   // Bottom-Right
};

// ============================================================================
// INSTANCED BATCH RENDERING - Der Game-Changer!
// 76% weniger Daten-Transfer = Massive Performance-Steigerung
// ============================================================================

JNIEXPORT void JNICALL Java_demo_DemoApp_fillRects(JNIEnv* env, jclass, 
    jobject rectDataBuffer, jint count) {
    if (!g_device || count == 0) return;
    if (count > (int)MAX_BATCH_RECTS) count = (int)MAX_BATCH_RECTS;
    
    // Direct Buffer Address - ZERO COPY!
    float* rects = (float*)env->GetDirectBufferAddress(rectDataBuffer);
    if (!rects) return;
    
    // Viewport holen
    D3D11_VIEWPORT vp; UINT num = 1;
    if (g_context) g_context->RSGetViewports(&num, &vp);
    float vw = (float)vp.Width;
    float vh = (float)vp.Height;
    
    // === ERSTMAL: Instanced Rendering Setup (nur einmal) ===
    if (!g_quadVB) {
        // 1. Quad Buffer erstellen (statisch, einmal)
        D3D11_BUFFER_DESC qbd = {};
        qbd.Usage = D3D11_USAGE_IMMUTABLE;
        qbd.ByteWidth = sizeof(g_quadVertices);
        qbd.BindFlags = D3D11_BIND_VERTEX_BUFFER;
        D3D11_SUBRESOURCE_DATA qsd = { g_quadVertices };
        g_device->CreateBuffer(&qbd, &qsd, &g_quadVB);
        
        // 2. Instanced Shader kompilieren
        ID3DBlob* vsBlob = nullptr;
        if (CompileShader(VS_INSTANCED_SRC, "main", "vs_4_0", &vsBlob)) {
            g_device->CreateVertexShader(vsBlob->GetBufferPointer(), vsBlob->GetBufferSize(), nullptr, &g_vsInstanced);
            
            // Input Layout für Instanced Rendering
            D3D11_INPUT_ELEMENT_DESC layout[] = {
                { "POSITION", 0, DXGI_FORMAT_R32G32_FLOAT, 0, 0, D3D11_INPUT_PER_VERTEX_DATA, 0 },
                { "INSTANCE_POS", 0, DXGI_FORMAT_R32G32_FLOAT, 1, 0, D3D11_INPUT_PER_INSTANCE_DATA, 1 },
                { "INSTANCE_SIZE", 0, DXGI_FORMAT_R32G32_FLOAT, 1, 8, D3D11_INPUT_PER_INSTANCE_DATA, 1 },
                { "INSTANCE_COLOR", 0, DXGI_FORMAT_R32G32B32_FLOAT, 1, 16, D3D11_INPUT_PER_INSTANCE_DATA, 1 }
            };
            g_device->CreateInputLayout(layout, 4, vsBlob->GetBufferPointer(), vsBlob->GetBufferSize(), &g_layoutInstanced);
            vsBlob->Release();
        }
        
        // 3. Screen Constant Buffer
        D3D11_BUFFER_DESC cbd = {};
        cbd.Usage = D3D11_USAGE_DYNAMIC;
        cbd.ByteWidth = 16; // float2 + padding
        cbd.BindFlags = D3D11_BIND_CONSTANT_BUFFER;
        cbd.CPUAccessFlags = D3D11_CPU_ACCESS_WRITE;
        g_device->CreateBuffer(&cbd, nullptr, &g_screenCB);
    }
    
    // === INSTANZ-DATEN vorbereiten ===
    // Direkt aus dem Java Buffer kopieren (bereits im richtigen Format!)
    size_t instanceDataSize = count * 7 * sizeof(float);
    memcpy(g_instanceData, rects, instanceDataSize);
    
    // === PERSISTENTER INSTANZ BUFFER ===
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
    
    // Instanz-Daten mappen
    D3D11_MAPPED_SUBRESOURCE mapped;
    if (SUCCEEDED(g_context->Map(g_instanceVB, 0, D3D11_MAP_WRITE_DISCARD, 0, &mapped))) {
        memcpy(mapped.pData, g_instanceData, instanceDataSize);
        g_context->Unmap(g_instanceVB, 0);
    }
    
    // === SCREEN CONSTANT BUFFER updaten ===
    if (SUCCEEDED(g_context->Map(g_screenCB, 0, D3D11_MAP_WRITE_DISCARD, 0, &mapped))) {
        float* cbData = (float*)mapped.pData;
        cbData[0] = vw;
        cbData[1] = vh;
        g_context->Unmap(g_screenCB, 0);
    }
    
    // === INSTANCED RENDERING ===
    g_context->OMSetRenderTargets(1, &g_rtv, nullptr);
    g_context->VSSetShader(g_vsInstanced, nullptr, 0);
    g_context->VSSetConstantBuffers(0, 1, &g_screenCB);
    g_context->PSSetShader(g_ps, nullptr, 0);
    g_context->IASetInputLayout(g_layoutInstanced);
    
    // Zwei Vertex Buffer binden
    ID3D11Buffer* buffers[] = { g_quadVB, g_instanceVB };
    UINT strides[] = { 8, 28 }; // 2 floats, 7 floats
    UINT offsets[] = { 0, 0 };
    g_context->IASetVertexBuffers(0, 2, buffers, strides, offsets);
    g_context->IASetPrimitiveTopology(D3D11_PRIMITIVE_TOPOLOGY_TRIANGLESTRIP);
    
    // 4 Vertices (Quad), count Instanzen = 1 Draw Call für ALLE Rechtecke!
    g_context->DrawInstanced(4, count, 0, 0);
}

// ============================================================================
// DRAW CIRCLES - Für TV Test Pattern Konvergenz-Kreise
// ============================================================================

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
    
    // circleData: x, y, radius, r, g, b pro Kreis (6 floats)
    float* circles = (float*)env->GetDirectBufferAddress(circleData);
    if (!circles) return;
    
    // Viewport holen
    D3D11_VIEWPORT vp; UINT num = 1;
    if (g_context) g_context->RSGetViewports(&num, &vp);
    float vw = (float)vp.Width;
    float vh = (float)vp.Height;
    
    // Kreis als OUTLINE (Line Strip) - wie Java2D drawOval()
    // 64 Segmente für glatten Kreisrand
    const int segments = 64;
    const int vertsPerCircle = segments + 1;  // segments + closing vertex
    
    // Wir brauchen nur Rand-Vertices (kein Center)
    float* vertices = new float[count * vertsPerCircle * 5]; // x, y, r, g, b
    
    int vIdx = 0;
    for (int i = 0; i < count; i++) {
        float cx = circles[i * 6 + 0];
        float cy = circles[i * 6 + 1];
        float radius = circles[i * 6 + 2];
        float cr = circles[i * 6 + 3];
        float cg = circles[i * 6 + 4];
        float cb = circles[i * 6 + 5];
        
        // Berechne Rand-Punkte des Kreises
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
    
    // Vertex Buffer
    size_t bufferSize = count * vertsPerCircle * 5 * sizeof(float);
    if (g_circleVB) g_circleVB->Release();
    
    D3D11_BUFFER_DESC bd = {};
    bd.Usage = D3D11_USAGE_DEFAULT;
    bd.ByteWidth = bufferSize;
    bd.BindFlags = D3D11_BIND_VERTEX_BUFFER;
    D3D11_SUBRESOURCE_DATA sd = { vertices };
    g_device->CreateBuffer(&bd, &sd, &g_circleVB);
    delete[] vertices;
    
    // Line Shader brauchen wir nicht - Standard Shader reicht
    g_context->OMSetRenderTargets(1, &g_rtv, nullptr);
    g_context->VSSetShader(g_vs, nullptr, 0);
    g_context->PSSetShader(g_ps, nullptr, 0);
    g_context->IASetInputLayout(g_layout);
    
    UINT stride = 20; // 5 floats * 4 bytes
    UINT offset = 0;
    g_context->IASetVertexBuffers(0, 1, &g_circleVB, &stride, &offset);
    
    // Line Strip für Outline
    g_context->IASetPrimitiveTopology((D3D11_PRIMITIVE_TOPOLOGY)D3D11_PRIMITIVE_TOPOLOGY_LINESTRIP);
    
    // Debug: Anzahl Kreise und Vertices
    char debugMsg[256];
    sprintf(debugMsg, "DEBUG: Drawing %d circles, %d verts each, topology=%d\n", count, vertsPerCircle, D3D11_PRIMITIVE_TOPOLOGY_LINESTRIP);
    OutputDebugStringA(debugMsg);
    
    // Jeden Kreis einzeln zeichnen (wegen LineStrip)
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

JNIEXPORT void JNICALL Java_fastgraphics_FastGraphics2D_init(JNIEnv*, jclass, jlong hwnd) {
    Java_demo_DemoApp_init(nullptr, nullptr, hwnd);
}

// JNI-Aliases für BenchmarkApp
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

// JNI-Aliases für Benchmark Klasse
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

// JNI-Aliases für Comparator Klasse
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

// JNI-Aliases für TVTestPattern Klasse
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
