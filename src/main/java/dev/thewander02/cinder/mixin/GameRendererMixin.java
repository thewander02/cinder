package dev.thewander02.cinder.mixin;

import dev.thewander02.cinder.CinderClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V",
                    shift = At.Shift.BEFORE
            )
    )
    private void cinder$renderFinalPass(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo callbackInfo) {
        CinderClient.renderFinalPass(((GameRenderer) (Object) this).mainRenderTarget());
    }
}
