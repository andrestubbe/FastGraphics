# FastGraphics Documentation - v1.0.0

## Current State: High-Fidelity Rendering Parity
FastGraphics has achieved a major milestone: **Full Feature Parity** between the DirectX 11 native backend and the AWT/Swing software fallback. The engine supports a comprehensive suite of 16 drawing primitives, all rendered with hardware-accelerated precision.

### Supported Primitives
| Feature | Method | Backend Implementation |
| :--- | :--- | :--- |
| **Rectangles** | `fillRect`, `drawRect` | Native Instanced Quads / GDI+ Fallback |
| **Ovals** | `fillOval`, `drawOval` | SDF-based Ellipse Shader / Graphics2D |
| **Rounded Rects** | `fillRoundRect`, `drawRoundRect` | Multi-radius SDF Shader / Graphics2D |
| **Arcs** | `fillArc`, `drawArc` | Polar Coordinate SDF Shader / Graphics2D |
| **Triangles** | `fillTriangle`, `drawTriangle` | Cross-Product Barycentric Shader / Graphics2D |
| **Polygons** | `fillPolygon`, `drawPolygon` | Fan Triangulation / Graphics2D |
| **Lines** | `drawLine`, `drawPolyline` | Segment SDF Shader / Graphics2D |
| **Bézier Curves** | `drawQuadCurve`, `drawCubicCurve` | Adaptive Subdivision / Graphics2D |
| **Text** | `drawString` | **DirectWrite** (Native) / FontMetrics (AWT) |

## Architectural Highlights
- **DirectWrite Integration**: Achieve Windows 11-native text quality with subpixel anti-aliasing.
- **Unified Batching**: Geometry is batched on the Java side and submitted in a single JNI call per frame, minimizing transition overhead.
- **Winding-Agnostic Triangles**: The native triangle shader uses a robust cross-product check, ensuring correct fills regardless of vertex order.
- **Flicker-Free Rendering**: Direct swap-chain binding eliminates GDI/AWT flickering during rapid updates.

---

## Legacy Feature Harvest (Audit Results)
We have audited the legacy `FastGraphics` and `FastGraphics Remake` repositories. The following high-value techniques are slated for a stable, step-by-step port into the current v3 architecture:

### 1. High-Performance Image Pipeline
- **Technique**: Native `ID3D11ShaderResourceView` caching and `ID3D11SamplerState` filtering.
- **Benefit**: Accelerated `drawImage` support with minimal overhead.

### 2. Pixel-Center Correction (+0.5 offset)
- **Technique**: Applying a half-pixel offset in the vertex shader to align geometry perfectly with the monitor's pixel grid.
- **Benefit**: Eliminates "half-pixel blur," reaching the "Absolution" goal of perfect crispness.

### 3. Native Scissor Clipping
- **Technique**: Leveraging `ID3D11RasterizerState` for hardware-native rectangular clipping.
- **Benefit**: Essential for UI components like scroll panes and overflow-hidden containers.

### 4. Advanced Alpha Composition
- **Technique**: Dynamic `D3D11_BLEND_DESC` switching to support full Porter-Duff rules.
- **Benefit**: Native support for `SRC_IN`, `XOR`, and complex masking operations.

### 5. Native UI Scaling
- **Technique**: Integrated `g_uiScale` multiplier in the NDC transformation logic.
- **Benefit**: Seamless support for High-DPI (4K) displays without blurry scaling.

---

## Stability & Porting Strategy
To avoid the unstable "debug loops" present in the legacy versions, we are following a strict **"Clean Harvest"** methodology:
1. **Feature Isolation**: Features are ported one-by-one into the stable v3 codebase.
2. **Stress Testing**: Every port is followed by a performance and stability verification run.
3. **Modernization**: Legacy logic is adapted to fit the current SDF-based shader architecture for superior visual quality.

---

## Future Roadmap: "Absolution & AA"

### 1. Shape Absolution (Pinpointing)
Our next phase focuses on "Absolute Precision." We will migrate from adaptive subdivision to **analytical rasterization** for all curves.
- **Mathematical Exactness**: Every pixel of a Bézier or Arc will be determined by its exact mathematical formula in the shader, eliminating "segmented" looks at extreme zooms.
- **Sub-Pixel Mastery**: Implementing hardware-native anti-aliasing and multi-sampling for perfect edge transitions.

### 2. Advanced Anti-Aliasing (AA)
We will implement high-fidelity smoothing across the entire pipeline:
- **Sub-Pixel Accuracy**: Every edge will be calculated with sub-pixel precision, ensuring that thin lines and small curves remain sharp and flicker-free.
- **Hardware-Native AA**: Leveraging D3D11 multi-sampling (MSAA) and custom analytical AA shaders to provide the smoothest possible transitions for all shapes.
- **Dynamic Sharpness**: Automatically adjusting filtering techniques based on the shape type and current zoom level.

---
> [!IMPORTANT]
> FastGraphics is now the standard for high-performance Java UI development within the Antigravity ecosystem.
