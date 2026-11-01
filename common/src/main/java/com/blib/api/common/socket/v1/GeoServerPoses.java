package com.blib.api.common.socket.v1;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import com.blib.api.common.dismemberment.v1.hitbox.GeoHitboxProfile;
import com.blib.api.common.dismemberment.v1.hitbox.GeoLimbHitboxes;

/**
 * Where an entity type's model is on the SERVER, for bone sockets ({@link AzBoneSockets}).
 * <p>
 * The client knows every bone's pose because it renders the model. The server never does, so BLib reads the same geo
 * and animation files the client uses and poses them itself - the evaluator behind BLib's server-side limb hitboxes.
 * Nothing is synced. A mob gets server-side sockets in one of two ways:
 * </p>
 * <ul>
 * <li>It already has Geo limb hitboxes ({@link GeoLimbHitboxes#register}): nothing more to do, the sockets use the same
 * model and the same animation choice.</li>
 * <li>Any other mob: one line, {@link #registerModel}, naming its geo file and - optionally - its animation file and
 * how to tell which clip is playing. With no animation choice, sockets use the model's rest pose, which is exact for
 * anything that does not move relative to the body.</li>
 * </ul>
 */
public final class GeoServerPoses {

    /** The model and the clip it is visibly playing. {@code selector} may be {@code null} for the rest pose. */
    public record Entry(
        GeoHitboxProfile profile,
        @Nullable GeoLimbHitboxes.PoseSelector selector
    ) {

        public Entry {
            Objects.requireNonNull(profile, "profile");
        }

        /** The selected clip, or the rest pose. Living entities only; anything else uses the rest pose. */
        public GeoLimbHitboxes.PoseSelector.Pose pose(Entity entity) {
            if (selector != null && entity instanceof LivingEntity living) {
                return selector.select(living, profile);
            }
            return REST;
        }
    }

    private static final GeoLimbHitboxes.PoseSelector.Pose REST = new GeoLimbHitboxes.PoseSelector.Pose(null, 0.0D);

    private static final Map<ResourceLocation, Entry> BY_TYPE = new ConcurrentHashMap<>();

    /**
     * Registers an already-loaded model for an entity type. {@link GeoLimbHitboxes#register} calls this for you.
     *
     * @param selector which clip is playing, or {@code null} for the rest pose
     */
    public static void register(ResourceLocation entityTypeId, GeoHitboxProfile profile, @Nullable GeoLimbHitboxes.PoseSelector selector) {
        BY_TYPE.put(Objects.requireNonNull(entityTypeId, "entityTypeId"), new Entry(profile, selector));
    }

    /**
     * Loads a model for an entity type that has no limb hitboxes.
     *
     * @param anchor        any class from the mod that ships the model, so its jar is the one read
     * @param geometryPath  e.g. {@code /assets/mymod/geo/entity/thing.geo.json}
     * @param animationPath e.g. {@code /assets/mymod/animations/entity/thing.animation.json}, or {@code null}
     * @param selector      which clip is playing, or {@code null} for the rest pose
     */
    public static void registerModel(
        ResourceLocation entityTypeId,
        Class<?> anchor,
        String geometryPath,
        @Nullable String animationPath,
        @Nullable GeoLimbHitboxes.PoseSelector selector
    ) {
        var profile = GeoHitboxProfile.load(entityTypeId, anchor, geometryPath, animationPath, Map.of());
        register(entityTypeId, profile, selector);
    }

    /** The model registered for this entity's type, or {@code null}. */
    public static @Nullable Entry find(Entity entity) {
        return BY_TYPE.get(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
    }

    private GeoServerPoses() {}
}
