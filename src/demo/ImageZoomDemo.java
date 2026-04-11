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
 * Demo: Screenshot aufnehmen und zoomen
 * 
 * Zeigt drawImage() Funktionalität mit:
 * - Screenshot als BufferedImage
 * - Skalierung (Zoom)
 * - Interpolation Qualität
 * 
 * Steuerung:
 * - +/- : Zoomen
 * - 0   : Zoom zurücksetzen
 * - ESC : Beenden
 */
public class ImageZoomDemo {
    
    private static float zoom = 1.0f;
    private static float offsetX = 0;
    private static float offsetY = 0;
    
    public static void main(String[] args) throws Exception {
        // Fenster erstellen (normal, nicht fullscreen für Screenshot)
        JFrame frame = new JFrame("ImageZoomDemo - Screenshot + Zoom Test");
        frame.setSize(800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
        
        // Kurz warten damit Fenster sichtbar ist
        Thread.sleep(500);
        
        // Fenster in den Hintergrund bringen für Screenshot vom Desktop
        frame.toBack();
        Thread.sleep(200);
        
        // Screenshot machen (ganzer Bildschirm oder Bereich)
        System.out.println("Mache Screenshot...");
        Robot robot = new Robot();
        Rectangle screenRect = new Rectangle(0, 0, 1920, 1080); // Anpassen bei Bedarf
        BufferedImage screenshot = robot.createScreenCapture(screenRect);
        System.out.println("Screenshot: " + screenshot.getWidth() + "x" + screenshot.getHeight());
        
        // Fenster nach vorne holen
        frame.toFront();
        frame.requestFocus();
        Thread.sleep(500);
        
        // Native Handle holen - exakter Titel oder Teil davon
        String windowTitle = "ImageZoomDemo - Screenshot + Zoom Test";
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
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_PLUS:
                    case KeyEvent.VK_EQUALS:
                        zoom *= 1.2f;
                        System.out.println("Zoom: " + String.format("%.2f", zoom));
                        break;
                    case KeyEvent.VK_MINUS:
                        zoom /= 1.2f;
                        if (zoom < 0.1f) zoom = 0.1f;
                        System.out.println("Zoom: " + String.format("%.2f", zoom));
                        break;
                    case KeyEvent.VK_0:
                        zoom = 1.0f;
                        offsetX = 0;
                        offsetY = 0;
                        System.out.println("Zoom zurückgesetzt");
                        break;
                    case KeyEvent.VK_UP:
                        offsetY -= 20 / zoom;
                        break;
                    case KeyEvent.VK_DOWN:
                        offsetY += 20 / zoom;
                        break;
                    case KeyEvent.VK_LEFT:
                        offsetX -= 20 / zoom;
                        break;
                    case KeyEvent.VK_RIGHT:
                        offsetX += 20 / zoom;
                        break;
                    case KeyEvent.VK_ESCAPE:
                        System.exit(0);
                        break;
                }
            }
        });
        
        // Render Loop mit Sinus-Animation
        int windowW = 800;
        int windowH = 600;
        float time = 0;
        
        while (frame.isVisible()) {
            // Zeit für Animation
            time += 0.016f; // ~60 FPS
            
            // Sinus-Animation für Zoom: 0.5 -> 2.0 -> 0.5
            zoom = 1.0f + 0.8f * (float)Math.sin(time * 0.5);
            
            // Sinus-Animation für Pan (kreisförmig)
            offsetX = 100.0f * (float)Math.cos(time * 0.3);
            offsetY = 100.0f * (float)Math.sin(time * 0.3);
            
            // Hintergrund
            g.setColor(Color.DARK_GRAY);
            g.clear(Color.DARK_GRAY);
            
            // Bildgröße berechnen mit Zoom
            int imgW = (int)(screenshot.getWidth() * zoom);
            int imgH = (int)(screenshot.getHeight() * zoom);
            
            // Zentrieren + Offset
            float drawX = (windowW - imgW) / 2.0f + offsetX;
            float drawY = (windowH - imgH) / 2.0f + offsetY;
            
            // Bild zeichnen
            g.drawImage(screenshot, drawX, drawY, imgW, imgH);
            
            // Zoom-Anzeige (als Rechtecke statt Text, da drawString noch nicht geht)
            g.setColor(Color.BLACK);
            g.fillRect(10, 10, 180, 60);
            g.setColor(Color.GREEN);
            g.drawRect(10, 10, 180, 60);
            
            // Farbbalken für Zoom-Level visualisieren
            int barWidth = (int)(150 * Math.min(zoom, 2.0f) / 2.0f);
            g.setColor(Color.YELLOW);
            g.fillRect(25, 45, barWidth, 15);
            
            // Pan-Position als kleine Kreise anzeigen
            float panIndicatorX = 95 + offsetX * 0.3f;
            float panIndicatorY = 80 + offsetY * 0.3f;
            g.setColor(Color.RED);
            g.fillOval(panIndicatorX - 5, panIndicatorY - 5, 10, 10);
            
            g.present();
            
            Thread.sleep(16); // ~60 FPS
        }
    }
}
