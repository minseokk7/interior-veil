package dev.minse.interiorveil.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VeilAdminActionPayload(UUID barrierId, String action, String value) implements CustomPacketPayload {
    public static final Type<VeilAdminActionPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("interiorveil", "admin_action")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, VeilAdminActionPayload> STREAM_CODEC =
            CustomPacketPayload.codec(VeilAdminActionPayload::write, VeilAdminActionPayload::new);

    private VeilAdminActionPayload(RegistryFriendlyByteBuf buffer) {
        this(buffer.readUUID(), buffer.readUtf(24), buffer.readUtf(64));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(barrierId);
        buffer.writeUtf(action, 24);
        buffer.writeUtf(value, 64);
    }

    @Override
    public Type<VeilAdminActionPayload> type() {
        return TYPE;
    }
}
