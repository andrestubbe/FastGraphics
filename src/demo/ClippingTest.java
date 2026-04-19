package demo;

import fastgraphics.FastGraphics2D;
import javax.swing.JFrame;
import java.awt.Color;

/**
 * Test für Clipping mit FastGraphics2D
 */
public class ClippingTest {
    public static void main(String[] args) throws Exception {
        JFrame frame = new JFrame("FastGraphics Clipping Test");
        frame.setSize(800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
        
        Thread.sleep(100);
        long hwnd = FastGraphics2D.findWindow("FastGraphics Clipping Test");
        System.out.println("HWND: " + hwnd);
        
        FastGraphics2D g = new FastGraphics2D(hwnd);
        
        System.out.println("Clipping Test - Drücke ESC zum Beenden");
        System.out.println("Test 1: Ein rotes Rechteck OHNE Clipping (links oben)");
        System.out.println("Test 2: Ein blaues Rechteck MIT Clipping (rechts oben) - nur Teil sichtbar");
        System.out.println("Test 3: Drei Kreise, nur der mittlere ist voll sichtbar (Clipping-Region)");
        
        // Animation loop
        long startTime = System.currentTimeMillis();
        while (frame.isVisible()) {
            double time = (System.currentTimeMillis() - startTime) / 1000.0;
            
            // Hintergrund schwarz
            g.setColor(Color.BLACK);
            g.clear(Color.BLACK);
            
            // === TEST 1: Kein Clipping ===
            g.resetClip();
            g.setColor(Color.RED);
            g.fillRect(50, 50, 200, 150);
            // Weißer Rahmen um zu zeigen wo das Rechteck ist
            g.setColor(Color.WHITE);
            g.drawRect(50, 50, 200, 150);
            
            // === TEST 2: Mit Clipping ===
            // Clipping-Region: rechteckiger Ausschnitt
            float clipX = 400 + (float)Math.sin(time) * 50;
            float clipY = 50;
            float clipW = 150;
            float clipH = 150;
            
            g.setClip(clipX, clipY, clipW, clipH);
            g.setColor(Color.BLUE);
            // Dieses große Rechteck wird geclippt
            g.fillRect(350, 0, 300, 250);
            g.resetClip();
            // Rahmen der Clipping-Region zeichnen (gelb)
            g.setColor(Color.YELLOW);
            g.drawRect(clipX, clipY, clipW, clipH);
            
            // === TEST 3: Drei Kreise mit Clipping in der Mitte ===
            float centerClipX = 200;
            float centerClipY = 350;
            float centerClipW = 200;
            float centerClipH = 200;
            
            g.setClip(centerClipX, centerClipY, centerClipW, centerClipH);
            // Linker Kreis (wird teilweise geclippt)
            g.setColor(new Color(255, 100, 100));
            g.fillOval(100, 375, 150, 150);
            // Mittlerer Kreis (voll sichtbar)
            g.setColor(new Color(100, 255, 100));
            g.fillOval(225, 375, 150, 150);
            // Rechter Kreis (wird teilweise geclippt)
            g.setColor(new Color(100, 100, 255));
            g.fillOval(350, 375, 150, 150);
            g.resetClip();
            // Rahmen der Clipping-Region
            g.setColor(Color.CYAN);
            g.drawRect(centerClipX, centerClipY, centerClipW, centerClipH);
            
            // === TEST 4: Animierter Clip-Bereich ===
            float animClipX = 500 + (float)Math.cos(time * 2) * 30;
            float animClipY = 350 + (float)Math.sin(time * 1.5) * 30;
            g.setClip(animClipX, animClipY, 180, 180);
            // Mehrere überlappende Rechtecke
            for (int i = 0; i < 5; i++) {
                float offset = i * 20 + (float)Math.sin(time + i) * 10;
                g.setColor(new Color(255 - i * 40, i * 50, 150 + i * 20));
                g.fillRect(450 + offset, 300 + offset, 120, 120);
            }
            g.resetClip();
            // Rahmen des animierten Clips
            g.setColor(Color.MAGENTA);
            g.drawRect(animClipX, animClipY, 180, 180);
            
            // === Labels ===
            // (Kein Text-Rendering, daher nur visuelle Markierungen)
            g.setColor(Color.WHITE);
            g.drawLine(50, 40, 250, 40); // Linie über rotem Rechteck
            g.drawLine(clipX, 40, clipX + clipW, 40); // Linie über blauem Bereich
            
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
