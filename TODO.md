# FastGraphics - TODO List

This document tracks remaining features needed to make FastGraphics a complete Graphics2D replacement.

## Priority: High - Core Graphics2D Features

### Alpha Compositing & Transparency
- [ ] Implement `setComposite(Composite comp)` method
  - [ ] Support `AlphaComposite` for transparency blending
  - [ ] Implement blend modes (SRC_OVER, SRC_IN, DST_OVER, etc.)
  - [ ] Add alpha channel support to vertex shader
  - [ ] Enable blending in DirectX 11 (D3D11_BLEND_DESC)
  - [ ] Test transparency with overlapping shapes

### Color with Alpha
- [ ] Extend `setColor(Color c)` to handle alpha channel
  - [ ] Pass alpha from Color to native layer
  - [ ] Store alpha in vertex data
  - [ ] Update shader to use alpha in output
  - [ ] Test semi-transparent shapes

### Text Rendering
- [ ] Implement `drawString(String str, float x, float y)` properly
  - [ ] Add font texture atlas generation
  - [ ] Implement textured shader for text
  - [ ] Add font metrics calculation
  - [ ] Support basic font loading (system fonts)
  - [ ] Implement text positioning and rendering
  - [ ] Add support for `setFont(Font f)`

### Image Rendering
- [ ] Implement `drawImage(Image img, int x, int y, int w, int h)` properly
  - [ ] Add texture loading from BufferedImage/InputStream
  - [ ] Implement textured shader for images
  - [ ] Support image scaling and transformations
  - [ ] Add texture cache/reuse
  - [ ] Support multiple image formats (PNG, JPEG)

## Priority: Medium - Advanced Graphics Features

### Clipping
- [ ] Implement proper clipping with `setClip(Shape clip)` and `setClip(int x, int y, int w, int h)`
  - [ ] Implement Scissor Rect support in DirectX 11
  - [ ] Alternative: Stencil Buffer for complex clipping shapes
  - [ ] Test clipping with various shapes
  - [ ] Implement `getClip()` to retrieve current clip

### Line Width & Stroke
- [ ] Implement proper `setStroke(Stroke s)` functionality
  - [ ] Support line width (basic stroke width)
  - [ ] Support dashed lines
  - [ ] Support line caps (ROUND, BUTT, SQUARE)
  - [ ] Support line joins (ROUND, BEVEL, MITER)
  - [ ] Update geometry generation for thick lines

### Anti-Aliasing
- [ ] Implement proper anti-aliasing support
  - [ ] Configure MSAA in Swap Chain (runtime switch not possible, needs re-init)
  - [ ] Alternative: FXAA post-processing shader
  - [ ] Alternative: Supersampling (render at higher resolution)
  - [ ] Document anti-aliasing limitations and workarounds

### Rounded Rectangles
- [ ] Implement proper `drawRoundRect()` and `fillRoundRect()`
  - [ ] Fix geometry generation for rounded corners
  - [ ] Implement arc segments for corners
  - [ ] Handle edge cases (arc width/height > dimensions)
  - [ ] Test with various arc sizes

## Priority: Low - Additional Graphics2D Features

### Gradient Fills
- [ ] Implement `GradientPaint` support
  - [ ] Add gradient shader
  - [ ] Support linear gradients
  - [ ] Support radial gradients
  - [ ] Add gradient stops and interpolation

### Pattern Fills
- [ ] Implement `TexturePaint` support
  - [ ] Tile textures for pattern fills
  - [ ] Handle pattern transformations

### Advanced Shapes
- [ ] Implement `draw(Shape s)` for arbitrary shapes
  - [ ] Support `Path2D` rendering
  - [ ] Implement bezier curves
  - [ ] Support quadratic and cubic curves

### Rendering Hints
- [ ] Implement additional rendering hints
  - [ ] KEY_RENDERING_QUALITY
  - [ ] KEY_STROKE_CONTROL
  - [ ] KEY_TEXT_ANTIALIASING
  - [ ] KEY_FRACTIONALMETRICS
  - [ ] KEY_INTERPOLATION

## Priority: Medium - Performance & Architecture

### Batch Optimization
- [ ] Implement automatic batching for same-color operations
  - [ ] Group fillRect calls by color
  - [ ] Reduce draw calls through batching
  - [ ] Implement batch flush on state changes

### Resource Management
- [ ] Add proper resource cleanup
  - [ ] Implement dispose() method
  - [ ] Release DirectX resources on cleanup
  - [ ] Add reference counting for shared resources

### Error Handling
- [ ] Add comprehensive error handling
  - [ ] Validate input parameters
  - [ ] Handle DirectX initialization failures
  - [ ] Add meaningful error messages
  - [ ] Implement graceful fallbacks

## Priority: Low - Cross-Platform & Ecosystem

### Linux Support
- [ ] Port to OpenGL/Vulkan backend
  - [ ] Abstract graphics backend interface
  - [ ] Implement OpenGL renderer
  - [ ] Test on Linux distributions

### macOS Support
- [ ] Port to Metal backend
  - [ ] Implement Metal renderer
  - [ ] Test on macOS

### Documentation
- [ ] Add Javadoc for all public methods
- [ ] Create more usage examples
- [ ] Add performance tuning guide
- [ ] Document limitations and workarounds

### Testing
- [ ] Add comprehensive unit tests
- [ ] Add integration tests
- [ ] Add performance regression tests
- [ ] Test on various hardware configurations

## Completed Features

- ✅ Basic shapes: fillRect, drawRect, fillOval, drawOval, drawLine, drawPolygon, fillPolygon, drawArc, fillArc
- ✅ Transformations: translate, scale, rotate, resetTransform
- ✅ Basic color support (solid colors only)
- ✅ Clear and present operations
- ✅ Instanced rendering for batch performance
- ✅ Window handle finding
- ✅ Basic API structure matching Graphics2D
