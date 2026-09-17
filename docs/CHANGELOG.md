# Changelog

All notable changes to **FastGraphics** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]
- Vulkan 1.3 2D rendering pipeline integration
- Hardware-accelerated GPU font rendering via SDF

## [0.1.0] - 2026-09-17
### Added
- Native DirectX 11 backend hooking directly into Win32 HWND swapchains
- Automatic single-draw-call batching engine
- Instanced shape rendering for rectangles and rounded rectangles
- 32-bit ARGB alpha transparency and blending support
- Hardware scissor rect clipping (`setClip` / `resetClip`)
- GPU texture caching for `drawImage`
- JMH performance benchmark suite (`Benchmark.java`)
- Full documentation suite (`REFERENCE.md`, `PHILOSOPHY.md`, `ROADMAP.md`, `COMPILE.md`)
