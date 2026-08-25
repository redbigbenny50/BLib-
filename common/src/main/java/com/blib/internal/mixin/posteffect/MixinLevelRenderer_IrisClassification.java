package com.blib.internal.mixin.posteffect;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.blib.internal.client.posteffect.BLibIrisAuxTarget;
import com.blib.internal.client.posteffect.BLibIrisClassificationPass;

/**
 * Captures scene depth BEFORE water is drawn, then runs {@link BLibIrisClassificationPass} at the end of the level
 * pass.
 * <h2>The bug this splits in two</h2> The classification pass re-renders entities and depth-tests them against the
 * scene. Run at the end of the level pass — the only place it demonstrably works — the water surface has already
 * written its depth, so anything beneath it loses the test and is invisible from above the surface. A swimming
 * xenomorph is precisely what the mask exists to reveal, so that is not an acceptable limit.
 * <p>
 * Moving the whole pass earlier was tried at length and does not work. At that point it runs cleanly — complete
 * framebuffer, valid depth, restored bindings, correct viewport, private buffer source, no exception thrown — and
 * writes zero pixels. Five separate mechanisms were measured and eliminated and the cause is still unknown.
 * <h2>Why capturing only DEPTH early does work</h2> ⭐ A depth capture is a framebuffer BLIT. No entity rendering, no
 * buffer source, no shader binding, no batching — none of the machinery that fails when the pass itself runs early is
 * involved. The early injection point is known to fire, and the depth there is known to be good: measured populated on
 * 47 of 48 samples, minimum 0.9966.
 * <p>
 * So the two halves are separated. Depth is copied while it still contains no water. The pass stays where it works and
 * tests against that copy. Entities below the surface keep their classification, and the mask sees through water the
 * way it sees through darkness.
 */
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer_IrisClassification {

    /** Copies scene depth into the pass's private attachment while it still contains no translucent terrain. */
    @Inject(
        method = "renderLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/Sheets;translucentCullBlockSheet()Lnet/minecraft/client/renderer/RenderType;"
        ),
        require = 0
    )
    private void blib$captureSceneDepthBeforeWater(
        DeltaTracker deltaTracker,
        boolean renderBlockOutline,
        Camera camera,
        GameRenderer gameRenderer,
        LightTexture lightTexture,
        Matrix4f frustumMatrix,
        Matrix4f projectionMatrix,
        CallbackInfo ci
    ) {
        BLibIrisAuxTarget.INSTANCE.copySceneDepthIfReady();
    }

    /** Runs the pass where it works, against the pre-water depth captured above. */
    @Inject(method = "renderLevel", at = @At("RETURN"), require = 0)
    private void blib$irisClassificationRun(
        DeltaTracker deltaTracker,
        boolean renderBlockOutline,
        Camera camera,
        GameRenderer gameRenderer,
        LightTexture lightTexture,
        Matrix4f frustumMatrix,
        Matrix4f projectionMatrix,
        CallbackInfo ci
    ) {
        BLibIrisClassificationPass.run(deltaTracker, frustumMatrix, projectionMatrix);
    }
}
