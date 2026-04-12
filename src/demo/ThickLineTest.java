package demo;

import fastgraphics.FastGraphics2D;
import javax.swing.JFrame;
import java.awt.Color;

/**
 * Test für Thick Lines / Linienstärke in FastGraphics2D
 */
public class ThickLineTest {
    public static void main(String[] args) throws Exception {
        JFrame frame = new JFrame("FastGraphics Thick Lines Test");
        frame.setSize(800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
        
        Thread.sleep(100);
        long hwnd = FastGraphics2D.findWindow("FastGraphics Thick Lines Test");
        System.out.println("HWND: " + hwnd);
        
        FastGraphics2D g = new FastGraphics2D(hwnd);
        
        System.out.println("Thick Lines Test - Drücke ESC zum Beenden");
        
        long startTime = System.currentTimeMillis();
        while (frame.isVisible()) {
            double time = (System.currentTimeMillis() - startTime) / 1000.0;
            
            // Hintergrund - SCHWARZ
            g.setColor(Color.BLACK);
            g.clear(Color.BLACK);
            
            // Dünne Linie (1 Pixel) - WEISS
            g.setStroke(1.0f);
            g.setColor(Color.WHITE);
            g.drawLine(50, 50, 200, 100);
            
            // Dicke Linien verschiedener Stärken - ALLE WEISS
            g.setStroke(5.0f);
            g.setColor(Color.WHITE);
            g.drawLine(50, 150, 300, 150);
            
            g.setStroke(10.0f);
            g.setColor(Color.WHITE);
            g.drawLine(50, 200, 350, 250);
            
            g.setStroke(20.0f);
            g.setColor(Color.WHITE);
            g.drawLine(50, 300, 400, 400);
            
            // Sehr dicke Linie - WEISS
            g.setStroke(40.0f);
            g.setColor(Color.WHITE);
            g.drawLine(450, 100, 750, 500);
            
            // Vertikale und horizontale dicke Linien - WEISS
            g.setStroke(15.0f);
            g.setColor(Color.WHITE);
            g.drawLine(500, 50, 500, 200);  // Vertikal
            g.drawLine(550, 125, 750, 125); // Horizontal
            
            // Diagonale Linien verschiedener Dicken - WEISS
            g.setStroke(3.0f);
            g.setColor(Color.WHITE);
            g.drawLine(600, 300, 700, 400);
            
            g.setStroke(8.0f);
            g.setColor(Color.WHITE);
            g.drawLine(620, 320, 720, 420);
            
            g.setStroke(15.0f);
            g.setColor(Color.WHITE);
            g.drawLine(640, 340, 740, 440);
            
            // Animierter Strich - pulsierende Breite - WEISS
            float pulseWidth = 5.0f + (float)Math.sin(time * 3.0) * 4.0f;
            g.setStroke(Math.max(1.0f, pulseWidth));
            g.setColor(Color.WHITE);
            g.drawLine(100, 500, 400, 500);
            
            g.present();
            
            if (System.in.available() > 0 && System.in.read() == 27) {
                break;
            }
            
            Thread.sleep(16);
        }
        
        frame.dispose();
        System.out.println("Test beendet.");
    }
}
