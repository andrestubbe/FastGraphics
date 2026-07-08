package fastgraphics;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Paint;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.*;
import java.util.ArrayList;
import java.util.List;

/**
 * FastGraphics2D extends FastGraphics to provide advanced rendering features
 * similar to java.awt.Graphics2D, including support for AWT Shapes.
 */
public class FastGraphics2D extends FastGraphics {

    public FastGraphics2D(long hwnd, int maxInstances) {
        super(hwnd, maxInstances);
    }

    /**
     * Sets the paint for the graphics context.
     */
    public void setPaint(Paint p) {
        if (p instanceof Color) {
            setColor((Color) p);
        }
    }

    /**
     * Sets the stroke using an AWT Stroke object.
     */
    public void setStroke(Stroke s) {
        if (s instanceof BasicStroke) {
            setStroke(((BasicStroke) s).getLineWidth());
        }
    }

    /**
     * Sets the user-space transformation matrix using a standard AWT AffineTransform.
     */
    public void setTransform(AffineTransform t) {
        if (t == null) {
            resetTransform();
            return;
        }
        double[] f = new double[6];
        t.getMatrix(f);
        resetTransform();
        matrix[0] = (float) f[0];
        matrix[1] = (float) f[1];
        matrix[4] = (float) f[2];
        matrix[5] = (float) f[3];
        matrix[12] = (float) f[4];
        matrix[13] = (float) f[5];
    }

    public AffineTransform getTransform() {
        return new AffineTransform(matrix[0], matrix[1], matrix[4], matrix[5], matrix[12], matrix[13]);
    }

    public void translate(double tx, double ty) {
        AffineTransform at = getTransform();
        at.translate(tx, ty);
        setTransform(at);
    }

    public void rotate(double theta) {
        AffineTransform at = getTransform();
        at.rotate(theta);
        setTransform(at);
    }

    public void rotate(double theta, double x, double y) {
        AffineTransform at = getTransform();
        at.rotate(theta, x, y);
        setTransform(at);
    }

    public void scale(double sx, double sy) {
        AffineTransform at = getTransform();
        at.scale(sx, sy);
        setTransform(at);
    }

    public void shear(double shx, double shy) {
        AffineTransform at = getTransform();
        at.shear(shx, shy);
        setTransform(at);
    }
}
