package dev.thewander02.cinder.render;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.Optional;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

final class MinecraftGpuAdapter implements GpuAdapter {
    @Override
    public void assertOnRenderThread() {
        RenderSystem.assertOnRenderThread();
    }

    @Override
    public String backendName() {
        return RenderSystem.getDevice().getDeviceInfo().backendName();
    }

    @Override
    public void resetPipelines() {
        RenderSystem.getDevice().clearPipelineCache();
        RenderSystem.getDevice().loadCriticalShaders();
    }

    @Override
    public boolean compile(PackPipeline pipeline) {
        CompiledRenderPipeline compiled = RenderSystem.getDevice()
                .precompilePipeline(pipeline.pipeline(), pipeline.shaderSource());
        return compiled.isValid();
    }

    @Override
    public Target createTarget(int width, int height) {
        return new MinecraftTarget(new TextureTarget(
                "Cinder Final",
                width,
                height,
                false,
                GpuFormat.RGBA8_UNORM
        ));
    }

    @Override
    public void draw(PackPipeline pipeline, Target offscreenTarget, Frame mainTarget) {
        if (!(offscreenTarget instanceof MinecraftTarget target)
                || !(mainTarget instanceof MinecraftFrame frame)) {
            throw new IllegalArgumentException("Minecraft GPU adapter received an incompatible render target");
        }

        GpuTexture offscreenColor = requireTexture(
                target.delegate.getColorTexture(),
                "Cinder offscreen color texture"
        );
        GpuTextureView offscreenView = requireView(
                target.delegate.getColorTextureView(),
                "Cinder offscreen color texture view"
        );
        GpuTexture mainColor = requireTexture(
                frame.delegate.getColorTexture(),
                "Minecraft main color texture"
        );

        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (RenderPass pass = encoder.createRenderPass(
                () -> "Cinder final pass / " + pipeline.pack().identity(),
                offscreenView,
                Optional.of(new Vector4f(0.0F, 0.0F, 0.0F, 1.0F)))) {
            pass.setPipeline(pipeline.pipeline());
            pass.draw(3, 1, 0, 0);
        }
        encoder.copyTextureToTexture(
                offscreenColor,
                mainColor,
                0,
                0,
                0,
                0,
                0,
                frame.width(),
                frame.height()
        );
    }

    record MinecraftFrame(RenderTarget delegate) implements Frame {
        @Override
        public int width() {
            return delegate.width;
        }

        @Override
        public int height() {
            return delegate.height;
        }
    }

    private static final class MinecraftTarget implements Target {
        private final TextureTarget delegate;

        private MinecraftTarget(TextureTarget delegate) {
            this.delegate = delegate;
        }

        @Override
        public int width() {
            return delegate.width;
        }

        @Override
        public int height() {
            return delegate.height;
        }

        @Override
        public void resize(int width, int height) {
            delegate.resize(width, height);
        }

        @Override
        public void close() {
            delegate.destroyBuffers();
        }
    }

    private static GpuTexture requireTexture(@Nullable GpuTexture texture, String description) {
        if (texture == null) {
            throw new IllegalStateException(description + " is unavailable");
        }
        return texture;
    }

    private static GpuTextureView requireView(@Nullable GpuTextureView view, String description) {
        if (view == null) {
            throw new IllegalStateException(description + " is unavailable");
        }
        return view;
    }
}
