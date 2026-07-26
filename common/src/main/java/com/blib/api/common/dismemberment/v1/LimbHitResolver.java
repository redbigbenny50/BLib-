package com.blib.api.common.dismemberment.v1;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Server-safe ray intersection against attached, posed model cubes. */
public final class LimbHitResolver {

    public record Hit(LimbDefinition definition, Vec3 location, double distance) {}

    public static Optional<Hit> findNearest(LivingEntity entity, Vec3 rayStart, Vec3 rayEnd) {
        if (!(entity instanceof Dismemberable dismemberable)) {
            return Optional.empty();
        }

        var manager = dismemberable.getDismembermentManager();
        Hit nearest = null;

        for (var part : LimbCollisionAdapterRegistry.resolve(entity)) {
            var definition = LimbDefinitionRegistry.getDefinition(entity.getType(), part.limbId());
            if (definition == null || manager.isDetached(definition)) {
                continue;
            }
            var localStart = part.box().toLocal(rayStart);
            var localEnd = part.box().toLocal(rayEnd);
            var half = part.box().halfExtents();
            var localHit = new net.minecraft.world.phys.AABB(-half.x, -half.y, -half.z, half.x, half.y, half.z)
                .clip(localStart, localEnd);
            if (localHit.isEmpty()) {
                continue;
            }
            var worldHit = part.box().toWorld(localHit.get());
            var distance = rayStart.distanceTo(worldHit);
            if (nearest == null || distance < nearest.distance()) {
                nearest = new Hit(definition, worldHit, distance);
            }
        }

        return Optional.ofNullable(nearest);
    }

    private LimbHitResolver() {}
}
