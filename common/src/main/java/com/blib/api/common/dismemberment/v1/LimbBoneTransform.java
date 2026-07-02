package com.blib.api.common.dismemberment.v1;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Optional;

/**
 * Optional per-bone static transform applied while rendering a detached limb.
 */
public record LimbBoneTransform(
    Optional<Vec3> position,
    Optional<Vec3> rotation,
    Optional<Vec3> scale
) {

    public LimbBoneTransform {
        Objects.requireNonNull(position, "LimbBoneTransform position must not be null");
        Objects.requireNonNull(rotation, "LimbBoneTransform rotation must not be null");
        Objects.requireNonNull(scale, "LimbBoneTransform scale must not be null");
    }

    public static LimbBoneTransform rotation(Vec3 rotation) {
        return new LimbBoneTransform(Optional.empty(), Optional.of(rotation), Optional.empty());
    }

    public static final Codec<LimbBoneTransform> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            Vec3.CODEC.optionalFieldOf("position").forGetter(LimbBoneTransform::position),
            Vec3.CODEC.optionalFieldOf("rotation").forGetter(LimbBoneTransform::rotation),
            Vec3.CODEC.optionalFieldOf("scale").forGetter(LimbBoneTransform::scale)
        ).apply(instance, LimbBoneTransform::new)
    );
}
