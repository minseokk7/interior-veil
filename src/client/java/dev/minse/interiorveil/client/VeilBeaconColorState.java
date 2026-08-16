package dev.minse.interiorveil.client;

import dev.minse.interiorveil.network.BeaconColorPayload;
import net.minecraft.core.BlockPos;

public final class VeilBeaconColorState {
    private static BlockPos position;
    private static int color = 0xFFFFFF;

    private VeilBeaconColorState() {
    }

    public static void accept(BeaconColorPayload payload) {
        position = payload.active() ? new BlockPos(payload.x(), payload.y(), payload.z()) : null;
        color = payload.color() & 0xFFFFFF;
    }

    public static int colorAt(BlockPos blockPos) {
        return position != null && position.equals(blockPos) ? color : -1;
    }

    public static boolean hasPosition() {
        return position != null;
    }

    public static boolean isAssignedPosition(BlockPos blockPos) {
        return position != null && position.equals(blockPos);
    }

    public static void clear() {
        position = null;
    }
}
