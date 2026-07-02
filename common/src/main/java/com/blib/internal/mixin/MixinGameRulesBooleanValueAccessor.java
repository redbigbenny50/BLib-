package com.blib.internal.mixin;

import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRules.BooleanValue.class)
public interface MixinGameRulesBooleanValueAccessor {

    @Invoker("create")
    static GameRules.Type<GameRules.BooleanValue> blib$create(boolean defaultValue) {
        throw new AssertionError();
    }
}
