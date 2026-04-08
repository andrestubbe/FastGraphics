package demo;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Side-by-Side Benchmark: AWT Graphics2D vs FastGraphics (DirectX)
 */
public class BenchmarkApp {
    
    static { System.loadLibrary("FastGraphics"); }
    
    private static native void init(long hwnd);
    private static native void fillRect(float x, float y, float w, float h, float r, float g, float b);
    private static native void clear(float r, float g, float b);
    private static native void present();
    private static native long findWindow(String title);
    
    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale", "1.0");
        
        // Linke Seite: AWT Graphics2D
        JFrame awtFrame = new JFrame("AWT Graphics2D - Standard Java");
        awtFrame.setSize(800, 600);
        awtFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        awtFrame.setLocation(100, 100);
        
        AWTCanvas awtCanvas = new AWTCanvas();
        awtFrame.add(awtCanvas);
        awtFrame.setVisible(true);
        
        // Rechte Seite: FastGraphics DirectX
        JFrame dxFrame = new JFrame("FastGraphics DirectX");
        dxFrame.setSize(800, 600);
        dxFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        dxFrame.setLocation(920, 100);
        dxFrame.setVisible(true);
        
        // DirectX initialisieren
        long hwnd = 0;
        for (int i = 0; i < 20 && hwnd == 0; i++) {
            try { Thread.sleep(50); } catch (InterruptedException e) {}
            hwnd = findWindow("FastGraphics DirectX");
        }
        
        if (hwnd == 0) {
            System.out.println("FEHLER: DirectX HWND nicht gefunden");
            return;
        }
        
        init(hwnd);
        
        // DirectX Animation starten
        DXCanvas dxCanvas = new DXCanvas();
        Thread dxThread = new Thread(() -> {
            JFrame frame = null;
            for (int i = 0; i < 20 && frame == null; i++) {
                try { Thread.sleep(100); } catch (InterruptedException e) {}
                frame = dxFrame;
            }
            
            while (frame != null && frame.isVisible()) {
                dxCanvas.render();
                
                // FPS im Titel aktualisieren
                if (dxCanvas.fps > 0) {
                    frame.setTitle("FastGraphics DirectX - " + dxCanvas.fps + " FPS");
                }
                
                try { Thread.sleep(16); } catch (InterruptedException e) { break; }
            }
        });
        dxThread.start();
        
        System.out.println("Benchmark läuft...");
        System.out.println("Links: AWT Graphics2D | Rechts: FastGraphics DirectX");
        System.out.println("Schließe eines der Fenster zum Beenden\n");
        
        // Beide Fenster schließen bei einem Klick
        awtFrame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });
        dxFrame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });
    }
    
    // AWT Canvas (linke Seite)
    static class AWTCanvas extends JPanel {
        private int particleCount = 1024;
        private float[] px = new float[particleCount];
        private float[] py = new float[particleCount];
        private float[] pdx = new float[particleCount];
        private float[] pdy = new float[particleCount];
        
        private long frameCount = 0;
        private long lastTime = System.currentTimeMillis();
        private int fps = 0;
        
        public AWTCanvas() {
            // Partikel initialisieren
            for (int i = 0; i < particleCount; i++) {
                px[i] = (float)(Math.random() * 800);
                py[i] = (float)(Math.random() * 600);
                pdx[i] = (float)(Math.random() - 0.5) * 4;
                pdy[i] = (float)(Math.random() - 0.5) * 4;
            }
            
            // Animation Timer
            Timer timer = new Timer(16, e -> repaint());
            timer.start();
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            
            // Hintergrund
            g2d.setColor(new Color(0.02f, 0.02f, 0.05f));
            g2d.fillRect(0, 0, getWidth(), getHeight());
            
            // Partikel bewegen und zeichnen
            for (int i = 0; i < particleCount; i++) {
                px[i] += pdx[i];
                py[i] += pdy[i];
                if (px[i] < 0 || px[i] > 800) pdx[i] = -pdx[i];
                if (py[i] < 0 || py[i] > 600) pdy[i] = -pdy[i];
                
                float hue = (i % 360) / 360.0f;
                g2d.setColor(Color.getHSBColor(hue, 1.0f, 1.0f));
                g2d.fillRect((int)(px[i] - 1), (int)(py[i] - 1), 3, 3);
            }
            
            // FPS berechnen
            frameCount++;
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastTime >= 1000) {
                fps = (int)frameCount;
                frameCount = 0;
                lastTime = currentTime;
                JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(this);
                frame.setTitle("AWT Graphics2D - " + fps + " FPS");
                System.out.println("=== DEBUG AWT Graphics2D ===");
                System.out.println("FPS: " + fps + " | Partikel: " + particleCount + " | Zeit pro Frame: " + (1000.0/fps) + "ms");
                System.out.println("Performance: " + (fps * particleCount) + " Partikel/Sekunde");
                System.out.println("==============================");
            }
        }
    }
    
    // DirectX Canvas (rechte Seite - wird von main() gesteuert)
    static class DXCanvas {
        private int particleCount = 1024;
        private float[] px = new float[particleCount];
        private float[] py = new float[particleCount];
        private float[] pdx = new float[particleCount];
        private float[] pdy = new float[particleCount];
        
        private long frameCount = 0;
        private long lastTime = System.currentTimeMillis();
        public int fps = 0;
        
        public DXCanvas() {
            // Partikel initialisieren
            for (int i = 0; i < particleCount; i++) {
                px[i] = (float)(Math.random() * 800);
                py[i] = (float)(Math.random() * 600);
                pdx[i] = (float)(Math.random() - 0.5) * 4;
                pdy[i] = (float)(Math.random() - 0.5) * 4;
            }
        }
        
        public void render() {
            clear(0.02f, 0.02f, 0.05f);
            
            // Partikel bewegen und zeichnen
            for (int i = 0; i < particleCount; i++) {
                px[i] += pdx[i];
                py[i] += pdy[i];
                if (px[i] < 0 || px[i] > 800) pdx[i] = -pdx[i];
                if (py[i] < 0 || py[i] > 600) pdy[i] = -pdy[i];
                
                fillRect(px[i] - 1, py[i] - 1, 3, 3, 1.0f, 0.0f, 0.0f);
            }
            
            present();
            
            // FPS berechnen
            frameCount++;
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastTime >= 1000) {
                fps = (int)frameCount;
                frameCount = 0;
                lastTime = currentTime;
                System.out.println("=== DEBUG FastGraphics DirectX ===");
                System.out.println("FPS: " + fps + " | Partikel: " + particleCount + " | Zeit pro Frame: " + (1000.0/fps) + "ms");
                System.out.println("Performance: " + (fps * particleCount) + " Partikel/Sekunde");
                System.out.println("=====================================");
            }
        }
    }
}
