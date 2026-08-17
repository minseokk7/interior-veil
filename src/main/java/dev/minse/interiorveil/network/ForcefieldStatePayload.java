package dev.minse.interiorveil.network;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ForcefieldStatePayload(
        List<DomeEntry> domes
) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("interiorveil", "forcefield_state");
    public static final Type<ForcefieldStatePayload> TYPE = new Type<>(ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, ForcefieldStatePayload> STREAM_CODEC =
            CustomPacketPayload.codec(ForcefieldStatePayload::write, ForcefieldStatePayload::new);

    public record DomeEntry(
            double centerX,
            double centerY,
            double centerZ,
            double radius,
            int color,
            int density
    ) {}

    // 하위 호환 및 단일 생성자
    public ForcefieldStatePayload(boolean active, double centerX, double centerZ, double centerY, double radius, int color, int density) {
        this(active ? List.of(new DomeEntry(centerX, centerY, centerZ, radius, color, density)) : List.of());
    }

    private ForcefieldStatePayload(RegistryFriendlyByteBuf buffer) {
        this(readDomes(buffer));
    }

    private static List<DomeEntry> readDomes(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        List<DomeEntry> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(new DomeEntry(
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readInt(),
                    buffer.readInt()
            ));
        }
        return list;
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(domes != null ? domes.size() : 0);
        if (domes != null) {
            for (DomeEntry d : domes) {
                buffer.writeDouble(d.centerX());
                buffer.writeDouble(d.centerY());
                buffer.writeDouble(d.centerZ());
                buffer.writeDouble(d.radius());
                buffer.writeInt(d.color());
                buffer.writeInt(d.density());
            }
        }
    }

    public boolean active() {
        return domes != null && !domes.isEmpty();
    }

    public DomeEntry primaryDome() {
        return (domes != null && !domes.isEmpty()) ? domes.get(0) : null;
    }

    public double centerX() {
        DomeEntry d = primaryDome();
        return d != null ? d.centerX() : 0;
    }

    public double centerY() {
        DomeEntry d = primaryDome();
        return d != null ? d.centerY() : 0;
    }

    public double centerZ() {
        DomeEntry d = primaryDome();
        return d != null ? d.centerZ() : 0;
    }

    public double radius() {
        DomeEntry d = primaryDome();
        return d != null ? d.radius() : 0;
    }

    public int color() {
        DomeEntry d = primaryDome();
        return d != null ? d.color() : 0x00F0FF;
    }

    public int density() {
        DomeEntry d = primaryDome();
        return d != null ? d.density() : 64;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
