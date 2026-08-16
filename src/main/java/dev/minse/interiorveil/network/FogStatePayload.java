package dev.minse.interiorveil.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record FogStatePayload(boolean active, int distance, int fadeTicks, int color) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("interiorveil", "fog_state");
    public static final Type<FogStatePayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, FogStatePayload> STREAM_CODEC =
            CustomPacketPayload.codec(FogStatePayload::write, FogStatePayload::new);

    private FogStatePayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(active);
        buffer.writeVarInt(distance);
        buffer.writeVarInt(fadeTicks);
        buffer.writeVarInt(color);
    }

    @Override
    public Type<FogStatePayload> type() {
        return TYPE;
    }
}
