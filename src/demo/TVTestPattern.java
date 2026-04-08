package demo;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * TV Test Pattern - Classic 80s Broadcast Test Pattern
 * For visual validation of FastGraphics vs Java2D rendering
 * 
 * Features:
 * - Color Bars (NTSC/PAL standard)
 * - Grayscale Gradient
 * - Convergence Circles
 * - Resolution wedges
 * - Station ID text
 */
public class TVTestPattern {
    
    private static final int WIDTH = 800;
    private static final int HEIGHT = 600;
    
    static { System.loadLibrary("FastGraphics"); }
    
    private static native void init(long hwnd);
    private static native void fillRects(ByteBuffer rectData, int count);
    private static native void drawCircles(ByteBuffer circleData, int count);  // NEU!
    private static native void clear(float r, float g, float b);
    private static native void present();
    private static native long findWindow(String title);
    
    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale", "1.0");
        
        // FastGraphics Window
        JFrame fgFrame = new JFrame("FastGraphics - TV Test Pattern");
        fgFrame.setSize(WIDTH, HEIGHT);
        fgFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        fgFrame.setLocation(50, 50);
        fgFrame.setVisible(true);
        
        // AWT Window
        JFrame awtFrame = new JFrame("AWT Java2D - TV Test Pattern");
        awtFrame.setSize(WIDTH, HEIGHT);
        awtFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        awtFrame.setLocation(50 + WIDTH + 20, 50);
        awtFrame.setVisible(true);
        
        Canvas awtCanvas = new Canvas();
        awtCanvas.setSize(WIDTH, HEIGHT);
        awtFrame.add(awtCanvas);
        awtFrame.setIgnoreRepaint(true);
        awtCanvas.setIgnoreRepaint(true);
        
        // Wait for window
        long hwnd = 0;
        for (int i = 0; i < 20 && hwnd == 0; i++) {
            try { Thread.sleep(50); } catch (InterruptedException e) {}
            hwnd = findWindow("FastGraphics - TV Test Pattern");
        }
        
        if (hwnd == 0) {
            System.err.println("Could not find FastGraphics window!");
            return;
        }
        
        init(hwnd);
        
        // Test Pattern Data
        TestPatternData pattern = generateTestPattern();
        
        // ByteBuffer for FastGraphics
        ByteBuffer fgBuffer = ByteBuffer.allocateDirect(pattern.rectCount * 7 * 4)
            .order(ByteOrder.nativeOrder());
        
        System.out.println("TV Test Pattern Running...");
        System.out.println("Rectangles: " + pattern.rectCount);
        
        while (fgFrame.isVisible()) {
            long startTime = System.currentTimeMillis();
            
            // === FASTGRAPHICS ===
            clear(0.1f, 0.1f, 0.1f); // Dark gray background
            
            fgBuffer.clear();
            for (int i = 0; i < pattern.rectCount; i++) {
                int idx = i * 7;
                fgBuffer.putFloat(pattern.rectData[idx + 0]); // x
                fgBuffer.putFloat(pattern.rectData[idx + 1]); // y
                fgBuffer.putFloat(pattern.rectData[idx + 2]); // w
                fgBuffer.putFloat(pattern.rectData[idx + 3]); // h
                fgBuffer.putFloat(pattern.rectData[idx + 4]); // r
                fgBuffer.putFloat(pattern.rectData[idx + 5]); // g
                fgBuffer.putFloat(pattern.rectData[idx + 6]); // b
            }
            fgBuffer.flip();
            
            fillRects(fgBuffer, pattern.rectCount);
            
            // Kreise für FastGraphics (weiß, nur Outlines sichtbar)
            ByteBuffer circleBuffer = ByteBuffer.allocateDirect(pattern.circles.length * 6 * 4)
                .order(ByteOrder.nativeOrder());
            circleBuffer.clear();
            for (Circle c : pattern.circles) {
                circleBuffer.putFloat(c.x);
                circleBuffer.putFloat(c.y);
                circleBuffer.putFloat(c.r);
                circleBuffer.putFloat(1.0f); // Weiß
                circleBuffer.putFloat(1.0f);
                circleBuffer.putFloat(1.0f);
            }
            circleBuffer.flip();
            drawCircles(circleBuffer, pattern.circles.length);
            
            present();
            
            long fgTime = System.currentTimeMillis() - startTime;
            
            // === AWT ===
            long awtStart = System.currentTimeMillis();
            
            Graphics2D g2d = (Graphics2D) awtCanvas.getGraphics();
            if (g2d != null) {
                BufferedImage buffer = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
                Graphics2D bg = buffer.createGraphics();
                
                // Background
                bg.setColor(new Color(25, 25, 25));
                bg.fillRect(0, 0, WIDTH, HEIGHT);
                bg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
                
                // Draw all pattern elements
                for (int i = 0; i < pattern.rectCount; i++) {
                    int idx = i * 7;
                    float x = pattern.rectData[idx + 0];
                    float y = pattern.rectData[idx + 1];
                    float w = pattern.rectData[idx + 2];
                    float h = pattern.rectData[idx + 3];
                    float r = pattern.rectData[idx + 4];
                    float g = pattern.rectData[idx + 5];
                    float b = pattern.rectData[idx + 6];
                    
                    bg.setColor(new Color(r, g, b));
                    bg.fillRect((int)x, (int)y, (int)w, (int)h);
                }
                
                // Draw circles (as ovals since we only have rect batch)
                bg.setColor(Color.WHITE);
                for (Circle c : pattern.circles) {
                    bg.drawOval((int)(c.x - c.r), (int)(c.y - c.r), (int)(c.r * 2), (int)(c.r * 2));
                }
                
                // Station ID
                bg.setColor(Color.WHITE);
                bg.setFont(new Font("Monospaced", Font.BOLD, 24));
                bg.drawString("FASTGRAPHICS", 50, HEIGHT - 50);
                bg.drawString("TV TEST PATTERN", 50, HEIGHT - 25);
                
                bg.setFont(new Font("Monospaced", Font.PLAIN, 14));
                bg.drawString("1000x600 | 60Hz | DX11 vs Java2D", WIDTH - 250, HEIGHT - 30);
                
                g2d.drawImage(buffer, 0, 0, null);
                bg.dispose();
                g2d.dispose();
            }
            
            long awtTime = System.currentTimeMillis() - awtStart;
            
            // Print timing every 60 frames
            if ((System.currentTimeMillis() / 1000) % 5 == 0) {
                System.out.printf("[TV Test] FastGraphics: %dms | AWT: %dms | Ratio: %.1fx%n",
                    fgTime, awtTime, (double)awtTime / fgTime);
            }
            
            // 60 FPS cap
            try { Thread.sleep(16); } catch (InterruptedException e) { break; }
        }
    }
    
    static class Circle {
        float x, y, r;
        Circle(float x, float y, float r) { this.x = x; this.y = y; this.r = r; }
    }
    
    static class TestPatternData {
        float[] rectData;
        int rectCount;
        Circle[] circles;
    }
    
    private static TestPatternData generateTestPattern() {
        java.util.List<Float> rects = new java.util.ArrayList<>();
        java.util.List<Circle> circles = new java.util.ArrayList<>();
        
        // === TOP: COLOR BARS (75% saturation) ===
        int barWidth = WIDTH / 7;
        int barHeight = HEIGHT / 3;
        Color[] barColors = {
            new Color(191, 191, 191), // White (75%)
            new Color(191, 191, 0),   // Yellow
            new Color(0, 191, 191),   // Cyan
            new Color(0, 191, 0),     // Green
            new Color(191, 0, 191),   // Magenta
            new Color(191, 0, 0),     // Red
            new Color(0, 0, 191)      // Blue
        };
        
        for (int i = 0; i < 7; i++) {
            addRect(rects, i * barWidth, 0, barWidth, barHeight, barColors[i]);
        }
        
        // === MIDDLE: GRAYSCALE GRADIENT ===
        int grayHeight = HEIGHT / 6;
        int grayY = barHeight;
        int graySteps = 16;
        int grayWidth = WIDTH / graySteps;
        
        for (int i = 0; i < graySteps; i++) {
            int gray = (int)(255 * i / (graySteps - 1));
            addRect(rects, i * grayWidth, grayY, grayWidth + 1, grayHeight, 
                new Color(gray, gray, gray));
        }
        
        // === CENTER: CONVERGENCE CIRCLES ===
        int centerY = HEIGHT / 2;
        int centerX = WIDTH / 2;
        
        // Add circles for convergence test
        for (int r = 20; r < 200; r += 20) {
            circles.add(new Circle(centerX, centerY, r));
        }
        
        // === BOTTOM: RESOLUTION WEDGES ===
        int wedgeY = grayY + grayHeight + 50;
        int wedgeHeight = 100;
        int wedgeWidth = WIDTH / 3;
        
        // Vertical resolution wedges
        for (int i = 0; i < 3; i++) {
            int x = i * wedgeWidth;
            // Alternating black/white stripes
            int stripeWidth = Math.max(1, wedgeWidth / (16 * (i + 1)));
            for (int s = 0; s < wedgeWidth; s += stripeWidth * 2) {
                addRect(rects, x + s, wedgeY, stripeWidth, wedgeHeight, Color.WHITE);
                if (s + stripeWidth < wedgeWidth) {
                    addRect(rects, x + s + stripeWidth, wedgeY, stripeWidth, wedgeHeight, Color.BLACK);
                }
            }
        }
        
        // === BOTTOM ROW: COLOR CHECKERBOARD ===
        int checkY = wedgeY + wedgeHeight + 20;
        int checkSize = 40;
        int checkCols = WIDTH / checkSize;
        int checkRows = 2;
        
        for (int row = 0; row < checkRows; row++) {
            for (int col = 0; col < checkCols; col++) {
                Color c = ((row + col) % 2 == 0) ? Color.RED : Color.GREEN;
                if (col % 4 == 2) c = Color.BLUE;
                addRect(rects, col * checkSize, checkY + row * checkSize, checkSize, checkSize, c);
            }
        }
        
        // Convert to arrays
        TestPatternData data = new TestPatternData();
        data.rectCount = rects.size() / 7;
        data.rectData = new float[rects.size()];
        for (int i = 0; i < rects.size(); i++) {
            data.rectData[i] = rects.get(i);
        }
        data.circles = circles.toArray(new Circle[0]);
        
        return data;
    }
    
    private static void addRect(java.util.List<Float> list, float x, float y, float w, float h, Color c) {
        list.add(x);
        list.add(y);
        list.add(w);
        list.add(h);
        list.add(c.getRed() / 255.0f);
        list.add(c.getGreen() / 255.0f);
        list.add(c.getBlue() / 255.0f);
    }
}
