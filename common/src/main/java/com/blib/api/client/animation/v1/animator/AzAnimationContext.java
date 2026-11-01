package com.blib.api.client.animation.v1.animator;

import com.blib.api.client.animation.v1.pose.AzPoseLayer;
import com.blib.internal.client.animation.AzAnimationTimer;
import com.blib.internal.client.animation.cache.AzBoneCache;
import com.blib.internal.common.molang.MolangScope;

public class AzAnimationContext<T> {

    private final AzBoneCache boneCache;

    private final AzAnimatorConfig config;

    private final AzAnimationTimer timer;

    /** AzureLib 3.2.2 (ported Oct 31) - this animatable's own Molang {@code variable.*} values. */
    private final MolangScope molangScope = new MolangScope();

    /** AzureLib 3.2.3 (ported Oct 31) - per-animatable data other systems hang off this context (overlay models). */
    private final java.util.Map<Object, Object> attachments = new java.util.IdentityHashMap<>(2);

    /** AzureLib 3.2.3 (ported Oct 31) - animators that follow this one (animated overlay layers). */
    private final java.util.List<AzAnimator<?, T>> linkedAnimators = new java.util.ArrayList<>(0);

    /** AzureLib 3.2.3 (ported Oct 31) - this animatable's procedural pose state. */
    private final AzPoseLayer<T> poseLayer;

    // Package-private for mutability purposes.
    private T animatable;

    public AzAnimationContext(
        AzBoneCache boneCache,
        AzAnimatorConfig config,
        AzAnimationTimer timer
    ) {
        this.boneCache = boneCache;
        this.config = config;
        this.timer = timer;
        this.poseLayer = new AzPoseLayer<>(this);
    }

    public T animatable() {
        return animatable;
    }

    public void setAnimatable(T animatable) {
        this.animatable = animatable;
    }

    public AzBoneCache boneCache() {
        return boneCache;
    }

    public AzAnimatorConfig config() {
        return config;
    }

    public AzAnimationTimer timer() {
        return timer;
    }

    /**
     * This animatable's Molang {@code variable.*} values. Animations read and write them while this animatable is
     * animated; code can set them too, e.g. {@code context.molangScope().set("v.charge", charge)}, to drive an
     * animation per animatable without registering a global variable.
     *
     * @return the Molang variable scope of this animatable
     */
    public MolangScope molangScope() {
        return molangScope;
    }

    /**
     * This animatable's procedural pose state: the live weight of each pose modifier (see
     * {@link AzAnimator#registerPoseModifiers}), their per-animatable state, and the bookkeeping that keeps procedural
     * offsets out of the animated pose. Use it to switch or fade modifiers per animatable, e.g.
     * {@code context.poseLayer().fadeWeight("look", 0, 10)} while the entity is attacking.
     *
     * @return the pose layer of this animatable
     */
    public AzPoseLayer<T> poseLayer() {
        return poseLayer;
    }

    /**
     * Data kept per animatable by other systems, by identity key; e.g. render layers keep their overlay model copies
     * here so they live and die with this animatable's context.
     *
     * @return the mutable attachment map
     */
    public java.util.Map<Object, Object> attachments() {
        return attachments;
    }

    /**
     * Animators that follow this animatable's animator: every animation command dispatched to this animatable is also
     * applied to them. Animated overlay layers add their overlay animator here.
     *
     * @return the mutable list of linked animators
     */
    public java.util.List<AzAnimator<?, T>> linkedAnimators() {
        return linkedAnimators;
    }
}
