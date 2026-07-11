package com.blib.internal.client.territory.compat;

import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.ApiStatus;

import com.blib.api.BLibAPI;

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
        } catch (ReflectiveOperationException | LinkageError exception) {
            isLoaded = false;
        }
    }

    public static void invalidateChunk(int chunkX, int chunkZ) {
        if (!isLoaded) {
            return;
        }

        try {
            highlighterClass().getMethod("invalidateChunk", int.class, int.class).invoke(null, chunkX, chunkZ);
        } catch (ReflectiveOperationException | LinkageError exception) {
            isLoaded = false;
        }
    }

    public static boolean contestedBlinkPhase() {
        var level = Minecraft.getInstance().level;
        var gameTime = level == null ? 0L : level.getGameTime();

        return (gameTime / 10L) % 2L == 0L;
    }

    private static boolean isHighlighterApiAvailable() {
        try {
            Class.forName("xaero.map.highlight.ChunkHighlighter", false, XaeroWorldMapCompat.class.getClassLoader());
            Class.forName("xaero.map.highlight.HighlighterRegistry", false, XaeroWorldMapCompat.class.getClassLoader());
            Class.forName("xaero.map.WorldMapSession", false, XaeroWorldMapCompat.class.getClassLoader());
            highlighterClass();
            return true;
        } catch (ClassNotFoundException | LinkageError exception) {
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
