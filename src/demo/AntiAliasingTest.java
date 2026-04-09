package demo;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * AntiAliasing Vergleichstest - AWT Graphics2D vs FastGraphics
 * Testet RenderingHints.KEY_ANTIALIASING in beiden Implementierungen
 */
public class AntiAliasingTest {
    
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    
    static { System.loadLibrary("FastGraphics"); }

    private static native void init(long hwnd);
    private static native void clear(float r, float g, float b);
    private static native void present();
    private static native long findWindow(String title);
    private static native void drawOvalNative(float x, float y, float w, float h, float r, float g, float b);
    private static native void drawLineNative(float x1, float y1, float x2, float y2, float r, float g, float b);
    private static native void setAntiAliasingNative(boolean enabled);
    
    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale", "1.0");
        
        JFrame fgFrame = new JFrame("FastGraphics - AntiAliasing Test");
        fgFrame.setSize(WIDTH, HEIGHT);
        fgFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        fgFrame.setLocation(100, 100);
        fgFrame.setVisible(true);
        
        JFrame awtFrame = new JFrame("AWT Graphics2D - AntiAliasing Test");
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
            hwnd = findWindow("FastGraphics - AntiAliasing Test");
        }
        
        if (hwnd == 0) {
            System.err.println("HWND nicht gefunden!");
            return;
        }
        
        init(hwnd);
        
        System.out.println("AntiAliasing Vergleichstest läuft...");
        System.out.println("Links: FastGraphics | Rechts: AWT Graphics2D");
        System.out.println("Oben: Ohne AntiAliasing | Unten: Mit AntiAliasing");
        
        boolean useAA = false;
        long lastSwitch = System.currentTimeMillis();
        
        while (fgFrame.isVisible()) {
            long startTime = System.currentTimeMillis();
            
            // AntiAliasing alle 5 Sekunden umschalten
            if (System.currentTimeMillis() - lastSwitch > 5000) {
                useAA = !useAA;
                lastSwitch = System.currentTimeMillis();
                System.out.println("AntiAliasing: " + (useAA ? "ON" : "OFF"));
            }
            
            clear(0.1f, 0.1f, 0.1f);
            setAntiAliasingNative(useAA);

            // Oben: Ohne AntiAliasing (immer OFF)
            setAntiAliasingNative(false);
            
            // Ovale oben
            drawOvalNative(100, 50, 100, 100, 1.0f, 0.0f, 0.0f);      // Roter Kreis
            drawOvalNative(300, 50, 150, 80, 0.0f, 1.0f, 0.0f);     // Grüne Ellipse
            drawOvalNative(500, 50, 80, 120, 0.0f, 0.0f, 1.0f);    // Blaue Ellipse

            // Linien oben
            drawLineNative(50, 200, 750, 200, 1.0f, 1.0f, 0.0f);    // Gelbe Linie
            drawLineNative(50, 220, 750, 220, 1.0f, 0.0f, 1.0f);    // Magenta Linie
            drawLineNative(50, 240, 750, 240, 0.0f, 1.0f, 1.0f);    // Cyan Linie

            // Unten: Mit AntiAliasing (je nach useAA)
            setAntiAliasingNative(useAA);
            
            // Ovale unten
            drawOvalNative(100, 300, 100, 100, 1.0f, 0.5f, 0.0f);    // Orange Kreis
            drawOvalNative(300, 300, 150, 80, 0.5f, 1.0f, 0.5f);    // Hellgrüne Ellipse
            drawOvalNative(500, 300, 80, 120, 0.5f, 0.5f, 1.0f);    // Hellblaue Ellipse

            // Linien unten
            drawLineNative(50, 450, 750, 450, 0.8f, 0.8f, 0.8f);    // Hellgraue Linie
            drawLineNative(50, 470, 750, 470, 0.5f, 0.5f, 0.5f);    // Graue Linie
            drawLineNative(50, 490, 750, 490, 0.3f, 0.3f, 0.3f);    // Dunkelgraue Linie

            present();
            
            long fgTime = System.currentTimeMillis() - startTime;
            
            long awtStart = System.currentTimeMillis();
            
            Graphics2D g2d = (Graphics2D) awtCanvas.getGraphics();
            if (g2d != null) {
                BufferedImage buffer = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
                Graphics2D bg = buffer.createGraphics();
                
                bg.setColor(new Color(25, 25, 25));
                bg.fillRect(0, 0, WIDTH, HEIGHT);
                
                // Oben: Ohne AntiAliasing
                bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                
                // Ovale oben
                bg.setColor(Color.RED);
                bg.drawOval(100, 50, 100, 100);
                bg.setColor(Color.GREEN);
                bg.drawOval(300, 50, 150, 80);
                bg.setColor(Color.BLUE);
                bg.drawOval(500, 50, 80, 120);

                // Linien oben
                bg.setColor(Color.YELLOW);
                bg.drawLine(50, 200, 750, 200);
                bg.setColor(Color.MAGENTA);
                bg.drawLine(50, 220, 750, 220);
                bg.setColor(Color.CYAN);
                bg.drawLine(50, 240, 750, 240);

                // Unten: Mit AntiAliasing (je nach useAA)
                bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, 
                    useAA ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
                
                // Ovale unten
                bg.setColor(Color.ORANGE);
                bg.drawOval(100, 300, 100, 100);
                bg.setColor(new Color(128, 255, 128));
                bg.drawOval(300, 300, 150, 80);
                bg.setColor(new Color(128, 128, 255));
                bg.drawOval(500, 300, 80, 120);

                // Linien unten
                bg.setColor(Color.LIGHT_GRAY);
                bg.drawLine(50, 450, 750, 450);
                bg.setColor(Color.GRAY);
                bg.drawLine(50, 470, 750, 470);
                bg.setColor(Color.DARK_GRAY);
                bg.drawLine(50, 490, 750, 490);
                
                g2d.drawImage(buffer, 0, 0, null);
                bg.dispose();
                g2d.dispose();
            }
            
            long awtTime = System.currentTimeMillis() - awtStart;
            
            if ((System.currentTimeMillis() / 1000) % 5 == 0) {
                System.out.printf("[AntiAliasing] FastGraphics: %dms | AWT: %dms | Ratio: %.1fx | AA: %s%n",
                    fgTime, awtTime, (double)awtTime / fgTime, useAA ? "ON" : "OFF");
            }
            
            try { Thread.sleep(16); } catch (InterruptedException e) { break; }
        }
    }
}
