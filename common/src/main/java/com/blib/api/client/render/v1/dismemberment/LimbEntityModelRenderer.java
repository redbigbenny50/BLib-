package com.blib.api.client.render.v1.dismemberment;

import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.blib.api.client.model.v1.AzBone;
import com.blib.api.client.render.v1.AzLayerRenderer;
import com.blib.api.client.render.v1.AzRendererPipelineContext;
import com.blib.api.client.render.v1.entity.AzEntityRenderer;
import com.blib.api.client.render.v1.entity.model.AzEntityModelRenderer;
import com.blib.api.client.render.v1.entity.pipeline.AzEntityRendererPipeline;
import com.blib.api.common.dismemberment.v1.entity.DismemberedLimbEntity;
import com.blib.internal.mixin.MixinEntityRenderDispatcher_Accessor;

/**
 * Walks only the bone subtree rooted at the limb's bone instead of all top-level bones, so the limb entity renders just
 * the detached piece.
 */
public class LimbEntityModelRenderer extends AzEntityModelRenderer<DismemberedLimbEntity> {

    private Set<String> excludedBoneNames = Set.of();

    public LimbEntityModelRenderer(
        AzEntityRendererPipeline<DismemberedLimbEntity> entityRendererPipeline,
        AzLayerRenderer<UUID, DismemberedLimbEntity> layerRenderer
    ) {
        super(entityRendererPipeline, layerRenderer);
    }

    @Override
    public void render(AzRendererPipelineContext<UUID, DismemberedLimbEntity> context, boolean isReRender) {
        var animatable = context.animatable();
        var visuals = animatable.resolveVisuals();

        if (visuals == null) {
            return;
        }

        var rootBoneName = visuals.rootBoneName();

        // Pick the render path based on the source mob's renderer. AzEntityRenderer means BLib
        // geo bones; anything else (LivingEntityRenderer subclasses, etc.) means vanilla
        // ModelPart rendering. The ghost is always live as long as the limb is, so this lookup
        // never falls back unless the source NBT hasn't synced yet.
        var ghost = animatable.getOrCreateGhost();

        if (ghost != null) {
            var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            var sourceRenderer = ((MixinEntityRenderDispatcher_Accessor) dispatcher).blib$getRenderers()
                .get(ghost.getType());

            if (!(sourceRenderer instanceof AzEntityRenderer<?>)) {
                if (!isReRender && context.vertexConsumer() != null) {
                    VanillaLimbRenderer.render(
                        animatable,
                        context.poseStack(),
                        context.vertexConsumer(),
                        context.packedLight(),
                        context.packedOverlay()
                    );

                    // Re-run the source mob's armor + held-item layers at the limb's pose so armor and any held item
                    // follow the limb (e.g. helmet on a severed head, bow on a severed arm). Bone-only path is
                    // preserved above; these passes use the buffer source.
                    if (context.multiBufferSource() != null) {
                        LimbArmorRenderer.render(
                            animatable,
                            context.poseStack(),
                            context.multiBufferSource(),
                            context.packedLight()
                        );
                        LimbHeldItemRenderer.render(
                            animatable,
                            context.poseStack(),
                            context.multiBufferSource(),
                            context.packedLight()
                        );
                    }
                }
                return;
            }
        }

        var bakedModel = context.bakedModel();

        if (bakedModel == null) {
            return;
        }

        var rootBone = bakedModel.getBoneOrNull(rootBoneName);

        if (rootBone == null) {
            return;
        }

        var poseStack = context.poseStack();
        poseStack.pushPose();
        LimbRenderTransforms.applySourceTransform(poseStack, animatable);

        var selectedPose = visuals.poseOrDefault(animatable.getPoseId());
        var renderOffset = selectedPose.renderOffset();
        var renderRotation = selectedPose.renderRotation();
        var renderScale = selectedPose.renderScale();
        if (selectedPose.modelerTransform()) {
            var renderPivot = selectedPose.renderPivot();
            poseStack.translate(renderOffset.x, renderOffset.y, renderOffset.z);
            poseStack.translate(renderPivot.x, renderPivot.y, renderPivot.z);
            poseStack.mulPose(Axis.ZP.rotationDegrees((float) renderRotation.z));
            poseStack.mulPose(Axis.YP.rotationDegrees((float) renderRotation.y));
            poseStack.mulPose(Axis.XP.rotationDegrees((float) renderRotation.x));
            poseStack.scale((float) renderScale.x, (float) renderScale.y, (float) renderScale.z);
            poseStack.translate(
                -renderPivot.x - rootBone.getPivotX() / 16f,
                -renderPivot.y - rootBone.getPivotY() / 16f,
                -renderPivot.z - rootBone.getPivotZ() / 16f
            );
        } else {
            // Legacy limb visuals treat render_offset as a local post-scale translation. Keep that ordering for
            // existing JSON that has no render_pivot field.
            poseStack.mulPose(Axis.ZP.rotationDegrees((float) renderRotation.z));
            poseStack.mulPose(Axis.YP.rotationDegrees((float) renderRotation.y));
            poseStack.mulPose(Axis.XP.rotationDegrees((float) renderRotation.x));
            poseStack.scale((float) renderScale.x, (float) renderScale.y, (float) renderScale.z);
            poseStack.translate(
                -rootBone.getPivotX() / 16f + renderOffset.x,
                -rootBone.getPivotY() / 16f + renderOffset.y,
                -rootBone.getPivotZ() / 16f + renderOffset.z
            );
        }

        if (!isReRender) {
            var animator = entityRendererPipeline.getRenderer().getAnimator();

            if (animator != null) {
                handleAnimation(animator, animatable, context.partialTick());
            }
        }

        entityRendererPipeline.getModelRenderTranslations().set(poseStack.last().pose());

        if (context.vertexConsumer() != null) {
            entityRendererPipeline.updateAnimatedTextureFrame(animatable);
            excludedBoneNames = Set.copyOf(visuals.excludedBoneNames());
            var restoredBones = applyBoneTransforms(context, visuals.boneTransforms());
            try {
                renderRecursively(context, rootBone, isReRender);

                // Companion bones live outside the root subtree but ride along with this limb (e.g. authored sibling
                // bones the modeler kept independent of the head bone). Render each at the same pose stack as the root.
                if (!visuals.companionBoneNames().isEmpty()) {
                    for (var companionBoneName : visuals.companionBoneNames()) {
                        if (excludedBoneNames.contains(companionBoneName)) {
                            continue;
                        }

                        var companionBone = bakedModel.getBoneOrNull(companionBoneName);

                        if (companionBone != null) {
                            renderRecursively(context, companionBone, isReRender);
                        }
                    }
                }
            } finally {
                restoreBoneTransforms(restoredBones);
                excludedBoneNames = Set.of();
            }

            entityRendererPipeline.config().renderEntry(context);
        }

        poseStack.popPose();
    }

    @Override
    public void renderRecursively(AzRendererPipelineContext<UUID, DismemberedLimbEntity> context, AzBone bone, boolean isReRender) {
        if (excludedBoneNames.contains(bone.getName())) {
            return;
        }

        super.renderRecursively(context, bone, isReRender);
    }

    private static List<RestoredBoneTransform> applyBoneTransforms(
        AzRendererPipelineContext<UUID, DismemberedLimbEntity> context,
        java.util.Map<String, com.blib.api.common.dismemberment.v1.LimbBoneTransform> transforms
    ) {
        if (transforms.isEmpty()) {
            return List.of();
        }

        var restoredBones = new ArrayList<RestoredBoneTransform>();
        var model = context.bakedModel();

        for (var entry : transforms.entrySet()) {
            var bone = model.getBoneOrNull(entry.getKey());

            if (bone == null) {
                continue;
            }

            restoredBones.add(RestoredBoneTransform.capture(bone));
            var transform = entry.getValue();

            transform.position().ifPresent(position -> bone.updatePosition((float) position.x, (float) position.y, (float) position.z));
            transform
                .rotation()
                .ifPresent(
                    rotation -> bone.updateRotation(
                        (float) Math.toRadians(-rotation.x),
                        (float) Math.toRadians(-rotation.y),
                        (float) Math.toRadians(rotation.z)
                    )
                );
            transform.scale().ifPresent(scale -> bone.updateScale((float) scale.x, (float) scale.y, (float) scale.z));
        }

        return restoredBones;
    }

    private static void restoreBoneTransforms(List<RestoredBoneTransform> restoredBones) {
        for (var i = restoredBones.size() - 1; i >= 0; i--) {
            restoredBones.get(i).restore();
        }
    }

    private record RestoredBoneTransform(
        AzBone bone,
        float posX,
        float posY,
        float posZ,
        float rotX,
        float rotY,
        float rotZ,
        float scaleX,
        float scaleY,
        float scaleZ
    ) {

        private static RestoredBoneTransform capture(AzBone bone) {
            return new RestoredBoneTransform(
                bone,
                bone.getPosX(),
                bone.getPosY(),
                bone.getPosZ(),
                bone.getRotX(),
                bone.getRotY(),
                bone.getRotZ(),
                bone.getScaleX(),
                bone.getScaleY(),
                bone.getScaleZ()
            );
        }

        private void restore() {
            bone.updatePosition(posX, posY, posZ);
            bone.updateRotation(rotX, rotY, rotZ);
            bone.updateScale(scaleX, scaleY, scaleZ);
        }
    }
}
