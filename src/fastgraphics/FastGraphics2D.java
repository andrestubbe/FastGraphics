package fastgraphics;

import java.awt.Color;

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
    
    static { System.loadLibrary("FastGraphics"); }
    
    // Aktueller Zeichen-Zustand
    private Color currentColor = Color.BLACK;
    
    // Batch-Buffer: 6 Vertices pro Rechteck (2 Dreiecke)
    // Format: x, y, r, g, b für jeden Vertex
    private float[] batchBuffer;
    private int maxRects;
    private int currentRectCount = 0;
    
    // Batch-Größe (1024 Rechtecke = guter Sweet-Spot)
    private static final int DEFAULT_BATCH_SIZE = 1024;
    private static final int VERTICES_PER_RECT = 6;
    private static final int FLOATS_PER_VERTEX = 5; // x, y, r, g, b
    private static final int FLOATS_PER_RECT = VERTICES_PER_RECT * FLOATS_PER_VERTEX;
    
    public FastGraphics2D(int hwnd, int batchSize) {
        init(hwnd);
        this.maxRects = batchSize;
        this.batchBuffer = new float[maxRects * FLOATS_PER_RECT];
    }
    
    public FastGraphics2D(int hwnd) {
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
        
        // 2 Dreiecke für ein Rechteck (Triangle List)
        // Dreieck 1: Top-Left, Top-Right, Bottom-Left
        addVertex(x, y, r, g, b);
        addVertex(x + w, y, r, g, b);
        addVertex(x, y + h, r, g, b);
        
        // Dreieck 2: Top-Right, Bottom-Right, Bottom-Left
        addVertex(x + w, y, r, g, b);
        addVertex(x + w, y + h, r, g, b);
        addVertex(x, y + h, r, g, b);
        
        currentRectCount++;
    }
    
    private int currentVertexOffset = 0;
    
    private void addVertex(float x, float y, float r, float g, float b) {
        int idx = currentVertexOffset * FLOATS_PER_VERTEX;
        batchBuffer[idx + 0] = x;
        batchBuffer[idx + 1] = y;
        batchBuffer[idx + 2] = r;
        batchBuffer[idx + 3] = g;
        batchBuffer[idx + 4] = b;
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
