package fastgraphics;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import fastcore.LibraryLoader;

/**
 * FastGraphicsEngine is the low-level native engine for FastJava.
 * It manages the D3D11 device, JNI bridge, and raw instance batching.
 */
public class FastGraphicsEngine {
    static {
        LibraryLoader.load("fastgraphics");
    }

    // --- Native Lifecycle ---
    public static native long findWindow(String title);
    public static native long findCanvas(long parent);
    public native boolean init(long hwnd);
    public native void destroy();
    public native void clear(float r, float g, float b, float a);
    public native void setAntialiasing(boolean enabled);
    public native void render(ByteBuffer buffer, int count, float[] transform);
    public native void present();
    public native void drawString(String text, float x, float y, float size, float r, float g, float b, float a);
    public native void drawImage(int textureId, float x, float y, float w, float h, float alpha);
    public native int loadTexture(int[] pixels, int width, int height);
    public native void destroyTexture(int textureId);

    // --- Batching Logic ---
    public static final int INSTANCE_SIZE = 48; // 12 floats
    private final ByteBuffer buffer;
    private final FloatBuffer floatBuffer;
    private final int maxInstances;
    private int count = 0;

    public FastGraphicsEngine(long hwnd, int maxInstances) {
        this.maxInstances = maxInstances;
        this.buffer = ByteBuffer.allocateDirect(maxInstances * INSTANCE_SIZE).order(ByteOrder.nativeOrder());
        this.floatBuffer = buffer.asFloatBuffer();
        if (!init(hwnd)) {
            throw new IllegalStateException("Failed to initialize FastGraphics native engine. Check console for details.");
        }
    }

    public void addInstance(float x, float y, float w, float h, float r, float g, float b, float a, float p1, float p2, float type, float p3) {
        if (count >= maxInstances) flush(new float[16]); // Should be flushed with current transform externally
        
        int offset = count * 12;
        floatBuffer.put(offset + 0, x);
        floatBuffer.put(offset + 1, y);
        floatBuffer.put(offset + 2, w);
        floatBuffer.put(offset + 3, h);
        floatBuffer.put(offset + 4, r);
        floatBuffer.put(offset + 5, g);
        floatBuffer.put(offset + 6, b);
        floatBuffer.put(offset + 7, a);
        floatBuffer.put(offset + 8, p1);
        floatBuffer.put(offset + 9, p2);
        floatBuffer.put(offset + 10, type);
        floatBuffer.put(offset + 11, p3);
        count++;
    }

    public void flush(float[] matrix) {
        if (count > 0) {
            render(buffer, count, matrix);
            count = 0;
        }
    }

    public int getCount() { return count; }
    public int getMaxInstances() { return maxInstances; }
}
