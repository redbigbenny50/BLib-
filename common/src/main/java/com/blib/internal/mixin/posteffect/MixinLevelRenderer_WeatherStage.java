package com.blib.internal.mixin.posteffect;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.blib.internal.client.posteffect.BLibWeatherStage;

/**
 * Marks the window in which rain and snow are drawing. See {@link BLibWeatherStage}.
 * <p>
 * ⚠ The per-frame FAIL-SAFE reset for this flag lives in {@code MixinLevelRenderer_SkyStage}, alongside the sky one — a
 * stuck-true flag here would suppress auxiliary writes for EVERY particle for the rest of the frame, silently
 * unclassifying smoke, flame and the rest.
 * <p>
 * {@code require = 0} matches the sky pair: if a future Minecraft renames this, the flag stays false and weather is
 * classified as an ordinary particle again — the artifact returns, which is cosmetic, rather than anything breaking.
 */
@Mixin(LevelRenderer.class)
public abstract class MixinLevelRenderer_WeatherStage {

    @Inject(method = "renderSnowAndRain", at = @At("HEAD"), require = 0)
    private void blib$beginWeatherStage(
        LightTexture lightTexture,
        float partialTick,
        double camX,
        double camY,
        double camZ,
        CallbackInfo ci
    ) {
        BLibWeatherStage.begin();
    }

    @Inject(method = "renderSnowAndRain", at = @At("RETURN"), require = 0)
    private void blib$endWeatherStage(
        LightTexture lightTexture,
        float partialTick,
        double camX,
        double camY,
        double camZ,
        CallbackInfo ci
    ) {
        BLibWeatherStage.end();
    }
}
