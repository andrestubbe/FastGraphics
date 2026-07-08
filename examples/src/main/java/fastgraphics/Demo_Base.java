package fastgraphics;

import fastgraphics.FastGraphics;
import fasttheme.FastTheme;
import javax.swing.JFrame;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferStrategy;

public class Demo_Base {
    private static int renderMode = 0; // 0: Fast (No AA), 1: AWT (No AA), 2: Fast (AA), 3: AWT (AA)
    
    private static String title = "FastGraphics Parity Demo";
    private static JFrame frame;
    private static int[] randomPixels = new int[64 * 64];
    private static int fastTextureId = -1;
    private static int awtTextureId = -1;
    private static FastGraphics2D fastG;
    private static AWTAdapter awtG;
    private static BufferStrategy strategy;

    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale", "1.0");

        // Initialize random pixels
        java.util.Random rnd = new java.util.Random();
        for (int i = 0; i < randomPixels.length; i++) {
            randomPixels[i] = 0xFF000000 | rnd.nextInt(0xFFFFFF);
        }

        String title = "FastGraphics (SPACE to Toggle Mode)";
        frame = new JFrame(title);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(2368, 1242);
        frame.setLocationRelativeTo(null);
        
        Canvas canvas = new Canvas();
        canvas.setBackground(Color.BLACK);
        frame.add(canvas);
        
        frame.setVisible(true);
        canvas.createBufferStrategy(2);
        strategy = canvas.getBufferStrategy();
        
        long frameHwnd = 0;
        for (int i = 0; i < 100 && frameHwnd == 0; i++) {
            frameHwnd = FastGraphicsEngine.findWindow(title);
            if (frameHwnd == 0) try { Thread.sleep(50); } catch (Exception e) {}
        }
        if (frameHwnd == 0) {
            System.err.println("CRITICAL: Could not find window with title: " + title);
            return;
        }
        System.out.println("Window found: " + frameHwnd);

        FastTheme.setTitleBarDarkMode(frameHwnd, true);
        FastTheme.setTitleBarColor(frameHwnd, 0, 0, 0);

        long canvasHwnd = FastGraphicsEngine.findCanvas(frameHwnd);
        fastG = new FastGraphics2D(canvasHwnd != 0 ? canvasHwnd : frameHwnd, 1000);
        awtG = new AWTAdapter();

        fastTextureId = fastG.loadTexture(randomPixels, 64, 64);
        awtTextureId = awtG.loadTexture(randomPixels, 64, 64);

        KeyAdapter toggleListener = new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                    renderMode = (renderMode + 1) % 4;
                    render();
                }
            }
        };
        frame.addKeyListener(toggleListener);
        canvas.addKeyListener(toggleListener);

        ComponentAdapter resizeListener = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                render();
            }
        };
        frame.addComponentListener(resizeListener);
        canvas.addComponentListener(resizeListener);

        // Initial render
        render();
    }

    private static void render() {
        long start = System.nanoTime();
        boolean useFast = (renderMode == 0 || renderMode == 2);
        boolean useAA = (renderMode >= 2);
        
        String backend = useFast ? "FastGraphics" : "AWT/Swing";
        String aaStr = useAA ? "AA" : "No-AA";
        
        if (useFast) {
            fastG.setAntialiasing(useAA);
            fastG.clear(Color.BLACK);
            draw(fastG);
            fastG.present();
        } else {
            fastG.clear(new Color(0, 0, 0, 0));
            fastG.present();

            Graphics2D g2d = (Graphics2D) strategy.getDrawGraphics();
            if (g2d != null) {
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, 
                    useAA ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    useAA ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
                
                g2d.setColor(Color.BLACK); 
                g2d.fillRect(0, 0, frame.getWidth(), frame.getHeight());
                
                awtG.setGraphics(g2d);
                draw(awtG);
                g2d.dispose();
                strategy.show();
            }
        }
        
        long end = System.nanoTime();
        long micros = (end - start) / 1000;
        frame.setTitle(title + " | " + backend + " (" + aaStr + ") | " + micros + " us");
        System.out.println(backend + " " + aaStr + ": " + micros + " us");
    }

    private static void draw(FastRenderer r) {
        int rows = 4;
        int cols = 10;
        float cellSize = 220;
        float startX = 50;
        float startY = 50;
        float gridWidth = cols * cellSize;
        float gridHeight = rows * cellSize;

        r.setStroke(1.0f);
        r.setColor(new Color(64, 64, 64));
        for (int i = 0; i <= rows; i++) {
            float y = startY + i * cellSize;
            r.drawLine(startX, y, startX + gridWidth, y);
        }
        for (int i = 0; i <= cols; i++) {
            float x = startX + i * cellSize;
            r.drawLine(x, startY, x, startY + gridHeight);
        }

        float padding = 40;
        float s = cellSize - padding * 2;
        r.setColor(Color.WHITE);

        for (int i = 0; i < 16; i++) {
            int col = i % cols;
            int row = i / cols;
            float sx = startX + col * cellSize + padding;
            float sy = startY + row * cellSize + padding;
            
            String label = "";
            switch (i) {
                case 0 -> { r.fillRect(sx, sy, s, s); label = "fillRect"; }
                case 1 -> { r.setStroke(2.0f); r.drawRect(sx, sy, s, s); label = "drawRect"; }
                case 2 -> { r.fillOval(sx, sy, s, s); label = "fillOval"; }
                case 3 -> { r.setStroke(2.0f); r.drawOval(sx, sy, s, s); label = "drawOval"; }
                case 4 -> { r.setStroke(2.0f); r.drawLine(sx, sy, sx + s, sy + s); label = "drawLine"; }
                case 5 -> { r.fillRoundRect(sx, sy, s, s, 20, 20); label = "fillRoundRect"; }
                case 6 -> { r.setStroke(2.0f); r.drawRoundRect(sx, sy, s, s, 20, 20); label = "drawRoundRect"; }
                case 7 -> { r.fillArc(sx, sy, s, s, 45, 270); label = "fillArc"; }
                case 8 -> { r.setStroke(2.0f); r.drawArc(sx, sy, s, s, 45, 270); label = "drawArc"; }
                case 9 -> { r.fillTriangle(sx, sy + s, sx + s/2, sy, sx + s, sy + s); label = "fillTriangle"; }
                case 10 -> { r.setStroke(2.0f); r.drawTriangle(sx, sy + s, sx + s/2, sy, sx + s, sy + s); label = "drawTriangle"; }
                case 11 -> {
                    int[] px = new int[6]; int[] py = new int[6];
                    for (int j = 0; j < 6; j++) {
                        double angle = Math.toRadians(j * 60);
                        px[j] = (int)(sx + s/2 + Math.cos(angle) * s/2);
                        py[j] = (int)(sy + s/2 + Math.sin(angle) * s/2);
                    }
                    r.fillPolygon(px, py, 6); label = "fillPolygon";
                }
                case 12 -> {
                    int[] px = new int[6]; int[] py = new int[6];
                    for (int j = 0; j < 6; j++) {
                        double angle = Math.toRadians(j * 60);
                        px[j] = (int)(sx + s/2 + Math.cos(angle) * s/2);
                        py[j] = (int)(sy + s/2 + Math.sin(angle) * s/2);
                    }
                    r.setStroke(2.0f); r.drawPolygon(px, py, 6); label = "drawPolygon";
                }
                case 13 -> {
                    int[] px = {(int)sx, (int)(sx+s/4), (int)(sx+s/2), (int)(sx+3*s/4), (int)(sx+s)};
                    int[] py = {(int)sy, (int)(sy+s), (int)sy, (int)(sy+s), (int)sy};
                    r.setStroke(2.0f); r.drawPolyline(px, py, 5); label = "drawPolyline";
                }
                case 14 -> { r.setStroke(2.0f); r.drawQuadCurve(sx, sy + s, sx + s/2, sy - s, sx + s, sy + s); label = "drawQuadCurve"; }
                case 15 -> { r.setStroke(2.0f); r.drawCubicCurve(sx, sy + s/2, sx + s/3, sy - s/4, sx + 2*s/3, sy + 5*s/4, sx + s, sy + s/2); label = "drawCubicCurve"; }
            }
            r.drawString(label, sx + 5, sy + s + 20, 14);
        }

        // Shape API Gallery Row
        float shapeY = startY + 2 * cellSize + padding;
        r.setStroke(2.0f);
        r.setColor(Color.ORANGE);

        for (int i = 0; i < 10; i++) {
            float sx = startX + i * cellSize + padding;
            String label = "";
            switch (i) {
                case 0 -> { r.draw(new java.awt.geom.Rectangle2D.Float(sx, shapeY, s, s)); label = "draw(Rect)"; }
                case 1 -> { r.fill(new java.awt.geom.Rectangle2D.Float(sx, shapeY, s, s)); label = "fill(Rect)"; }
                case 2 -> { r.draw(new java.awt.geom.Ellipse2D.Float(sx, shapeY, s, s)); label = "draw(Oval)"; }
                case 3 -> { r.fill(new java.awt.geom.Ellipse2D.Float(sx, shapeY, s, s)); label = "fill(Oval)"; }
                case 4 -> { r.draw(new java.awt.geom.RoundRectangle2D.Float(sx, shapeY, s, s, 30, 30)); label = "draw(RRect)"; }
                case 5 -> { r.fill(new java.awt.geom.RoundRectangle2D.Float(sx, shapeY, s, s, 30, 30)); label = "fill(RRect)"; }
                case 6 -> { r.draw(new java.awt.geom.Arc2D.Float(sx, shapeY, s, s, 45, 270, java.awt.geom.Arc2D.PIE)); label = "draw(Arc)"; }
                case 7 -> { r.fill(new java.awt.geom.Arc2D.Float(sx, shapeY, s, s, 45, 270, java.awt.geom.Arc2D.PIE)); label = "fill(Arc)"; }
                case 8 -> {
                    java.awt.geom.Path2D star = new java.awt.geom.Path2D.Float();
                    float cx = sx + s/2, cy = shapeY + s/2;
                    for (int j = 0; j < 5; j++) {
                        double a = Math.toRadians(j * 144 - 90);
                        if (j == 0) star.moveTo(cx + Math.cos(a)*s/2, cy + Math.sin(a)*s/2);
                        else star.lineTo(cx + Math.cos(a)*s/2, cy + Math.sin(a)*s/2);
                    }
                    star.closePath();
                    r.draw(star); label = "draw(Star)";
                }
                case 9 -> {
                    java.awt.geom.Path2D heart = new java.awt.geom.Path2D.Float();
                    heart.moveTo(sx + s/2, shapeY + s/4);
                    heart.curveTo(sx + s/4, shapeY, sx, shapeY + s/2, sx + s/2, shapeY + s);
                    heart.curveTo(sx + s, shapeY + s/2, sx + 3*s/4, shapeY, sx + s/2, shapeY + s/4);
                    r.fill(heart); label = "fill(Heart)";
                }
            }
            r.drawString(label, sx + 5, shapeY + s + 20, 14);
        }

        // Color & Asset Test Row
        float testY = startY + 3 * cellSize + padding;
        r.setStroke(1.0f);
        r.setColor(Color.RED);
        r.fillRect(startX + 0 * cellSize + padding, testY, s, s);
        r.drawString("RED", startX + 0 * cellSize + padding + 5, testY + s + 20, 14);

        r.setColor(Color.GREEN);
        r.fillRect(startX + 1 * cellSize + padding, testY, s, s);
        r.drawString("GREEN", startX + 1 * cellSize + padding + 5, testY + s + 20, 14);

        r.setColor(Color.BLUE);
        r.fillRect(startX + 2 * cellSize + padding, testY, s, s);
        r.drawString("BLUE", startX + 2 * cellSize + padding + 5, testY + s + 20, 14);

        r.setColor(Color.WHITE);
        if (fastTextureId != -1) {
            r.drawImage(fastTextureId, startX + 3 * cellSize + padding, testY, s, s, 1.0f);
        }
        r.drawString("IMAGE", startX + 3 * cellSize + padding + 5, testY + s + 20, 14);
    }
}
