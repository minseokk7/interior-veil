package dev.minse.interiorveil;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import dev.minse.interiorveil.network.FogStatePayload;
import dev.minse.interiorveil.network.MirrorFramePayload;
import dev.minse.interiorveil.network.MirrorPlayerState;
import dev.minse.interiorveil.network.VeilConfigPayload;
import dev.minse.interiorveil.network.VeilAdminActionPayload;
import dev.minse.interiorveil.network.BeaconColorPayload;
import dev.minse.interiorveil.network.VeilOverviewPayload;
import dev.minse.interiorveil.network.VeilTargetMapPayload;
import dev.minse.interiorveil.network.ForcefieldStatePayload;
import dev.minse.interiorveil.network.StrikeBeamPayload;

final class VeilNetworking {
    private VeilNetworking() {
    }

    static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(MirrorFramePayload.TYPE, MirrorFramePayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(FogStatePayload.TYPE, FogStatePayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(VeilConfigPayload.TYPE, VeilConfigPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(BeaconColorPayload.TYPE, BeaconColorPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(VeilOverviewPayload.TYPE, VeilOverviewPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(VeilTargetMapPayload.TYPE, VeilTargetMapPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ForcefieldStatePayload.TYPE, ForcefieldStatePayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(StrikeBeamPayload.TYPE, StrikeBeamPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(dev.minse.interiorveil.network.BattleReportPayload.TYPE, dev.minse.interiorveil.network.BattleReportPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(VeilConfigPayload.TYPE, VeilConfigPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(VeilAdminActionPayload.TYPE, VeilAdminActionPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(
                VeilConfigPayload.TYPE,
                (payload, context) -> context.server().execute(() -> InteriorVeil.applyConfigUpdate(context.player(), payload))
        );
        ServerPlayNetworking.registerGlobalReceiver(
                VeilAdminActionPayload.TYPE,
                (payload, context) -> context.server().execute(() -> InteriorVeil.applyAdminAction(context.player(), payload))
        );
    }

    static void sendMirrors(
            MinecraftServer server,
            Collection<VeilBarrier> barriers,
            Map<UUID, UUID> pocketAssignments
    ) {
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {


            if (!viewer.level().dimension().equals(InteriorVeil.POCKET_LEVEL)) {
                // 오버월드 신호기 RGB 색상 전송
                VeilBarrier overworldBarrier = barriers.stream()
                        .filter(candidate -> candidate.sourceKey().equals(viewer.level().dimension()))
                        .filter(candidate -> candidate.advanced().absoluteBarrier() || candidate.contains(viewer.getX(), viewer.getY(), viewer.getZ(), candidate.radius() * 2.0, false))
                        .min(java.util.Comparator.comparingDouble(candidate -> BarrierGeometry.horizontalDistanceSquared(
                                viewer.getX(), viewer.getZ(), candidate.centerX() + 0.5, candidate.centerZ() + 0.5
                        )))
                        .orElse(null);

                if (overworldBarrier != null && ServerPlayNetworking.canSend(viewer, BeaconColorPayload.TYPE)) {
                    ServerPlayNetworking.send(viewer, new BeaconColorPayload(
                            true,
                            overworldBarrier.centerX(),
                            overworldBarrier.centerY(),
                            overworldBarrier.centerZ(),
                            overworldBarrier.beaconColor()
                    ));
                } else if (ServerPlayNetworking.canSend(viewer, BeaconColorPayload.TYPE)) {
                    ServerPlayNetworking.send(viewer, new BeaconColorPayload(false, 0, 0, 0, 0xFFFFFF));
                }
                continue;
            }
            UUID barrierId = pocketAssignments.get(viewer.getUUID());
            VeilBarrier barrier = barriers.stream()
                    .filter(candidate -> candidate.id().equals(barrierId))
                    .findFirst()
                    .orElse(null);
            if (barrier == null || !ServerPlayNetworking.canSend(viewer, MirrorFramePayload.TYPE)) {
                continue;
            }
            if (ServerPlayNetworking.canSend(viewer, BeaconColorPayload.TYPE)) {
                ServerPlayNetworking.send(viewer, new BeaconColorPayload(
                        true,
                        barrier.getPocketX(),
                        barrier.centerY(),
                        barrier.getPocketZ(),
                        barrier.beaconColor()
                ));
            }

            double offsetX = barrier.getPocketX() - barrier.centerX();
            double offsetZ = barrier.getPocketZ() - barrier.centerZ();

            List<MirrorPlayerState> visiblePlayers = server.getPlayerList().getPlayers().stream()
                    .filter(player -> player.level().dimension().equals(barrier.sourceKey()))
                    .filter(player -> barrier.mirrors(player.getX(), player.getY(), player.getZ(), false))
                    .filter(player -> player.distanceToSqr(barrier.centerX(), player.getY(), barrier.centerZ())
                            <= VeilConstants.MIRROR_RANGE * VeilConstants.MIRROR_RANGE)
                    .map(player -> MirrorPlayerState.from(player, offsetX, offsetZ))
                    .toList();
            ServerPlayNetworking.send(viewer, new MirrorFramePayload(visiblePlayers));
        }
    }
}
