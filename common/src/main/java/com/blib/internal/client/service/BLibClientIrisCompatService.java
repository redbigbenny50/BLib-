package com.blib.internal.client.service;

import org.jetbrains.annotations.ApiStatus;

/**
 * Loader-agnostic check for "is a shader-pack mod (Iris on Fabric / Oculus on NeoForge / etc.) currently loaded?"
 * Result is consulted lazily by {@link com.blib.internal.client.posteffect.BLibIrisCompat} and cached after first call.
 */
@ApiStatus.Internal
public interface BLibClientIrisCompatService {

    boolean isShaderModActive();

    /**
     * Whether {@link #isShaderModActive()} can answer authoritatively yet.
     * <p>
     * ⚠⚠ Exists because the answer is cached, and the first caller is the main render target's construction — early
     * enough on NeoForge that the mod list may not exist. Latching "no shader mod" there left Iris undetected for a
     * whole session, with BLib fighting it for the framebuffer: tinted GUI, and a pack left corrupted afterwards.
     */
    default boolean isDetectionReady() {
        return true;
    }

    /**
     * Whether a shader pack is loaded right now — not merely whether the mod is installed.
     * <p>
     * ⭐ Installing Iris is not the same as using it: it ships in countless packs for the Sodium ecosystem with no pack
     * selected, and in that state BLib's pipeline is perfectly safe. Gating on mod presence alone denied the feature to
     * a large share of players for no reason.
     */
    default boolean isShaderPackInUse() {
        return isShaderModActive()
            && com.blib.internal.client.posteffect.BLibIrisPackProbe.isShaderPackInUse();
    }
}
