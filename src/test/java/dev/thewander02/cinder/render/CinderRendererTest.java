package dev.thewander02.cinder.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.thewander02.cinder.pack.PreparedPack;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CinderRendererTest {
    @Test
    void initialSuccessAllocatesOnceAndResizesInPlace() {
        FakeGpu gpu = new FakeGpu();
        CinderRenderer renderer = new CinderRenderer(gpu);

        assertTrue(renderer.install(pack("first")).success());
        renderer.renderFrame(new FakeFrame(320, 180));
        renderer.renderFrame(new FakeFrame(320, 180));
        renderer.renderFrame(new FakeFrame(640, 360));

        assertEquals(1, gpu.targetsCreated);
        assertEquals(1, gpu.lastTarget.resizeCount);
        assertEquals(3, gpu.drawnPacks.size());
        assertEquals("first", gpu.drawnPacks.getLast());
        assertEquals(List.of("first", "first", "first", "first"), gpu.compiledPacks);
        renderer.close();
    }

    @Test
    void failedReloadRollsBackToThePreviousPipeline() {
        FakeGpu gpu = new FakeGpu();
        CinderRenderer renderer = new CinderRenderer(gpu);
        renderer.install(pack("first"));
        gpu.invalidPacks.add("broken");

        CinderRenderer.ReloadResult result = renderer.install(pack("broken"));
        renderer.renderFrame(new FakeFrame(320, 180));

        assertFalse(result.success());
        assertEquals(List.of("first", "broken", "first", "first"), gpu.compiledPacks);
        assertEquals("first", gpu.drawnPacks.getLast());
        renderer.close();
    }

    @Test
    void successfulReloadReplacesTheActivePipeline() {
        FakeGpu gpu = new FakeGpu();
        CinderRenderer renderer = new CinderRenderer(gpu);
        renderer.install(pack("first"));

        assertTrue(renderer.install(pack("second")).success());
        renderer.renderFrame(new FakeFrame(320, 180));

        assertEquals("second", gpu.drawnPacks.getLast());
        renderer.close();
    }

    @Test
    void unchangedReloadDoesNotClearOrRecompileThePipelineCache() {
        FakeGpu gpu = new FakeGpu();
        CinderRenderer renderer = new CinderRenderer(gpu);
        PreparedPack first = pack("first");
        renderer.install(first);

        CinderRenderer.ReloadResult result = renderer.install(first);

        assertTrue(result.success());
        assertTrue(result.message().contains("unchanged"));
        assertEquals(1, gpu.resets);
        assertEquals(List.of("first"), gpu.compiledPacks);
        renderer.close();
    }

    @Test
    void disableStopsRenderingAndDestroysTheTarget() {
        FakeGpu gpu = new FakeGpu();
        CinderRenderer renderer = new CinderRenderer(gpu);
        renderer.install(pack("first"));
        renderer.renderFrame(new FakeFrame(320, 180));

        assertTrue(renderer.disable().success());
        renderer.renderFrame(new FakeFrame(640, 360));

        assertEquals(1, gpu.drawnPacks.size());
        assertEquals(1, gpu.lastTarget.closeCount);
        renderer.close();
    }

    @Test
    void openGlFallbackDoesNotCompileAllocateOrDraw() {
        FakeGpu gpu = new FakeGpu();
        gpu.backendName = "OpenGL";
        CinderRenderer renderer = new CinderRenderer(gpu);

        assertFalse(renderer.install(pack("first")).success());
        renderer.renderFrame(new FakeFrame(320, 180));

        assertEquals(0, gpu.resets);
        assertEquals(0, gpu.targetsCreated);
        assertTrue(gpu.compiledPacks.isEmpty());
        assertTrue(gpu.drawnPacks.isEmpty());
        renderer.close();
    }

    @Test
    void shutdownClearsThePipelineAndDestroysTheTarget() {
        FakeGpu gpu = new FakeGpu();
        CinderRenderer renderer = new CinderRenderer(gpu);
        renderer.install(pack("first"));
        renderer.renderFrame(new FakeFrame(320, 180));

        renderer.close();
        renderer.renderFrame(new FakeFrame(320, 180));

        assertEquals(2, gpu.resets);
        assertEquals(1, gpu.lastTarget.closeCount);
        assertEquals(1, gpu.drawnPacks.size());
    }

    private static PreparedPack pack(String identity) {
        String hash = switch (identity) {
            case "first" -> "1111111111111111111111111111111111111111111111111111111111111111";
            case "second" -> "2222222222222222222222222222222222222222222222222222222222222222";
            default -> "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        };
        return new PreparedPack(identity, Path.of(identity), "vertex", "fragment", hash);
    }

    private record FakeFrame(int width, int height) implements GpuAdapter.Frame {
    }

    private static final class FakeGpu implements GpuAdapter {
        private String backendName = "Vulkan";
        private final Set<String> invalidPacks = new HashSet<>();
        private final List<String> compiledPacks = new ArrayList<>();
        private final List<String> drawnPacks = new ArrayList<>();
        private int resets;
        private int targetsCreated;
        private FakeTarget lastTarget;

        @Override
        public void assertOnRenderThread() {
        }

        @Override
        public String backendName() {
            return backendName;
        }

        @Override
        public void resetPipelines() {
            resets++;
        }

        @Override
        public boolean compile(PackPipeline pipeline) {
            String identity = pipeline.pack().identity();
            compiledPacks.add(identity);
            return !invalidPacks.contains(identity);
        }

        @Override
        public Target createTarget(int width, int height) {
            targetsCreated++;
            lastTarget = new FakeTarget(width, height);
            return lastTarget;
        }

        @Override
        public void draw(PackPipeline pipeline, Target offscreenTarget, Frame mainTarget) {
            drawnPacks.add(pipeline.pack().identity());
        }
    }

    private static final class FakeTarget implements GpuAdapter.Target {
        private int width;
        private int height;
        private int resizeCount;
        private int closeCount;

        private FakeTarget(int width, int height) {
            this.width = width;
            this.height = height;
        }

        @Override
        public int width() {
            return width;
        }

        @Override
        public int height() {
            return height;
        }

        @Override
        public void resize(int width, int height) {
            this.width = width;
            this.height = height;
            resizeCount++;
        }

        @Override
        public void close() {
            closeCount++;
        }
    }
}
