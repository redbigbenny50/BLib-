package com.blib.internal.common.dismemberment;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import com.blib.api.common.dismemberment.v1.hitbox.LimbHitboxSettings;
import com.blib.api.common.dismemberment.v1.hitbox.LimbHitboxSettingsRegistry;

/**
 * Server-side data-pack reload listener for {@code data/<namespace>/blib_hitboxes/<model>.json}.
 * <p>
 * The file id ({@code <namespace>:<model>}) is the model the settings apply to, the same id a {@code GeoHitboxProfile}
 * is loaded under. Shape:
 * </p>
 *
 * <pre>
 * {
 *   "replace": false,
 *   "limbs": {
 *     "head":     { "damage_multiplier": 2.0, "padding": 0.08, "tags": ["weak_point"] },
 *     "left_arm": { "sever_damage": 25.0 }
 *   }
 * }
 * </pre>
 * <p>
 * Packs stack: a later pack's entries are added to (or override) an earlier pack's, unless it sets
 * {@code "replace": true}. A broken file is logged and skipped; it never stops the rest from loading.
 * </p>
 */
public final class LimbHitboxSettingsLoader extends SimplePreparableReloadListener<Map<ResourceLocation, Map<String, LimbHitboxSettings>>> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LimbHitboxSettingsLoader.class);

    private static final String DIRECTORY = "blib_hitboxes";

    private record DecodedFile(
        boolean replace,
        Map<String, LimbHitboxSettings> limbs
    ) {

        static final Codec<DecodedFile> CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                Codec.BOOL.optionalFieldOf("replace", false).forGetter(DecodedFile::replace),
                Codec.unboundedMap(Codec.STRING, LimbHitboxSettings.CODEC)
                    .optionalFieldOf("limbs", Map.of())
                    .forGetter(DecodedFile::limbs)
            ).apply(instance, DecodedFile::new)
        );
    }

    @Override
    protected Map<ResourceLocation, Map<String, LimbHitboxSettings>> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        var converter = FileToIdConverter.json(DIRECTORY);
        var result = new LinkedHashMap<ResourceLocation, Map<String, LimbHitboxSettings>>();
        for (var entry : converter.listMatchingResourceStacks(resourceManager).entrySet()) {
            var fileLocation = entry.getKey();
            var modelId = converter.fileToId(fileLocation);
            var merged = new LinkedHashMap<String, LimbHitboxSettings>();
            for (var resource : entry.getValue()) {
                try (var reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
                    var json = JsonParser.parseReader(reader);
                    var decoded = DecodedFile.CODEC.parse(JsonOps.INSTANCE, json);
                    var file = decoded.resultOrPartial(
                        error -> LOGGER.error("[BLib] Bad limb hitbox file {} in {}: {}", fileLocation, resource.sourcePackId(), error)
                    );
                    if (file.isEmpty()) {
                        continue;
                    }
                    if (file.get().replace()) {
                        merged.clear();
                    }
                    merged.putAll(file.get().limbs());
                } catch (Exception exception) {
                    LOGGER.error("[BLib] Could not read limb hitbox file {} in {}", fileLocation, resource.sourcePackId(), exception);
                }
            }
            result.put(modelId, Map.copyOf(merged));
        }
        return result;
    }

    @Override
    protected void apply(
        Map<ResourceLocation, Map<String, LimbHitboxSettings>> loaded,
        ResourceManager resourceManager,
        ProfilerFiller profiler
    ) {
        LimbHitboxSettingsRegistry.replaceAll(loaded);
        if (!loaded.isEmpty()) {
            LOGGER.info("[BLib] Loaded limb hitbox settings for {} model(s)", loaded.size());
        }
    }
}
