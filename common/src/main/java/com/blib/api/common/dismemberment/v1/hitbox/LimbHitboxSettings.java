package com.blib.api.common.dismemberment.v1.hitbox;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Optional;

/**
 * Data-file overrides for one limb's hitbox, read from {@code data/<namespace>/blib_hitboxes/<model>.json}.
 * <p>
 * Every field is optional. A field that is left out keeps the value the mod set in code, so a file only needs the
 * numbers you actually want to change.
 * </p>
 * <ul>
 * <li>{@code damage_multiplier} - multiplies normal health damage when this limb is hit (2.0 is a headshot).</li>
 * <li>{@code limb_damage_multiplier} - how much of the hit counts towards severing the limb.</li>
 * <li>{@code sever_damage} - damage the limb absorbs before it comes off. 0 means it can never be shot off.</li>
 * <li>{@code padding} - extra size added around every box of the limb, in blocks.</li>
 * <li>{@code tags} - free labels such as {@code weak_point}; shown in F3+B and readable by gameplay code.</li>
 * </ul>
 */
public record LimbHitboxSettings(
    Optional<Float> damageMultiplier,
    Optional<Float> limbDamageMultiplier,
    Optional<Float> severDamage,
    Optional<Double> padding,
    Optional<List<String>> tags
) {

    public static final LimbHitboxSettings NONE = new LimbHitboxSettings(
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty()
    );

    public static final Codec<LimbHitboxSettings> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("damage_multiplier").forGetter(LimbHitboxSettings::damageMultiplier),
            Codec.FLOAT.optionalFieldOf("limb_damage_multiplier").forGetter(LimbHitboxSettings::limbDamageMultiplier),
            Codec.FLOAT.optionalFieldOf("sever_damage").forGetter(LimbHitboxSettings::severDamage),
            Codec.DOUBLE.optionalFieldOf("padding").forGetter(LimbHitboxSettings::padding),
            Codec.STRING.listOf().optionalFieldOf("tags").forGetter(LimbHitboxSettings::tags)
        ).apply(instance, LimbHitboxSettings::new)
    );
}
