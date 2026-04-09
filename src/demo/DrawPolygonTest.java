package demo;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * DrawPolygon Vergleichstest - AWT Graphics2D vs FastGraphics
 * Testet drawPolygon() und fillPolygon() in beiden Implementierungen
 */
public class DrawPolygonTest {
    
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    
    static { System.loadLibrary("FastGraphics"); }

    private static native void init(long hwnd);
    private static native void clear(float r, float g, float b);
    private static native void present();
    private static native long findWindow(String title);
    private static native void drawPolygonNative(float[] xPoints, float[] yPoints, int nPoints, float r, float g, float b);
    private static native void fillPolygonNative(float[] xPoints, float[] yPoints, int nPoints, float r, float g, float b);
    
    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale", "1.0");
        
        JFrame fgFrame = new JFrame("FastGraphics - drawPolygon Test");
        fgFrame.setSize(WIDTH, HEIGHT);
        fgFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        fgFrame.setLocation(100, 100);
        fgFrame.setVisible(true);
        
        JFrame awtFrame = new JFrame("AWT Graphics2D - drawPolygon Test");
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
            hwnd = findWindow("FastGraphics - drawPolygon Test");
        }
        
        if (hwnd == 0) {
            System.err.println("HWND nicht gefunden!");
            return;
        }
        
        init(hwnd);
        
        System.out.println("DrawPolygon Vergleichstest läuft...");
        System.out.println("Links: FastGraphics | Rechts: AWT Graphics2D");
        
        while (fgFrame.isVisible()) {
            long startTime = System.currentTimeMillis();
            
            clear(0.1f, 0.1f, 0.1f);

            // Dreiecke
            float[] triX = {100, 150, 50};
            float[] triY = {50, 150, 150};
            fillPolygonNative(triX, triY, 3, 1.0f, 0.0f, 0.0f);

            float[] triX2 = {200, 250, 150};
            float[] triY2 = {50, 150, 150};
            fillPolygonNative(triX2, triY2, 3, 0.0f, 1.0f, 0.0f);

            // Vierecke
            float[] quadX = {300, 400, 400, 300};
            float[] quadY = {50, 50, 150, 150};
            fillPolygonNative(quadX, quadY, 4, 0.0f, 0.0f, 1.0f);

            float[] quadX2 = {420, 520, 520, 420};
            float[] quadY2 = {50, 50, 150, 150};
            drawPolygonNative(quadX2, quadY2, 4, 1.0f, 1.0f, 0.0f);

            // Fünfeck
            float[] pentX = {100, 150, 130, 70, 50};
            float[] pentY = {200, 220, 280, 280, 220};
            fillPolygonNative(pentX, pentY, 5, 1.0f, 0.0f, 1.0f);

            float[] pentX2 = {200, 250, 230, 170, 150};
            float[] pentY2 = {200, 220, 280, 280, 220};
            drawPolygonNative(pentX2, pentY2, 5, 0.0f, 1.0f, 1.0f);

            // Sechseck
            float[] hexX = {350, 400, 400, 350, 300, 300};
            float[] hexY = {200, 220, 280, 300, 280, 220};
            fillPolygonNative(hexX, hexY, 6, 1.0f, 0.5f, 0.0f);

            // Achteck (konvex statt Stern)
            float[] octX = {500, 540, 560, 540, 500, 460, 440, 460};
            float[] octY = {200, 210, 250, 290, 300, 290, 250, 210};
            fillPolygonNative(octX, octY, 8, 0.5f, 1.0f, 0.5f);

            // Irreguläres Polygon
            float[] irrX = {600, 650, 700, 680, 620, 580};
            float[] irrY = {200, 180, 220, 300, 350, 280};
            drawPolygonNative(irrX, irrY, 6, 1.0f, 0.753f, 0.796f);

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
                
                // Dreiecke
                int[] triXInt = {100, 150, 50};
                int[] triYInt = {50, 150, 150};
                bg.setColor(Color.RED);
                bg.fillPolygon(triXInt, triYInt, 3);

                int[] triXInt2 = {200, 250, 150};
                int[] triYInt2 = {50, 150, 150};
                bg.setColor(Color.GREEN);
                bg.fillPolygon(triXInt2, triYInt2, 3);

                // Vierecke
                int[] quadXInt = {300, 400, 400, 300};
                int[] quadYInt = {50, 50, 150, 150};
                bg.setColor(Color.BLUE);
                bg.fillPolygon(quadXInt, quadYInt, 4);

                int[] quadXInt2 = {420, 520, 520, 420};
                int[] quadYInt2 = {50, 50, 150, 150};
                bg.setColor(Color.YELLOW);
                bg.drawPolygon(quadXInt2, quadYInt2, 4);

                // Fünfeck
                int[] pentXInt = {100, 150, 130, 70, 50};
                int[] pentYInt = {200, 220, 280, 280, 220};
                bg.setColor(Color.MAGENTA);
                bg.fillPolygon(pentXInt, pentYInt, 5);

                int[] pentXInt2 = {200, 250, 230, 170, 150};
                int[] pentYInt2 = {200, 220, 280, 280, 220};
                bg.setColor(Color.CYAN);
                bg.drawPolygon(pentXInt2, pentYInt2, 5);

                // Sechseck
                int[] hexXInt = {350, 400, 400, 350, 300, 300};
                int[] hexYInt = {200, 220, 280, 300, 280, 220};
                bg.setColor(Color.ORANGE);
                bg.fillPolygon(hexXInt, hexYInt, 6);

                // Achteck
                int[] octXInt = {500, 540, 560, 540, 500, 460, 440, 460};
                int[] octYInt = {200, 210, 250, 290, 300, 290, 250, 210};
                bg.setColor(new Color(128, 255, 128));
                bg.fillPolygon(octXInt, octYInt, 8);

                // Irreguläres Polygon
                int[] irrXInt = {600, 650, 700, 680, 620, 580};
                int[] irrYInt = {200, 180, 220, 300, 350, 280};
                bg.setColor(Color.PINK);
                bg.drawPolygon(irrXInt, irrYInt, 6);
                
                g2d.drawImage(buffer, 0, 0, null);
                bg.dispose();
                g2d.dispose();
            }
            
            long awtTime = System.currentTimeMillis() - awtStart;
            
            if ((System.currentTimeMillis() / 1000) % 5 == 0) {
                System.out.printf("[DrawPolygon] FastGraphics: %dms | AWT: %dms | Ratio: %.1fx%n",
                    fgTime, awtTime, (double)awtTime / fgTime);
            }
            
            try { Thread.sleep(16); } catch (InterruptedException e) { break; }
        }
    }
}
