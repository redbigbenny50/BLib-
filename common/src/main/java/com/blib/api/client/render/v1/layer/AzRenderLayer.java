package com.blib.api.client.render.v1.layer;

import java.util.function.Supplier;

import com.blib.api.client.animation.v1.animator.AzAnimator;
import com.blib.api.client.model.v1.AzBakedModel;
import com.blib.api.client.model.v1.AzBone;
import com.blib.api.client.render.v1.AzRendererPipelineContext;
import com.blib.internal.client.animation.AzAnimatorAccessor;

public interface AzRenderLayer<K, T> {

    void preRender(AzRendererPipelineContext<K, T> context);

    void render(AzRendererPipelineContext<K, T> context);

    void renderForBone(AzRendererPipelineContext<K, T> context, AzBone bone);

    /**
     * AzureLib 3.2.3 (ported Oct 31) - whether this layer's translucent geometry is sorted together with the model's
     * when the renderer has translucency sorting on ({@code setTranslucencySorting(true)} on its config builder).
     * Return {@code false} for layers drawn over the model's own surfaces, like a glow or overlay texture: they are
     * coplanar with the model, so sorting gains nothing and interleaving them costs a draw call per switch. Opted-out
     * layers still draw after all sorted geometry, in order, as they do without sorting.
     *
     * @return {@code true} (the default) to sort this layer's translucent geometry with the model's
     */
    default boolean sortsTranslucency() {
        return true;
    }

    /**
     * Re-renders the animatable with a different baked model (an overlay or attachment model that shares bone names
     * with the main one), posed to match the model that was just rendered. Every bone of {@code model} that shares a
     * name with a bone of the current model copies that bone's animated pose (relative to each model's own bind pose)
     * and visibility; bones that only exist in {@code model} keep their bind pose relative to their parent.
     * <p>
     * AzureLib 3.2.3 (ported Oct 31): the animatable's bone cache is left untouched and the animator is NOT run a
     * second time, so animation state, transitions, bone snapshots, bone matrix tracking (hitboxes, sockets) and pose
     * modifiers of the base model are unaffected, and no model is deep-copied per frame. Use
     * {@link #reRenderWithAnimatedBakedModel} when the overlay has bones of its own that must animate.
     * </p>
     *
     * @param model the model to render, or {@code null} for {@link AzBakedModel#getDefault()}
     */
    default void reRenderWithBakedModel(AzRendererPipelineContext<K, T> context, AzBakedModel model) {
        var baseModel = context.bakedModel();
        var targetModel = model != null ? model : AzBakedModel.getDefault();
        var baseAnimator = AzAnimatorAccessor.getOrNull(context.animatable());
        var baseContext = baseAnimator != null ? baseAnimator.context() : null;
        var overlay = baseModel != null ? AzOverlayModels.posedOverlay(baseContext, baseModel, targetModel) : targetModel;

        renderOverlay(context, overlay);
    }

    /**
     * AzureLib 3.2.3 (ported Oct 31) - like {@link #reRenderWithBakedModel}, for overlays with bones of their own that
     * need animating (a strap that swings, a flap that opens).
     * <p>
     * The overlay gets its own animator, created once per animatable from {@code overlayAnimatorFactory} and kept as
     * long as the animatable's animator lives. It receives every animation command sent to the animatable, and every
     * frame, before it animates, each of its tracks is aligned with the base track of the same name (sequence, stage,
     * repeat count, direction and progress), so overlay-only bones stay frame-exact with the base animation - also when
     * the overlay is created mid-animation or was not rendered for a while. Shared bones are then copied from the base
     * model so they line up exactly.
     * </p>
     * <p>
     * Typically the overlay animator returns the base animation file from {@code getAnimationLocation} and registers
     * tracks with the same names as the base animator, with the overlay-only bones keyed in that file. Do not register
     * sound, particle or custom keyframe handlers on it unless they should fire twice. Costs one extra animator update
     * per animatable per frame, so prefer {@link #reRenderWithBakedModel} when the overlay has nothing of its own to
     * animate.
     * </p>
     *
     * @param model                  the overlay's baked model template
     * @param overlayAnimatorFactory creates the overlay's animator; called once per animatable and overlay model
     */
    default void reRenderWithAnimatedBakedModel(
        AzRendererPipelineContext<K, T> context,
        AzBakedModel model,
        Supplier<? extends AzAnimator<?, T>> overlayAnimatorFactory
    ) {
        var baseModel = context.bakedModel();
        var targetModel = model != null ? model : AzBakedModel.getDefault();
        AzAnimator<?, T> baseAnimator = AzAnimatorAccessor.getOrNull(context.animatable());
        var overlay = baseModel != null
            ? AzOverlayModels.animatedOverlay(
                baseAnimator,
                context.animatable(),
                baseModel,
                targetModel,
                overlayAnimatorFactory,
                context.partialTick()
            )
            : targetModel;

        renderOverlay(context, overlay);
    }

    private void renderOverlay(AzRendererPipelineContext<K, T> context, AzBakedModel overlay) {
        var previousModel = context.bakedModel();
        context.setBakedModel(overlay);

        try {
            // Never re-run the base animator: the overlay is already posed.
            context.rendererPipeline().reRender(context, false);
        } finally {
            context.setBakedModel(previousModel);
        }
    }
}
