package demo;

import javax.swing.*;
import java.awt.*;

/**
 * Transform Vergleichstest - AWT Graphics2D vs FastGraphics
 * Testet translate(), scale(), rotate() in beiden Implementierungen
 * HINWEIS: Transformationen in FastGraphics sind aktuell nur Stub-Implementierungen
 */
public class TransformTest {
    
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    
    static { System.loadLibrary("FastGraphics"); }

    private static native void init(long hwnd);
    private static native void clear(float r, float g, float b);
    private static native void present();
    private static native long findWindow(String title);
    private static native void fillRectNative(float x, float y, float w, float h, float r, float g, float b);
    private static native void translateNative(float tx, float ty);
    private static native void scaleNative(float sx, float sy);
    private static native void rotateNative(float angle);
    private static native void resetTransformNative();
    
    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale", "1.0");
        
        JFrame fgFrame = new JFrame("FastGraphics - Transform Test");
        fgFrame.setSize(WIDTH, HEIGHT);
        fgFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        fgFrame.setLocation(100, 100);
        fgFrame.setVisible(true);
        
        JFrame awtFrame = new JFrame("AWT Graphics2D - Transform Test");
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
            hwnd = findWindow("FastGraphics - Transform Test");
        }
        
        if (hwnd == 0) {
            System.err.println("HWND nicht gefunden!");
            return;
        }
        
        init(hwnd);
        
        System.out.println("Transform Vergleichstest läuft...");
        System.out.println("HINWEIS: Transformationen in FastGraphics sind aktuell nur Stub-Implementierungen");
        System.out.println("Links: FastGraphics | Rechts: AWT Graphics2D");
        
        while (fgFrame.isVisible()) {
            long startTime = System.currentTimeMillis();
            
            clear(0.1f, 0.1f, 0.1f);

            // Ohne Transformation
            fillRectNative(50, 50, 50, 50, 1.0f, 0.0f, 0.0f);

            // Translate
            translateNative(100, 100);
            fillRectNative(50, 50, 50, 50, 0.0f, 1.0f, 0.0f);
            resetTransformNative();

            // Scale
            scaleNative(2.0f, 2.0f);
            fillRectNative(50, 200, 50, 50, 0.0f, 0.0f, 1.0f);
            resetTransformNative();

            present();
            
            long fgTime = System.currentTimeMillis() - startTime;
            
            long awtStart = System.currentTimeMillis();
            
            Graphics2D g2d = (Graphics2D) awtCanvas.getGraphics();
            if (g2d != null) {
                g2d.setColor(new Color(25, 25, 25));
                g2d.fillRect(0, 0, WIDTH, HEIGHT);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                
                // Ohne Transformation
                g2d.setColor(Color.RED);
                g2d.fillRect(50, 50, 50, 50);

                // Translate
                g2d.setColor(Color.GREEN);
                g2d.translate(100, 100);
                g2d.fillRect(50, 50, 50, 50);
                g2d.translate(-100, -100);

                // Scale
                g2d.setColor(Color.BLUE);
                g2d.scale(2.0f, 2.0f);
                g2d.fillRect(50, 200, 50, 50);
                g2d.scale(0.5f, 0.5f);
                
                g2d.dispose();
            }
            
            long awtTime = System.currentTimeMillis() - awtStart;
            
            if ((System.currentTimeMillis() / 1000) % 5 == 0) {
                System.out.printf("[Transform] FastGraphics: %dms | AWT: %dms | Ratio: %.1fx%n",
                    fgTime, awtTime, (double)awtTime / fgTime);
            }
            
            try { Thread.sleep(16); } catch (InterruptedException e) { break; }
        }
    }
}
