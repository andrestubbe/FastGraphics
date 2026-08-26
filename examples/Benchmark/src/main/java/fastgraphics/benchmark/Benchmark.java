package fastgraphics.benchmark;

import fastgraphics.FastGraphics2D;
import org.openjdk.jmh.annotations.*;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 2, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
public class Benchmark {

    private BufferedImage bufferedImage;
    private Graphics2D java2D;
    private FastGraphics2D fastGraphics2D;

    @Setup
    public void setup() {
        bufferedImage = new BufferedImage(800, 600, BufferedImage.TYPE_INT_ARGB);
        java2D = bufferedImage.createGraphics();
        fastGraphics2D = new FastGraphics2D(0L);
    }

    @TearDown
    public void tearDown() {
        if (java2D != null) {
            java2D.dispose();
        }
    }

    @org.openjdk.jmh.annotations.Benchmark
    public void benchmarkJava2DFillRect() {
        java2D.setColor(Color.RED);
        java2D.fillRect(50, 50, 200, 150);
    }

    @org.openjdk.jmh.annotations.Benchmark
    public void benchmarkFastGraphicsBatchFillRect() {
        fastGraphics2D.setColor(Color.RED);
        fastGraphics2D.fillRect(50.0f, 50.0f, 200.0f, 150.0f);
    }
}
