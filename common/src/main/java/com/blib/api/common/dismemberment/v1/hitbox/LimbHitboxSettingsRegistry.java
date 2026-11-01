package com.blib.api.common.dismemberment.v1.hitbox;

import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.Optional;

/**
 * Holds the {@code blib_hitboxes} data files, keyed by model id ({@code <namespace>:<model>}).
 * <p>
 * Replaced as a whole on every data-pack reload, so readers never see a half-loaded set. Lookups are a couple of map
 * reads, and {@link GeoHitboxProfile} caches its volumes per tick, so this costs nothing measurable.
 * </p>
 */
public final class LimbHitboxSettingsRegistry {

    private static volatile Map<ResourceLocation, Map<String, LimbHitboxSettings>> files = Map.of();

    /** Called by the data loader; replaces every file at once. */
    public static void replaceAll(Map<ResourceLocation, Map<String, LimbHitboxSettings>> loaded) {
        files = Map.copyOf(loaded);
    }

    /**
     * The settings for one limb of one model, or empty when the model has no file or the file has no entry for it.
     * <p>
     * An entry matches a limb when its name equals the limb id's path, or when the path ends with {@code _<name>} (so
     * {@code head} matches {@code avp_alien:drone_head}). When several entries match, the longest name wins, so
     * {@code left_arm} beats {@code arm}.
     * </p>
     */
    public static Optional<LimbHitboxSettings> find(ResourceLocation modelId, ResourceLocation limbId) {
        var entries = files.get(modelId);
        if (entries == null || entries.isEmpty()) {
            return Optional.empty();
        }
        var path = limbId.getPath();
        LimbHitboxSettings best = null;
        var bestLength = -1;
        for (var entry : entries.entrySet()) {
            var name = entry.getKey();
            if (LimbNameMatcher.matches(path, name) && name.length() > bestLength) {
                best = entry.getValue();
                bestLength = name.length();
            }
        }
        return Optional.ofNullable(best);
    }

    /** True when a model has a data file at all; used by the F3+B overlay. */
    public static boolean hasFile(ResourceLocation modelId) {
        return files.containsKey(modelId);
    }

    private LimbHitboxSettingsRegistry() {}
}
