package com.blib.internal.client.posteffect;

import org.jetbrains.annotations.ApiStatus;

/**
 * Whether vanilla's weather pass — rain and snow — is currently drawing.
 * <h2>Why this is needed</h2> {@code LevelRenderer.renderSnowAndRain} draws with
 * {@code GameRenderer::getParticleShader}, so every raindrop and snowflake classifies as PARTICLE alongside genuine
 * particles. PARTICLE is not a neutral answer: the thermal path reads it as {@code 0.80 * emission + blockHeat}, which
 * makes falling water and ice register WARM. The visible result is bright streaks across the sky whenever it rains, and
 * clusters of hot dashes drifting past terrain and mobs.
 * <p>
 * ⚠ Classifying by shader NAME cannot separate them: weather and campfire smoke are the same shader. Only WHEN the draw
 * happens distinguishes the cases, which is what this tracks — the same problem, and the same solution, as
 * {@link BLibSkyStage}.
 * <h2>Why suppression rather than a cold value</h2> Weather is treated as an unpatched overlay while this is set, so
 * whatever is behind a raindrop keeps its own classification. That is what a thermal camera actually shows: rain
 * attenuates the scene behind it, it does not paint over it. Writing a cold value instead would punch drop-shaped holes
 * in every warm body standing in the rain.
 */
@ApiStatus.Internal
public final class BLibWeatherStage {

    private static boolean drawing;

    private BLibWeatherStage() {
        throw new UnsupportedOperationException();
    }

    public static void begin() {
        drawing = true;
    }

    public static void end() {
        drawing = false;
    }

    public static boolean isDrawing() {
        return drawing;
    }
}
