package com.blib.api.common.dismemberment.v1;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Registry for deterministic, model-backed limb collision adapters. */
public final class LimbCollisionAdapterRegistry {

    private static final Map<EntityType<?>, LimbCollisionAdapter<?>> ADAPTERS = new ConcurrentHashMap<>();

    private static final Map<ResourceLocation, LimbCollisionAdapter<?>> ID_ADAPTERS = new ConcurrentHashMap<>();

    public static <T extends LivingEntity> void register(EntityType<T> entityType, LimbCollisionAdapter<? super T> adapter) {
        ADAPTERS.put(entityType, adapter);
    }

    /** Registers without forcing a deferred entity holder to bind during mod construction. */
    public static void register(ResourceLocation entityTypeId, LimbCollisionAdapter<?> adapter) {
        ID_ADAPTERS.put(entityTypeId, adapter);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static List<LimbCollisionAdapter.Part> resolve(LivingEntity entity) {
        var adapter = ADAPTERS.get(entity.getType());
        if (adapter == null) {
            adapter = ID_ADAPTERS.get(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()));
        }
        return adapter == null ? List.of() : ((LimbCollisionAdapter) adapter).resolve(entity);
    }

    private LimbCollisionAdapterRegistry() {}
}
