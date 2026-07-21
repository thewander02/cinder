package dev.thewander02.cinder.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.thewander02.cinder.CinderClient;
import dev.thewander02.cinder.pack.PreparedPack;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

public final class CinderRenderer implements AutoCloseable {
    private final PipelineTransaction<PackPipeline> pipelines = new PipelineTransaction<>();
    private final GpuAdapter gpu;
    private GpuAdapter.@Nullable Target offscreenTarget;
    private boolean backendWarningLogged;

    public CinderRenderer() {
        this(new MinecraftGpuAdapter());
    }

    CinderRenderer(GpuAdapter gpu) {
        this.gpu = gpu;
    }

    public ReloadResult install(PreparedPack pack) {
        gpu.assertOnRenderThread();
        if (!isVulkanBackend()) {
            warnAboutBackendOnce();
            return ReloadResult.failure("Minecraft is using OpenGL; Cinder remains inactive", null);
        }

        PackPipeline candidate = PackPipeline.create(pack);
        PipelineTransaction.Transition transition = pipelines.install(candidate, compiler());
        if (transition.installed()) {
            return ReloadResult.success("Loaded " + pack.identity() + " (" + shortHash(pack.contentHash()) + ")");
        }
        if (transition.previousRestored()) {
            return ReloadResult.failure("Compilation failed; restored the previous Cinder pipeline", transition.failure());
        }
        destroyTarget();
        return ReloadResult.failure("Compilation failed; Cinder fell back to vanilla rendering", transition.failure());
    }

    public ReloadResult disable() {
        gpu.assertOnRenderThread();
        Exception failure = pipelines.disable(compiler());
        destroyTarget();
        if (failure != null) {
            return ReloadResult.failure("Cinder was disabled, but pipeline-cache cleanup failed", failure);
        }
        return ReloadResult.success("Cinder is disabled");
    }

    public void render(RenderTarget mainTarget) {
        renderFrame(new MinecraftGpuAdapter.MinecraftFrame(mainTarget));
    }

    void renderFrame(GpuAdapter.Frame frame) {
        gpu.assertOnRenderThread();
        PackPipeline active = pipelines.active();
        if (active == null) {
            return;
        }
        if (!isVulkanBackend()) {
            warnAboutBackendOnce();
            return;
        }
        if (frame.width() <= 0 || frame.height() <= 0) {
            return;
        }

        ensureCompiled(active);
        ensureTarget(frame.width(), frame.height());
        gpu.draw(active, offscreenTarget, frame);
    }

    public void failClosed() {
        Exception failure = pipelines.disable(compiler());
        destroyTarget();
        if (failure != null) {
            CinderClient.LOGGER.error("Cinder could not fully clear its pipeline cache after a render failure", failure);
        }
    }

    @Override
    public void close() {
        Exception failure = pipelines.disable(compiler());
        destroyTarget();
        if (failure != null) {
            CinderClient.LOGGER.error("Cinder could not fully clear its pipeline cache during shutdown", failure);
        }
    }

    private PipelineTransaction.Compiler<PackPipeline> compiler() {
        return new PipelineTransaction.Compiler<>() {
            @Override
            public void reset() throws Exception {
                gpu.resetPipelines();
            }

            @Override
            public boolean compile(PackPipeline pipeline) throws Exception {
                return gpu.compile(pipeline);
            }
        };
    }

    private void ensureTarget(int width, int height) {
        if (offscreenTarget == null) {
            offscreenTarget = gpu.createTarget(width, height);
        } else if (offscreenTarget.width() != width || offscreenTarget.height() != height) {
            offscreenTarget.resize(width, height);
        }
    }

    private void ensureCompiled(PackPipeline active) {
        try {
            if (!gpu.compile(active)) {
                throw new IllegalStateException("The active Cinder pipeline is no longer valid");
            }
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not restore the active Cinder pipeline", exception);
        }
    }

    private void destroyTarget() {
        if (offscreenTarget != null) {
            offscreenTarget.close();
            offscreenTarget = null;
        }
    }

    private boolean isVulkanBackend() {
        return "vulkan".equals(gpu.backendName().toLowerCase(Locale.ROOT));
    }

    private void warnAboutBackendOnce() {
        if (!backendWarningLogged) {
            backendWarningLogged = true;
            CinderClient.LOGGER.warn(
                    "Minecraft selected graphics backend '{}'; Cinder requires Vulkan and will leave vanilla rendering untouched",
                    gpu.backendName()
            );
        }
    }

    private static String shortHash(String hash) {
        return hash.substring(0, Math.min(12, hash.length()));
    }

    public record ReloadResult(boolean success, String message, @Nullable Exception failure) {
        static ReloadResult success(String message) {
            return new ReloadResult(true, message, null);
        }

        static ReloadResult failure(String message, @Nullable Exception failure) {
            return new ReloadResult(false, message, failure);
        }
    }
}
