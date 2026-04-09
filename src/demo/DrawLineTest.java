package demo;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * DrawLine Vergleichstest - AWT Graphics2D vs FastGraphics
 * Testet drawLine() in beiden Implementierungen
 */
public class DrawLineTest {
    
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    
    static { System.loadLibrary("FastGraphics"); }

    private static native void init(long hwnd);
    private static native void clear(float r, float g, float b);
    private static native void present();
    private static native long findWindow(String title);
    private static native void drawLineNative(float x1, float y1, float x2, float y2, float r, float g, float b);
    
    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale", "1.0");
        
        JFrame fgFrame = new JFrame("FastGraphics - drawLine Test");
        fgFrame.setSize(WIDTH, HEIGHT);
        fgFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        fgFrame.setLocation(100, 100);
        fgFrame.setVisible(true);
        
        JFrame awtFrame = new JFrame("AWT Graphics2D - drawLine Test");
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
            hwnd = findWindow("FastGraphics - drawLine Test");
        }
        
        if (hwnd == 0) {
            System.err.println("HWND nicht gefunden!");
            return;
        }
        
        init(hwnd);
        
        System.out.println("DrawLine Vergleichstest läuft...");
        System.out.println("Links: FastGraphics | Rechts: AWT Graphics2D");
        
        while (fgFrame.isVisible()) {
            long startTime = System.currentTimeMillis();
            
            clear(0.1f, 0.1f, 0.1f);

            // Horizontale Linien
            drawLineNative(50, 50, 750, 50, 1.0f, 0.0f, 0.0f);
            drawLineNative(50, 100, 750, 100, 0.0f, 1.0f, 0.0f);
            drawLineNative(50, 150, 750, 150, 0.0f, 0.0f, 1.0f);

            // Vertikale Linien
            drawLineNative(100, 200, 100, 550, 1.0f, 1.0f, 0.0f);
            drawLineNative(200, 200, 200, 550, 1.0f, 0.0f, 1.0f);
            drawLineNative(300, 200, 300, 550, 0.0f, 1.0f, 1.0f);

            // Diagonale Linien
            drawLineNative(400, 200, 750, 550, 1.0f, 0.784f, 0.0f);   // Orange (255, 200, 0)
            drawLineNative(400, 550, 750, 200, 1.0f, 0.753f, 0.796f); // Pink (255, 192, 203)

            // Gitter
            for (int i = 0; i < 10; i++) {
                drawLineNative(50 + i * 70, 200, 50 + i * 70, 550, 0.5f, 0.5f, 0.5f);
            }
            for (int i = 0; i < 10; i++) {
                drawLineNative(50, 200 + i * 35, 720, 200 + i * 35, 0.5f, 0.5f, 0.5f);
            }

            present();
            
            long fgTime = System.currentTimeMillis() - startTime;
            
            long awtStart = System.currentTimeMillis();
            
            Graphics2D g2d = (Graphics2D) awtCanvas.getGraphics();
            if (g2d != null) {
                BufferedImage buffer = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
                Graphics2D bg = buffer.createGraphics();
                
                bg.setColor(new Color(25, 25, 25));
                bg.fillRect(0, 0, WIDTH, HEIGHT);
                bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                
                // Horizontale Linien
                bg.setColor(Color.RED);
                bg.drawLine(50, 50, 750, 50);
                bg.setColor(Color.GREEN);
                bg.drawLine(50, 100, 750, 100);
                bg.setColor(Color.BLUE);
                bg.drawLine(50, 150, 750, 150);

                // Vertikale Linien
                bg.setColor(Color.YELLOW);
                bg.drawLine(100, 200, 100, 550);
                bg.setColor(Color.MAGENTA);
                bg.drawLine(200, 200, 200, 550);
                bg.setColor(Color.CYAN);
                bg.drawLine(300, 200, 300, 550);

                // Diagonale Linien
                bg.setColor(Color.ORANGE);
                bg.drawLine(400, 200, 750, 550);
                bg.setColor(Color.PINK);
                bg.drawLine(400, 550, 750, 200);

                // Gitter
                bg.setColor(Color.GRAY);
                for (int i = 0; i < 10; i++) {
                    bg.drawLine(50 + i * 70, 200, 50 + i * 70, 550);
                }
                for (int i = 0; i < 10; i++) {
                    bg.drawLine(50, 200 + i * 35, 720, 200 + i * 35);
                }
                
                g2d.drawImage(buffer, 0, 0, null);
                bg.dispose();
                g2d.dispose();
            }
            
            long awtTime = System.currentTimeMillis() - awtStart;
            
            if ((System.currentTimeMillis() / 1000) % 5 == 0) {
                System.out.printf("[DrawLine] FastGraphics: %dms | AWT: %dms | Ratio: %.1fx%n",
                    fgTime, awtTime, (double)awtTime / fgTime);
            }
            
            try { Thread.sleep(16); } catch (InterruptedException e) { break; }
        }
    }
}
