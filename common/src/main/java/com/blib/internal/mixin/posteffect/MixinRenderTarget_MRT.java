package com.blib.internal.mixin.posteffect;

import com.mojang.blaze3d.pipeline.MainTarget;
import com.mojang.blaze3d.pipeline.RenderTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.blib.internal.client.posteffect.BLibIrisCompat;
import com.blib.internal.client.posteffect.BLibMainTargetMRT;

/**
 * Picks up the resize path: when {@link RenderTarget#createBuffers(int, int, boolean)} runs on a {@link MainTarget}
 * instance (which happens during window resize, since {@code MainTarget} doesn't override {@code createBuffers}),
 * re-allocate the MRT auxiliary attachments. {@code destroyBuffers} similarly cleans up our auxiliaries so resize
 * doesn't leak GL textures.
 * <p>
 * {@code instanceof MainTarget} guards against firing on the framework's own ping-pong targets and any other non-main
 * render targets in the JVM (entity outline, particle target, etc.).
 */
@Mixin(RenderTarget.class)
public abstract class MixinRenderTarget_MRT {

    @Inject(method = "createBuffers", at = @At("TAIL"))
    private void blib$attachAuxOnResize(int width, int height, boolean clearError, CallbackInfo ci) {
        if (BLibIrisCompat.isShaderPackActive()) {
            return;
        }

        var self = (RenderTarget) (Object) this;

        if (self instanceof MainTarget) {
            BLibMainTargetMRT.attach(self.frameBufferId, self.viewWidth, self.viewHeight);
        }
    }

    @Inject(method = "destroyBuffers", at = @At("HEAD"))
    private void blib$destroyAuxOnDestroy(CallbackInfo ci) {
        var self = (RenderTarget) (Object) this;

        if (self instanceof MainTarget) {
            BLibMainTargetMRT.destroy();
        }
    }

    @Inject(
        method = "clear",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/GlStateManager;_clear(IZ)V",
            shift = At.Shift.AFTER
        )
    )
    private void blib$clearAuxiliaryAttachments(boolean clearError, CallbackInfo ci) {
        if (BLibIrisCompat.isShaderPackActive()) {
            return;
        }

        var self = (RenderTarget) (Object) this;

        if (self instanceof MainTarget) {
            // ⚠⚠ ORDER AND BINDING BOTH MATTER. glDrawBuffers and glClearBufferfv act on the DRAW framebuffer, and
            // glClearBufferfv clears the Nth DRAW BUFFER — so the draw-buffer mapping has to be in place, on the
            // right framebuffer, before the clear runs. clearAuxiliaryAttachments now binds the MainTarget itself and
            // restores the previous binding, so this pair no longer depends on what vanilla happened to leave bound.
            BLibMainTargetMRT.clearAuxiliaryAttachments();

            // ⚠ AND AGAIN AFTERWARDS, against the framebuffer vanilla has bound right now. The call inside the clear
            // runs while the MainTarget is bound EXPLICITLY and then restores the previous binding; this one restores
            // the draw-buffer mapping in whatever state the rest of the frame expects. Cheap, and dropping it was a
            // regression risk I do not want to take on a live bug.
            BLibMainTargetMRT.restoreDrawBuffers();
        }
    }
}
