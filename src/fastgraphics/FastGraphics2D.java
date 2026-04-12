package fastgraphics;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * FastGraphics2D - GPU-beschleunigtes Graphics2D mit Batching
 * 
 * API kompatibel zu java.awt.Graphics2D:
 * - setColor(Color c)
 * - fillRect(int x, int y, int w, int h)
 * - clear()
 * - present()
 * 
 * Intern: Batching für maximale Performance
 */
public class FastGraphics2D {
    
    // Native Methoden
    private static native void init(long hwnd);
    private static native void renderBatch(float[] vertices, int count);
    private static native void clearNative(float r, float g, float b);
    private static native void presentNative();
    private static native long findWindowNative(String title);
    private static native void drawRectNative(float x, float y, float w, float h, float r, float g, float b, float a);
    private static native void fillRectNative(float x, float y, float w, float h, float r, float g, float b, float a);
    private static native void fillOvalNative(float x, float y, float w, float h, float r, float g, float b, float a);
    private static native void drawOvalNative(float x, float y, float w, float h, float r, float g, float b, float a);
    private static native void drawLineNative(float x1, float y1, float x2, float y2, float r, float g, float b, float a);
    private static native void drawPolygonNative(float[] xPoints, float[] yPoints, int nPoints, float r, float g, float b, float a);
    private static native void fillPolygonNative(float[] xPoints, float[] yPoints, int nPoints, float r, float g, float b, float a);
    private static native void drawArcNative(float x, float y, float w, float h, float startAngle, float arcAngle, float r, float g, float b, float a);
    private static native void fillArcNative(float x, float y, float w, float h, float startAngle, float arcAngle, float r, float g, float b, float a);
    private static native void drawRoundRectNative(float x, float y, float w, float h, float arcWidth, float arcHeight, float r, float g, float b, float a);
    private static native void fillRoundRectNative(float x, float y, float w, float h, float arcWidth, float arcHeight, float r, float g, float b, float a);
    private static native void translateNative(float tx, float ty);
    private static native void scaleNative(float sx, float sy);
    private static native void rotateNative(float angle);
    private static native void resetTransformNative();
    private static native void setStrokeNative(float lineWidth);
    private static native void setAntiAliasingNative(boolean enabled);
    private static native void setClipNative(float x, float y, float w, float h);
    private static native void resetClipNative();
    private static native void drawStringNative(String str, float x, float y, float r, float g, float b, float a);
    private static native void drawImageNative(float x, float y, float w, float h);
    private static native int loadTextureNative(int[] pixels, int width, int height);
    private static native void unloadTextureNative(int textureId);
    private static native void drawTexturedQuadNative(int textureId, float x, float y, float w, float h);
    
    static { System.loadLibrary("FastGraphics"); }
    
    // Aktueller Zeichen-Zustand
    private Color currentColor = Color.BLACK;
    
    // Rendering Hints
    private Object antiAliasing = RenderingHints.VALUE_ANTIALIAS_DEFAULT;
    
    // Texture Cache: BufferedImage -> DirectX Texture ID
    private final Map<BufferedImage, Integer> textureCache = new WeakHashMap<>();
    
    // Batch-Buffer: 6 Vertices pro Rechteck (2 Dreiecke)
    // Format: x, y, r, g, b, a für jeden Vertex
    private float[] batchBuffer;
    private int maxRects;
    private int currentRectCount = 0;
    
    // Batch-Größe (1024 Rechtecke = guter Sweet-Spot)
    private static final int DEFAULT_BATCH_SIZE = 1024;
    private static final int VERTICES_PER_RECT = 6;
    private static final int FLOATS_PER_VERTEX = 6; // x, y, r, g, b, a
    private static final int FLOATS_PER_RECT = VERTICES_PER_RECT * FLOATS_PER_VERTEX;
    
    public FastGraphics2D(long hwnd, int batchSize) {
        init(hwnd);
        this.maxRects = batchSize;
        this.batchBuffer = new float[maxRects * FLOATS_PER_RECT];
    }
    
    public FastGraphics2D(long hwnd) {
        this(hwnd, DEFAULT_BATCH_SIZE);
    }
    
    /**
     * Setzt die aktuelle Farbe (wie Graphics2D.setColor)
     */
    public void setColor(Color c) {
        this.currentColor = c;
    }
    
    /**
     * Zeichnet ein gefülltes Rechteck (wie Graphics2D.fillRect)
     * Wird gebatcht, nicht sofort gerendert!
     */
    public void fillRect(float x, float y, float w, float h) {
        if (currentRectCount >= maxRects) {
            flush(); // Buffer voll, erst rendern
        }

        float r = currentColor.getRed() / 255.0f;
        float g = currentColor.getGreen() / 255.0f;
        float b = currentColor.getBlue() / 255.0f;
        float a = currentColor.getAlpha() / 255.0f;

        // 2 Dreiecke für ein Rechteck (Triangle List)
        // Dreieck 1: Top-Left, Top-Right, Bottom-Left
        addVertex(x, y, r, g, b, a);
        addVertex(x + w, y, r, g, b, a);
        addVertex(x, y + h, r, g, b, a);

        // Dreieck 2: Top-Right, Bottom-Right, Bottom-Left
        addVertex(x + w, y, r, g, b, a);
        addVertex(x + w, y + h, r, g, b, a);
        addVertex(x, y + h, r, g, b, a);

        currentRectCount++;
    }

    /**
     * Zeichnet ein Rechteck-Outline (wie Graphics2D.drawRect)
     * Wird sofort gerendert (nicht gebatcht)
     */
    public void drawRect(float x, float y, float w, float h) {
        flush(); // Erst pending Batch rendern

        float r = currentColor.getRed() / 255.0f;
        float g = currentColor.getGreen() / 255.0f;
        float b = currentColor.getBlue() / 255.0f;
        float a = currentColor.getAlpha() / 255.0f;

        drawRectNative(x, y, w, h, r, g, b, a);
    }

    /**
     * Zeichnet einen gefüllten Kreis/Ellipse (wie Graphics2D.fillOval)
     * Wird sofort gerendert (nicht gebatcht)
     */
    public void fillOval(float x, float y, float w, float h) {
        flush();

        float r = currentColor.getRed() / 255.0f;
        float g = currentColor.getGreen() / 255.0f;
        float b = currentColor.getBlue() / 255.0f;
        float a = currentColor.getAlpha() / 255.0f;

        fillOvalNative(x, y, w, h, r, g, b, a);
    }

    /**
     * Zeichnet einen Kreis/Ellipse-Outline (wie Graphics2D.drawOval)
     * Wird sofort gerendert (nicht gebatcht)
     */
    public void drawOval(float x, float y, float w, float h) {
        flush();

        float r = currentColor.getRed() / 255.0f;
        float g = currentColor.getGreen() / 255.0f;
        float b = currentColor.getBlue() / 255.0f;
        float a = currentColor.getAlpha() / 255.0f;

        drawOvalNative(x, y, w, h, r, g, b, a);
    }

    /**
     * Zeichnet eine Linie (wie Graphics2D.drawLine)
     * Wird sofort gerendert (nicht gebatcht)
     */
    public void drawLine(float x1, float y1, float x2, float y2) {
        flush();

        float r = currentColor.getRed() / 255.0f;
        float g = currentColor.getGreen() / 255.0f;
        float b = currentColor.getBlue() / 255.0f;
        float a = currentColor.getAlpha() / 255.0f;

        drawLineNative(x1, y1, x2, y2, r, g, b, a);
    }

    /**
     * Zeichnet ein Polygon-Outline (wie Graphics2D.drawPolygon)
     * Wird sofort gerendert (nicht gebatcht)
     */
    public void drawPolygon(float[] xPoints, float[] yPoints, int nPoints) {
        flush();

        float r = currentColor.getRed() / 255.0f;
        float g = currentColor.getGreen() / 255.0f;
        float b = currentColor.getBlue() / 255.0f;
        float a = currentColor.getAlpha() / 255.0f;

        drawPolygonNative(xPoints, yPoints, nPoints, r, g, b, a);
    }

    /**
     * Zeichnet ein gefülltes Polygon (wie Graphics2D.fillPolygon)
     * Wird sofort gerendert (nicht gebatcht)
     */
    public void fillPolygon(float[] xPoints, float[] yPoints, int nPoints) {
        flush();

        float r = currentColor.getRed() / 255.0f;
        float g = currentColor.getGreen() / 255.0f;
        float b = currentColor.getBlue() / 255.0f;
        float a = currentColor.getAlpha() / 255.0f;

        fillPolygonNative(xPoints, yPoints, nPoints, r, g, b, a);
    }

    /**
     * Zeichnet einen Arc-Outline (wie Graphics2D.drawArc)
     * Wird sofort gerendert (nicht gebatcht)
     */
    public void drawArc(float x, float y, float w, float h, float startAngle, float arcAngle) {
        flush();

        float r = currentColor.getRed() / 255.0f;
        float g = currentColor.getGreen() / 255.0f;
        float b = currentColor.getBlue() / 255.0f;
        float a = currentColor.getAlpha() / 255.0f;

        drawArcNative(x, y, w, h, startAngle, arcAngle, r, g, b, a);
    }

    /**
     * Zeichnet einen gefüllten Arc (wie Graphics2D.fillArc)
     * Wird sofort gerendert (nicht gebatcht)
     */
    public void fillArc(float x, float y, float w, float h, float startAngle, float arcAngle) {
        flush();

        float r = currentColor.getRed() / 255.0f;
        float g = currentColor.getGreen() / 255.0f;
        float b = currentColor.getBlue() / 255.0f;
        float a = currentColor.getAlpha() / 255.0f;

        fillArcNative(x, y, w, h, startAngle, arcAngle, r, g, b, a);
    }

    /**
     * Zeichnet ein abgerundetes Rechteck (wie Graphics2D.drawRoundRect)
     * Wird sofort gerendert (nicht gebatcht)
     */
    public void drawRoundRect(float x, float y, float w, float h, float arcWidth, float arcHeight) {
        flush();

        float r = currentColor.getRed() / 255.0f;
        float g = currentColor.getGreen() / 255.0f;
        float b = currentColor.getBlue() / 255.0f;
        float a = currentColor.getAlpha() / 255.0f;

        drawRoundRectNative(x, y, w, h, arcWidth, arcHeight, r, g, b, a);
    }

    /**
     * Zeichnet ein gefülltes abgerundetes Rechteck (wie Graphics2D.fillRoundRect)
     * Wird sofort gerendert (nicht gebatcht)
     */
    public void fillRoundRect(float x, float y, float w, float h, float arcWidth, float arcHeight) {
        flush();

        float r = currentColor.getRed() / 255.0f;
        float g = currentColor.getGreen() / 255.0f;
        float b = currentColor.getBlue() / 255.0f;
        float a = currentColor.getAlpha() / 255.0f;

        fillRoundRectNative(x, y, w, h, arcWidth, arcHeight, r, g, b, a);
    }

    /**
     * Verschiebt den Ursprung des Koordinatensystems (wie Graphics2D.translate)
     */
    public void translate(float tx, float ty) {
        translateNative(tx, ty);
    }

    /**
     * Skaliert das Koordinatensystem (wie Graphics2D.scale)
     */
    public void scale(float sx, float sy) {
        scaleNative(sx, sy);
    }

    /**
     * Rotiert das Koordinatensystem (wie Graphics2D.rotate)
     */
    public void rotate(float angle) {
        rotateNative(angle);
    }

    /**
     * Setzt die Transformation zurück (wie Graphics2D.setTransform mit Identitätsmatrix)
     */
    public void resetTransform() {
        resetTransformNative();
    }

    /**
     * Setzt die Linienstärke (wie Graphics2D.setStroke mit BasicStroke)
     * HINWEIS: In DirectX 11 ist dies komplex und erfordert Geometry Shader oder Rasterizer State Änderungen
     * Aktuell wird nur der Wert gespeichert, aber nicht auf das Rendering angewendet
     */
    public void setStroke(float lineWidth) {
        setStrokeNative(lineWidth);
    }

    /**
     * Setzt einen Rendering Hint (wie Graphics2D.setRenderingHint)
     * Unterstützt KEY_ANTIALIASING mit Werten VALUE_ANTIALIAS_ON, VALUE_ANTIALIAS_OFF, VALUE_ANTIALIAS_DEFAULT
     */
    public void setRenderingHint(RenderingHints.Key key, Object value) {
        if (key == RenderingHints.KEY_ANTIALIASING) {
            antiAliasing = value;
            boolean enabled = (value == RenderingHints.VALUE_ANTIALIAS_ON);
            setAntiAliasingNative(enabled);
        }
    }

    /**
     * Gibt einen Rendering Hint zurück (wie Graphics2D.getRenderingHint)
     */
    public Object getRenderingHint(RenderingHints.Key key) {
        if (key == RenderingHints.KEY_ANTIALIASING) {
            return antiAliasing;
        }
        return null;
    }

    /**
     * Setzt das Clipping-Rechteck (wie Graphics2D.setClip mit Rectangle)
     * HINWEIS: In DirectX 11 ist dies komplex und erfordert Scissor Rects oder Stencil Buffer
     * Aktuell wird nur der Wert gespeichert, aber nicht auf das Rendering angewendet
     */
    public void setClip(float x, float y, float w, float h) {
        setClipNative(x, y, w, h);
    }

    /**
     * Setzt das Clipping zurück (wie Graphics2D.setClip mit null)
     */
    public void resetClip() {
        resetClipNative();
    }

    /**
     * Zeichnet einen Text-String (wie Graphics2D.drawString)
     * HINWEIS: In DirectX 11 erfordert dies texturierte Shader und Font-Rendering - sehr komplex
     * Aktuell ist dies nur ein Stub
     */
    public void drawString(String str, float x, float y) {
        flush();

        float r = currentColor.getRed() / 255.0f;
        float g = currentColor.getGreen() / 255.0f;
        float b = currentColor.getBlue() / 255.0f;
        float a = currentColor.getAlpha() / 255.0f;

        drawStringNative(str, x, y, r, g, b, a);
    }

    /**
     * Zeichnet ein Bild (wie Graphics2D.drawImage)
     * Verwendet internes Caching - gleiches Bild wird nur einmal als Textur hochgeladen
     */
    public void drawImage(BufferedImage img, float x, float y, float w, float h) {
        flush();

        // Check cache
        Integer textureId = textureCache.get(img);
        
        if (textureId == null) {
            // Load texture
            textureId = loadTexture(img);
            if (textureId >= 0) {
                textureCache.put(img, textureId);
            }
        }
        
        if (textureId != null && textureId >= 0) {
            drawTexturedQuadNative(textureId, x, y, w, h);
        }
    }
    
    /**
     * Entfernt ein Bild aus dem Cache und gibt die DirectX-Textur frei
     */
    public void disposeImage(BufferedImage img) {
        Integer textureId = textureCache.remove(img);
        if (textureId != null && textureId >= 0) {
            unloadTextureNative(textureId);
        }
    }
    
    /**
     * Lädt ein BufferedImage als DirectX-Textur
     */
    private int loadTexture(BufferedImage img) {
        // Ensure ARGB format
        BufferedImage argbImg;
        if (img.getType() != BufferedImage.TYPE_INT_ARGB) {
            argbImg = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            argbImg.getGraphics().drawImage(img, 0, 0, null);
            argbImg.getGraphics().dispose();
        } else {
            argbImg = img;
        }
        
        int width = argbImg.getWidth();
        int height = argbImg.getHeight();
        
        // Get pixel data
        int[] pixels = ((DataBufferInt) argbImg.getRaster().getDataBuffer()).getData();
        
        return loadTextureNative(pixels, width, height);
    }

    private int currentVertexOffset = 0;
    
    private void addVertex(float x, float y, float r, float g, float b, float a) {
        int idx = currentVertexOffset * FLOATS_PER_VERTEX;
        batchBuffer[idx + 0] = x;
        batchBuffer[idx + 1] = y;
        batchBuffer[idx + 2] = r;
        batchBuffer[idx + 3] = g;
        batchBuffer[idx + 4] = b;
        batchBuffer[idx + 5] = a;
        currentVertexOffset++;
    }
    
    /**
     * Rendert alle gepufferten Rechtecke auf einmal
     * 1 Draw-Call für alle!
     */
    public void flush() {
        if (currentRectCount == 0) return;
        
        int vertexCount = currentRectCount * VERTICES_PER_RECT;
        renderBatch(batchBuffer, vertexCount);
        
        currentRectCount = 0;
        currentVertexOffset = 0;
    }
    
    /**
     * Löscht den Hintergrund
     */
    public void clear(Color c) {
        flush(); // Erst pending Batch rendern
        clearNative(c.getRed() / 255.0f, c.getGreen() / 255.0f, c.getBlue() / 255.0f);
    }
    
    public void clear(float r, float g, float b) {
        flush();
        clearNative(r, g, b);
    }
    
    /**
     * Zeigt das gerenderte Bild an (Swap Buffer)
     */
    public void present() {
        flush(); // Alle gepufferten Rechtecke rendern
        presentNative();
    }
    
    /**
     * Utility: HWND anhand Fenster-Titel finden
     */
    public static long findWindow(String title) {
        return findWindowNative(title);
    }
}
