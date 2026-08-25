package com.blib.internal.mixin.posteffect;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.blib.internal.client.posteffect.BLibBlockEntityStage;

/**
 * Marks the window in which a block entity is drawing. See {@link BLibBlockEntityStage}.
 * <p>
 * ⚠ The per-frame fail-safe reset lives in {@code MixinLevelRenderer_SkyStage} with the other stage flags — a
 * stuck-true flag here would misclassify every genuine entity for the rest of the frame, which is far worse than the
 * bug it fixes.
 * <p>
 * {@code require = 0}: if this ever stops matching, block entities go back to reading as entities — the current
 * cosmetic bug, not a crash.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class MixinBlockEntityRenderDispatcher_Stage {

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private <E extends BlockEntity> void blib$beginBlockEntityStage(
        E blockEntity,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        CallbackInfo ci
    ) {
        BLibBlockEntityStage.begin();
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private <E extends BlockEntity> void blib$endBlockEntityStage(
        E blockEntity,
        float partialTick,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        CallbackInfo ci
    ) {
        BLibBlockEntityStage.end();
    }
}
