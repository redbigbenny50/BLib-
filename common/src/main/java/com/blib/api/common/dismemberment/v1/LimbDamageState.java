package com.blib.api.common.dismemberment.v1;

import com.just.codec.stream.StreamCodec;
import com.just.codec.stream.StreamDecoder;
import com.just.codec.stream.StreamEncoder;
import com.just.codec.stream.impl.StreamCodecs;
import com.just.codec.stream.schema.StreamCodecSchema;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import com.blib.api.common.codec.v1.BLibCodecs;

/** Compact network snapshot of one attached limb's accumulated damage. */
public record LimbDamageState(
    ResourceLocation limbId,
    float damage
) {

    public static final StreamCodec<LimbDamageState> CODEC = StreamCodec.of(
        new StreamDecoder<>() {

            @Override
            public <T> @NotNull LimbDamageState decode(@NotNull StreamCodecSchema<T> schema, @NotNull T input) {
                return new LimbDamageState(
                    BLibCodecs.Stream.RESOURCE_LOCATION.decode(schema, input),
                    StreamCodecs.FLOAT.decode(schema, input)
                );
            }
        },
        new StreamEncoder<>() {

            @Override
            public <T> void encode(@NotNull StreamCodecSchema<T> schema, @NotNull T output, @NotNull LimbDamageState value) {
                BLibCodecs.Stream.RESOURCE_LOCATION.encode(schema, output, value.limbId());
                StreamCodecs.FLOAT.encode(schema, output, value.damage());
            }
        }
    );
}
