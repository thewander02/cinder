package dev.thewander02.cinder;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.thewander02.cinder.config.CinderConfig;
import dev.thewander02.cinder.config.CinderConfigLoader;
import dev.thewander02.cinder.pack.PackRepository;
import dev.thewander02.cinder.pack.PreparedPack;
import dev.thewander02.cinder.render.CinderRenderer;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

final class CinderRuntime implements AutoCloseable {
    private final CinderConfigLoader configLoader;
    private final PackRepository packRepository;
    private final CinderRenderer renderer = new CinderRenderer();
    private final ExecutorService packLoaderExecutor = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon(true).name("Cinder Pack Loader").factory()
    );
    private final AtomicReference<PendingReload> pendingReload = new AtomicReference<>();
    private final AtomicBoolean loading = new AtomicBoolean();
    private boolean closed;

    CinderRuntime(Path gameDirectory, Path configDirectory) {
        this.configLoader = new CinderConfigLoader(configDirectory);
        this.packRepository = new PackRepository(gameDirectory);
    }

    void requestReload() {
        if (closed || !loading.compareAndSet(false, true)) {
            return;
        }

        CompletableFuture.supplyAsync(this::prepareReload, packLoaderExecutor)
                .whenComplete((plan, throwable) -> {
                    Throwable failure = throwable == null ? null : unwrapCompletionFailure(throwable);
                    pendingReload.set(new PendingReload(plan, failure));
                    loading.set(false);
                });
    }

    void beginFrame() {
        if (closed) {
            return;
        }

        PendingReload pending = pendingReload.getAndSet(null);
        if (pending != null) {
            applyReload(pending);
        }
    }

    void renderFinalPass(RenderTarget mainTarget) {
        if (closed) {
            return;
        }

        try {
            renderer.render(mainTarget);
        } catch (RuntimeException exception) {
            renderer.failClosed();
            CinderClient.LOGGER.error("Cinder final pass failed; returning to vanilla rendering", exception);
            notifyPlayer("Final pass failed; using vanilla rendering", ChatFormatting.RED);
        }
    }

    @Override
    public void close() {
        closed = true;
        packLoaderExecutor.shutdownNow();
        renderer.close();
    }

    private ReloadPlan prepareReload() {
        try {
            CinderConfig config = configLoader.load();
            if (!config.enabled()) {
                return ReloadPlan.disabled();
            }
            CinderClient.LOGGER.info("Preparing shaderpack '{}' from {}", config.shaderPack(), packRepository.shaderpacksDirectory());
            return ReloadPlan.enabled(packRepository.load(config.shaderPack()));
        } catch (Exception exception) {
            throw new ReloadPreparationException(exception);
        }
    }

    private void applyReload(PendingReload pending) {
        if (pending.failure() != null) {
            CinderClient.LOGGER.error("Could not prepare the selected Cinder shaderpack", pending.failure());
            notifyPlayer("Reload failed; the previous pipeline remains active", ChatFormatting.RED);
            return;
        }

        ReloadPlan plan = pending.plan();
        if (plan == null) {
            CinderClient.LOGGER.error("Cinder reload completed without a plan");
            return;
        }

        CinderRenderer.ReloadResult result = plan.enabled()
                ? renderer.install(plan.pack())
                : renderer.disable();
        if (result.success()) {
            CinderClient.LOGGER.info(result.message());
            notifyPlayer(result.message(), ChatFormatting.GREEN);
        } else {
            CinderClient.LOGGER.error(result.message(), result.failure());
            notifyPlayer(result.message(), ChatFormatting.RED);
        }
    }

    private static void notifyPlayer(String message, ChatFormatting color) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal("[Cinder] " + message).withStyle(color));
        }
    }

    private static Throwable unwrapCompletionFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null
                && (current instanceof java.util.concurrent.CompletionException
                || current instanceof ReloadPreparationException)) {
            current = current.getCause();
        }
        return current;
    }

    private record ReloadPlan(boolean enabled, @Nullable PreparedPack pack) {
        static ReloadPlan enabled(PreparedPack pack) {
            return new ReloadPlan(true, pack);
        }

        static ReloadPlan disabled() {
            return new ReloadPlan(false, null);
        }
    }

    private record PendingReload(@Nullable ReloadPlan plan, @Nullable Throwable failure) {
    }

    private static final class ReloadPreparationException extends RuntimeException {
        private ReloadPreparationException(Throwable cause) {
            super(cause);
        }
    }
}
