package fastgraphics;

import java.awt.Color;
import java.awt.Font;

/**
 * FastRenderer defines the common interface for both native and AWT backends.
 * It follows the Graphics/Graphics2D state-based pattern.
 */
public interface FastRenderer {
    // --- State Management ---
    void setColor(Color c);
    void setAntialiasing(boolean enabled);
    void setStroke(float width);
    void setFont(Font font);

    // --- Drawing Operations ---
    void drawLine(float x1, float y1, float x2, float y2);
    
    void drawRect(float x, float y, float width, float height);
    void fillRect(float x, float y, float width, float height);
    
    void drawOval(float x, float y, float width, float height);
    void fillOval(float x, float y, float width, float height);
    
    void drawRoundRect(float x, float y, float width, float height, float arcWidth, float arcHeight);
    void fillRoundRect(float x, float y, float width, float height, float arcWidth, float arcHeight);
    
    void drawArc(float x, float y, float width, float height, float startAngle, float arcAngle);
    void fillArc(float x, float y, float width, float height, float startAngle, float arcAngle);
    
    void drawTriangle(float x1, float y1, float x2, float y2, float x3, float y3);
    void fillTriangle(float x1, float y1, float x2, float y2, float x3, float y3);
    
    void drawPolygon(int[] xPoints, int[] yPoints, int nPoints);
    void fillPolygon(int[] xPoints, int[] yPoints, int nPoints);
    void drawPolyline(int[] xPoints, int[] yPoints, int nPoints);

    // --- Complex Primitives ---
    void drawQuadCurve(float x1, float y1, float ctrlx, float ctrly, float x2, float y2);
    void drawCubicCurve(float x1, float y1, float ctrlx1, float ctrly1, float ctrlx2, float ctrly2, float x2, float y2);

    void draw(java.awt.Shape s);
    void fill(java.awt.Shape s);

    // --- Assets and Text ---
    void drawString(String text, float x, float y, float size);
    int loadTexture(int[] pixels, int width, int height);
    void destroyTexture(int textureId);
    void drawImage(int textureId, float x, float y, float w, float h, float alpha);

    // --- Lifecycle ---
    void dispose();
}
