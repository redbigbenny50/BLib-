package com.blib.api.common.dismemberment.v1.hitbox;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * One-call registration of bone-accurate limb hitboxes for any mob with a Bedrock geo model.
 * <p>
 * The mod supplies a {@link GeoHitboxProfile} and a {@link PoseSelector} that says which authored animation (and how
 * far into it) the entity is playing right now. Volumes are cached per entity per server tick, so several shots or
 * players in the same tick do not rebuild the pose.
 * </p>
 *
 * <pre>
 * var profile = GeoHitboxProfile.load(
 *     id,
 *     MyMod.class,
 *     "/assets/mymod/geo/entity/x.geo.json",
 *     "/assets/mymod/animations/entity/x.animation.json",
 *     Map.of("head", new LimbRule(headId, 2F, 0F, 0F, 0.08D))
 * );
 * GeoLimbHitboxes.register(MyEntities.X_ID, profile, entity -> new PoseSelector.Pose("idle", entity.tickCount / 20.0));
 * </pre>
 */
public final class GeoLimbHitboxes {

    private static final Map<LivingEntity, Cached> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    private static final Map<LivingEntity, Cached> CLIENT_CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    /** Chooses the authored animation and time (in seconds) that the entity is visibly playing. */
    @FunctionalInterface
    public interface PoseSelector {

        Pose select(LivingEntity entity, GeoHitboxProfile profile);

        /**
         * The clip, and - Oct 31 - any turn the renderer gives the whole model (see
         * {@link GeoHitboxProfile#volumes(LivingEntity, String, double, float, float, float)}).
         */
        record Pose(
            String animation,
            double timeSeconds,
            float pitchDegrees,
            float rollDegrees,
            float pivotY
        ) {

            /** A clip with the model upright, as before. */
            public Pose(String animation, double timeSeconds) {
                this(animation, timeSeconds, 0.0F, 0.0F, 0.0F);
            }
        }
    }

    public static void register(ResourceLocation entityTypeId, GeoHitboxProfile profile, PoseSelector selector) {
        Objects.requireNonNull(profile, "profile");
        Objects.requireNonNull(selector, "selector");
        LimbHitboxRegistry.register(entityTypeId, entity -> volumes(entity, profile, selector));
        // Oct 31 - the same model and clip choice place this mob's bone sockets on the server.
        com.blib.api.common.socket.v1.GeoServerPoses.register(entityTypeId, profile, selector);
    }

    /** The cached volumes for this tick, building them if needed. Safe to call from gameplay code. */
    public static List<LimbHitboxVolume> volumes(LivingEntity entity, GeoHitboxProfile profile, PoseSelector selector) {
        var gameTime = entity.level().getGameTime();
        // Oct 31 - one cache per side. Entity equality is by network id, and in singleplayer the client copy of a mob
        // has
        // the same id as the server one, so a shared map let each side read volumes the other side had worked out.
        var cache = entity.level().isClientSide ? CLIENT_CACHE : CACHE;
        var cached = cache.get(entity);
        if (cached != null && cached.gameTime() == gameTime && cached.profile() == profile) {
            return cached.volumes();
        }
        var pose = selector.select(entity, profile);
        var volumes = List.copyOf(
            profile.volumes(entity, pose.animation(), pose.timeSeconds(), pose.pitchDegrees(), pose.rollDegrees(), pose.pivotY())
        );
        cache.put(entity, new Cached(gameTime, profile, volumes));
        return volumes;
    }

    private record Cached(
        long gameTime,
        GeoHitboxProfile profile,
        List<LimbHitboxVolume> volumes
    ) {}

    private GeoLimbHitboxes() {}
}
