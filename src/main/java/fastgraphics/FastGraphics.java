package fastgraphics;

import java.awt.Color;
import java.awt.Font;

/**
 * FastGraphics implements the core java.awt.Graphics API.
 * It uses a state-based model where color, stroke, and font persist across calls.
 */
public class FastGraphics implements FastRenderer {
    protected final FastGraphicsEngine engine;
    protected Color currentColor = Color.WHITE;
    protected float currentStrokeWidth = 1.0f;
    protected Font currentFont = new Font("Segoe UI", Font.PLAIN, 12);
    protected final float[] matrix = new float[16];

    public FastGraphics(long hwnd, int maxInstances) {
        this.engine = new FastGraphicsEngine(hwnd, maxInstances);
        resetTransform();
    }

    protected void resetTransform() {
        for (int i = 0; i < 16; i++)
            matrix[i] = (i % 5 == 0) ? 1.0f : 0.0f;
    }

    // --- State Management ---

    @Override
    public void setColor(Color c) {
        this.currentColor = c != null ? c : Color.WHITE;
    }

    @Override
    public void setAntialiasing(boolean enabled) {
        engine.setAntialiasing(enabled);
    }

    @Override
    public void setStroke(float width) {
        this.currentStrokeWidth = width;
    }

    @Override
    public void setFont(Font font) {
        this.currentFont = font != null ? font : new Font("Segoe UI", Font.PLAIN, 12);
    }

    public void clear(Color c) {
        engine.clear(c.getRed() / 255f, c.getGreen() / 255f, c.getBlue() / 255f, c.getAlpha() / 255f);
    }

    public void present() {
        engine.flush(matrix);
        engine.present();
    }

    @Override
    public void dispose() {
        engine.destroy();
    }

    // --- Drawing API (Float-based for precision) ---

    @Override
    public void drawLine(float x1, float y1, float x2, float y2) {
        add(x1 + 0.5f, y1 + 0.5f, x2 + 0.5f, y2 + 0.5f, currentStrokeWidth, 0, 8, 0);
    }

    @Override
    public void drawRect(float x, float y, float width, float height) {
        add(x, y, width, height, 0, currentStrokeWidth / Math.min(width, height), 2, 0);
    }

    @Override
    public void fillRect(float x, float y, float width, float height) {
        add(x, y, width, height, 0, 0, 0, 0);
    }

    @Override
    public void drawOval(float x, float y, float width, float height) {
        add(x, y, width, height, 0, currentStrokeWidth / Math.min(width, height), 3, 0);
    }

    @Override
    public void fillOval(float x, float y, float width, float height) {
        add(x, y, width, height, 1.0f, 0, 1, 0);
    }

    @Override
    public void drawRoundRect(float x, float y, float width, float height, float arcWidth, float arcHeight) {
        add(x, y, width, height, (arcWidth / width), currentStrokeWidth / Math.min(width, height), 2, 0);
    }

    @Override
    public void fillRoundRect(float x, float y, float width, float height, float arcWidth, float arcHeight) {
        add(x, y, width, height, (arcWidth / width), 0, 0, 0);
    }

    @Override
    public void drawArc(float x, float y, float width, float height, float startAngle, float arcAngle) {
        add(x, y, width, height, startAngle, arcAngle, 5, currentStrokeWidth / Math.min(width, height));
    }

    @Override
    public void fillArc(float x, float y, float width, float height, float startAngle, float arcAngle) {
        add(x, y, width, height, startAngle, arcAngle, 4, 0);
    }

    @Override
    public void drawTriangle(float x1, float y1, float x2, float y2, float x3, float y3) {
        add(x1, y1, x2, y2, x3, y3, 7, currentStrokeWidth);
    }

    @Override
    public void fillTriangle(float x1, float y1, float x2, float y2, float x3, float y3) {
        add(x1, y1, x2, y2, x3, y3, 6, 0);
    }

    // --- Integer Overloads for AWT Compatibility ---

    public void drawLine(int x1, int y1, int x2, int y2) { drawLine((float)x1, (float)y1, (float)x2, (float)y2); }
    public void drawRect(int x, int y, int w, int h) { drawRect((float)x, (float)y, (float)w, (float)h); }
    public void fillRect(int x, int y, int w, int h) { fillRect((float)x, (float)y, (float)w, (float)h); }
    public void drawOval(int x, int y, int w, int h) { drawOval((float)x, (float)y, (float)w, (float)h); }
    public void fillOval(int x, int y, int w, int h) { fillOval((float)x, (float)y, (float)w, (float)h); }
    public void drawString(String str, int x, int y) { drawString(str, (float)x, (float)y, (float)currentFont.getSize()); }

    // --- Utility Methods ---

    public void draw3DRect(float x, float y, float width, float height, boolean raised) {
        Color c = currentColor;
        Color brighter = c.brighter();
        Color darker = c.darker();
        
        setColor(raised ? brighter : darker);
        drawLine(x, y, x, y + height);
        drawLine(x + 1, y, x + width - 1, y);
        
        setColor(raised ? darker : brighter);
        drawLine(x + 1, y + height, x + width, y + height);
        drawLine(x + width, y, x + width, y + height - 1);
        
        setColor(c);
    }

    @Override
    public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        if (nPoints < 2) return;
        for (int i = 0; i < nPoints; i++) {
            int next = (i + 1) % nPoints;
            drawLine(xPoints[i], yPoints[i], xPoints[next], yPoints[next]);
        }
    }

    @Override
    public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        if (nPoints < 3) return;
        for (int i = 1; i < nPoints - 1; i++) {
            fillTriangle(xPoints[0], yPoints[0], xPoints[i], yPoints[i], xPoints[i + 1], yPoints[i + 1]);
        }
    }

    @Override
    public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints) {
        if (nPoints < 2) return;
        for (int i = 0; i < nPoints - 1; i++) {
            drawLine(xPoints[i], yPoints[i], xPoints[i + 1], yPoints[i + 1]);
        }
    }

    @Override
    public void drawQuadCurve(float x1, float y1, float ctrlx, float ctrly, float x2, float y2) {
        int segments = 24;
        float lastX = x1, lastY = y1;
        for (int i = 1; i <= segments; i++) {
            float t = i / (float) segments;
            float invT = 1.0f - t;
            float x = invT * invT * x1 + 2 * invT * t * ctrlx + t * t * x2;
            float y = invT * invT * y1 + 2 * invT * t * ctrly + t * t * y2;
            drawLine(lastX, lastY, x, y);
            lastX = x; lastY = y;
        }
    }

    @Override
    public void drawCubicCurve(float x1, float y1, float ctrlx1, float ctrly1, float ctrlx2, float ctrly2, float x2, float y2) {
        int segments = 24;
        float lastX = x1, lastY = y1;
        for (int i = 1; i <= segments; i++) {
            float t = i / (float) segments;
            float invT = 1.0f - t;
            float x = invT * invT * invT * x1 + 3 * invT * invT * t * ctrlx1 + 3 * invT * t * t * ctrlx2 + t * t * t * x2;
            float y = invT * invT * invT * y1 + 3 * invT * invT * t * ctrly1 + 3 * invT * t * t * ctrly2 + t * t * t * y2;
            drawLine(lastX, lastY, x, y);
            lastX = x; lastY = y;
        }
    }

    @Override
    public void fill(java.awt.Shape s) {
        if (s instanceof java.awt.geom.Rectangle2D) {
            java.awt.geom.Rectangle2D r = (java.awt.geom.Rectangle2D) s;
            fillRect((float)r.getX(), (float)r.getY(), (float)r.getWidth(), (float)r.getHeight());
            return;
        }
        if (s instanceof java.awt.geom.Ellipse2D) {
            java.awt.geom.Ellipse2D e = (java.awt.geom.Ellipse2D) s;
            fillOval((float)e.getX(), (float)e.getY(), (float)e.getWidth(), (float)e.getHeight());
            return;
        }
        
        java.awt.geom.PathIterator pi = s.getPathIterator(null, 0.5);
        float[] coords = new float[6];
        java.util.List<Float> px = new java.util.ArrayList<>();
        java.util.List<Float> py = new java.util.ArrayList<>();
        
        while (!pi.isDone()) {
            int type = pi.currentSegment(coords);
            if (type == java.awt.geom.PathIterator.SEG_MOVETO || type == java.awt.geom.PathIterator.SEG_LINETO) {
                px.add(coords[0]); py.add(coords[1]);
            } else if (type == java.awt.geom.PathIterator.SEG_CLOSE) {
                flushPolygon(px, py);
            }
            pi.next();
        }
        flushPolygon(px, py); // Auto-close and fill
    }

    private void flushPolygon(java.util.List<Float> px, java.util.List<Float> py) {
        if (px.size() >= 3) {
            int[] x = new int[px.size()]; int[] y = new int[py.size()];
            for(int i=0; i<x.length; i++) { x[i] = px.get(i).intValue(); y[i] = py.get(i).intValue(); }
            fillPolygon(x, y, x.length);
        }
        px.clear(); py.clear();
    }

    @Override
    public void draw(java.awt.Shape s) {
        if (s instanceof java.awt.geom.Rectangle2D) {
            java.awt.geom.Rectangle2D r = (java.awt.geom.Rectangle2D) s;
            drawRect((float)r.getX(), (float)r.getY(), (float)r.getWidth(), (float)r.getHeight());
            return;
        }
        if (s instanceof java.awt.geom.Ellipse2D) {
            java.awt.geom.Ellipse2D e = (java.awt.geom.Ellipse2D) s;
            drawOval((float)e.getX(), (float)e.getY(), (float)e.getWidth(), (float)e.getHeight());
            return;
        }

        java.awt.geom.PathIterator pi = s.getPathIterator(null, 1.0);
        float[] coords = new float[6];
        float moveX = 0, moveY = 0, lastX = 0, lastY = 0;
        while (!pi.isDone()) {
            int type = pi.currentSegment(coords);
            switch (type) {
                case java.awt.geom.PathIterator.SEG_MOVETO -> { moveX = lastX = coords[0]; moveY = lastY = coords[1]; }
                case java.awt.geom.PathIterator.SEG_LINETO -> { drawLine(lastX, lastY, coords[0], coords[1]); lastX = coords[0]; lastY = coords[1]; }
                case java.awt.geom.PathIterator.SEG_QUADTO -> { drawQuadCurve(lastX, lastY, coords[0], coords[1], coords[2], coords[3]); lastX = coords[2]; lastY = coords[3]; }
                case java.awt.geom.PathIterator.SEG_CUBICTO -> { drawCubicCurve(lastX, lastY, coords[0], coords[1], coords[2], coords[3], coords[4], coords[5]); lastX = coords[4]; lastY = coords[5]; }
                case java.awt.geom.PathIterator.SEG_CLOSE -> drawLine(lastX, lastY, moveX, moveY);
            }
            pi.next();
        }
    }

    @Override
    public void drawString(String text, float x, float y, float size) {
        engine.flush(matrix);
        engine.drawString(text, x, y, size, currentColor.getRed()/255f, currentColor.getGreen()/255f, currentColor.getBlue()/255f, currentColor.getAlpha()/255f);
    }

    @Override
    public int loadTexture(int[] pixels, int width, int height) {
        return engine.loadTexture(pixels, width, height);
    }

    @Override
    public void destroyTexture(int textureId) {
        engine.destroyTexture(textureId);
    }

    @Override
    public void drawImage(int textureId, float x, float y, float w, float h, float alpha) {
        engine.flush(matrix);
        engine.drawImage(textureId, x, y, w, h, alpha);
    }

    protected void add(float x, float y, float w, float h, float p1, float p2, float type, float p3) {
        engine.addInstance(x, y, w, h, 
            currentColor.getRed()/255f, currentColor.getGreen()/255f, currentColor.getBlue()/255f, currentColor.getAlpha()/255f,
            p1, p2, type, p3);
    }
}
