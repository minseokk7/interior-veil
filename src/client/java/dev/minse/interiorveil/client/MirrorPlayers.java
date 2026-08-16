package dev.minse.interiorveil.client;

import com.mojang.authlib.GameProfile;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldExtractionContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.EquipmentSlot;
import dev.minse.interiorveil.network.MirrorFramePayload;
import dev.minse.interiorveil.network.MirrorPlayerState;

final class MirrorPlayers {
    private static final Map<UUID, RemotePlayer> PLAYERS = new HashMap<>();

    private MirrorPlayers() {
    }

    static void accept(Minecraft client, MirrorFramePayload payload) {
        if (client.level == null) {
            clear();
            return;
        }

        Set<UUID> incoming = new HashSet<>();
        for (MirrorPlayerState state : payload.players()) {
            incoming.add(state.id());
            RemotePlayer player = PLAYERS.get(state.id());
            if (player == null || player.level() != client.level) {
                player = new RemotePlayer(client.level, new GameProfile(state.id(), state.name()));
                player.setPos(state.x(), state.y(), state.z());
                PLAYERS.put(state.id(), player);
            }
            update(player, state);
        }
        PLAYERS.keySet().removeIf(id -> !incoming.contains(id));
    }

    static void extractRenderStates(WorldExtractionContext context) {
        if (PLAYERS.isEmpty()) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || context.world() != client.level) {
            clear();
            return;
        }

        float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(true);
        for (RemotePlayer player : PLAYERS.values()) {
            EntityRenderState renderState = client.getEntityRenderDispatcher().extractEntity(player, partialTick);
            context.worldState().entityRenderStates.add(renderState);
        }
    }

    static void clear() {
        PLAYERS.clear();
    }

    private static void update(RemotePlayer player, MirrorPlayerState state) {
        player.xOld = player.getX();
        player.yOld = player.getY();
        player.zOld = player.getZ();
        player.setPos(state.x(), state.y(), state.z());
        player.setYRot(state.yaw());
        player.setXRot(state.pitch());
        player.setYHeadRot(state.yaw());
        player.setShiftKeyDown(state.crouching());
        player.setSprinting(state.sprinting());
        player.setOnGround(state.onGroundState());
        player.setItemSlot(EquipmentSlot.MAINHAND, state.mainHand());
        player.setItemSlot(EquipmentSlot.OFFHAND, state.offHand());
        player.setItemSlot(EquipmentSlot.HEAD, state.head());
        player.setItemSlot(EquipmentSlot.CHEST, state.chest());
        player.setItemSlot(EquipmentSlot.LEGS, state.legs());
        player.setItemSlot(EquipmentSlot.FEET, state.feet());
    }
}
