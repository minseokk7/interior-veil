package dev.minse.interiorveil.network;

import dev.minse.interiorveil.InteriorVeil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BattleReportPayload(
        int strikeType,
        int kills,
        float totalDamage,
        int debuffedCount,
        int x, int y, int z
) implements CustomPacketPayload {
    public static final Type<BattleReportPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(InteriorVeil.MOD_ID, "battle_report"));

    public static final StreamCodec<FriendlyByteBuf, BattleReportPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.strikeType());
                buf.writeVarInt(payload.kills());
                buf.writeFloat(payload.totalDamage());
                buf.writeVarInt(payload.debuffedCount());
                buf.writeVarInt(payload.x());
                buf.writeVarInt(payload.y());
                buf.writeVarInt(payload.z());
            },
            buf -> new BattleReportPayload(
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readFloat(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
