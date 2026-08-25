package com.blib.internal.client.posteffect;

import org.jetbrains.annotations.ApiStatus;

/**
 * Whether the sky stage is currently drawing.
 * <h2>Why this is needed</h2> Vanilla reuses {@code position_tex_color} for three unrelated jobs at different points in
 * the frame: the void plane and the sunrise/sunset gradient during the sky stage, and the full-screen underwater
 * overlay long after terrain and entities have drawn.
 * <p>
 * Those want opposite treatment. During the sky stage it must zero the auxiliary attachments, or undefined writes leak
 * driver garbage into them. Afterwards it must leave them alone, or a full-screen overlay wipes the classification of
 * everything behind it — which is what made every fish and dolphin stop reading as an entity the moment the camera went
 * underwater.
 * <p>
 * ⚠ Classifying by shader NAME cannot separate them: it is the same shader. Removing it from the passthrough list fixed
 * the overlay and immediately turned the void plane into a flat green sheet across the lower sky. The only thing that
 * distinguishes the cases is <em>when</em> the draw happens, which is what this tracks.
 */
@ApiStatus.Internal
public final class BLibSkyStage {

    private static boolean drawing;

    private BLibSkyStage() {
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
