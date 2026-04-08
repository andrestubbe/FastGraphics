package demo;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Systematischer Benchmark - zeigt wo FastGraphics gewinnt
 */
public class Benchmark {
    
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    
    static { System.loadLibrary("FastGraphics"); }
    
    private static native void init(long hwnd);
    private static native void fillRects(java.nio.ByteBuffer rectData, int count);
    private static native void clear(float r, float g, float b);
    private static native void present();
    private static native long findWindow(String title);
    
    private static final Object lock = new Object();
    
    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale", "1.0");
        
        // Test-Konfigurationen
        int[] particleCounts = {1000, 5000, 10000, 20000, 50000};
        int testDurationSec = 10;
        
        System.out.println("========================================");
        System.out.println("FASTGRAPHICS vs AWT BENCHMARK");
        System.out.println("========================================");
        System.out.println("Testdauer: " + testDurationSec + " Sekunden pro Konfiguration\n");
        
        for (int count : particleCounts) {
            System.out.println("\n--- " + count + " Partikel ---");
            
            // FastGraphics Test
            double[] fgStats = runFastGraphicsTest(count, testDurationSec);
            
            // AWT Test  
            double[] awtStats = runAwtTest(count, testDurationSec);
            
            // Ergebnisse
            System.out.printf("FastGraphics: Avg=%.1f, Min=%.1f, Max=%.1f FPS%n", fgStats[0], fgStats[1], fgStats[2]);
            System.out.printf("AWT:          Avg=%.1f, Min=%.1f, Max=%.1f FPS%n", awtStats[0], awtStats[1], awtStats[2]);
            System.out.printf("Vorteil: %.1f%% schneller%n", (fgStats[0] / awtStats[0] - 1) * 100);
        }
        
        System.out.println("\n========================================");
        System.out.println("Benchmark abgeschlossen!");
        System.out.println("========================================");
    }
    
    private static double[] runFastGraphicsTest(int particleCount, int durationSec) {
        JFrame frame = new JFrame("Benchmark FastGraphics - " + particleCount);
        frame.setSize(WIDTH, HEIGHT);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocation(100, 100);
        frame.setVisible(true);
        
        long hwnd = 0;
        for (int i = 0; i < 20 && hwnd == 0; i++) {
            try { Thread.sleep(50); } catch (InterruptedException e) {}
            hwnd = findWindow("Benchmark FastGraphics - " + particleCount);
        }
        if (hwnd == 0) return new double[]{0,0,0};
        
        init(hwnd);
        
        // Partikel
        float[] px = new float[particleCount];
        float[] py = new float[particleCount];
        for (int i = 0; i < particleCount; i++) {
            px[i] = (float)(Math.random() * WIDTH);
            py[i] = (float)(Math.random() * HEIGHT);
        }
        
        // Physik-Thread
        startPhysics(px, py, particleCount);
        
        // Benchmark
        long startTime = System.currentTimeMillis();
        int frameCount = 0;
        int minFps = Integer.MAX_VALUE;
        int maxFps = 0;
        long lastFpsTime = startTime;
        
        // Direct ByteBuffer - einmal allozieren, wiederverwenden
        java.nio.ByteBuffer rectData = java.nio.ByteBuffer.allocateDirect(particleCount * 7 * 4)
            .order(java.nio.ByteOrder.nativeOrder());
        
        while (System.currentTimeMillis() - startTime < durationSec * 1000) {
            clear(0.02f, 0.02f, 0.05f);
            
            int startIdx = frameCount % particleCount;
            rectData.clear();
            
            synchronized (lock) {
                for (int i = 0; i < particleCount; i++) {
                    int idx = (startIdx + i) % particleCount;
                    rectData.putFloat(px[idx] - 2);
                    rectData.putFloat(py[idx] - 2);
                    rectData.putFloat(5);
                    rectData.putFloat(5);
                    rectData.putFloat(0.5f);
                    rectData.putFloat(0.8f);
                    rectData.putFloat(1.0f);
                }
            }
            
            rectData.flip();
            fillRects(rectData, particleCount);  // Zero-Copy!
            present();
            frameCount++;
            
            // FPS jede Sekunde
            long now = System.currentTimeMillis();
            if (now - lastFpsTime >= 1000) {
                int fps = frameCount;
                minFps = Math.min(minFps, fps);
                maxFps = Math.max(maxFps, fps);
                frameCount = 0;
                lastFpsTime = now;
            }
        }
        
        frame.dispose();
        
        double avg = (double)frameCount / ((System.currentTimeMillis() - startTime) / 1000.0);
        return new double[]{avg, minFps == Integer.MAX_VALUE ? 0 : minFps, maxFps};
    }
    
    private static double[] runAwtTest(int particleCount, int durationSec) {
        JFrame frame = new JFrame("Benchmark AWT - " + particleCount);
        frame.setSize(WIDTH, HEIGHT);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocation(100 + WIDTH + 20, 100);
        
        Canvas canvas = new Canvas();
        canvas.setSize(WIDTH, HEIGHT);
        frame.add(canvas);
        frame.setVisible(true);
        
        // Warte auf Canvas
        while (canvas.getGraphics() == null) {
            try { Thread.sleep(10); } catch (InterruptedException e) { break; }
        }
        try { Thread.sleep(100); } catch (InterruptedException e) {}
        
        // Partikel
        float[] px = new float[particleCount];
        float[] py = new float[particleCount];
        for (int i = 0; i < particleCount; i++) {
            px[i] = (float)(Math.random() * WIDTH);
            py[i] = (float)(Math.random() * HEIGHT);
        }
        
        // Physik-Thread
        startPhysics(px, py, particleCount);
        
        // Benchmark
        long startTime = System.currentTimeMillis();
        int frameCount = 0;
        int actualFrames = 0;
        int minFps = Integer.MAX_VALUE;
        int maxFps = 0;
        long lastFpsTime = startTime;
        
        while (System.currentTimeMillis() - startTime < durationSec * 1000) {
            int startIdx = frameCount % particleCount;
            
            Graphics2D g2d = (Graphics2D) canvas.getGraphics();
            if (g2d != null) {
                BufferedImage buffer = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
                Graphics2D bg = buffer.createGraphics();
                bg.setColor(new Color(5, 5, 13));
                bg.fillRect(0, 0, WIDTH, HEIGHT);
                
                synchronized (lock) {
                    for (int i = 0; i < particleCount; i++) {
                        int idx = (startIdx + i) % particleCount;
                        bg.setColor(new Color(128, 204, 255));
                        bg.fillRect((int)px[idx] - 2, (int)py[idx] - 2, 5, 5);
                    }
                }
                
                g2d.drawImage(buffer, 0, 0, null);
                bg.dispose();
                g2d.dispose();
                actualFrames++;
            }
            
            frameCount++;
            
            // FPS jede Sekunde
            long now = System.currentTimeMillis();
            if (now - lastFpsTime >= 1000) {
                int fps = actualFrames;
                minFps = Math.min(minFps, fps);
                maxFps = Math.max(maxFps, fps);
                actualFrames = 0;
                frameCount = 0;
                lastFpsTime = now;
            }
        }
        
        frame.dispose();
        
        double avg = (double)actualFrames / ((System.currentTimeMillis() - startTime) / 1000.0);
        return new double[]{avg, minFps == Integer.MAX_VALUE ? 0 : minFps, maxFps};
    }
    
    private static void startPhysics(float[] px, float[] py, int count) {
        new Thread(() -> {
            long lastTime = System.nanoTime();
            final double nsPerTick = 1_000_000_000.0 / 60.0;
            while (true) {
                long now = System.nanoTime();
                if ((now - lastTime) / nsPerTick >= 1.0) {
                    lastTime = now;
                    synchronized (lock) {
                        for (int i = 0; i < count; i++) {
                            px[i] += (float)(Math.random() - 0.5) * 3;
                            py[i] += (float)(Math.random() - 0.5) * 3;
                            if (px[i] < 0 || px[i] > WIDTH) px[i] = WIDTH / 2;
                            if (py[i] < 0 || py[i] > HEIGHT) py[i] = HEIGHT / 2;
                        }
                    }
                }
                try { Thread.sleep(1); } catch (InterruptedException e) { break; }
            }
        }, "Physics-" + count).start();
    }
}
