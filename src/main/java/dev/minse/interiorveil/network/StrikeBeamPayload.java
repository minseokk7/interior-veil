package dev.minse.interiorveil.network;

import dev.minse.interiorveil.InteriorVeil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record StrikeBeamPayload(
        double targetX,
        double targetY,
        double targetZ,
        int durationTicks,
        int color
) implements CustomPacketPayload {
    public static final Type<StrikeBeamPayload> TYPE = new Type<>(InteriorVeil.id("strike_beam"));
    public static final StreamCodec<RegistryFriendlyByteBuf, StrikeBeamPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE,
            StrikeBeamPayload::targetX,
            ByteBufCodecs.DOUBLE,
            StrikeBeamPayload::targetY,
            ByteBufCodecs.DOUBLE,
            StrikeBeamPayload::targetZ,
            ByteBufCodecs.INT,
            StrikeBeamPayload::durationTicks,
            ByteBufCodecs.INT,
            StrikeBeamPayload::color,
            StrikeBeamPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
