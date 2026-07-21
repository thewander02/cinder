package dev.thewander02.cinder;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CinderClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("Cinder");
    private static CinderRuntime runtime;

    @Override
    public void onInitializeClient() {
        FabricLoader loader = FabricLoader.getInstance();
        runtime = new CinderRuntime(loader.getGameDir(), loader.getConfigDir());

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("cinder", "controls")
        );
        KeyMapping reload = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.cinder.reload",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (reload.consumeClick()) {
                runtime.requestReload();
            }
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> runtime.close());

        runtime.requestReload();
        LOGGER.info("Cinder initialized; press F6 to reload the selected shaderpack");
    }

    public static void renderFinalPass(RenderTarget mainTarget) {
        if (runtime != null) {
            runtime.renderFinalPass(mainTarget);
        }
    }

    public static void beginFrame() {
        if (runtime != null) {
            runtime.beginFrame();
        }
    }
}
