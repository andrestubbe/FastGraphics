package demo;

import fastgraphics.FastGraphics2D;
import javax.swing.JFrame;
import java.awt.Color;

/**
 * Einfacher Clipping-Test - statisch, keine Animation
 */
public class SimpleClipTest {
    public static void main(String[] args) throws Exception {
        JFrame frame = new JFrame("Simple Clipping Test");
        frame.setSize(800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
        
        Thread.sleep(100);
        long hwnd = FastGraphics2D.findWindow("Simple Clipping Test");
        
        FastGraphics2D g = new FastGraphics2D(hwnd);
        
        System.out.println("SIMPLER CLIPPING TEST");
        System.out.println("=====================");
        System.out.println("Erwartet:");
        System.out.println("1. Roter Kreis OHNE Clipping (ganz links) - voll sichtbar");
        System.out.println("2. Roter Kreis MIT Clipping (mitte) - nur Teil im gelben Rahmen");
        System.out.println("Drücke ESC zum Beenden");
        
        while (frame.isVisible()) {
            // Hintergrund schwarz
            g.setColor(Color.BLACK);
            g.clear(Color.BLACK);
            
            // === TEST 1: Rotes Rechteck OHNE Clipping ===
            g.resetClip();
            g.setColor(Color.RED);
            g.fillRect(50, 200, 200, 200); // Großes rotes Rechteck links
            
            // === TEST 2: Rotes Rechteck MIT Clipping ===
            // Clip-Region: kleines Rechteck in der Mitte
            g.setClip(400, 250, 150, 150);
            g.setColor(Color.RED);
            g.fillRect(350, 200, 200, 200); // Gleiches Rechteck, aber geclippt
            g.flush(); // WICHTIG: Sofort rendern mit Clipping!
            g.resetClip();
            
            // Rahmen der Clip-Region (gelb) - zeigt wo Clipping sein sollte
            g.setColor(Color.YELLOW);
            g.drawRect(400, 250, 150, 150);
            
            // Beschriftung
            g.setColor(Color.WHITE);
            g.drawLine(50, 190, 250, 190); // Linie über linkem Kreis
            g.drawLine(400, 240, 550, 240); // Linie über geclipptem Bereich
            
            g.present();
            
            // ESC zum Beenden
            if (System.in.available() > 0 && System.in.read() == 27) {
                break;
            }
            
            Thread.sleep(16);
        }
        
        frame.dispose();
        System.out.println("Test beendet.");
    }
}
