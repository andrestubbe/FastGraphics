package demo;

import javax.swing.*;
import java.awt.*;

/**
 * DrawRoundRect Vergleichstest - AWT Graphics2D vs FastGraphics
 * Testet drawRoundRect() und fillRoundRect() in beiden Implementierungen
 */
public class DrawRoundRectTest {
    
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    
    static { System.loadLibrary("FastGraphics"); }

    private static native void init(long hwnd);
    private static native void clear(float r, float g, float b);
    private static native void present();
    private static native long findWindow(String title);
    private static native void drawRoundRectNative(float x, float y, float w, float h, float arcWidth, float arcHeight, float r, float g, float b);
    private static native void fillRoundRectNative(float x, float y, float w, float h, float arcWidth, float arcHeight, float r, float g, float b);
    
    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale", "1.0");
        
        JFrame fgFrame = new JFrame("FastGraphics - DrawRoundRect Test");
        fgFrame.setSize(WIDTH, HEIGHT);
        fgFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        fgFrame.setLocation(100, 100);
        fgFrame.setVisible(true);
        
        JFrame awtFrame = new JFrame("AWT Graphics2D - DrawRoundRect Test");
        awtFrame.setSize(WIDTH, HEIGHT);
        awtFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        awtFrame.setLocation(100 + WIDTH + 20, 100);
        awtFrame.setVisible(true);
        
        Canvas awtCanvas = new Canvas();
        awtCanvas.setSize(WIDTH, HEIGHT);
        awtFrame.add(awtCanvas);
        awtFrame.setIgnoreRepaint(true);
        awtCanvas.setIgnoreRepaint(true);
        
        long hwnd = 0;
        for (int i = 0; i < 20 && hwnd == 0; i++) {
            try { Thread.sleep(50); } catch (InterruptedException e) {}
            hwnd = findWindow("FastGraphics - DrawRoundRect Test");
        }
        
        if (hwnd == 0) {
            System.err.println("HWND nicht gefunden!");
            return;
        }
        
        init(hwnd);
        
        System.out.println("DrawRoundRect Vergleichstest läuft...");
        System.out.println("Links: FastGraphics | Rechts: AWT Graphics2D");
        
        while (fgFrame.isVisible()) {
            long startTime = System.currentTimeMillis();
            
            clear(0.1f, 0.1f, 0.1f);

            // Kleine abgerundete Rechtecke
            drawRoundRectNative(50, 50, 100, 80, 20, 20, 1.0f, 0.0f, 0.0f);       // Rot
            fillRoundRectNative(200, 50, 100, 80, 20, 20, 0.0f, 1.0f, 0.0f);     // Grün

            // Mittlere abgerundete Rechtecke
            drawRoundRectNative(50, 200, 150, 100, 30, 30, 0.0f, 0.0f, 1.0f);     // Blau
            fillRoundRectNative(250, 200, 150, 100, 30, 30, 1.0f, 1.0f, 0.0f);   // Gelb

            // Große abgerundete Rechtecke
            drawRoundRectNative(50, 400, 200, 120, 40, 40, 1.0f, 0.0f, 1.0f);     // Magenta
            fillRoundRectNative(300, 400, 200, 120, 40, 40, 0.0f, 1.0f, 1.0f);   // Cyan

            // Elliptische Ecken
            drawRoundRectNative(500, 50, 150, 100, 50, 30, 1.0f, 0.5f, 0.0f);   // Orange
            fillRoundRectNative(500, 200, 150, 100, 50, 30, 0.5f, 1.0f, 0.5f); // Hellgrün

            // Sehr abgerundet (fast oval)
            drawRoundRectNative(500, 350, 120, 120, 60, 60, 0.5f, 0.5f, 1.0f);   // Hellblau
            fillRoundRectNative(500, 500, 120, 120, 60, 60, 1.0f, 0.0f, 0.5f);   // Pink

            present();
            
            long fgTime = System.currentTimeMillis() - startTime;
            
            long awtStart = System.currentTimeMillis();
            
            Graphics2D g2d = (Graphics2D) awtCanvas.getGraphics();
            if (g2d != null) {
                g2d.setColor(new Color(25, 25, 25));
                g2d.fillRect(0, 0, WIDTH, HEIGHT);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                
                // Kleine abgerundete Rechtecke
                g2d.setColor(Color.RED);
                g2d.drawRoundRect(50, 50, 100, 80, 20, 20);
                g2d.setColor(Color.GREEN);
                g2d.fillRoundRect(200, 50, 100, 80, 20, 20);

                // Mittlere abgerundete Rechtecke
                g2d.setColor(Color.BLUE);
                g2d.drawRoundRect(50, 200, 150, 100, 30, 30);
                g2d.setColor(Color.YELLOW);
                g2d.fillRoundRect(250, 200, 150, 100, 30, 30);

                // Große abgerundete Rechtecke
                g2d.setColor(Color.MAGENTA);
                g2d.drawRoundRect(50, 400, 200, 120, 40, 40);
                g2d.setColor(Color.CYAN);
                g2d.fillRoundRect(300, 400, 200, 120, 40, 40);

                // Elliptische Ecken
                g2d.setColor(Color.ORANGE);
                g2d.drawRoundRect(500, 50, 150, 100, 50, 30);
                g2d.setColor(new Color(128, 255, 128));
                g2d.fillRoundRect(500, 200, 150, 100, 50, 30);

                // Sehr abgerundet (fast oval)
                g2d.setColor(new Color(128, 128, 255));
                g2d.drawRoundRect(500, 350, 120, 120, 60, 60);
                g2d.setColor(new Color(255, 0, 128));
                g2d.fillRoundRect(500, 500, 120, 120, 60, 60);
                
                g2d.dispose();
            }
            
            long awtTime = System.currentTimeMillis() - awtStart;
            
            if ((System.currentTimeMillis() / 1000) % 5 == 0) {
                System.out.printf("[DrawRoundRect] FastGraphics: %dms | AWT: %dms | Ratio: %.1fx%n",
                    fgTime, awtTime, (double)awtTime / fgTime);
            }
            
            try { Thread.sleep(16); } catch (InterruptedException e) { break; }
        }
    }
}
