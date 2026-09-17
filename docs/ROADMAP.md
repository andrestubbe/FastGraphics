# FastGraphics Roadmap 🗺️

**Vision:** Provide the fastest, lowest-latency 2D GPU rendering engine on the JVM for real-time visualization, trading screens, and desktop user interfaces.

## 🟢 v0.1.0: DirectX 11 Core (Current)
- [x] **DirectX 11 Backend**: Win32 HWND direct swapchain creation.
- [x] **Automatic Batching**: Batch renderer for rectangles, lines, and ovals.
- [x] **Alpha Blending**: 32-bit ARGB transparency.
- [x] **Texture Ingestion**: Fast `drawImage` with native GPU texture caching.
- [x] **Scissor Clipping**: Direct HWND scissor rectangle support.
- [x] **Blueprint Standardization**: README, Reference, Philosophy, and Benchmark unification.

## 🟡 v0.2.0: Vulkan & Cross-Platform Backend
- [ ] **Vulkan 1.3 2D Pipeline**: Cross-platform rendering path for Linux and Windows.
- [ ] **MSAA Anti-Aliasing**: Configurable multi-sample anti-aliasing on swapchain creation.
- [ ] **GPU Text Rendering**: Signed Distance Field (SDF) or FreeType GPU font rasterizer.

## 🟠 v0.5.0: Metal & Advanced Effects
- [ ] **macOS Metal Backend**: Native Apple Silicon Metal support.
- [ ] **Custom Shaders**: Expose programmable HLSL/GLSL compute and fragment filters.
- [ ] **Path Geometry**: Full Bézier curve and SVG path hardware tessellation.

## 🔴 v1.0.0: Production Hardening
- [ ] **Full Java2D Drop-in Compatibility**: Full coverage of `Graphics2D` interface methods.
- [ ] **Headless & Off-Screen Buffers**: GPU-accelerated rendering to off-screen shared DirectX/Vulkan textures.
