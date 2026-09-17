> [!WARNING]
> **🚧 WIP — Active Rendering Pipeline Refactoring & DirectX 11 / Vulkan Modernization in Progress.**

# FastGraphics 0.1.0 [ALPHA] — High-Performance GPU-Accelerated Graphics2D for Java

[![Status](https://img.shields.io/badge/status-0.1.0-brightgreen.svg)](https://github.com/andrestubbe/FastGraphics/releases/tag/0.1.0)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17+-blue.svg)](https://www.java.com)
[![Platform](https://img.shields.io/badge/Platform-Windows%2010+-lightgrey.svg)]()
[![JitPack](https://img.shields.io/badge/JitPack-ready-green.svg)](https://jitpack.io/#andrestubbe/FastGraphics)

---

**⚡ High-performance GPU-accelerated 2D rendering engine replacing java.awt.Graphics2D with native DirectX 11 and Vulkan pipelines.**

**FastGraphics** delivers ultra-fast native 2D rendering directly on the JVM. By bypassing Java2D CPU rasterization bottlenecks and utilizing single-draw-call automatic batching and instanced rendering, it achieves 1,000+ FPS across tens of thousands of geometric primitives.

---

## Quick Start

```java
import fastgraphics.FastGraphics2D;
import javax.swing.JFrame;
import java.awt.Color;

public class Demo {
    public static void main(String[] args) {
        // 1. Create native window
        JFrame frame = new JFrame("FastGraphics Demo");
        frame.setSize(800, 600);
        frame.setVisible(true);

        // 2. Obtain native window handle & initialize FastGraphics2D
        long hwnd = FastGraphics2D.findWindow("FastGraphics Demo");
        FastGraphics2D g = new FastGraphics2D(hwnd);

        // 3. Render loop with single GPU draw-call batching
        while (frame.isVisible()) {
            g.setColor(Color.BLACK);
            g.clear();

            g.setColor(Color.RED);
            g.fillRect(10, 10, 100, 50);

            g.setColor(new Color(0, 200, 255, 180));
            g.fillRoundRect(200, 100, 150, 100, 20, 20);

            g.present();  // 1 Draw Call for all queued geometry!
        }
    }
}
```

---

## Table of Contents

- [Why FastGraphics?](#why-fastgraphics)
- [Key Features](#key-features)
- [Real-World Use Cases](#real-world-use-cases)
- [Performance Benchmarks](#performance-benchmarks)
- [API Quick Reference](#api-quick-reference)
- [Technical Demos & Benchmarks](#technical-demos--benchmarks)
- [Installation](#installation)
- [Documentation](#documentation)
- [Platform Support](#platform-support)
- [License](#license)
- [Related Projects](#related-projects)

---

## Why FastGraphics?

`java.awt.Graphics2D` is convenient but fundamentally CPU-bound. Its immediate-mode API creates severe CPU bottlenecks, and Java2D's software rasterizer limits performance to ~100–200 simple shapes per frame before dropping below 60 FPS:

- **Immediate-Mode CPU Bottlenecks** — Standard Java2D submits individual drawing commands sequentially, wasting CPU cycles on state synchronization.
- **High Garbage Collection Pressure** — Generating temporary shape, transform, and color objects floods the JVM young generation during high-frequency animation loops.
- **Bandwidth Starvation** — Expanding 2D vertices on the CPU and re-uploading geometry every frame consumes memory bus bandwidth.

FastGraphics pairs direct Win32 DirectX 11 hardware swapchains with zero-allocation off-heap batching:

| Feature | `java.awt.Graphics2D` | JavaFX `GraphicsContext` | FastGraphics |
|:---|:---|:---|:---|
| **Render Backend** | Java2D (CPU rasterizer / GDI)| Prism (OpenGL / D3D9) | **DirectX 11 / Vulkan native GPU** |
| **Draw Call Model** | Immediate per-shape CPU call | Retained scene graph overhead | **Single-draw-call automatic batching** |
| **Max Shapes @ 60 FPS** | ~1,000 shapes | ~5,000 shapes | **50,000+ shapes (> 1,000 FPS)** |
| **Instanced Rendering** | Not supported | Not supported | **76% less GPU bandwidth via instancing** |
| **Memory Pressure** | High JVM heap allocations | Moderate object churn | **Zero GC (Direct ByteBuffers)** |
| **Dependencies** | JDK standard lib | Bulky JavaFX runtime (>50 MB) | **Pure Java 17+ backed by FastCore** |

---

## Key Features

- 🚀 **Hardware-Accelerated DirectX 11 Pipeline** — Renders directly to HWND swapchains with sub-millisecond frame latencies.
- ⚡ **Automatic Single-Draw-Call Batching** — Automatically merges consecutive geometry operations without requiring manual `beginBatch()` / `endBatch()` blocks.
- 📦 **Instanced Shape Rendering** — Transmits per-instance transformations and attributes, reducing vertex bus traffic by up to **76%**.
- 🧠 **Zero Garbage Collection Overhead** — Executes rendering using pre-allocated off-heap `ByteBuffer` and `FloatBuffer` pools.
- 🎨 **Alpha Blending & Anti-Aliasing** — Full 32-bit ARGB transparency support and DirectX 11 MSAA multi-sampling.
- 🖼️ **GPU Texture Caching** — Fast `drawImage` ingestion with native GPU texture caching.

---

## Real-World Use Cases

- 🎮 **2D Game Engines & Simulators**: Render thousands of animated particles, sprites, and bullet-hell entities at over 1,000 FPS.
- 📊 **High-Frequency Financial Charts & Trading Terminals**: Draw millisecond-level candlestick feeds, order books, and real-time tick overlays without UI lag.
- 🔬 **Scientific Visualization & Signal Scopes**: Plot massive waveform feeds from `FastAudioProcess` or high-rate sensor streams from `FastHardware`.
- 🪟 **High-Refresh Desktop UIs**: Power custom lightweight GUI toolkits with smooth 120Hz/360Hz window composition.

---

## Performance Benchmarks

In real-time rendering stress tests measuring fillRect throughput on Windows 11 (RTX 3070, Java 17, 360Hz display):

| Shape Count | Java2D (`java.awt.Graphics2D`) | FastGraphics (DirectX 11) | Speedup |
|:---|:---|:---|:---|
| **1,000 Rectangles** | ~120 FPS | **5,335 FPS** | **44× faster** |
| **5,000 Rectangles** | ~60 FPS | **6,056 FPS** | **100× faster** |
| **10,000 Rectangles** | ~40 FPS | **4,585 FPS** | **114× faster** |
| **50,000 Rectangles** | ~5 FPS | **1,060 FPS** | **212× faster** |

In the official [JMH Benchmark](examples/Benchmark), raw fillRect invocation throughput was verified:

```text
Benchmark                                    Mode  Cnt     Score     Error   Units
Benchmark.benchmarkFastGraphicsBatchFillRect thrpt    3  1428.512 ± 112.430  ops/ms
Benchmark.benchmarkJava2DFillRect            thrpt    3   118.230 ±  14.120  ops/ms
```

> **12× Microbenchmark Invocation Throughput**: FastGraphics batches drawing commands in off-heap memory, bypassing Java2D lock contention and pipeline flushes.

---

## API Quick Reference

| Method / Class | Return Type | Description |
|:---|:---|:---|
| `new FastGraphics2D(hwnd)` | `FastGraphics2D` | Creates hardware-accelerated rendering context for native window. |
| `g.setColor(Color c)` | `void` | Sets current drawing color (RGB / ARGB with alpha). |
| `g.fillRect(x, y, w, h)` | `void` | Fills rectangle (batched GPU draw call). |
| `g.fillOval(x, y, w, h)` | `void` | Fills oval or circle. |
| `g.drawRect(x, y, w, h)` | `void` | Draws outline rectangle. |
| `g.drawOval(x, y, w, h)` | `void` | Draws outline oval or circle. |
| `g.drawLine(x1, y1, x2, y2)` | `void` | Draws 2D line segment. |
| `g.drawRoundRect(x, y, w, h, rw, rh)` | `void` | Draws outline rounded rectangle. |
| `g.fillRoundRect(x, y, w, h, rw, rh)` | `void` | Fills rounded rectangle with corner radii. |
| `g.drawPolygon(xPoints, yPoints)` | `void` | Draws outline polygon. |
| `g.fillPolygon(xPoints, yPoints)` | `void` | Fills convex polygon. |
| `g.drawImage(img, x, y, w, h)` | `void` | Draws image with GPU texture caching. |
| `g.setClip(x, y, w, h)` | `void` | Configures hardware scissor rectangle clipping. |
| `g.resetClip()` | `void` | Clears clipping rectangle. |
| `g.translate(tx, ty)` | `void` | Applies translation matrix. |
| `g.scale(sx, sy)` | `void` | Applies scale matrix. |
| `g.rotate(angle)` | `void` | Applies rotation matrix. |
| `g.clear()` / `g.clear(Color c)` | `void` | Clears background color buffer. |
| `g.present()` | `void` | Flushes queued batches and presents swapchain frame. |

---

## Technical Demos & Benchmarks

Run standalone verification demos or execute JMH throughput benchmarks:

| Type | Target / Launcher | Source File | Description |
| :--- | :--- | :--- | :--- |
| **Interactive Demo** | [`run-demo.bat`](run-demo.bat) | [`Demo.java`](src/demo/Demo.java) | 10,000 particle simulation and real-time DirectX 11 vs AWT comparison |
| **TV Test Pattern Demo** | [`run_imagezoom.bat`](run_imagezoom.bat) | [`Comparator.java`](src/demo/Comparator.java) | Side-by-side pixel-perfect calibration test pattern |
| **Throughput Benchmark** | [`run-benchmark.bat`](run-benchmark.bat) | [`Benchmark.java`](examples/Benchmark/src/main/java/fastgraphics/benchmark/Benchmark.java) | JMH benchmark evaluating batched fillRect throughput |

---

## Installation

### Option 1: Maven (Recommended via JitPack)

Add the JitPack repository and dependency to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.andrestubbe</groupId>
        <artifactId>FastGraphics</artifactId>
        <version>0.1.0</version>
    </dependency>
    <dependency>
        <groupId>com.github.andrestubbe</groupId>
        <artifactId>FastCore</artifactId>
        <version>0.1.0</version>
    </dependency>
</dependencies>
```

### Option 2: Gradle (via JitPack)

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.andrestubbe:FastGraphics:0.1.0'
    implementation 'com.github.andrestubbe:FastCore:0.1.0'
}
```

---

## Documentation

- **[REFERENCE.md](docs/REFERENCE.md)**: Full API contracts, JNI signatures, and memory layouts.
- **[PHILOSOPHY.md](docs/PHILOSOPHY.md)**: GPU-first, zero-allocation, and single-draw-call architectural principles.
- **[ROADMAP.md](docs/ROADMAP.md)**: Future milestones (Vulkan 2D, Metal, and GPU SDF font rasterization).
- **[CHANGELOG.md](docs/CHANGELOG.md)**: Version history, release notes, and migration guides.
- **[COMPILE.md](docs/COMPILE.md)**: Native C++ DirectX 11 backend compilation guide and build scripts.

---

## Platform Support

| Platform | Architecture | Status | Notes |
|:---|:---|:---|:---|
| Windows 10/11 | x64, ARM64 | ✅ Fully Supported | Direct Win32 / DirectX 11 hardware swapchain |
| Linux | x64, ARM64 | 🚧 Planned | Vulkan / OpenGL backend |
| macOS | Apple Silicon, x64 | 🚧 Planned | Metal backend |

---

## License

MIT License — See [LICENSE](LICENSE) file for details.

---

## Related Projects

- [FastGPU](https://github.com/andrestubbe/FastGPU) — Native GPU acceleration and Vulkan/Metal compute kernels
- [FastImage](https://github.com/andrestubbe/FastImage) — SIMD-accelerated image decoding and image processing
- [FastUI](https://github.com/andrestubbe/FastUI) — High-performance immediate-mode desktop UI framework
- [FastCore](https://github.com/andrestubbe/FastCore) — Unified JNI loader and native extraction runtime

---

**Part of the FastJava Ecosystem** — *Making the JVM faster. Small package. Maximum speed. Zero bloat. 🚀📋*
