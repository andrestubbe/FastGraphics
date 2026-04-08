package demo;

import fastgraphics.FastGraphics2D;
import javax.swing.*;
import java.awt.Color;

/**
 * Test für FastGraphics2D mit Batching
 */
public class BatchTest {
    
    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale", "1.0");
        
        JFrame frame = new JFrame("FastGraphics2D Batching Test");
        frame.setSize(1600, 1200);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        
        // HWND holen
        long hwnd = 0;
        for (int i = 0; i < 20 && hwnd == 0; i++) {
            try { Thread.sleep(100); } catch (InterruptedException e) {}
            hwnd = FastGraphics2D.findWindow("FastGraphics2D Batching Test");
        }
        
        if (hwnd == 0) {
            System.out.println("FEHLER: HWND nicht gefunden");
            return;
        }
        
        // FastGraphics2D mit Batching
        FastGraphics2D g2d = new FastGraphics2D((int)hwnd, 10000);
        
        System.out.println("Batching Test läuft...");
        System.out.println("Zeichne 10000 Rechtecke pro Frame mit 1 Draw-Call!");
        
        int particleCount = 10000;
        float[] px = new float[particleCount];
        float[] py = new float[particleCount];
        float[] pdx = new float[particleCount];
        float[] pdy = new float[particleCount];
        
        for (int i = 0; i < particleCount; i++) {
            px[i] = (float)(Math.random() * 1600);
            py[i] = (float)(Math.random() * 1200);
            pdx[i] = (float)(Math.random() - 0.5) * 4;
            pdy[i] = (float)(Math.random() - 0.5) * 4;
        }
        
        long frameCount = 0;
        long lastTime = System.currentTimeMillis();
        
        while (frame.isVisible()) {
            // Hintergrund löschen
            g2d.clear(new Color(0.02f, 0.02f, 0.05f));
            
            // Alle Partikel mit setColor zeichnen (wird gebatcht!)
            for (int i = 0; i < particleCount; i++) {
                px[i] += pdx[i];
                py[i] += pdy[i];
                if (px[i] < 0 || px[i] > 1600) pdx[i] = -pdx[i];
                if (py[i] < 0 || py[i] > 1200) pdy[i] = -pdy[i];
                
                // Farbe setzen (wie Graphics2D!)
                float hue = (i % 360) / 360.0f;
                Color c = Color.getHSBColor(hue, 1.0f, 1.0f);
                g2d.setColor(c);
                
                // Rechteck zeichnen (wird zum Batch hinzugefügt)
                g2d.fillRect(px[i] - 5, py[i] - 5, 10, 10);
            }
            
            // Helles Rechteck in unterer linker Ecke
            g2d.setColor(new Color(1.0f, 1.0f, 1.0f)); // Weiß
            g2d.fillRect(0, 1150, 200, 50); // x=0, y=1150, w=200, h=50 (unten links bei 1600x1200)
            
            // Ein Draw-Call für alle 10000 Rechtecke!
            g2d.present();
            
            // FPS
            frameCount++;
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastTime >= 1000) {
                int fps = (int)frameCount;
                frameCount = 0;
                lastTime = currentTime;
                frame.setTitle("FastGraphics2D - " + fps + " FPS (Batching!)");
                System.out.println("FPS: " + fps + " | 10000 Rechtecke mit 1 Draw-Call!");
            }
            
            try { Thread.sleep(1); } catch (InterruptedException e) { break; }
        }
    }
}
