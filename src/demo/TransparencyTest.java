package demo;

import fastgraphics.FastGraphics2D;
import javax.swing.JFrame;
import java.awt.Color;

/**
 * Test für Alpha-Transparenz in FastGraphics2D
 * Zeigt überlappende transparente Formen
 */
public class TransparencyTest {
    public static void main(String[] args) throws Exception {
        // Fenster erstellen
        JFrame frame = new JFrame("FastGraphics Transparency Test");
        frame.setSize(800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
        
        // HWND finden
        Thread.sleep(100);
        long hwnd = FastGraphics2D.findWindow("FastGraphics Transparency Test");
        System.out.println("HWND: " + hwnd);
        
        // FastGraphics initialisieren
        FastGraphics2D g = new FastGraphics2D(hwnd);
        
        System.out.println("Transparency Test - Drücke ESC zum Beenden");
        
        // Demo-Schleife
        long startTime = System.currentTimeMillis();
        while (frame.isVisible()) {
            double time = (System.currentTimeMillis() - startTime) / 1000.0;
            
            // Hintergrund löschen (dunkelgrau)
            g.setColor(new Color(40, 40, 40));
            g.clear(g.getRenderingHint(null) != null ? Color.BLACK : new Color(40, 40, 40));
            g.clear(new Color(40, 40, 40));
            
            // Roter Kreis (50% transparent)
            g.setColor(new Color(255, 0, 0, 128));
            g.fillOval(200, 150, 200, 200);
            
            // Grüner Kreis (50% transparent, überlappend)
            g.setColor(new Color(0, 255, 0, 128));
            g.fillOval(300, 150, 200, 200);
            
            // Blauer Kreis (50% transparent, überlappend)
            g.setColor(new Color(0, 0, 255, 128));
            g.fillOval(250, 250, 200, 200);
            
            // Transparente Rechtecke
            g.setColor(new Color(255, 255, 0, 100));
            g.fillRect(100, 100, 150, 150);
            
            g.setColor(new Color(255, 0, 255, 100));
            g.fillRect(150, 150, 150, 150);
            
            // Transparente abgerundete Rechtecke (neues Feature!)
            g.setColor(new Color(0, 255, 255, 150));
            g.fillRoundRect(450, 200, 200, 150, 40, 40);
            
            // Outline mit Transparenz
            g.setColor(new Color(255, 255, 255, 200));
            g.drawRoundRect(450, 200, 200, 150, 40, 40);
            
            // Animierter transparenter Kreis
            int animX = (int)(400 + Math.sin(time) * 150);
            int animY = (int)(300 + Math.cos(time * 0.7) * 100);
            g.setColor(new Color(255, 128, 0, 160));
            g.fillOval(animX - 50, animY - 50, 100, 100);
            
            // Viele kleine transparente Partikel
            for (int i = 0; i < 20; i++) {
                double angle = time + i * 0.3;
                int px = (int)(400 + Math.cos(angle) * (200 + i * 5));
                int py = (int)(300 + Math.sin(angle) * (150 + i * 5));
                int alpha = 50 + (i * 10);
                g.setColor(new Color(200, 200, 255, alpha));
                g.fillRect(px, py, 20, 20);
            }
            
            g.present();
            
            // ESC zum Beenden prüfen
            if (System.in.available() > 0 && System.in.read() == 27) {
                break;
            }
            
            Thread.sleep(16); // ~60 FPS
        }
        
        frame.dispose();
        System.out.println("Test beendet.");
    }
}
