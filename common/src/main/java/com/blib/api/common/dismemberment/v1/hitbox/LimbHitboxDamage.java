package com.blib.api.common.dismemberment.v1.hitbox;

import com.blib.api.common.dismemberment.v1.Dismemberable;
import com.blib.api.common.dismemberment.v1.LimbDefinitionRegistry;
import com.blib.api.common.dismemberment.v1.LimbDismemberer;
import net.minecraft.world.entity.LivingEntity;

/** Applies one confirmed firearm hit to normal health and, where applicable, to a limb pool. */
public final class LimbHitboxDamage {

    public static Result apply(LivingEntity entity, LimbHitboxRegistry.Hit hit, float actualHealthDamage) {
        var volume = hit.volume();
        var limbId = volume.limbId();
        if (!volume.hasLimbDamagePool() || !(entity instanceof Dismemberable dismemberable)) {
            return new Result(0.0F, 0.0F, false);
        }
        if (LimbDefinitionRegistry.getDefinition(entity.getType(), limbId) == null) {
            return new Result(0.0F, 0.0F, false);
        }

        var accumulated = dismemberable.getDismembermentManager()
            .addLimbDamage(limbId, actualHealthDamage * volume.limbDamageMultiplier());
        var detached = accumulated >= volume.limbDamageThreshold()
            && LimbDismemberer.detach(entity, limbId).isPresent();
        return new Result(accumulated, volume.limbDamageThreshold(), detached);
    }

    public record Result(float limbDamage, float threshold, boolean detached) {}

    private LimbHitboxDamage() {}
}
