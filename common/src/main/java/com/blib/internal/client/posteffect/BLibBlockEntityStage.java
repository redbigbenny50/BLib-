package com.blib.internal.client.posteffect;

import org.jetbrains.annotations.ApiStatus;

/**
 * Whether a BLOCK ENTITY is currently being rendered.
 * <h2>Why this exists</h2> Vanilla draws block entities — chests, beds, signs, banners, shulker boxes, item frames,
 * enchanting tables — through ENTITY render types ({@code Sheets.chestSheet()}, {@code Sheets.bedSheet()} and friends
 * all resolve to {@code RenderType.entityCutout}/{@code entitySolid}). Those shader names sit in the patcher's ENTITY
 * category, so a chest was being stamped with the ENTITY classification and lighting up on electromagnetic vision
 * alongside living things.
 * <p>
 * ⭐ Same problem, same solution, third time today: {@link BLibSkyStage} for the sun-versus-underwater-overlay split and
 * {@link BLibWeatherStage} for rain-versus-smoke. **A shader NAME cannot say what is being drawn — only WHEN it is
 * drawn distinguishes the cases.**
 * <p>
 * ⚠ It is not only cosmetic on EM. Thermal classified furniture as an entity too; it simply landed in the ambient tier
 * and read cold, so nobody noticed a chest being treated as a creature.
 */
@ApiStatus.Internal
public final class BLibBlockEntityStage {

    private static boolean drawing;

    private BLibBlockEntityStage() {
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
