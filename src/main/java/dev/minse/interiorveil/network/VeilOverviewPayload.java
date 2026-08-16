package dev.minse.interiorveil.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

public record VeilOverviewPayload(List<VeilSummary> veils) implements CustomPacketPayload {

    public record VeilSummary(UUID id, String name, String dimension, int x, int y, int z, int level, boolean secure, String recent) {
        public static final StreamCodec<RegistryFriendlyByteBuf, VeilSummary> STREAM_CODEC = StreamCodec.composite(
                net.minecraft.core.UUIDUtil.STREAM_CODEC, VeilSummary::id,
                ByteBufCodecs.stringUtf8(1024), VeilSummary::name,
                ByteBufCodecs.stringUtf8(1024), VeilSummary::dimension,
                ByteBufCodecs.VAR_INT, VeilSummary::x,
                ByteBufCodecs.VAR_INT, VeilSummary::y,
                ByteBufCodecs.VAR_INT, VeilSummary::z,
                ByteBufCodecs.VAR_INT, VeilSummary::level,
                ByteBufCodecs.BOOL, VeilSummary::secure,
                ByteBufCodecs.stringUtf8(1024), VeilSummary::recent,
                VeilSummary::new
        );
    }

    public static final Type<VeilOverviewPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("interiorveil", "overview")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, VeilOverviewPayload> STREAM_CODEC =
            VeilSummary.STREAM_CODEC.apply(ByteBufCodecs.list()).map(VeilOverviewPayload::new, VeilOverviewPayload::veils);

    @Override
    public Type<VeilOverviewPayload> type() {
        return TYPE;
    }
}
