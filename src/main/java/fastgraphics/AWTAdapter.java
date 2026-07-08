package fastgraphics;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

/**
 * AWTAdapter provides a FastRenderer implementation using standard Java2D.
 * Used for comparison and as a fallback backend.
 */
public class AWTAdapter implements FastRenderer {
    private Graphics2D g2d;
    private Color currentColor = Color.WHITE;
    private float currentStrokeWidth = 1.0f;
    private Font currentFont = new Font("Segoe UI", Font.PLAIN, 12);
    private final Map<Integer, BufferedImage> textureCache = new HashMap<>();
    private int nextTextureId = 1;

    public void setGraphics(Graphics2D g) {
        this.g2d = g;
        if (g2d != null) {
            g2d.setColor(currentColor);
            g2d.setStroke(new BasicStroke(currentStrokeWidth));
            g2d.setFont(currentFont);
        }
    }

    @Override
    public void setColor(Color c) {
        this.currentColor = c != null ? c : Color.WHITE;
        if (g2d != null) g2d.setColor(currentColor);
    }

    @Override
    public void setAntialiasing(boolean enabled) {
        if (g2d == null) return;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, 
            enabled ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            enabled ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
    }

    @Override
    public void setStroke(float width) {
        this.currentStrokeWidth = width;
        if (g2d != null) g2d.setStroke(new BasicStroke(width));
    }

    @Override
    public void setFont(Font font) {
        this.currentFont = font != null ? font : new Font("Segoe UI", Font.PLAIN, 12);
        if (g2d != null) g2d.setFont(currentFont);
    }

    // --- Drawing API ---

    @Override
    public void drawLine(float x1, float y1, float x2, float y2) {
        if (g2d != null) g2d.drawLine((int) x1, (int) y1, (int) x2, (int) y2);
    }

    @Override
    public void drawRect(float x, float y, float width, float height) {
        if (g2d != null) g2d.drawRect((int) x, (int) y, (int) width, (int) height);
    }

    @Override
    public void fillRect(float x, float y, float width, float height) {
        if (g2d != null) g2d.fillRect((int) x, (int) y, (int) width, (int) height);
    }

    @Override
    public void drawOval(float x, float y, float width, float height) {
        if (g2d != null) g2d.drawOval((int) x, (int) y, (int) width, (int) height);
    }

    @Override
    public void fillOval(float x, float y, float width, float height) {
        if (g2d != null) g2d.fillOval((int) x, (int) y, (int) width, (int) height);
    }

    @Override
    public void drawRoundRect(float x, float y, float width, float height, float arcWidth, float arcHeight) {
        if (g2d != null) g2d.drawRoundRect((int) x, (int) y, (int) width, (int) height, (int) arcWidth, (int) arcHeight);
    }

    @Override
    public void fillRoundRect(float x, float y, float width, float height, float arcWidth, float arcHeight) {
        if (g2d != null) g2d.fillRoundRect((int) x, (int) y, (int) width, (int) height, (int) arcWidth, (int) arcHeight);
    }

    @Override
    public void drawArc(float x, float y, float width, float height, float startAngle, float arcAngle) {
        if (g2d != null) g2d.drawArc((int) x, (int) y, (int) width, (int) height, (int) startAngle, (int) arcAngle);
    }

    @Override
    public void fillArc(float x, float y, float width, float height, float startAngle, float arcAngle) {
        if (g2d != null) g2d.fillArc((int) x, (int) y, (int) width, (int) height, (int) startAngle, (int) arcAngle);
    }

    @Override
    public void drawTriangle(float x1, float y1, float x2, float y2, float x3, float y3) {
        if (g2d != null) {
            g2d.drawPolygon(new int[]{(int)x1, (int)x2, (int)x3}, new int[]{(int)y1, (int)y2, (int)y3}, 3);
        }
    }

    @Override
    public void fillTriangle(float x1, float y1, float x2, float y2, float x3, float y3) {
        if (g2d != null) {
            g2d.fillPolygon(new int[]{(int)x1, (int)x2, (int)x3}, new int[]{(int)y1, (int)y2, (int)y3}, 3);
        }
    }

    @Override
    public void drawPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        if (g2d != null) g2d.drawPolygon(xPoints, yPoints, nPoints);
    }

    @Override
    public void fillPolygon(int[] xPoints, int[] yPoints, int nPoints) {
        if (g2d != null) g2d.fillPolygon(xPoints, yPoints, nPoints);
    }

    @Override
    public void drawPolyline(int[] xPoints, int[] yPoints, int nPoints) {
        if (g2d != null) g2d.drawPolyline(xPoints, yPoints, nPoints);
    }

    @Override
    public void drawQuadCurve(float x1, float y1, float ctrlx, float ctrly, float x2, float y2) {
        if (g2d != null) g2d.draw(new java.awt.geom.QuadCurve2D.Float(x1, y1, ctrlx, ctrly, x2, y2));
    }

    @Override
    public void drawCubicCurve(float x1, float y1, float ctrlx1, float ctrly1, float ctrlx2, float ctrly2, float x2, float y2) {
        if (g2d != null) g2d.draw(new java.awt.geom.CubicCurve2D.Float(x1, y1, ctrlx1, ctrly1, ctrlx2, ctrly2, x2, y2));
    }

    @Override
    public void draw(Shape s) {
        if (g2d != null) g2d.draw(s);
    }

    @Override
    public void fill(Shape s) {
        if (g2d != null) g2d.fill(s);
    }

    @Override
    public void drawString(String text, float x, float y, float size) {
        if (g2d != null) {
            g2d.setFont(new Font(currentFont.getName(), currentFont.getStyle(), (int)size));
            g2d.drawString(text, (int) x, (int) y);
        }
    }

    @Override
    public int loadTexture(int[] pixels, int width, int height) {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, width, height, pixels, 0, width);
        int id = nextTextureId++;
        textureCache.put(id, img);
        return id;
    }

    @Override
    public void destroyTexture(int textureId) {
        textureCache.remove(textureId);
    }

    @Override
    public void drawImage(int textureId, float x, float y, float w, float h, float alpha) {
        BufferedImage img = textureCache.get(textureId);
        if (img != null && g2d != null) {
            Composite oldComp = g2d.getComposite();
            if (alpha < 1.0f) {
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            }
            g2d.drawImage(img, (int) x, (int) y, (int) w, (int) h, null);
            g2d.setComposite(oldComp);
        }
    }

    @Override
    public void dispose() {
        if (g2d != null) g2d.dispose();
        textureCache.clear();
    }
}
