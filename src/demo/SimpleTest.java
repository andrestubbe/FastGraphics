package demo;

import javax.swing.*;

/**
 * Einfacher Test: Weißer Hintergrund + rotes Rechteck
 */
public class SimpleTest {
    
    static { System.loadLibrary("FastGraphics"); }
    
    private static native void init(long hwnd);
    private static native void fillRect(float x, float y, float w, float h, float r, float g, float b);
    private static native void clear(float r, float g, float b);
    private static native void present();
    private static native long findWindow(String title);
    
    public static void main(String[] args) {
        JFrame frame = new JFrame("SimpleTest - Rotes Rechteck");
        frame.setSize(400, 300);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        
        long hwnd = 0;
        for (int i = 0; i < 20 && hwnd == 0; i++) {
            try { Thread.sleep(50); } catch (InterruptedException e) {}
            hwnd = findWindow("SimpleTest - Rotes Rechteck");
        }
        
        if (hwnd == 0) {
            System.out.println("FEHLER: HWND nicht gefunden");
            return;
        }
        
        System.out.println("HWND: 0x" + Long.toHexString(hwnd));
        init(hwnd);
        
        // Weißer Hintergrund
        clear(1.0f, 1.0f, 1.0f);
        
        // Rotes Rechteck in der Mitte
        System.out.println("Zeichne rotes Rechteck...");
        fillRect(150, 100, 100, 100, 1.0f, 0.0f, 0.0f);
        
        present();
        
        System.out.println("Fertig! Du solltest ein WEISSES Fenster mit ROTEM Rechteck sehen.");
        System.out.println("Fenster schließt sich nach 5 Sekunden...");
        
        try { Thread.sleep(5000); } catch (InterruptedException e) {}
    }
}
