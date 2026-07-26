package com.blib.api.common.dismemberment.v1;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.AABB;

/**
 * Entity-local anatomical volume. X/Z coordinates are multiples of entity width relative to its center; Y coordinates
 * are multiples of entity height relative to its feet.
 */
public record LimbHitVolume(
    double minX,
    double minY,
    double minZ,
    double maxX,
    double maxY,
    double maxZ
) {

    public static final Codec<LimbHitVolume> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            Codec.DOUBLE.fieldOf("min_x").forGetter(LimbHitVolume::minX),
            Codec.DOUBLE.fieldOf("min_y").forGetter(LimbHitVolume::minY),
            Codec.DOUBLE.fieldOf("min_z").forGetter(LimbHitVolume::minZ),
            Codec.DOUBLE.fieldOf("max_x").forGetter(LimbHitVolume::maxX),
            Codec.DOUBLE.fieldOf("max_y").forGetter(LimbHitVolume::maxY),
            Codec.DOUBLE.fieldOf("max_z").forGetter(LimbHitVolume::maxZ)
        ).apply(instance, LimbHitVolume::new)
    );

    public LimbHitVolume {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Limb hit volume minimums must not exceed maximums");
        }
    }

    public AABB bounds() {
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
