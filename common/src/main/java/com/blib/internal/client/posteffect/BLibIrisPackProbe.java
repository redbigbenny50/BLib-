package com.blib.internal.client.posteffect;

import org.jetbrains.annotations.ApiStatus;

import java.lang.reflect.Method;

import com.blib.mod.BLib;

/**
 * Asks Iris whether a shader pack is actually loaded right now.
 * <h2>Why reflection, when BLib avoids it elsewhere</h2> Compiling against Iris's published API would be tidier, and is
 * worth revisiting — but it needs a resolvable {@code compileOnly} coordinate on both loader modules, and the
 * loader-specific artifact names have moved around.
 * <p>
 * The reflection hazard BLib genuinely cares about is different from this: a {@code Class.forName} probe against a
 * <em>third-party</em> class triggers every other mod's mixins on that class during the load, which is how BLib once
 * took the blame for a crash that belonged to a Xaero compat mod. {@code IrisApi} is Iris's own API surface, already
 * loaded by the time anything here runs, and mods do not mix into it.
 * <p>
 * Everything is cached after the first attempt, and any failure falls back to "assume a pack is in use", which is the
 * conservative answer — BLib stands down rather than fighting a pipeline it might not own.
 */
@ApiStatus.Internal
public final class BLibIrisPackProbe {

    private static final String IRIS_API_CLASS = "net.irisshaders.iris.api.v0.IrisApi";

    private static volatile boolean resolved;

    private static volatile Object instance;

    private static volatile Method isShaderPackInUse;

    private BLibIrisPackProbe() {
        throw new UnsupportedOperationException();
    }

    /**
     * @return true when a shader pack is loaded. Falls back to true if Iris cannot be asked, so an unknown answer errs
     *         toward standing down.
     */
    public static boolean isShaderPackInUse() {
        if (!resolved) {
            resolve();
        }

        if (isShaderPackInUse == null || instance == null) {
            return true;
        }

        try {
            return (Boolean) isShaderPackInUse.invoke(instance);
        } catch (Throwable throwable) {
            return true;
        }
    }

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }

        resolved = true;

        try {
            var apiClass = Class.forName(IRIS_API_CLASS);

            instance = apiClass.getMethod("getInstance").invoke(null);
            isShaderPackInUse = apiClass.getMethod("isShaderPackInUse");

            BLib.LOGGER.info("[BLib] Iris API found; effects will stand down only while a pack is actually in use.");
        } catch (Throwable throwable) {
            // Catch Throwable, not Exception: a mixin failure during class load surfaces as an Error, and an unrelated
            // mod's broken transformer must not become BLib's crash.
            BLib.LOGGER.warn(
                "[BLib] Iris is present but its API could not be reached ({}); effects will stand down whenever it is"
                    + " installed rather than only when a pack is in use.",
                throwable.getClass().getSimpleName()
            );
        }
    }
}
