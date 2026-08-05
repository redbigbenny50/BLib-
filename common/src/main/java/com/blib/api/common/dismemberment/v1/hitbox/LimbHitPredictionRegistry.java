package com.blib.api.common.dismemberment.v1.hitbox;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.Optional;

/** Optional bridge from client rendering to weapon input. Dedicated servers never install a provider. */
public final class LimbHitPredictionRegistry {

    private static volatile LimbHitPredictionProvider provider;

    public static void registerClientProvider(LimbHitPredictionProvider clientProvider) {
        provider = Objects.requireNonNull(clientProvider, "clientProvider");
    }

    public static Optional<LimbHitPrediction> findNearest(Level level, Vec3 rayStart, Vec3 rayEnd) {
        var current = provider;
        return current == null ? Optional.empty() : current.findNearest(level, rayStart, rayEnd);
    }

    private LimbHitPredictionRegistry() {}
}
