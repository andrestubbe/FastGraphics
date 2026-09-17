# FastGraphics Reference Manual

## 1. Architecture & Core Concepts
FastGraphics is a high-performance GPU-accelerated 2D graphics engine providing a DirectX 11 / DXGI swapchain rendering pipeline for Java applications.

* **Single-Draw-Call Automatic Batching**: Geometry commands (`fillRect`, `fillOval`, `drawRoundRect`, etc.) are recorded into pre-allocated off-heap buffers without immediate GPU state switches. Batch flushes happen automatically on color changes or explicitly upon `present()`.
* **Instanced Geometry Streaming**: Rectangles and textured quads leverage hardware instanced rendering, transferring 76% less bandwidth than CPU-expanded triangle lists.
* **Zero JVM Garbage Collection**: Drawing calls operate on primitive parameters and direct off-heap memory, producing zero heap allocations during the animation loop.

## 2. API Contract & Core Methods

### Initialization & Presenting
* `FastGraphics2D(long hwnd)`: Attaches a DirectX 11 rendering context to the native window handle.
* `findWindow(String title)`: Win32 utility function to find top-level HWND by title.
* `clear()` / `clear(Color c)`: Clears back-buffer render target.
* `present()`: Flushes all queued batch commands to the GPU and executes `IDXGISwapChain::Present`.

### Geometry Operations
* `setColor(Color c)`: Sets active color. Supports 32-bit ARGB transparency.
* `fillRect(float x, float y, float w, float h)`: Batched solid rectangle.
* `fillOval(float x, float y, float w, float h)`: Solid ellipse/circle.
* `drawRect(float x, float y, float w, float h)`: Rectangle wireframe outline.
* `drawOval(float x, float y, float w, float h)`: Ellipse wireframe outline.
* `drawLine(float x1, float y1, float x2, float y2)`: 2D line primitive.
* `drawRoundRect(float x, float y, float w, float h, float rw, float rh)`: Outline rounded rectangle.
* `fillRoundRect(float x, float y, float w, float h, float rw, float rh)`: Filled rounded rectangle with corner radii.
* `drawPolygon(float[] xPoints, float[] yPoints, int nPoints)`: Outline polygon.
* `fillPolygon(float[] xPoints, float[] yPoints, int nPoints)`: Filled convex polygon.

### Textures & Images
* `drawImage(BufferedImage img, float x, float y, float w, float h)`: Uploads or updates GPU texture cache and renders textured quad.
* `loadTexture(int[] pixels, int width, int height)`: Returns native GPU texture ID.
* `unloadTexture(int textureId)`: Releases GPU texture allocation.

### Clipping & Transforms
* `setClip(float x, float y, float w, float h)`: Maps to DirectX 11 scissor rect (`RSSetScissorRects`).
* `resetClip()`: Clears active scissor rectangle.
* `translate(float tx, float ty)`: Multiplies translation matrix.
* `scale(float sx, float sy)`: Multiplies scale matrix.
* `rotate(float angle)`: Multiplies 2D rotation matrix.
* `resetTransform()`: Resets world transform matrix to identity.

## 3. Platform & Hardware Guarantees
* **DirectX Level**: DirectX 11.0 feature level minimum.
* **Thread Safety**: Graphic contexts are bound to the creating thread or must be synchronized when sharing swapchains across threads.
* **Platform**: Windows 10/11 x64 and ARM64.

---
**Part of the FastJava Ecosystem** — *Making the JVM faster.*
