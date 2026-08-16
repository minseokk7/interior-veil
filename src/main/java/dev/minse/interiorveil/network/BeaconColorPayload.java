package dev.minse.interiorveil.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BeaconColorPayload(boolean active, int x, int y, int z, int color) implements CustomPacketPayload {
    public static final Type<BeaconColorPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("interiorveil", "beacon_color")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, BeaconColorPayload> STREAM_CODEC =
            CustomPacketPayload.codec(BeaconColorPayload::write, BeaconColorPayload::new);

    private BeaconColorPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readBoolean(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt());
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(active);
        buffer.writeVarInt(x);
        buffer.writeVarInt(y);
        buffer.writeVarInt(z);
        buffer.writeVarInt(color);
    }

    @Override
    public Type<BeaconColorPayload> type() {
        return TYPE;
    }
}
