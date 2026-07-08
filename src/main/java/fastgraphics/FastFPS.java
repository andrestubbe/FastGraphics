package fastgraphics;

import javax.swing.JFrame;

public class FastFPS {
    private long lastTime;
    private int frames;
    private int currentFPS;
    private final String baseTitle;
    private final JFrame frame;

    public FastFPS(JFrame frame) {
        this.frame = frame;
        this.baseTitle = frame.getTitle();
        this.lastTime = System.nanoTime();
    }

    private String prefix = "";

    public void tick() {
        tick(null);
    }

    public void tick(String prefix) {
        if (prefix != null) this.prefix = prefix;
        frames++;
        long now = System.nanoTime();
        if (now - lastTime >= 1_000_000_000L) {
            currentFPS = frames;
            frames = 0;
            lastTime = now;
            updateTitle();
        }
    }

    private void updateTitle() {
        if (frame != null) {
            String p = prefix.isEmpty() ? "" : "[" + prefix + "] ";
            frame.setTitle(p + baseTitle + " | FPS: " + currentFPS);
        }
    }

    public int getFPS() { return currentFPS; }
}
