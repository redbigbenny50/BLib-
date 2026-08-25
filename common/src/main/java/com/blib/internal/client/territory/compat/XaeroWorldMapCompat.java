package com.blib.internal.client.territory.compat;

import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.ApiStatus;

import com.blib.api.BLibAPI;
import com.blib.mod.BLib;

@ApiStatus.Internal
public class XaeroWorldMapCompat {

    private static boolean isLoaded = false;

    public static void init() {
        isLoaded = BLibAPI.isModLoaded("xaeroworldmap") && isHighlighterApiAvailable();
    }

    public static boolean isLoaded() {
        return isLoaded;
    }

    public static void invalidateAll() {
        if (!isLoaded) {
            return;
        }

        try {
            highlighterClass().getMethod("invalidateAll").invoke(null);
        } catch (Throwable throwable) {
            // Same reasoning as the availability probe: a failure here means the integration is unusable, not that the
            // game should stop. Latching isLoaded off stops it being retried every invalidation.
            isLoaded = false;
        }
    }

    public static void invalidateChunk(int chunkX, int chunkZ) {
        if (!isLoaded) {
            return;
        }

        try {
            highlighterClass().getMethod("invalidateChunk", int.class, int.class).invoke(null, chunkX, chunkZ);
        } catch (Throwable throwable) {
            isLoaded = false;
        }
    }

    public static boolean contestedBlinkPhase() {
        var level = Minecraft.getInstance().level;
        var gameTime = level == null ? 0L : level.getGameTime();

        return (gameTime / 10L) % 2L == 0L;
    }

    /**
     * Whether Xaero's highlighter API is present and usable.
     * <p>
     * ⚠⚠ CATCHES THROWABLE ON PURPOSE, AND NARROWING IT WILL BRING THE CRASH BACK. Loading a class runs the mixin
     * transformer over it, so this probe executes every OTHER mod's mixins that target Xaero. If one of those is broken
     * - a redirect whose target method no longer exists after a Xaero update, say - mixin throws
     * {@code MixinTransformerError}, which is an {@code Error} but NOT a {@code LinkageError}, so the narrower catch
     * that used to be here let it straight through. The result was a hard load failure blamed on BLib, in a stack where
     * BLib's only involvement was asking whether a class existed.
     * <p>
     * This is a feature-availability question and nothing more. Any answer other than "yes" means the same thing to us,
     * so every failure gets the same reply and the game keeps loading.
     */
    private static boolean isHighlighterApiAvailable() {
        try {
            Class.forName("xaero.map.highlight.ChunkHighlighter", false, XaeroWorldMapCompat.class.getClassLoader());
            Class.forName("xaero.map.highlight.HighlighterRegistry", false, XaeroWorldMapCompat.class.getClassLoader());
            Class.forName("xaero.map.WorldMapSession", false, XaeroWorldMapCompat.class.getClassLoader());
            highlighterClass();
            return true;
        } catch (Throwable throwable) {
            BLib.LOGGER.warn(
                "Xaero world map highlighter API is unavailable - map highlighting will be disabled. This is usually "
                    + "another mod's Xaero integration failing to apply, not Xaero itself.",
                throwable
            );

            return false;
        }
    }

    private static Class<?> highlighterClass() throws ClassNotFoundException {
        return Class.forName(
            "com.blib.internal.client.territory.compat.xaero.BLibChunkHighlighter",
            false,
            XaeroWorldMapCompat.class.getClassLoader()
        );
    }

    private XaeroWorldMapCompat() {}
}
