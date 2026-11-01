package com.blib.api.client.animation.v1.animator;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;

import com.blib.api.client.animation.v1.pose.AzPoseLayer;
import com.blib.api.client.animation.v1.pose.AzPoseModifierRegistry;
import com.blib.api.client.animation.v1.pose.AzPoseModifiers;
import com.blib.api.client.animation.v1.track.AzAnimationTrackContainer;
import com.blib.api.client.profiling.v1.AzProfileStage;
import com.blib.api.client.profiling.v1.AzProfiler;
import com.blib.internal.client.animation.AzAnimationTimer;
import com.blib.internal.client.animation.cache.AzBakedAnimationCache;
import com.blib.internal.client.animation.cache.AzBoneCache;
import com.blib.internal.client.animation.molang.AzMolangQueryContext;
import com.blib.internal.client.animation.primitive.AzBakedAnimation;
import com.blib.internal.common.molang.MolangQueries;
import com.blib.internal.common.molang.MolangScope;
import com.blib.internal.common.molang.MolangVariableRef;
import com.blib.mod.BLib;

/**
 * The {@code AzAnimator} class is an abstract base class for managing animations for various types of objects such as
 * entities, blocks, or items. It provides a reusable structure for animating objects, allowing the integration of a
 * variety of animation tracks and custom animations.
 *
 * @param <K> The type of the key used to identify the animatable object. Typically, a UUID for items/entities and Long
 *            for BlockEntities.
 * @param <T> The type of object this animator will animate (e.g., an entity, block entity, or item stack).
 */
public abstract class AzAnimator<K, T> {

    private static final MolangVariableRef LIFE_TIME_REF = new MolangVariableRef(MolangQueries.LIFE_TIME);

    private static final MolangVariableRef ACTOR_COUNT_REF = new MolangVariableRef(MolangQueries.ACTOR_COUNT);

    private static final MolangVariableRef TIME_OF_DAY_REF = new MolangVariableRef(MolangQueries.TIME_OF_DAY);

    private static final MolangVariableRef MOON_PHASE_REF = new MolangVariableRef(MolangQueries.MOON_PHASE);

    private static final MolangVariableRef MOON_BRIGHTNESS_REF = new MolangVariableRef(MolangQueries.MOON_BRIGHTNESS);

    private static final MolangVariableRef DAY_REF = new MolangVariableRef(MolangQueries.DAY);

    private static final MolangVariableRef TIME_STAMP_REF = new MolangVariableRef(MolangQueries.TIME_STAMP);

    private static final MolangVariableRef FRAME_ALPHA_REF = new MolangVariableRef(MolangQueries.FRAME_ALPHA);

    private static final MolangVariableRef CLIENT_MAX_RENDER_DISTANCE_REF = new MolangVariableRef(
        MolangQueries.CLIENT_MAX_RENDER_DISTANCE
    );

    private static final double[] MOON_BRIGHTNESS = { 1, 0.75, 0.5, 0.25, 0, 0.25, 0.5, 0.75 };

    private static final Set<ResourceLocation> MISSING_ANIMATION_FILES = ConcurrentHashMap.newKeySet();

    private AzAnimationContext<T> currentContext;

    /** AzureLib 3.2.3 (ported Oct 31) - procedural pose modifiers, registered once on first use. */
    private final AzPoseModifierRegistry<T> poseModifiers = new AzPoseModifierRegistry<>();

    private boolean poseModifiersRegistered;

    private final List<AzAnimatorUpdateHook<T>> updateHooks = new ArrayList<>(0);

    /** Set while a render layer re-animates a swapped-in model; see {@link #suspendPoseModifiers}. */
    private int poseModifiersSuspended;

    private final WeakHashMap<K, AzAnimationContext<T>> contextCache = new WeakHashMap<>();

    private final AzAnimationTrackContainer<T> animationTrackContainer;

    protected final AzAnimatorConfig config;

    public boolean reloadAnimations;

    private double molangAnimTime;

    private float molangPartialTicks;

    /** AzureLib 3.1.22: set by the first {@link #animate} call, which runs {@link #onFirstAnimate} once. */
    private boolean hasAnimated;

    private final DoubleSupplier lifetimeSupplier = () -> molangAnimTime / 20d;

    private final DoubleSupplier actorCountSupplier = () -> {
        var lvl = Minecraft.getInstance().level;
        return lvl != null ? lvl.getEntityCount() : 0;
    };

    private final DoubleSupplier timeOfDaySupplier = () -> {
        var lvl = Minecraft.getInstance().level;
        return lvl != null ? lvl.getDayTime() / 24000f : 0;
    };

    private final DoubleSupplier moonPhaseSupplier = AzAnimator::moonPhase;

    private final DoubleSupplier moonBrightnessSupplier = () -> MOON_BRIGHTNESS[moonPhase()];

    private final DoubleSupplier daySupplier = () -> {
        var lvl = Minecraft.getInstance().level;
        return lvl != null ? Math.floorDiv(lvl.getDayTime(), 24000L) : 0;
    };

    private final DoubleSupplier timeStampSupplier = () -> {
        var lvl = Minecraft.getInstance().level;
        return lvl != null ? lvl.getGameTime() : 0;
    };

    private final DoubleSupplier frameAlphaSupplier = () -> molangPartialTicks;

    private final DoubleSupplier clientMaxRenderDistanceSupplier = () -> Minecraft.getInstance().options
        .getEffectiveRenderDistance();

    private static int moonPhase() {
        var lvl = Minecraft.getInstance().level;

        if (lvl == null)
            return 0;

        return lvl.getMoonPhase();
    }

    protected AzAnimator() {
        this(AzAnimatorConfig.defaultConfig());
    }

    protected AzAnimator(AzAnimatorConfig config) {
        this.animationTrackContainer = new AzAnimationTrackContainer<>();

        this.config = config;
    }

    public AzBoneCache createBoneCache() {
        return new AzBoneCache();
    }

    public AzAnimationTimer createAzAnimationTimer(AzAnimatorConfig config) {
        return new AzAnimationTimer(config);
    }

    /**
     * AzureLib 3.2.3 (ported Oct 31) - the context this animator keeps for {@code key}, without creating one or
     * changing the current context. Molang's arrow operator uses it to read another animatable's {@code variable.*}
     * values.
     *
     * @return the context, or {@code null} if this animator has never animated {@code key}
     */
    public @Nullable AzAnimationContext<T> findContext(K key) {
        return contextCache.get(key);
    }

    public AzAnimationContext<T> getOrCreateContext(K uuid) {
        var ctx = contextCache.computeIfAbsent(
            uuid,
            ignored -> new AzAnimationContext<>(createBoneCache(), config, createAzAnimationTimer(config))
        );
        this.currentContext = ctx;
        return ctx;
    }

    /**
     * AzureLib 3.1.22 (ported Oct 17) - {@link #getOrCreateContext(Object)} that also binds the animatable to the
     * context, so anything that runs before the first {@link #animate} call (queued commands replayed when the animator
     * is created, {@link #onFirstAnimate}) sees a non-null {@link AzAnimationContext#animatable()}.
     */
    public AzAnimationContext<T> getOrCreateContext(K uuid, T animatable) {
        var ctx = getOrCreateContext(uuid);
        ctx.setAnimatable(animatable);
        return ctx;
    }

    public abstract void registerTracks(AzAnimationTrackContainer<T> animationTrackContainer);

    public abstract @NotNull ResourceLocation getAnimationLocation(T animatable);

    public void animate(T animatable, float partialTicks, boolean updateTimer) {
        AzProfiler.begin(AzProfileStage.ANIMATE, animatable);

        // AzureLib 3.2.2 (ported Oct 31) - Molang variable.* reads and writes go to this animatable's own scope while
        // it
        // animates, so two entities playing the same clip never share a v.* value.
        var previousScope = MolangScope.setCurrent(this.currentContext.molangScope());

        // ⚠ Kept INSIDE animate (not split into a helper): avp_human's cryo pose mixin redirects the
        // setCustomAnimations call by its location in this method.
        try {
            this.currentContext.setAnimatable(animatable);

            var boneCache = this.currentContext.boneCache();
            var timer = this.currentContext.timer();

            if (updateTimer) {
                timer.tick();
            }

            // AzureLib 3.2.3 (ported Oct 31) - take last frame's procedural offsets back out before anything reads or
            // animates the bones.
            AzPoseLayer<T> poseLayer = this.currentContext.poseLayer();
            var posing = poseModifiersSuspended == 0;

            if (posing) {
                poseLayer.restore();
            }

            if (!hasAnimated) {
                hasAnimated = true;
                onFirstAnimate(animatable);
            }

            AzProfiler.begin(AzProfileStage.MOLANG_SETUP, animatable);
            preAnimationSetup(animatable, timer.getAnimTime(), partialTicks);
            AzProfiler.end(AzProfileStage.MOLANG_SETUP);

            if (!boneCache.isEmpty()) {
                for (int i = 0, n = updateHooks.size(); i < n; i++) {
                    updateHooks.get(i).beforeControllers(animatable, partialTicks);
                }

                for (var track : animationTrackContainer.getAll()) {
                    AzProfiler.begin(AzProfileStage.TRACK_UPDATE, track);
                    track.update();
                    AzProfiler.end(AzProfileStage.TRACK_UPDATE);
                }

                this.reloadAnimations = false;

                AzProfiler.begin(AzProfileStage.BONE_UPDATE, animatable);
                boneCache.update(this.currentContext);
                AzProfiler.end(AzProfileStage.BONE_UPDATE);
            }

            AzProfiler.begin(AzProfileStage.CUSTOM_ANIMATIONS, animatable);
            setCustomAnimations(animatable, partialTicks);
            AzProfiler.end(AzProfileStage.CUSTOM_ANIMATIONS);

            AzPoseModifierRegistry<T> modifiers = getPoseModifiers();

            if (posing && !boneCache.isEmpty() && !modifiers.isEmpty()) {
                AzProfiler.begin(AzProfileStage.POSE_MODIFIERS, animatable);
                poseLayer.apply(modifiers, animatable, partialTicks);
                AzProfiler.end(AzProfileStage.POSE_MODIFIERS);
            }
        } finally {
            MolangScope.setCurrent(previousScope);
            // Oct 31 - the shared query context must not keep this animatable (and its level) alive after the pass.
            com.blib.internal.client.animation.molang.AzMolangQueryContext.INSTANCE.release();
        }

        AzProfiler.end(AzProfileStage.ANIMATE);
    }

    public void animate(T animatable, float partialTicks) {
        this.animate(animatable, partialTicks, true);
    }

    /**
     * Apply transformations and settings prior to acting on any animation-related functionality.
     *
     * @param animatable   The animatable being animated.
     * @param animTime     Animation time in seconds.
     * @param partialTicks The partial tick for smooth animations.
     */
    protected void preAnimationSetup(T animatable, double animTime, float partialTicks) {
        applyMolangQueries(animatable, animTime, partialTicks);
    }

    /**
     * Handles MoLang queries with support for partial ticks.
     *
     * @param animatable   The animatable being animated.
     * @param animTime     Animation time in seconds.
     * @param partialTicks The partial tick for smooth animations.
     */
    protected void applyMolangQueries(T animatable, double animTime, float partialTicks) {
        var level = Minecraft.getInstance().level;

        if (level == null) {
            return;
        }

        this.molangAnimTime = animTime;
        this.molangPartialTicks = partialTicks;
        LIFE_TIME_REF.setMemoized(lifetimeSupplier);
        ACTOR_COUNT_REF.setMemoized(actorCountSupplier);
        TIME_OF_DAY_REF.setMemoized(timeOfDaySupplier);
        MOON_PHASE_REF.setMemoized(moonPhaseSupplier);
        MOON_BRIGHTNESS_REF.setMemoized(moonBrightnessSupplier);
        DAY_REF.setMemoized(daySupplier);
        TIME_STAMP_REF.setMemoized(timeStampSupplier);
        FRAME_ALPHA_REF.setMemoized(frameAlphaSupplier);
        CLIENT_MAX_RENDER_DISTANCE_REF.setMemoized(clientMaxRenderDistanceSupplier);

        AzMolangQueryContext.INSTANCE.bind(null, partialTicks);
    }

    /**
     * Sets custom animations for the given animatable object. This method is used to define and configure specific
     * animations unique to the context of the animatable and the current render state.
     *
     * @param animatable   The object for which custom animations are being set.
     * @param partialTicks The partial tick time used for interpolating animations smoothly between frames.
     */
    @SuppressWarnings("unused")
    public void setCustomAnimations(T animatable, float partialTicks) {}

    /**
     * AzureLib 3.2.3 (ported Oct 31) - registers this animator's procedural pose modifiers. Called once, before the
     * first {@link #animate}. Modifiers run every frame after the tracks, the bone reset pass and
     * {@link #setCustomAnimations}, in priority order, and their writes are undone before the next frame's tracks run.
     *
     * <pre>{@code
     *
     * protected void registerPoseModifiers(AzPoseModifierRegistry<MyEntity> modifiers) {
     *     modifiers.add("look", AzPoseModifiers.headLook("neck", "head"));
     *     modifiers.add("tail", AzPoseModifiers.spring("tail")).priority(100);
     * }
     * }</pre>
     *
     * @param modifiers the registry to add to
     * @see AzPoseModifiers
     * @see AzAnimationContext#poseLayer()
     */
    protected void registerPoseModifiers(AzPoseModifierRegistry<T> modifiers) {}

    /**
     * Oct 31 - turns the pose modifier pass off for the length of {@code pass}. A render layer that swaps a different
     * model into the bone cache and animates it again ({@code AzRenderLayer#reRenderWithBakedModel}) must not run the
     * modifiers on that model: the pose layer would re-bind to it, drop every modifier's state (spring tails would
     * reset each frame) and stop restoring the main model's bones. The swapped-in model gets the tracks' pose only.
     *
     * @param pass the work to run with pose modifiers off
     */
    public void suspendPoseModifiers(Runnable pass) {
        poseModifiersSuspended++;

        try {
            pass.run();
        } finally {
            poseModifiersSuspended--;
        }
    }

    /**
     * AzureLib 3.2.3 (ported Oct 31) - runs {@code action} for every animator linked to the current animatable (see
     * {@link AzAnimationContext#linkedAnimators()}). Animation commands use it to reach animated overlay layers.
     *
     * @param action what to do with each linked animator
     */
    public void forEachLinkedAnimator(java.util.function.Consumer<AzAnimator<?, T>> action) {
        var context = this.currentContext;

        if (context == null) {
            return;
        }

        var linked = context.linkedAnimators();

        for (int i = 0, n = linked.size(); i < n; i++) {
            action.accept(linked.get(i));
        }
    }

    /**
     * Adds a hook that runs every frame just before the tracks update, e.g. to set track weights from per-frame values.
     * Blend spaces register themselves here. Hooks run in the order they were added.
     *
     * @param hook the hook to add; adding the same hook twice does nothing
     */
    public void addUpdateHook(AzAnimatorUpdateHook<T> hook) {
        if (!updateHooks.contains(hook)) {
            updateHooks.add(hook);
        }
    }

    /**
     * Removes a hook added with {@link #addUpdateHook}.
     *
     * @param hook the hook to remove
     */
    public void removeUpdateHook(AzAnimatorUpdateHook<T> hook) {
        updateHooks.remove(hook);
    }

    /**
     * This animator's pose modifiers. Addons and compat code may add or remove modifiers here at any time; changes
     * apply from the next frame.
     *
     * @return the pose modifier registry
     */
    public AzPoseModifierRegistry<T> getPoseModifiers() {
        if (!poseModifiersRegistered) {
            poseModifiersRegistered = true;
            registerPoseModifiers(poseModifiers);
        }

        return poseModifiers;
    }

    /**
     * Get the baked animation object used for rendering from the given resource path
     */
    public @Nullable AzBakedAnimation getAnimation(T animatable, String name) {
        var location = getAnimationLocation(animatable);
        var bakedAnimations = AzBakedAnimationCache.getInstance().getOrNull(location);

        if (bakedAnimations == null) {
            warnOnce(location, name);

            return null;
        }

        return bakedAnimations.getAnimation(name);
    }

    private static void warnOnce(ResourceLocation location, String name) {
        if (MISSING_ANIMATION_FILES.add(location)) {
            BLib.LOGGER.warn(
                "No baked animations for '{}' (first missing clip: '{}'). The file is absent, misnamed, or failed"
                    + " to parse. Animations from it will not play.",
                location,
                name
            );
        }
    }

    /**
     * AzureLib 3.1.22 (ported Oct 17) - called once, on this animator's first {@link #animate} call, right before its
     * tracks update for the first time. Commands dispatched here (e.g. {@code MY_COMMAND.dispatchForEntity(entity)})
     * apply on that same frame, so the animatable never renders a frame without an animation.
     * <p>
     * This is the place to start a spawn or initial animation. Prefer it over keying off {@code tickCount == 0}: a
     * freshly spawned entity is not guaranteed to render before its first client tick. It runs again if the animator is
     * replaced, e.g. when the model changes on a resource reload.
     * </p>
     *
     * @param animatable the animatable being animated for the first time
     */
    protected void onFirstAnimate(T animatable) {}

    public AzAnimationContext<T> context() {
        return currentContext;
    }

    public AzAnimationTrackContainer<T> getAnimationTrackContainer() {
        return animationTrackContainer;
    }
}
