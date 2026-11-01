package com.blib.internal.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.blib.api.client.render.v1.dismemberment.LimbHitboxDebugRenderer;

/**
 * Draws limb hitboxes right after vanilla's own F3+B box, for any entity from any mod. The call only exists while F3+B
 * is on, so this costs nothing otherwise. {@code require = 0}: if a mod or update moves the call, the overlay is simply
 * absent instead of failing to load.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderDispatcher_LimbHitboxes {

    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;renderHitbox(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/world/entity/Entity;FFFF)V",
            shift = At.Shift.AFTER
        ),
        require = 0
    )
    private void blib$renderLimbHitboxes(
        Entity entity,
        double x,
        double y,
        double z,
        float rotationYaw,
        float partialTicks,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        CallbackInfo ci
    ) {
        LimbHitboxDebugRenderer.render(entity, partialTicks, poseStack, buffer);
    }
}
