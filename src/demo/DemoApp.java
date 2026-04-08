package demo;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * FastGraphics Demo - 120 FPS flüssige Animation
 * Mit AWT-Vergleichsfenster
 * 
 * OPTIMIERT: Pooled FloatBuffer - kein "new" pro Frame!
 */
public class DemoApp {
    
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    private static final int PARTICLE_COUNT = 10000;  // 10K Partikel!
    
    /**
     * Direct FloatBuffer - OFF-HEAP, kein GC, schneller JNI-Transfer
     * Layout pro Rechteck: x, y, w, h, r, g, b (7 floats)
     * Upgrade-Pfad: float[] → FloatBuffer (direct) → GPU Mapped
     */
    static class RectBuffer {
        private java.nio.ByteBuffer buffer;
        private int capacity;
        
        RectBuffer(int initialRects) {
            this.capacity = initialRects;
            // Direct ByteBuffer = Off-Heap, native Speicher
            this.buffer = java.nio.ByteBuffer.allocateDirect(initialRects * 7 * 4)
                          .order(java.nio.ByteOrder.nativeOrder());
        }
        
        /** Holt Buffer für n Rechtecke - wächst automatisch */
        java.nio.ByteBuffer get(int rects) {
            if (rects > capacity) {
                while (capacity < rects) capacity *= 2;
                buffer = java.nio.ByteBuffer.allocateDirect(capacity * 7 * 4)
                        .order(java.nio.ByteOrder.nativeOrder());
            }
            buffer.clear();
            buffer.limit(rects * 7 * 4);  // Bytes, nicht floats!
            return buffer;
        }
        
        int getCapacity() { return capacity; }
    }
    
    // STATISCHER POOL - einmal alloziert, immer wiederverwendet
    private static final RectBuffer rectBufferPool = new RectBuffer(PARTICLE_COUNT);

    static { System.loadLibrary("FastGraphics"); }
    
    private static native void init(long hwnd);
    private static native void fillRects(java.nio.ByteBuffer rectData, int count);  // Direct ByteBuffer!
    private static native void clear(float r, float g, float b);
    private static native void present();
    private static native long findWindow(String title);
    
    public static void main(String[] args) {
        // DPI-Scale auf 1.0 setzen für korrektes DirectX Rendering
        System.setProperty("sun.java2d.uiScale", "1.0");
        
        // FastGraphics Fenster (links)
        JFrame frame = new JFrame("FastGraphics - 120 FPS");
        frame.setSize(WIDTH, HEIGHT);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocation(100, 100);
        frame.setVisible(true);
        
        // AWT-Vergleichsfenster (rechts)
        JFrame awtFrame = new JFrame("AWT Standard - 120 FPS");
        awtFrame.setSize(WIDTH, HEIGHT);
        awtFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        awtFrame.setLocation(100 + WIDTH + 20, 100);
        awtFrame.setVisible(true);
        
        // AWT Canvas für Rendering mit Double Buffering
        Canvas awtCanvas = new Canvas() {
            @Override
            public void paint(Graphics g) {
                // Wird im Loop überschrieben
            }
        };
        awtCanvas.setSize(WIDTH, HEIGHT);
        awtFrame.add(awtCanvas);
        awtFrame.setIgnoreRepaint(true);
        awtCanvas.setIgnoreRepaint(true);
        
        long hwnd = 0;
        for (int i = 0; i < 20 && hwnd == 0; i++) {
            try { Thread.sleep(50); } catch (InterruptedException e) {}
            hwnd = findWindow("FastGraphics - 120 FPS");
        }
        
        if (hwnd == 0) {
            System.out.println("FEHLER: HWND nicht gefunden");
            return;
        }
        
        // Partikel-Daten vor den Threads initialisieren
        int particleCount = PARTICLE_COUNT;
        float[] px = new float[particleCount];
        float[] py = new float[particleCount];
        float[] pdx = new float[particleCount];
        float[] pdy = new float[particleCount];
        
        for (int i = 0; i < particleCount; i++) {
            px[i] = (float)(Math.random() * WIDTH);
            py[i] = (float)(Math.random() * HEIGHT);
            pdx[i] = (float)(Math.random() - 0.5) * 200;  // Geschwindigkeit in Pixel/SEKUNDE
            pdy[i] = (float)(Math.random() - 0.5) * 200;
        }
        
        init(hwnd);
        
        // PHYSICS-Thread starten (60 Updates/Sekunde = zeitbasiert)
        startPhysicsThread(px, py, pdx, pdy, particleCount);
        
        // AWT in separatem Thread starten
        startAwtThread(awtCanvas, awtFrame, px, py, particleCount);
        
        // FastGraphics Animation Loop
        long frameCount = 0;
        long lastTime = System.currentTimeMillis();
        int fps = 0;
        
        System.out.println("Demo läuft - FastGraphics (DirectX) vs AWT (Java2D)");
        
        while (frame.isVisible()) {
            long currentTime = System.currentTimeMillis();
            
            clear(0.02f, 0.02f, 0.05f);
            
            // ZEICHNEN - BATCH! Ein Aufruf für alle Rechtecke
            int drawCount = PARTICLE_COUNT;
            int startIdx = (int)(frameCount * 7) % particleCount;
            java.nio.ByteBuffer rectData = rectBufferPool.get(drawCount);  // DIRECT BUFFER!
            
            synchronized (particleLock) {
                for (int i = 0; i < drawCount; i++) {
                    int idx = (startIdx + i) % particleCount;
                    float hue = (idx % 360) / 360.0f;
                    rectData.putFloat(px[idx] - 2);  // x
                    rectData.putFloat(py[idx] - 2);  // y
                    rectData.putFloat(5);  // w
                    rectData.putFloat(5);  // h
                    rectData.putFloat((float)(0.5 + 0.5 * Math.sin(hue * 6.28)));      // r
                    rectData.putFloat((float)(0.5 + 0.5 * Math.sin(hue * 6.28 + 2.09))); // g
                    rectData.putFloat((float)(0.5 + 0.5 * Math.sin(hue * 6.28 + 4.18))); // b
                }
            }
            
            rectData.flip();
            fillRects(rectData, drawCount);  // EIN Draw Call - Zero Copy!
            
            present();
            frameCount++;
            
            // FPS Anzeige
            if (currentTime - lastTime >= 1000) {
                fps = (int)frameCount;
                frameCount = 0;
                lastTime = currentTime;
                frame.setTitle("FastGraphics - " + fps + " FPS");
            }
        }
    }
    
    // PHYSICS-THREAD - Bewegt Partikel mit fester Rate (60 Hz)
    private static final Object particleLock = new Object();
    
    private static void startPhysicsThread(float[] px, float[] py, float[] pdx, float[] pdy, 
                                           int particleCount) {
        new Thread(() -> {
            long lastTime = System.nanoTime();
            final double tickRate = 60.0;
            final double nsPerTick = 1_000_000_000.0 / tickRate;
            
            while (true) {
                long now = System.nanoTime();
                double delta = (now - lastTime) / nsPerTick;
                
                if (delta >= 1.0) {
                    lastTime = now;
                    
                    synchronized (particleLock) {
                        for (int i = 0; i < particleCount; i++) {
                            px[i] += pdx[i] / 60.0f;
                            py[i] += pdy[i] / 60.0f;
                            if (px[i] < 0 || px[i] > WIDTH) pdx[i] = -pdx[i];
                            if (py[i] < 0 || py[i] > HEIGHT) pdy[i] = -pdy[i];
                        }
                    }
                }
                
                try { Thread.sleep(1); } catch (InterruptedException e) { break; }
            }
        }, "Physics-Thread").start();
    }
    
    // SEPARATER THREAD für AWT Rendering
    private static void startAwtThread(Canvas awtCanvas, JFrame awtFrame, 
                                       float[] px, float[] py, int particleCount) {
        new Thread(() -> {
            // Warte bis Canvas bereit ist
            while (awtCanvas.getGraphics() == null) {
                try { Thread.sleep(10); } catch (InterruptedException e) { break; }
            }
            try { Thread.sleep(100); } catch (InterruptedException e) {} 
            
            long frameCount = 0;
            long lastTime = System.currentTimeMillis();
            int fps = 0;
            int startIdx = 0;
            int actualFrames = 0;
            
            while (awtFrame.isVisible()) {
                long currentTime = System.currentTimeMillis();
                startIdx = (int)(frameCount * 7) % particleCount;
                
                // AWT Rendering mit DoubleBuffering
                Graphics2D g2d = (Graphics2D) awtCanvas.getGraphics();
                if (g2d != null) {
                    BufferedImage buffer = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
                    Graphics2D bg = buffer.createGraphics();
                    
                    bg.setColor(new Color(5, 5, 13));
                    bg.fillRect(0, 0, WIDTH, HEIGHT);
                    bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                    
                    synchronized (particleLock) {
                        for (int i = 0; i < PARTICLE_COUNT; i++) {
                            int idx = (startIdx + i) % particleCount;
                            float hue = (idx % 360) / 360.0f;
                            int r = (int)((0.5 + 0.5 * Math.sin(hue * 6.28)) * 255);
                            int gr = (int)((0.5 + 0.5 * Math.sin(hue * 6.28 + 2.09)) * 255);
                            int b = (int)((0.5 + 0.5 * Math.sin(hue * 6.28 + 4.18)) * 255);
                            bg.setColor(new Color(r, gr, b));
                            bg.fillRect((int)px[idx] - 2, (int)py[idx] - 2, 5, 5);
                        }
                    }
                    
                    g2d.drawImage(buffer, 0, 0, null);
                    bg.dispose();
                    g2d.dispose();
                    actualFrames++;
                }
                frameCount++;
                
                // FPS Anzeige
                if (currentTime - lastTime >= 1000) {
                    fps = actualFrames;
                    actualFrames = 0;
                    frameCount = 0;
                    lastTime = currentTime;
                    awtFrame.setTitle("AWT Standard - " + fps + " FPS");
                }
            }
        }, "AWT-Render-Thread").start();
    }
}
