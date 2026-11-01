package com.blib.internal.mixin.azurelib;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.blib.api.client.registry.v1.AzItemRendererRegistry;
import com.blib.internal.client.animation.molang.AzMolangQueryContext;

@Mixin(ItemRenderer.class)
public class MixinItemRenderer {

    @Inject(
        method = "render", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/BlockEntityWithoutLevelRenderer;renderByItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V"
        ), cancellable = true
    )
    public void itemModelHook(
        ItemStack itemStack,
        ItemDisplayContext transformType,
        boolean bl,
        PoseStack poseStack,
        MultiBufferSource multiBufferSource,
        int i,
        int j,
        BakedModel bakedModel,
        CallbackInfo ci
    ) {
        var item = itemStack.getItem();
        var renderer = AzItemRendererRegistry.getOrNull(item);

        if (renderer != null) {
            switch (transformType) {
                case GUI -> renderer.renderByGui(itemStack, transformType, poseStack, multiBufferSource, i);
                default -> renderer.renderByItem(itemStack, transformType, poseStack, multiBufferSource, i);
            }
        }
    }

    /**
     * AzureLib 3.2.3 (ported Oct 31) - records which entity holds the item for the duration of the render, so item
     * animations can answer entity queries for the holder like Bedrock attachables. Optional: if the target ever
     * changes, items just see no holder.
     */
    @Inject(
        method = "renderStatic(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;III)V",
        at = @At("HEAD"), require = 0
    )
    public void blib$pushItemHolder(
        LivingEntity entity,
        ItemStack itemStack,
        ItemDisplayContext displayContext,
        boolean leftHand,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        Level level,
        int combinedLight,
        int combinedOverlay,
        int seed,
        CallbackInfo ci
    ) {
        AzMolangQueryContext.INSTANCE.pushItemHolder(entity);
    }

    @Inject(
        method = "renderStatic(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/world/level/Level;III)V",
        at = @At("RETURN"), require = 0
    )
    public void blib$popItemHolder(
        LivingEntity entity,
        ItemStack itemStack,
        ItemDisplayContext displayContext,
        boolean leftHand,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        Level level,
        int combinedLight,
        int combinedOverlay,
        int seed,
        CallbackInfo ci
    ) {
        AzMolangQueryContext.INSTANCE.popItemHolder();
    }
}
