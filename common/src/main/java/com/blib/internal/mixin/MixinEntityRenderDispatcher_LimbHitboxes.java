package com.blib.internal.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.blib.api.common.dismemberment.v1.Dismemberable;
import com.blib.api.common.dismemberment.v1.LimbCategories;
import com.blib.api.common.dismemberment.v1.LimbDefinitionRegistry;
import com.blib.api.common.dismemberment.v1.LimbHitVolumeRegistry;

/** Adds attached anatomical limb volumes to vanilla's F3+B entity-hitbox pass. */
@Mixin(EntityRenderDispatcher.class)
public abstract class MixinEntityRenderDispatcher_LimbHitboxes {

    @Inject(method = "renderHitbox", at = @At("TAIL"))
    private static void blib$renderLimbHitboxes(
        PoseStack poseStack,
        VertexConsumer vertexConsumer,
        Entity entity,
        float partialTick,
        float red,
        float green,
        float blue,
        CallbackInfo callbackInfo
    ) {
        if (!(entity instanceof LivingEntity livingEntity) || !(entity instanceof Dismemberable dismemberable)) {
            return;
        }

        var manager = dismemberable.getDismembermentManager();
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-livingEntity.getYRot()));
        poseStack.scale(-1.0F, 1.0F, 1.0F);

        for (var definition : LimbDefinitionRegistry.getDefinitions(livingEntity.getType())) {
            if (manager.isDetached(definition)) {
                continue;
            }
            var color = color(definition);
            for (var volume : LimbHitVolumeRegistry.resolve(definition, livingEntity)) {
                var bounds = volume.bounds();
                var scaledBounds = new net.minecraft.world.phys.AABB(
                    bounds.minX * livingEntity.getBbWidth(),
                    bounds.minY * livingEntity.getBbHeight(),
                    bounds.minZ * livingEntity.getBbWidth(),
                    bounds.maxX * livingEntity.getBbWidth(),
                    bounds.maxY * livingEntity.getBbHeight(),
                    bounds.maxZ * livingEntity.getBbWidth()
                );
                LevelRenderer.renderLineBox(
                    poseStack,
                    vertexConsumer,
                    scaledBounds,
                    color.red(),
                    color.green(),
                    color.blue(),
                    1.0F
                );
            }
        }
        poseStack.popPose();
    }

    private static Color color(com.blib.api.common.dismemberment.v1.LimbDefinition definition) {
        if (definition.category().equals(LimbCategories.HEAD)) {
            return new Color(1.0F, 0.25F, 0.25F);
        }
        if (definition.category().equals(LimbCategories.ARM)) {
            return new Color(1.0F, 0.75F, 0.15F);
        }
        if (definition.category().equals(LimbCategories.LEG)) {
            return new Color(0.2F, 0.75F, 1.0F);
        }
        if (definition.category().equals(LimbCategories.TAIL)) {
            return new Color(0.65F, 0.3F, 1.0F);
        }
        return new Color(0.85F, 0.85F, 0.85F);
    }

    private record Color(
        float red,
        float green,
        float blue
    ) {}
}
