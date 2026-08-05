package com.blib.api.common.dismemberment.v1.hitbox;

import com.blib.api.common.dismemberment.v1.DismembermentManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Registry and server raycast entry point for additive limb hurtboxes. */
public final class LimbHitboxRegistry {

    private static final Map<ResourceLocation, LimbHitboxProvider> PROVIDERS = new ConcurrentHashMap<>();

    public static void register(ResourceLocation entityTypeId, LimbHitboxProvider provider) {
        PROVIDERS.put(entityTypeId, provider);
    }

    public static void register(EntityType<?> entityType, LimbHitboxProvider provider) {
        register(BuiltInRegistries.ENTITY_TYPE.getKey(entityType), provider);
    }

    public static Optional<Hit> findNearest(LivingEntity entity, Vec3 rayStart, Vec3 rayEnd) {
        return getHitboxes(entity)
            .stream()
            .map(volume -> volume.clip(rayStart, rayEnd).map(location -> new Hit(volume, location)).orElse(null))
            .filter(java.util.Objects::nonNull)
            .min(Comparator.comparingDouble(hit -> rayStart.distanceToSqr(hit.location())));
    }

    public static List<LimbHitboxVolume> getHitboxes(LivingEntity entity) {
        var provider = PROVIDERS.get(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
        if (provider == null) {
            return List.of();
        }
        return provider.getHitboxes(entity)
            .stream()
            .filter(volume -> !DismembermentManager.isDetached(entity, volume.limbId()))
            .toList();
    }

    public record Hit(LimbHitboxVolume volume, Vec3 location) {}

    private LimbHitboxRegistry() {}
}
