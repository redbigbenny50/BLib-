package com.blib.internal.client.faction;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

import com.blib.api.common.faction.v1.ClaimMapStyle;
import com.blib.internal.client.territory.compat.XaeroWorldMapCompat;

@ApiStatus.Internal
public class ClientFactionCache {

    public static final ClientFactionCache INSTANCE = new ClientFactionCache();

    private final Map<ResourceLocation, FactionMetadata> metadataByFactionId;

    private ClientFactionCache() {
        this.metadataByFactionId = new HashMap<>();
    }

    public void update(ResourceLocation factionId, String name, int color, ClaimMapStyle claimMapStyle) {
        if (claimMapStyle == null) {
            claimMapStyle = ClaimMapStyle.DEFAULT;
        }
        metadataByFactionId.put(factionId, new FactionMetadata(name, color, claimMapStyle));

        XaeroWorldMapCompat.invalidateAll();
    }

    public @Nullable FactionMetadata get(ResourceLocation factionId) {
        return metadataByFactionId.get(factionId);
    }

    public void clear() {
        metadataByFactionId.clear();
    }

    public record FactionMetadata(
        String name,
        int color,
        ClaimMapStyle claimMapStyle
    ) {}
}
