package dev.minse.interiorveil.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public record MirrorPlayerState(
        UUID id,
        String name,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        byte flags,
        ItemStack mainHand,
        ItemStack offHand,
        ItemStack head,
        ItemStack chest,
        ItemStack legs,
        ItemStack feet
) {
    private static final int CROUCHING = 1;
    private static final int SPRINTING = 1 << 1;
    private static final int ON_GROUND = 1 << 2;

    public static MirrorPlayerState from(ServerPlayer player, double offsetX, double offsetZ) {
        int stateFlags = 0;
        if (player.isCrouching()) {
            stateFlags |= CROUCHING;
        }
        if (player.isSprinting()) {
            stateFlags |= SPRINTING;
        }
        if (player.onGround()) {
            stateFlags |= ON_GROUND;
        }
        return new MirrorPlayerState(
                player.getUUID(),
                player.getGameProfile().name(),
                player.getX() + offsetX,
                player.getY(),
                player.getZ() + offsetZ,
                player.getYRot(),
                player.getXRot(),
                (byte) stateFlags,
                player.getItemBySlot(EquipmentSlot.MAINHAND).copy(),
                player.getItemBySlot(EquipmentSlot.OFFHAND).copy(),
                player.getItemBySlot(EquipmentSlot.HEAD).copy(),
                player.getItemBySlot(EquipmentSlot.CHEST).copy(),
                player.getItemBySlot(EquipmentSlot.LEGS).copy(),
                player.getItemBySlot(EquipmentSlot.FEET).copy()
        );
    }

    MirrorPlayerState(RegistryFriendlyByteBuf buffer) {
        this(
                buffer.readUUID(),
                buffer.readUtf(64),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readByte(),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer)
        );
    }

    void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(id);
        buffer.writeUtf(name, 64);
        buffer.writeDouble(x);
        buffer.writeDouble(y);
        buffer.writeDouble(z);
        buffer.writeFloat(yaw);
        buffer.writeFloat(pitch);
        buffer.writeByte(flags);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, mainHand);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, offHand);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, head);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, chest);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, legs);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, feet);
    }

    public boolean crouching() {
        return (flags & CROUCHING) != 0;
    }

    public boolean sprinting() {
        return (flags & SPRINTING) != 0;
    }

    public boolean onGroundState() {
        return (flags & ON_GROUND) != 0;
    }
}
