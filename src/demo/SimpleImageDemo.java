package demo;

import fastgraphics.FastGraphics2D;

import javax.swing.JFrame;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;

/**
 * Einfache Demo: Screenshot anzeigen ohne Animation
 */
public class SimpleImageDemo {
    
    public static void main(String[] args) throws Exception {
        // Fenster erstellen
        JFrame frame = new JFrame("SimpleImageDemo - Statischer Screenshot");
        frame.setSize(800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
        
        // Kurz warten
        Thread.sleep(500);
        
        // Screenshot machen
        System.out.println("Mache Screenshot...");
        Robot robot = new Robot();
        Rectangle screenRect = new Rectangle(0, 0, 1920, 1080);
        BufferedImage screenshot = robot.createScreenCapture(screenRect);
        System.out.println("Screenshot: " + screenshot.getWidth() + "x" + screenshot.getHeight());
        
        // Native Handle holen
        String windowTitle = "SimpleImageDemo - Statischer Screenshot";
        long hwnd = FastGraphics2D.findWindow(windowTitle);
        int retries = 0;
        while (hwnd == 0 && retries < 20) {
            Thread.sleep(100);
            hwnd = FastGraphics2D.findWindow(windowTitle);
            retries++;
        }
        if (hwnd == 0) {
            System.err.println("Fenster nicht gefunden!");
            System.exit(1);
        }
        
        // FastGraphics initialisieren
        FastGraphics2D g = new FastGraphics2D(hwnd);
        
        // Tastatur-Input
        frame.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    System.exit(0);
                }
            }
        });
        
        // Render Loop - statisch, keine Animation
        int windowW = 800;
        int windowH = 600;
        
        // Bild skalieren um ins Fenster zu passen
        float scale = Math.min(
            (float)windowW / screenshot.getWidth(),
            (float)windowH / screenshot.getHeight()
        ) * 0.8f; // 80% vom Fenster
        
        int imgW = (int)(screenshot.getWidth() * scale);
        int imgH = (int)(screenshot.getHeight() * scale);
        
        // Zentrieren
        float drawX = (windowW - imgW) / 2.0f;
        float drawY = (windowH - imgH) / 2.0f;
        
        System.out.println("Zeichne Bild bei: " + drawX + "," + drawY + " Größe: " + imgW + "x" + imgH);
        
        while (frame.isVisible()) {
            // Hintergrund
            g.clear(Color.DARK_GRAY);
            
            // Bild zeichnen - statisch, keine Animation
            g.drawImage(screenshot, drawX, drawY, imgW, imgH);
            
            // Rahmen um das Bild
            g.setColor(Color.RED);
            g.drawRect(drawX - 2, drawY - 2, imgW + 4, imgH + 4);
            
            g.present();
            
            Thread.sleep(16); // ~60 FPS
        }
    }
}
