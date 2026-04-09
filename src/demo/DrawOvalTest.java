package demo;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * DrawOval Vergleichstest - AWT Graphics2D vs FastGraphics
 * Testet drawOval() und fillOval() in beiden Implementierungen
 */
public class DrawOvalTest {
    
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    
    static { System.loadLibrary("FastGraphics"); }

    private static native void init(long hwnd);
    private static native void clear(float r, float g, float b);
    private static native void present();
    private static native long findWindow(String title);
    private static native void fillOvalNative(float x, float y, float w, float h, float r, float g, float b);
    private static native void drawOvalNative(float x, float y, float w, float h, float r, float g, float b);
    
    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale", "1.0");
        
        // FastGraphics Fenster (links)
        JFrame fgFrame = new JFrame("FastGraphics - drawOval Test");
        fgFrame.setSize(WIDTH, HEIGHT);
        fgFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        fgFrame.setLocation(100, 100);
        fgFrame.setVisible(true);
        
        // AWT Fenster (rechts)
        JFrame awtFrame = new JFrame("AWT Graphics2D - drawOval Test");
        awtFrame.setSize(WIDTH, HEIGHT);
        awtFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        awtFrame.setLocation(100 + WIDTH + 20, 100);
        awtFrame.setVisible(true);
        
        Canvas awtCanvas = new Canvas();
        awtCanvas.setSize(WIDTH, HEIGHT);
        awtFrame.add(awtCanvas);
        awtFrame.setIgnoreRepaint(true);
        awtCanvas.setIgnoreRepaint(true);
        
        // HWND finden
        long hwnd = 0;
        for (int i = 0; i < 20 && hwnd == 0; i++) {
            try { Thread.sleep(50); } catch (InterruptedException e) {}
            hwnd = findWindow("FastGraphics - drawOval Test");
        }
        
        if (hwnd == 0) {
            System.err.println("HWND nicht gefunden!");
            return;
        }
        
        init(hwnd);
        
        System.out.println("DrawOval Vergleichstest läuft...");
        System.out.println("Links: FastGraphics | Rechts: AWT Graphics2D");
        
        while (fgFrame.isVisible()) {
            long startTime = System.currentTimeMillis();
            
            // === FASTGRAPHICS ===
            clear(0.1f, 0.1f, 0.1f);

            // Gefüllte rote Kreise
            fillOvalNative(50, 50, 100, 100, 1.0f, 0.0f, 0.0f);
            fillOvalNative(200, 50, 100, 100, 1.0f, 0.0f, 0.0f);

            // Gefüllte grüne Kreise
            fillOvalNative(50, 200, 100, 100, 0.0f, 1.0f, 0.0f);
            fillOvalNative(200, 200, 100, 100, 0.0f, 1.0f, 0.0f);

            // Gefüllte blaue Kreise
            fillOvalNative(50, 350, 100, 100, 0.0f, 0.0f, 1.0f);
            fillOvalNative(200, 350, 100, 100, 0.0f, 0.0f, 1.0f);

            // Outline Kreise (weiß)
            drawOvalNative(350, 50, 100, 100, 1.0f, 1.0f, 1.0f);
            drawOvalNative(500, 50, 100, 100, 1.0f, 1.0f, 1.0f);

            drawOvalNative(350, 200, 100, 100, 1.0f, 1.0f, 1.0f);
            drawOvalNative(500, 200, 100, 100, 1.0f, 1.0f, 1.0f);

            drawOvalNative(350, 350, 100, 100, 1.0f, 1.0f, 1.0f);
            drawOvalNative(500, 350, 100, 100, 1.0f, 1.0f, 1.0f);

            // Ellipsen
            fillOvalNative(620, 50, 150, 80, 1.0f, 1.0f, 0.0f);
            drawOvalNative(620, 200, 150, 80, 1.0f, 0.0f, 1.0f);
            fillOvalNative(620, 350, 80, 150, 0.0f, 1.0f, 1.0f);

            present();
            
            long fgTime = System.currentTimeMillis() - startTime;
            
            // === AWT ===
            long awtStart = System.currentTimeMillis();
            
            Graphics2D g2d = (Graphics2D) awtCanvas.getGraphics();
            if (g2d != null) {
                BufferedImage buffer = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
                Graphics2D bg = buffer.createGraphics();
                
                bg.setColor(new Color(25, 25, 25));
                bg.fillRect(0, 0, WIDTH, HEIGHT);
                bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                
                // Gefüllte rote Kreise
                bg.setColor(Color.RED);
                bg.fillOval(50, 50, 100, 100);
                bg.fillOval(200, 50, 100, 100);
                
                // Gefüllte grüne Kreise
                bg.setColor(Color.GREEN);
                bg.fillOval(50, 200, 100, 100);
                bg.fillOval(200, 200, 100, 100);
                
                // Gefüllte blaue Kreise
                bg.setColor(Color.BLUE);
                bg.fillOval(50, 350, 100, 100);
                bg.fillOval(200, 350, 100, 100);
                
                // Outline Kreise (weiß)
                bg.setColor(Color.WHITE);
                bg.drawOval(350, 50, 100, 100);
                bg.drawOval(500, 50, 100, 100);
                bg.drawOval(350, 200, 100, 100);
                bg.drawOval(500, 200, 100, 100);
                bg.drawOval(350, 350, 100, 100);
                bg.drawOval(500, 350, 100, 100);
                
                // Ellipsen
                bg.setColor(Color.YELLOW);
                bg.fillOval(620, 50, 150, 80);
                bg.setColor(Color.MAGENTA);
                bg.drawOval(620, 200, 150, 80);
                bg.setColor(Color.CYAN);
                bg.fillOval(620, 350, 80, 150);
                
                g2d.drawImage(buffer, 0, 0, null);
                bg.dispose();
                g2d.dispose();
            }
            
            long awtTime = System.currentTimeMillis() - awtStart;
            
            // Timing alle 60 Frames
            if ((System.currentTimeMillis() / 1000) % 5 == 0) {
                System.out.printf("[DrawOval] FastGraphics: %dms | AWT: %dms | Ratio: %.1fx%n",
                    fgTime, awtTime, (double)awtTime / fgTime);
            }
            
            try { Thread.sleep(16); } catch (InterruptedException e) { break; }
        }
    }
}
