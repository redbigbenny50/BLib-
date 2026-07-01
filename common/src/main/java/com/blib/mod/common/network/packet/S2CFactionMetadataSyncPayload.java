package com.blib.mod.common.network.packet;

import com.just.codec.stream.RecordStreamCodec;
import com.just.codec.stream.StreamCodec;
import com.just.codec.stream.impl.StreamCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import com.blib.api.common.codec.v1.BLibCodecs;
import com.blib.api.common.faction.v1.ClaimMapStyle;
import com.blib.mod.BLib;

public record S2CFactionMetadataSyncPayload(
    ResourceLocation factionId,
    String name,
    int color,
    ClaimMapStyle claimMapStyle
) implements CustomPacketPayload {

    public static final ResourceLocation PAYLOAD_ID = BLib.MOD.resources().createLocation("faction_metadata_sync");

    public static final Type<S2CFactionMetadataSyncPayload> TYPE = new Type<>(PAYLOAD_ID);

    public static final StreamCodec<S2CFactionMetadataSyncPayload> CODEC = RecordStreamCodec.of(
        BLibCodecs.Stream.RESOURCE_LOCATION,
        S2CFactionMetadataSyncPayload::factionId,
        StreamCodecs.STRING_UTF8,
        S2CFactionMetadataSyncPayload::name,
        StreamCodecs.INT,
        S2CFactionMetadataSyncPayload::color,
        ClaimMapStyleStreamCodec.INSTANCE,
        S2CFactionMetadataSyncPayload::claimMapStyle,
        S2CFactionMetadataSyncPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static final class ClaimMapStyleStreamCodec implements StreamCodec<ClaimMapStyle> {

        private static final ClaimMapStyleStreamCodec INSTANCE = new ClaimMapStyleStreamCodec();

        @Override
        public @NotNull <T> ClaimMapStyle decode(@NotNull com.just.codec.stream.schema.StreamCodecSchema<T> schema, @NotNull T input) {
            var overlayTexture = schema.read(input, StreamCodecs.STRING_UTF8);
            return overlayTexture.isBlank()
                ? ClaimMapStyle.DEFAULT
                : new ClaimMapStyle(ResourceLocation.tryParse(overlayTexture));
        }

        @Override
        public <T> void encode(
            @NotNull com.just.codec.stream.schema.StreamCodecSchema<T> schema,
            @NotNull T input,
            @NotNull ClaimMapStyle value
        ) {
            if (value == null) {
                value = ClaimMapStyle.DEFAULT;
            }
            var texture = value.overlayTexture();
            schema.write(input, StreamCodecs.STRING_UTF8, texture == null ? "" : texture.toString());
        }
    }
}
