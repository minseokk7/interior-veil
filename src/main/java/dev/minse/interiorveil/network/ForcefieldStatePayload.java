package dev.minse.interiorveil.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ForcefieldStatePayload(
        boolean active,
        double centerX,
        double centerZ,
        double centerY,
        double radius,
        int color,
        int density
) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("interiorveil", "forcefield_state");
    public static final Type<ForcefieldStatePayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ForcefieldStatePayload> STREAM_CODEC =
            CustomPacketPayload.codec(ForcefieldStatePayload::write, ForcefieldStatePayload::new);

    private ForcefieldStatePayload(RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readBoolean(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readInt(),
                buffer.readInt()
        );
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(active);
        buffer.writeDouble(centerX);
        buffer.writeDouble(centerZ);
        buffer.writeDouble(centerY);
        buffer.writeDouble(radius);
        buffer.writeInt(color);
        buffer.writeInt(density);
    }

    @Override
    public Type<ForcefieldStatePayload> type() {
        return TYPE;
    }
}
