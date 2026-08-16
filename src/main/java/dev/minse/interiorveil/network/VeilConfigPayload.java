package dev.minse.interiorveil.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VeilConfigPayload(
        UUID barrierId,
        String name,
        int radius,
        int height,
        int fogMargin,
        int fogDistance,
        int fogFadeTicks,
        int navigationRange,
        boolean boundaryVisible,
        int boundaryColor,
        int navigationColor,
        boolean securityMode,
        int beaconColor,
        int accessStart,
        int accessEnd,
        int boundaryDensity,
        float boundarySize,
        int navigationDensity,
        float navigationSize,
        boolean requireBeaconPower,
        boolean forceOpen,
        boolean disableFog,
        int fogColor,
        boolean attackMode,
        int attackTargetX,
        int attackTargetY,
        int attackTargetZ,
        int strikeRadius,
        boolean absoluteBarrier,
        boolean reflectProjectiles,
        String allowedPlayers
) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("interiorveil", "veil_config");
    public static final Type<VeilConfigPayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, VeilConfigPayload> STREAM_CODEC =
            CustomPacketPayload.codec(VeilConfigPayload::write, VeilConfigPayload::new);

    private VeilConfigPayload(RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readUUID(),
                buffer.readUtf(32),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readFloat(),
                buffer.readVarInt(),
                buffer.readFloat(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readUtf(512)
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(barrierId);
        buffer.writeUtf(name, 32);
        buffer.writeVarInt(radius);
        buffer.writeVarInt(height);
        buffer.writeVarInt(fogMargin);
        buffer.writeVarInt(fogDistance);
        buffer.writeVarInt(fogFadeTicks);
        buffer.writeVarInt(navigationRange);
        buffer.writeBoolean(boundaryVisible);
        buffer.writeVarInt(boundaryColor);
        buffer.writeVarInt(navigationColor);
        buffer.writeBoolean(securityMode);
        buffer.writeVarInt(beaconColor);
        buffer.writeVarInt(accessStart);
        buffer.writeVarInt(accessEnd);
        buffer.writeVarInt(boundaryDensity);
        buffer.writeFloat(boundarySize);
        buffer.writeVarInt(navigationDensity);
        buffer.writeFloat(navigationSize);
        buffer.writeBoolean(requireBeaconPower);
        buffer.writeBoolean(forceOpen);
        buffer.writeBoolean(disableFog);
        buffer.writeVarInt(fogColor);
        buffer.writeBoolean(attackMode);
        buffer.writeInt(attackTargetX);
        buffer.writeInt(attackTargetY);
        buffer.writeInt(attackTargetZ);
        buffer.writeVarInt(strikeRadius);
        buffer.writeBoolean(absoluteBarrier);
        buffer.writeBoolean(reflectProjectiles);
        buffer.writeUtf(allowedPlayers, 512);
    }

    @Override
    public Type<VeilConfigPayload> type() {
        return TYPE;
    }
}
