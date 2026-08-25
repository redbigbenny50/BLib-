package com.blib.internal.mixin.posteffect;

import com.mojang.blaze3d.platform.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.blib.internal.client.posteffect.BLibGbufferUniforms;

/**
 * Keeps blending OFF for the auxiliary attachments no matter what vanilla does to the global blend state.
 * <p>
 * ⚠⚠⚠ THIS IS THE OTHER HALF OF THE HORIZON-BAND FIX, AND WITHOUT IT THE FIRST HALF ONLY GETS PART OF THE WAY.
 * {@code BLibGbufferUniforms} disables blending per draw buffer at every shader BIND, but vanilla enables blending
 * AFTER binding: {@code setShader(position_tex)}, then {@code enableBlend}, then draw the sun. And a plain
 * {@code glEnable(GL_BLEND)} sets the state for ALL draw buffers, silently wiping an indexed disable — so the sun was
 * still blending into the classification byte.
 * <p>
 * ⭐ MEASURED: bind-time disabling alone took the bad-byte rate from 23% to 8%. The remainder is exactly this window
 * between the bind and the draw.
 * <p>
 * Every route vanilla takes to change blend state is covered, because each of them can re-enable blending for the
 * auxiliary buffers: enabling it outright, and the func/equation setters, which are non-indexed and therefore apply to
 * every draw buffer including ours.
 * <p>
 * ⚠ Cheap: six {@code glDisablei} calls, and only when something actually touches blend state. It is a no-op unless the
 * MRT is attached.
 */
@Mixin(GlStateManager.class)
public abstract class MixinGlStateManager_AuxBlend {

    @Inject(method = "_enableBlend", at = @At("TAIL"), require = 0)
    private static void blib$keepAuxBlendOffAfterEnable(CallbackInfo ci) {
        BLibGbufferUniforms.disableAuxBlending();
    }

    @Inject(method = "_blendFunc", at = @At("TAIL"), require = 0)
    private static void blib$keepAuxBlendOffAfterFunc(int sourceFactor, int destFactor, CallbackInfo ci) {
        BLibGbufferUniforms.disableAuxBlending();
    }

    @Inject(method = "_blendFuncSeparate", at = @At("TAIL"), require = 0)
    private static void blib$keepAuxBlendOffAfterFuncSeparate(
        int srcFactor,
        int dstFactor,
        int srcFactorAlpha,
        int dstFactorAlpha,
        CallbackInfo ci
    ) {
        BLibGbufferUniforms.disableAuxBlending();
    }

    @Inject(method = "_blendEquation", at = @At("TAIL"), require = 0)
    private static void blib$keepAuxBlendOffAfterEquation(int mode, CallbackInfo ci) {
        BLibGbufferUniforms.disableAuxBlending();
    }
}
