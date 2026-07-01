package com.blib.api.common.faction.v1;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Client-facing map style metadata for territory claims.
 *
 * @param overlayTexture optional texture that map integrations can draw over claimed chunks
 */
public record ClaimMapStyle(
    @Nullable ResourceLocation overlayTexture
) {

    public static final ClaimMapStyle DEFAULT = new ClaimMapStyle(null);

    public boolean hasOverlayTexture() {
        return overlayTexture != null;
    }
}
