package com.blib.internal.mixin.posteffect;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.blib.internal.client.posteffect.BLibDepthSnapshot;

/**
 * Copies the depth buffer at the end of the level pass, before vanilla clears it to draw the item in hand.
 * <p>
 * Without this, every post effect that reconstructs world position from depth sees only the held item — the rest of the
 * frame reads as far plane, because the clear has already happened by the time post-processing runs. See
 * {@link BLibDepthSnapshot} for the full account.
 * <p>
 * {@code require = 0} is deliberate, matching the terrain mask fixup's reasoning: if a future Minecraft or another mod
 * reshapes {@code renderLevel} so this target no longer matches, the injection is dropped and depth-consuming post
 * effects degrade to the old behaviour. A hard injection failure would take the game down at world load instead, which
 * is a far worse outcome than one effect losing a feature.
 */
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer_DepthSnapshot {

    @Inject(method = "renderLevel", at = @At("TAIL"), require = 0)
    private void blib$captureDepthSnapshot(CallbackInfo ci) {
        BLibDepthSnapshot.capture();
    }
}
