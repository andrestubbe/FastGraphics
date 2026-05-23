# FastGraphics — Ultra-Fast Native Graphics Engine for Java [v0.1.0]

**A high-performance native rendering module for the FastJava ecosystem. Accelerated drawing, blending, and image manipulation via DirectX and SIMD.**

[![Status](https://img.shields.io/badge/status-v0.1.0--alpha-orange.svg)]()
[![Java](https://img.shields.io/badge/Java-17+-blue.svg)](https://www.java.com)
[![Platform](https://img.shields.io/badge/Platform-Windows%2010+-lightgrey.svg)]()
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

---

**FastGraphics** bypasses the limitations of standard Java Graphics2D. It provides a direct pipeline to native rendering hardware, enabling complex visual effects and high-speed drawing with zero JVM lag.

## Table of Contents
- [Features](#features)
- [Installation](#installation)
- [License](#license)

## Features
- **⚡ Hardware Accelerated**: Direct drawing via DirectX/GDI+ native pipelines.
- **🎨 Advanced Blending**: High-speed alpha blending and transparency.
- **📦 Zero GC Stalls**: Native memory buffers for rendering assets.
- **🚀 Raw Speed**: Built for real-time visualization and complex motion graphics.

## Installation

### Option 1: Maven (Recommended)
Add the JitPack repository and the dependencies to your `pom.xml`:

`xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <!-- FastGraphics Library -->
    <dependency>
        <groupId>com.github.andrestubbe</groupId>
        <artifactId>fastgraphics</artifactId>
        <version>v0.1.0</version>
    </dependency>
    <!-- FastCore (Required Native Loader) -->
    <dependency>
        <groupId>com.github.andrestubbe</groupId>
        <artifactId>fastcore</artifactId>
        <version>v0.1.0</version>
    </dependency>
</dependencies>
`

### Option 2: Gradle (via JitPack)
`groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.andrestubbe:.1.0'
    implementation 'com.github.andrestubbe:fastcore:v0.1.0'
}
`

### Option 3: Direct Download (No Build Tool)
Download the latest JARs directly to add them to your classpath:

1. 📦 **[fastgraphics-v0.1.0.jar](https://github.com/andrestubbe/FastGraphics/releases/download/v0.1.0/fastgraphics-v0.1.0.jar)** (The Core Library)
2. ⚙️ **[fastcore-v0.1.0.jar](https://github.com/andrestubbe/FastCore/releases/download/v0.1.0/fastcore-v0.1.0.jar)** (The Mandatory Native Loader)

> [!IMPORTANT]
> All JARs must be in your classpath for the native JNI calls to function correctly.


## License
MIT License — See [LICENSE](LICENSE) for details.

---
**Part of the FastJava Ecosystem** — *Making the JVM faster.*
