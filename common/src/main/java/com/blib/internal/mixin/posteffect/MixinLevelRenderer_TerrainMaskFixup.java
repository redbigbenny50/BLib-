package com.blib.internal.mixin.posteffect;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.blib.internal.client.posteffect.BLibTerrainMaskFixup;

/**
 * Runs {@link BLibTerrainMaskFixup} once the terrain layers are down and before anything else is drawn.
 * <p>
 * The injection point is the return from the third {@code renderSectionLayer} call — solid, then cutout_mipped, then
 * cutout. Sodium replaces the BODY of that method, not the call site, so the target survives with Sodium installed;
 * that is the whole reason this hook works for a mod that has otherwise taken over terrain rendering.
 * <p>
 * {@code require = 0} is deliberate. If a future Minecraft or a mod reshapes {@code renderLevel} so this target no
 * longer matches, the injection is simply dropped and terrain goes back to being unclassified under Sodium. The
 * alternative — a hard injection failure — takes the whole game down at world load, which is a far worse outcome than a
 * degraded thermal view.
 */
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer_TerrainMaskFixup {

    @Inject(
        method = "renderLevel",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;renderSectionLayer(Lnet/minecraft/client/renderer/RenderType;DDDLorg/joml/Matrix4f;Lorg/joml/Matrix4f;)V",
            ordinal = 2,
            shift = At.Shift.AFTER
        ),
        require = 0
    )
    private void blib$fixupTerrainMask(CallbackInfo ci) {
        BLibTerrainMaskFixup.run();
    }
}
