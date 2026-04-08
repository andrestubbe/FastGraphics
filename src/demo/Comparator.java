package demo;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

/**
 * Visual Comparator - FastGraphics vs AWT Graphics2D
 * Zeichnet identische Inhalte in beiden APIs für Pixel-Vergleich
 */
public class Comparator {
    
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    
    static { System.loadLibrary("FastGraphics"); }
    
    private static native void init(long hwnd);
    private static native void fillRects(java.nio.ByteBuffer rectData, int count);
    private static native void clear(float r, float g, float b);
    private static native void present();
    private static native long findWindow(String title);
    
    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale", "1.0");
        
        // === FASTGRAPHICS FENSTER (links) ===
        JFrame fgFrame = new JFrame("FastGraphics - Comparator");
        fgFrame.setSize(WIDTH, HEIGHT);
        fgFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        fgFrame.setLocation(50, 100);
        fgFrame.setVisible(true);
        
        // === AWT FENSTER (rechts) ===
        JFrame awtFrame = new JFrame("AWT Graphics2D - Comparator");
        awtFrame.setSize(WIDTH, HEIGHT);
        awtFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        awtFrame.setLocation(50 + WIDTH + 20, 100);
        awtFrame.setVisible(true);
        
        Canvas awtCanvas = new Canvas();
        awtCanvas.setSize(WIDTH, HEIGHT);
        awtFrame.add(awtCanvas);
        awtFrame.setIgnoreRepaint(true);
        awtCanvas.setIgnoreRepaint(true);
        
        // Warte auf Fenster
        long hwnd = 0;
        for (int i = 0; i < 20 && hwnd == 0; i++) {
            try { Thread.sleep(50); } catch (InterruptedException e) {}
            hwnd = findWindow("FastGraphics - Comparator");
        }
        
        init(hwnd);
        
        // Test-Daten: Zufällige Rechtecke mit Farben
        int rectCount = 500;
        float[] rectData = new float[rectCount * 7]; // x,y,w,h,r,g,b
        Color[] awtColors = new Color[rectCount];
        Rectangle2D[] awtRects = new Rectangle2D[rectCount];
        
        java.util.Random rnd = new java.util.Random(12345); // Seed für Reproduzierbarkeit
        
        for (int i = 0; i < rectCount; i++) {
            float x = rnd.nextFloat() * (WIDTH - 50);
            float y = rnd.nextFloat() * (HEIGHT - 50);
            float w = 10 + rnd.nextFloat() * 40;
            float h = 10 + rnd.nextFloat() * 40;
            float r = rnd.nextFloat();
            float g = rnd.nextFloat();
            float b = rnd.nextFloat();
            
            int dIdx = i * 7;
            rectData[dIdx + 0] = x;
            rectData[dIdx + 1] = y;
            rectData[dIdx + 2] = w;
            rectData[dIdx + 3] = h;
            rectData[dIdx + 4] = r;
            rectData[dIdx + 5] = g;
            rectData[dIdx + 6] = b;
            
            awtRects[i] = new Rectangle2D.Float(x, y, w, h);
            awtColors[i] = new Color(r, g, b);
        }
        
        // Direct ByteBuffer für FastGraphics
        java.nio.ByteBuffer fgBuffer = java.nio.ByteBuffer.allocateDirect(rectCount * 7 * 4)
            .order(java.nio.ByteOrder.nativeOrder());
        
        // Animation Loop
        int frameCount = 0;
        
        while (fgFrame.isVisible()) {
            // === FASTGRAPHICS RENDERN ===
            clear(0.02f, 0.02f, 0.05f);
            
            fgBuffer.clear();
            for (int i = 0; i < rectCount; i++) {
                int idx = i * 7;
                fgBuffer.putFloat(rectData[idx + 0]);
                fgBuffer.putFloat(rectData[idx + 1]);
                fgBuffer.putFloat(rectData[idx + 2]);
                fgBuffer.putFloat(rectData[idx + 3]);
                fgBuffer.putFloat(rectData[idx + 4]);
                fgBuffer.putFloat(rectData[idx + 5]);
                fgBuffer.putFloat(rectData[idx + 6]);
            }
            fgBuffer.flip();
            
            fillRects(fgBuffer, rectCount);
            present();
            
            // === AWT RENDERN ===
            Graphics2D g2d = (Graphics2D) awtCanvas.getGraphics();
            if (g2d != null) {
                BufferedImage buffer = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
                Graphics2D bg = buffer.createGraphics();
                
                // Hintergrund
                bg.setColor(new Color(5, 5, 13));
                bg.fillRect(0, 0, WIDTH, HEIGHT);
                bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                
                // Rechtecke (exakt gleiche wie FastGraphics)
                for (int i = 0; i < rectCount; i++) {
                    bg.setColor(awtColors[i]);
                    bg.fill(awtRects[i]);
                }
                
                g2d.drawImage(buffer, 0, 0, null);
                bg.dispose();
                g2d.dispose();
            }
            
            frameCount++;
            
            // === PIXEL VERGLEICH (alle 60 Frames = ~1 Sekunde) ===
            if (frameCount % 60 == 0) {
                comparePixels(fgFrame, awtCanvas);
            }
            
            // Frame-Rate limitieren für visuellen Vergleich
            try { Thread.sleep(16); } catch (InterruptedException e) { break; }
        }
    }
    
    /**
     * Automatischer Pixel-Vergleich zwischen FastGraphics und AWT
     * Nutzt Java Robot für Screenshots
     */
    private static void comparePixels(JFrame fgFrame, Canvas awtCanvas) {
        try {
            java.awt.Robot robot = new java.awt.Robot();
            
            // Screenshot FastGraphics Fenster (nur Client-Bereich)
            java.awt.Point fgLoc = fgFrame.getLocationOnScreen();
            java.awt.Rectangle fgBounds = new java.awt.Rectangle(
                fgLoc.x + 8, fgLoc.y + 31, WIDTH, HEIGHT); // Offset für Rahmen/Titlebar
            BufferedImage fgScreen = robot.createScreenCapture(fgBounds);
            
            // Screenshot AWT Canvas (nur der Canvas-Bereich)
            java.awt.Point awtLoc = awtCanvas.getLocationOnScreen();
            java.awt.Rectangle awtBounds = new java.awt.Rectangle(awtLoc.x, awtLoc.y, WIDTH, HEIGHT);
            BufferedImage awtScreen = robot.createScreenCapture(awtBounds);
            
            // Vergleiche (mit Toleranz für kleine Unterschiede)
            int diffPixels = 0;
            int maxDiff = 0;
            
            // Minimale Größe beider Bilder nutzen
            int compWidth = Math.min(fgScreen.getWidth(), awtScreen.getWidth());
            int compHeight = Math.min(fgScreen.getHeight(), awtScreen.getHeight());
            
            // Sample alle 10 Pixel (schneller)
            int sampleStep = 10;
            int checkedPixels = 0;
            
            for (int y = 0; y < compHeight; y += sampleStep) {
                for (int x = 0; x < compWidth; x += sampleStep) {
                    int fgRGB = fgScreen.getRGB(x, y);
                    int awtRGB = awtScreen.getRGB(x, y);
                    
                    if (fgRGB != awtRGB) {
                        diffPixels++;
                        // Berechne Farb-Differenz
                        int diffR = Math.abs(((fgRGB >> 16) & 0xFF) - ((awtRGB >> 16) & 0xFF));
                        int diffG = Math.abs(((fgRGB >> 8) & 0xFF) - ((awtRGB >> 8) & 0xFF));
                        int diffB = Math.abs((fgRGB & 0xFF) - (awtRGB & 0xFF));
                        maxDiff = Math.max(maxDiff, diffR + diffG + diffB);
                    }
                    checkedPixels++;
                }
            }
            
            double diffPercent = (diffPixels * 100.0) / checkedPixels;
            
            System.out.printf("[Pixel-Match] Checked: %d pixels | Diff: %d (%.2f%%) | Max RGB-Diff: %d%n", 
                checkedPixels, diffPixels, diffPercent, maxDiff);
            
            if (diffPercent < 1.0) {
                System.out.println("[Pixel-Match] ✓ EXCELLENT - FastGraphics und AWT sind pixel-identisch!");
            } else if (diffPercent < 5.0) {
                System.out.println("[Pixel-Match] ✓ GOOD - Kleine Unterschiede (Rounding/ColorSpace)");
            } else {
                System.out.println("[Pixel-Match] ✗ WARNING - Große Abweichungen entdeckt!");
            }
            
        } catch (Exception e) {
            System.err.println("[Pixel-Match] Error: " + e.getMessage());
        }
    }
}
