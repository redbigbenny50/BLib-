package com.blib.internal.client.posteffect;

import org.jetbrains.annotations.ApiStatus;

import com.blib.internal.client.service.BLibInternalClientServices;
import com.blib.mod.BLib;

/**
 * Loader-agnostic facade for "is Sodium loaded?".
 * <p>
 * Sodium replaces terrain rendering wholesale: it ships its own chunk shaders and compiles them through its own loader,
 * never touching {@code com.mojang.blaze3d.shaders.Program}. {@link BLibEntityShaderPatcher} hooks that vanilla compile
 * path and selects targets by shader name, so with Sodium installed the six vanilla terrain shaders are never compiled
 * and terrain fragments never write the auxiliary attachments. Entities are unaffected, because Sodium does not replace
 * entity rendering — which is why the symptom is "mobs read correctly, the world does not".
 * <p>
 * Unlike Iris, this is not a reason to shut the pipeline down; everything except terrain still works. See
 * {@link BLibTerrainMaskFixup} for what is done about the terrain half.
 * <p>
 * The result is cached after the first call so hot-path checks don't re-resolve the loader's mod list per frame.
 */
@ApiStatus.Internal
public final class BLibSodiumCompat {

    /**
     * Escape hatch for packs where the fixup pass misbehaves. Set {@code -Dblib.terrainMaskFixup=false} to disable it
     * and fall back to leaving terrain unclassified.
     */
    private static final String FIXUP_ENABLED_PROPERTY = "blib.terrainMaskFixup";

    private static volatile boolean checked;

    private static volatile boolean sodiumActive;

    private BLibSodiumCompat() {
        throw new UnsupportedOperationException();
    }

    public static boolean isSodiumActive() {
        if (!checked) {
            sodiumActive = BLibInternalClientServices.SODIUM_COMPAT.isSodiumActive();
            checked = true;

            if (sodiumActive) {
                BLib.LOGGER.info(
                    "[BLib] Sodium detected; terrain writes no auxiliary attachments, enabling the depth-gated terrain mask fixup."
                );
            }
        }

        return sodiumActive;
    }

    /** True when the fixup pass should run: Sodium present, no shader mod owning the pipeline, and not opted out. */
    public static boolean isTerrainMaskFixupEnabled() {
        return isSodiumActive()
            && !BLibIrisCompat.isShaderModActive()
            && !"false".equalsIgnoreCase(System.getProperty(FIXUP_ENABLED_PROPERTY));
    }
}
