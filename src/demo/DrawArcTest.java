package demo;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * DrawArc Vergleichstest - AWT Graphics2D vs FastGraphics
 * Testet drawArc() und fillArc() in beiden Implementierungen
 */
public class DrawArcTest {
    
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    
    static { System.loadLibrary("FastGraphics"); }

    private static native void init(long hwnd);
    private static native void clear(float r, float g, float b);
    private static native void present();
    private static native long findWindow(String title);
    private static native void drawArcNative(float x, float y, float w, float h, float startAngle, float arcAngle, float r, float g, float b);
    private static native void fillArcNative(float x, float y, float w, float h, float startAngle, float arcAngle, float r, float g, float b);
    
    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale", "1.0");
        
        JFrame fgFrame = new JFrame("FastGraphics - drawArc Test");
        fgFrame.setSize(WIDTH, HEIGHT);
        fgFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        fgFrame.setLocation(100, 100);
        fgFrame.setVisible(true);
        
        JFrame awtFrame = new JFrame("AWT Graphics2D - drawArc Test");
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
            hwnd = findWindow("FastGraphics - drawArc Test");
        }
        
        if (hwnd == 0) {
            System.err.println("HWND nicht gefunden!");
            return;
        }
        
        init(hwnd);
        
        System.out.println("DrawArc Vergleichstest läuft...");
        System.out.println("Links: FastGraphics | Rechts: AWT Graphics2D");
        
        while (fgFrame.isVisible()) {
            long startTime = System.currentTimeMillis();
            
            clear(0.1f, 0.1f, 0.1f);

            // Outline Arcs
            drawArcNative(50, 50, 100, 100, 0, 90, 1.0f, 0.0f, 0.0f);      // Roter 90° Arc
            drawArcNative(200, 50, 100, 100, 90, 180, 0.0f, 1.0f, 0.0f);     // Grüner 180° Arc
            drawArcNative(350, 50, 100, 100, 180, 270, 0.0f, 0.0f, 1.0f);    // Blauer 90° Arc

            // Filled Arcs
            fillArcNative(50, 200, 100, 100, 0, 120, 1.0f, 1.0f, 0.0f);    // Gelber 120° Arc
            fillArcNative(200, 200, 100, 100, 120, 240, 1.0f, 0.0f, 1.0f);  // Magenta 120° Arc
            fillArcNative(350, 200, 100, 100, 240, 360, 0.0f, 1.0f, 1.0f);  // Cyan 120° Arc

            // Elliptische Arcs
            drawArcNative(500, 50, 150, 80, 0, 180, 1.0f, 0.784f, 0.0f);     // Orange Ellipsen-Arc (255, 200, 0)
            fillArcNative(500, 200, 150, 80, 180, 360, 0.5f, 1.0f, 0.5f); // Hellgrüner gefüllter Ellipsen-Arc

            // Kleine Arcs
            drawArcNative(50, 350, 50, 50, 0, 45, 1.0f, 0.753f, 0.796f); // Pink
            fillArcNative(120, 350, 50, 50, 45, 90, 0.5f, 0.5f, 0.5f);    // Grau

            // Große Arcs
            drawArcNative(200, 350, 200, 100, 0, 270, 0.8f, 0.8f, 0.8f);  // Hellgrau
            fillArcNative(450, 350, 200, 100, 270, 360, 0.3f, 0.3f, 0.3f); // Dunkelgrau

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
                
                // Outline Arcs
                bg.setColor(Color.RED);
                bg.drawArc(50, 50, 100, 100, 0, 90);
                bg.setColor(Color.GREEN);
                bg.drawArc(200, 50, 100, 100, 90, 180);
                bg.setColor(Color.BLUE);
                bg.drawArc(350, 50, 100, 100, 180, 270);

                // Filled Arcs
                bg.setColor(Color.YELLOW);
                bg.fillArc(50, 200, 100, 100, 0, 120);
                bg.setColor(Color.MAGENTA);
                bg.fillArc(200, 200, 100, 100, 120, 240);
                bg.setColor(Color.CYAN);
                bg.fillArc(350, 200, 100, 100, 240, 360);

                // Elliptische Arcs
                bg.setColor(Color.ORANGE);
                bg.drawArc(500, 50, 150, 80, 0, 180);
                bg.setColor(new Color(128, 255, 128));
                bg.fillArc(500, 200, 150, 80, 180, 360);

                // Kleine Arcs
                bg.setColor(Color.PINK);
                bg.drawArc(50, 350, 50, 50, 0, 45);
                bg.setColor(Color.GRAY);
                bg.fillArc(120, 350, 50, 50, 45, 90);

                // Große Arcs
                bg.setColor(Color.LIGHT_GRAY);
                bg.drawArc(200, 350, 200, 100, 0, 270);
                bg.setColor(Color.DARK_GRAY);
                bg.fillArc(450, 350, 200, 100, 270, 360);
                
                g2d.drawImage(buffer, 0, 0, null);
                bg.dispose();
                g2d.dispose();
            }
            
            long awtTime = System.currentTimeMillis() - awtStart;
            
            if ((System.currentTimeMillis() / 1000) % 5 == 0) {
                System.out.printf("[DrawArc] FastGraphics: %dms | AWT: %dms | Ratio: %.1fx%n",
                    fgTime, awtTime, (double)awtTime / fgTime);
            }
            
            try { Thread.sleep(16); } catch (InterruptedException e) { break; }
        }
    }
}
