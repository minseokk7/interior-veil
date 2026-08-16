package dev.minse.interiorveil.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MirrorFramePayload(List<MirrorPlayerState> players) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("interiorveil", "mirror_frame");
    public static final Type<MirrorFramePayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, MirrorFramePayload> STREAM_CODEC =
            CustomPacketPayload.codec(MirrorFramePayload::write, MirrorFramePayload::new);

    private MirrorFramePayload(RegistryFriendlyByteBuf buffer) {
        this(readPlayers(buffer));
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(players.size());
        for (MirrorPlayerState player : players) {
            player.write(buffer);
        }
    }

    private static List<MirrorPlayerState> readPlayers(RegistryFriendlyByteBuf buffer) {
        int size = buffer.readVarInt();
        if (size < 0 || size > 256) {
            throw new IllegalArgumentException("Invalid mirror player count: " + size);
        }
        List<MirrorPlayerState> players = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            players.add(new MirrorPlayerState(buffer));
        }
        return List.copyOf(players);
    }

    @Override
    public Type<MirrorFramePayload> type() {
        return TYPE;
    }
}
