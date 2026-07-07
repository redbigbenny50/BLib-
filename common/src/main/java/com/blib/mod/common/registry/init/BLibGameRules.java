package com.blib.mod.common.registry.init;

import net.minecraft.world.level.GameRules;

import com.blib.internal.mixin.MixinGameRulesAccessor;
import com.blib.internal.mixin.MixinGameRulesBooleanValueAccessor;

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
