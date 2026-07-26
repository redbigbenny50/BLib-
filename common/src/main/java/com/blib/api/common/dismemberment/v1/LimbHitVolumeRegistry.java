package com.blib.api.common.dismemberment.v1;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Runtime hit-volume fallback for definitions whose structural fields are supplied by JSON. */
public final class LimbHitVolumeRegistry {

    private static final Map<ResourceLocation, List<LimbHitVolume>> VOLUMES = new ConcurrentHashMap<>();

    private static final Map<ResourceLocation, List<LimbHitVolume>> MODEL_SPACE_VOLUMES = new ConcurrentHashMap<>();

    public static void register(ResourceLocation limbId, List<LimbHitVolume> volumes) {
        VOLUMES.put(limbId, List.copyOf(volumes));
    }

    public static List<LimbHitVolume> resolve(LimbDefinition definition) {
        return definition.hitVolumes().isEmpty()
            ? VOLUMES.getOrDefault(definition.id(), List.of())
            : definition.hitVolumes();
    }

    /**
     * Registers bounds in model/world blocks instead of fractions of the vanilla entity dimensions. This is intended
     * for models whose visible anatomy extends outside their coarse vanilla collision box.
     */
    public static void registerModelSpace(ResourceLocation limbId, List<LimbHitVolume> volumes) {
        MODEL_SPACE_VOLUMES.put(limbId, List.copyOf(volumes));
    }

    public static List<LimbHitVolume> resolve(LimbDefinition definition, LivingEntity entity) {
        var modelSpace = MODEL_SPACE_VOLUMES.get(definition.id());
        if (modelSpace == null) {
            return resolve(definition);
        }
        var width = Math.max(0.001D, entity.getBbWidth());
        var height = Math.max(0.001D, entity.getBbHeight());
        var scale = entity.getScale();
        return modelSpace.stream()
            .map(
                volume -> new LimbHitVolume(
                    volume.minX() * scale / width,
                    volume.minY() * scale / height,
                    volume.minZ() * scale / width,
                    volume.maxX() * scale / width,
                    volume.maxY() * scale / height,
                    volume.maxZ() * scale / width
                )
            )
            .toList();
    }

    private LimbHitVolumeRegistry() {}
}
