# The Philosophy of FastGraphics

> [!IMPORTANT]
> **"Hardware Direct. Zero CPU Rasterization. Single Draw Call. Native-First 2D."**

FastGraphics was created because standard `java.awt.Graphics2D` and Java2D are fundamentally architected around immediate-mode CPU rasterization and heavyweight JVM heap abstractions.

## Core Tenets

1. **Direct GPU Swapchains**
   Instead of rasterizing into a JVM `BufferedImage` and copying bytes across memory buses via GDI or DirectDraw, FastGraphics hooks directly into DirectX 11 HWND swapchains with double/triple buffering.

2. **Single Draw Call Batching**
   Java2D spends significant CPU time dispatching individual drawing primitives. FastGraphics queues geometry into packed off-heap buffers and dispatches hundreds of thousands of shapes in single instanced GPU draw calls.

3. **Zero Garbage Collection Overhead**
   Every animation frame must have deterministic timing. FastGraphics avoids creating short-lived Java `Shape`, `Color`, or `Transform` objects during rendering loops.

4. **Familiar Java Developer Experience**
   Developers familiar with `java.awt.Graphics2D` should feel immediately at home with `setColor`, `fillRect`, `drawOval`, and matrix transforms without having to write low-level HLSL shaders.

5. **Unified FastJava Foundation**
   FastGraphics integrates natively with `FastCore` for native binary extraction and pairs seamlessly with `FastGPU`, `FastImage`, and `FastUI`.

---
**⚡ FastGraphics — Powering real-time GPU graphics on the JVM.**
