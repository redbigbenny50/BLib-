package com.blib.mod.common.registry.init;

import com.blib.internal.mixin.MixinGameRulesBooleanValueAccessor;
import com.blib.internal.mixin.MixinGameRulesAccessor;
import net.minecraft.world.level.GameRules;

public final class BLibGameRules {

    public static final GameRules.Key<GameRules.BooleanValue> TERRITORY_BUILD_PROTECTION = MixinGameRulesAccessor
        .blib$register(
            "blibTerritoryBuildProtection",
            GameRules.Category.MISC,
            MixinGameRulesBooleanValueAccessor.blib$create(true)
        );

    private BLibGameRules() {}

    public static void initialize() {
        // Loads the static game rule registration.
    }
}
