package dev.minse.interiorveil;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import dev.minse.interiorveil.network.VeilConfigPayload;
import dev.minse.interiorveil.network.VeilAdminActionPayload;
import dev.minse.interiorveil.network.VeilOverviewPayload;
import dev.minse.interiorveil.network.VeilTargetMapPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class VeilManager {
    private final MinecraftServer server;
    private final Map<UUID, VeilBarrier> barriers = new LinkedHashMap<>();
    private final Map<UUID, TransitionState> transitions = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, UUID> pocketAssignments = new LinkedHashMap<>();
    private final Map<UUID, PendingRemoval> pendingRemovals = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<UUID, java.util.ArrayDeque<String>> recentEntries = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.List<StrikeBeam> activeStrikeBeams = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<PendingStrike> pendingStrikes = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final Map<UUID, Long> strikeCooldowns = new java.util.concurrent.ConcurrentHashMap<>();
    private final VeilPlatformGenerator platformGenerator = new VeilPlatformGenerator();
    // 절대 방어막 결계 캐시 - 매 틱 순회 최적화
    private final java.util.Set<UUID> absoluteBarrierCache = java.util.Collections.synchronizedSet(new java.util.HashSet<>());
    // 지연 블록 제거 작업 큐 (분할 처리로 서버 프리즈 방지)
    private final java.util.Deque<Runnable> pendingClearTasks = new java.util.concurrent.ConcurrentLinkedDeque<>();
    private int tickCounter = 0;

    private record StrikeBeam(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension, BlockPos basePos, int expireTick) {}

    public VeilManager(MinecraftServer server) {
        this.server = server;
        for (VeilBarrier barrier : VeilStore.load(server)) {
            barriers.put(barrier.id(), barrier);
            updateChunkForceload(barrier, true);
        }
        if (server.getLevel(InteriorVeil.POCKET_LEVEL) == null) {
            InteriorVeil.LOGGER.error("Pocket dimension {} is unavailable", InteriorVeil.POCKET_LEVEL.location());
        } else {
            InteriorVeil.LOGGER.info("Pocket dimension {} is ready", InteriorVeil.POCKET_LEVEL.location());
            migrateVisibleBeacons();
        }
        InteriorVeil.LOGGER.info("Loaded {} veil barrier(s)", barriers.size());
        // 초기 절대 방어막 캐시 구성
        for (VeilBarrier barrier : barriers.values()) {
            if (barrier.advanced().absoluteBarrier()) {
                absoluteBarrierCache.add(barrier.id());
            }
        }
    }

    public Collection<VeilBarrier> barriers() {
        return new ArrayList<>(barriers.values());
    }

    private void updateChunkForceload(VeilBarrier barrier, boolean load) {
        ServerLevel pocket = server.getLevel(InteriorVeil.POCKET_LEVEL);
        if (pocket == null) return;
        
        int minChunkX = (barrier.centerX() - barrier.radius()) >> 4;
        int maxChunkX = (barrier.centerX() + barrier.radius()) >> 4;
        int minChunkZ = (barrier.centerZ() - barrier.radius()) >> 4;
        int maxChunkZ = (barrier.centerZ() + barrier.radius()) >> 4;
        
        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                pocket.setChunkForced(cx, cz, load);
            }
        }
    }

    public InteractionResult useBeacon(ServerPlayer player, InteractionHand hand, BlockHitResult hit) {
        ServerLevel level = player.level();
        BlockPos position = hit.getBlockPos();
        if (!level.getBlockState(position).is(Blocks.BEACON)) {
            return InteractionResult.PASS;
        }

        boolean holdingKey = player.getItemInHand(hand).is(VeilItems.VEIL_KEY);
        if (hand == InteractionHand.MAIN_HAND && !holdingKey) {
            VeilBarrier barrier = findAt(level, position).orElse(null);
            if (barrier != null) {
                if (level.dimension().equals(InteriorVeil.POCKET_LEVEL) || barrier.advanced().absoluteBarrier()) {
                    return openConfig(player, barrier, false);
                }
            }
        }
        if (!holdingKey) {
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown()) {
            return removeAt(player, level, position, hand);
        }
        Optional<VeilBarrier> existing = findAt(level, position);
        if (existing.isPresent()) {
            return bindExistingKey(player, hand, existing.get());
        }
        return createAt(player, level, position, hand);
    }

    public void applyConfig(ServerPlayer player, VeilConfigPayload payload) {
        VeilBarrier current = barriers.get(payload.barrierId());
        // 절대 방어막 활성 상태이면 오버월드에서도 설정 허용
        boolean inPocket = player.level().dimension().equals(InteriorVeil.POCKET_LEVEL);
        boolean inSource = current != null && player.level().dimension().equals(current.sourceKey());
        boolean nearPocket = current != null && inPocket &&
                player.distanceToSqr(current.getPocketX() + 0.5, current.centerY() + 0.5, current.getPocketZ() + 0.5) <= 64.0;
        boolean nearSource = current != null && inSource && current.advanced().absoluteBarrier() &&
                player.distanceToSqr(current.centerX() + 0.5, current.centerY() + 0.5, current.centerZ() + 0.5) <= 64.0;
        // 열쇠 소지자는 어디서든 설정 저장 가능
        boolean holdingKey = current != null && holdsKeyFor(player, current);
        if (current == null
                || (!nearPocket && !nearSource && !holdingKey)
                || (!current.owner().equals(player.getUUID()) && !player.hasPermissions(2))) {
            player.displayClientMessage(Component.translatable("message.interiorveil.config_denied"), true);
            return;
        }

        ServerLevel pocket = server.getLevel(InteriorVeil.POCKET_LEVEL);
        BlockPos pocketPos = new BlockPos(current.getPocketX(), current.centerY(), current.getPocketZ());
        int beaconLevel = pocket == null ? 0 : beaconLevel(pocket, pocketPos);
        int levelRadius = maxRadiusForBeaconLevel(beaconLevel);
        if (payload.radius() > levelRadius && payload.radius() > current.radius()) {
            player.displayClientMessage(Component.translatable("message.interiorveil.beacon_radius", beaconLevel, levelRadius), true);
            return;
        }

        VeilBarrier updated = current.withSettings(
                payload.name(),
                payload.radius(),
                payload.height(),
                payload.fogMargin(),
                payload.fogDistance(),
                payload.fogFadeTicks(),
                payload.navigationRange(),
                payload.boundaryVisible(),
                payload.boundaryColor(),
                payload.navigationColor(),
                payload.securityMode(),
                payload.beaconColor(),
                new VeilAdvancedSettings(
                        current.advanced().keyRevision(),
                        current.advanced().allowedPlayers(),
                        payload.accessStart(),
                        payload.accessEnd(),
                        payload.boundaryDensity(),
                        payload.boundarySize(),
                        payload.navigationDensity(),
                        payload.navigationSize(),
                        payload.requireBeaconPower(),
                        payload.disableFog(),
                        payload.fogColor(),
                        payload.attackMode(),
                        payload.attackTargetX(),
                        payload.attackTargetY(),
                        payload.attackTargetZ(),
                        payload.strikeRadius(),
                        payload.absoluteBarrier(),
                        payload.reflectProjectiles()
                )
        );
        boolean overlaps = barriers.values().stream()
                .filter(other -> !other.id().equals(updated.id()))
                .anyMatch(updated::overlaps);
        if (overlaps) {
            player.displayClientMessage(Component.translatable("message.interiorveil.config_overlap"), true);
            return;
        }

        if (updated.radius() > current.radius()) {
            platformGenerator.addTask(updated.getPocketX(), updated.getPocketZ(), updated.centerY(), updated.radius());
        }

        if (current.advanced().absoluteBarrier() != updated.advanced().absoluteBarrier()) {
            if (updated.advanced().absoluteBarrier()) {
                copyBeaconToSource(updated);
            } else {
                deleteBeaconFromSource(updated);
            }
        }

        barriers.put(updated.id(), updated);
        // 절대 방어막 캐시 갱신
        if (updated.advanced().absoluteBarrier()) {
            absoluteBarrierCache.add(updated.id());
        } else {
            absoluteBarrierCache.remove(updated.id());
        }
        save();
        player.displayClientMessage(Component.translatable("message.interiorveil.config_saved"), true);
    }

    public void applyAdminAction(ServerPlayer player, VeilAdminActionPayload payload) {
        if ("open_config_screen".equals(payload.action())) {
            // 플레이어가 소유하거나 관리 권한이 있는 결계 탐색 (가장 가까운 결계 우선)
            VeilBarrier targetBarrier = barriers.values().stream()
                    .filter(b -> b.owner().equals(player.getUUID()) || player.hasPermissions(2))
                    .min(Comparator.comparingDouble(b -> {
                        if (player.level().dimension().equals(InteriorVeil.POCKET_LEVEL)) {
                            return BarrierGeometry.horizontalDistanceSquared(player.getX(), player.getZ(), b.getPocketX() + 0.5, b.getPocketZ() + 0.5);
                        } else {
                            return BarrierGeometry.horizontalDistanceSquared(player.getX(), player.getZ(), b.centerX() + 0.5, b.centerZ() + 0.5);
                        }
                    }))
                    .orElse(null);

            if (targetBarrier != null) {
                openConfig(player, targetBarrier, true);
            } else {
                player.displayClientMessage(
                        Component.literal("§c소유하거나 관리 권한이 있는 결계가 없습니다. 먼저 결계 열쇠로 신호기를 활성화하세요."),
                        true
                );
            }
            return;
        }

        VeilBarrier current = null;
        if (payload.barrierId() != null && !payload.barrierId().equals(new UUID(0L, 0L))) {
            current = barriers.get(payload.barrierId());
        }
        if (current == null) {
            current = barriers.values().stream()
                    .filter(b -> b.owner().equals(player.getUUID()) || player.hasPermissions(2))
                    .filter(b -> b.id().equals(pocketAssignments.get(player.getUUID())) || holdsKeyFor(player, b))
                    .findFirst()
                    .orElse(null);
        }
        if (current == null) {
            current = barriers.values().stream()
                    .filter(b -> b.owner().equals(player.getUUID()) || player.hasPermissions(2))
                    .findFirst()
                    .orElse(null);
        }
        if (current == null) {
            return;
        }
        if (!canManage(player, current)) {
            player.displayClientMessage(Component.translatable("message.interiorveil.config_denied"), true);
            return;
        }

        VeilAdvancedSettings advanced = current.advanced();
        switch (payload.action()) {
            case "overview" -> {
                java.util.List<dev.minse.interiorveil.network.VeilOverviewPayload.VeilSummary> summaries = barriers.values().stream()
                        .filter(barrier -> barrier.owner().equals(player.getUUID()) || player.hasPermissions(2))
                        .map(barrier -> {
                            ServerLevel source = server.getLevel(barrier.sourceKey());
                            int level = source == null ? 0 : beaconLevel(source, barrier.center());
                            String recent = recentEntries.getOrDefault(barrier.id(), new ArrayDeque<String>()).peekFirst();
                            return new dev.minse.interiorveil.network.VeilOverviewPayload.VeilSummary(
                                    barrier.id(),
                                    barrier.name(),
                                    barrier.sourceDimension(),
                                    barrier.centerX(), barrier.centerY(), barrier.centerZ(),
                                    level,
                                    barrier.securityMode(),
                                    recent == null ? "" : recent
                            );
                        })
                        .toList();
                ServerPlayNetworking.send(player, new dev.minse.interiorveil.network.VeilOverviewPayload(summaries));
                return;
            }
            case "teleport" -> {
                try {
                    UUID targetId = UUID.fromString(payload.value());
                    VeilBarrier targetVeil = barriers.get(targetId);
                    if (targetVeil != null && (targetVeil.owner().equals(player.getUUID()) || player.hasPermissions(2))) {
                        if (targetVeil.advanced().absoluteBarrier()) {
                            player.displayClientMessage(Component.translatable("message.interiorveil.absolute_barrier_active"), true);
                            return;
                        }
                        ServerLevel pocket = server.getLevel(InteriorVeil.POCKET_LEVEL);
                        if (pocket != null) {
                            ServerLevel currentLevel = (ServerLevel) player.level();
                            pocketAssignments.put(player.getUUID(), targetId);
                            double targetX = targetVeil.getPocketX() + 0.5;
                            double targetY = targetVeil.centerY() + 1.0;
                            double targetZ = targetVeil.getPocketZ() + 0.5;
                            player.teleportTo(
                                    pocket,
                                    targetX,
                                    targetY,
                                    targetZ,
                                    java.util.Set.of(),
                                    player.getYRot(),
                                    player.getXRot(),
                                    false
                            );
                            teleportPets(player, currentLevel, pocket, targetX, targetY, targetZ);
                        }
                    }
                } catch (IllegalArgumentException ignored) {}
                return;
            }
            case "delete_veil" -> {
                try {
                    UUID targetId = UUID.fromString(payload.value());
                    VeilBarrier targetVeil = barriers.get(targetId);
                    if (targetVeil != null && (targetVeil.owner().equals(player.getUUID()) || player.hasPermissions(2))) {
                        // 신호기 및 피라미드 아이템 지급
                        InteriorVeil.giveItemToPlayer(player, new ItemStack(Blocks.BEACON));
                        ServerLevel pocket = server.getLevel(InteriorVeil.POCKET_LEVEL);
                        if (pocket != null) {
                            BlockPos pocketPos = new BlockPos(targetVeil.getPocketX(), targetVeil.centerY(), targetVeil.getPocketZ());
                            pocket.setBlock(pocketPos, Blocks.AIR.defaultBlockState(), 3);
                            for (int tier = 1; tier <= 4; tier++) {
                                int py = targetVeil.centerY() - tier;
                                for (int x = targetVeil.getPocketX() - tier; x <= targetVeil.getPocketX() + tier; x++) {
                                    for (int z = targetVeil.getPocketZ() - tier; z <= targetVeil.getPocketZ() + tier; z++) {
                                        BlockPos p = new BlockPos(x, py, z);
                                        BlockState tState = pocket.getBlockState(p);
                                        if (isBeaconBaseBlock(tState)) {
                                            InteriorVeil.giveItemToPlayer(player, new ItemStack(tState.getBlock().asItem()));
                                            pocket.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                                        }
                                    }
                                }
                            }
                        }
                        deleteBeaconFromSource(targetVeil);
                        removeBarrier(targetVeil);
                        save();
                        player.displayClientMessage(Component.translatable("message.interiorveil.removed"), true);
                    }
                } catch (IllegalArgumentException ignored) {}
                return;
            }
            case "allow", "deny" -> {
                ServerPlayer target = server.getPlayerList().getPlayerByName(payload.value());
                if (target == null) {
                    player.displayClientMessage(Component.translatable("message.interiorveil.player_offline"), true);
                    return;
                }
                advanced = advanced.withAccess(
                        target.getUUID(),
                        target.getGameProfile().name(),
                        payload.action().equals("allow")
                );
            }
            case "revoke_keys" -> advanced = advanced.revokeAllKeys();
            case "laser_fire" -> {
                fireCoordinateLaser(player, current, payload.value());
                return;
            }
            case "map_request" -> {
                sendTargetMap(player, current);
                return;
            }
            default -> {
                return;
            }
        }

        VeilBarrier updated = current.withAdvanced(advanced);
        barriers.put(updated.id(), updated);
        save();
        sendConfig(player, updated, false);
        player.displayClientMessage(Component.translatable("message.interiorveil.admin_saved"), true);
    }

    private boolean canManage(ServerPlayer player, VeilBarrier barrier) {
        if (barrier == null) return false;
        if (!barrier.owner().equals(player.getUUID()) && !player.hasPermissions(2)) return false;

        boolean insidePocket = player.level().dimension().equals(InteriorVeil.POCKET_LEVEL)
                && player.distanceToSqr(barrier.getPocketX() + 0.5, barrier.centerY() + 0.5, barrier.getPocketZ() + 0.5) <= 64.0;
        
        boolean holdingKey = holdsKeyFor(player, barrier);
        
        return insidePocket || holdingKey;
    }

    InteractionResult openConfig(ServerPlayer player, VeilBarrier barrier, boolean forceOpen) {
        if (!barrier.owner().equals(player.getUUID()) && !player.hasPermissions(2)) {
            player.displayClientMessage(Component.translatable("message.interiorveil.owner"), true);
            return InteractionResult.FAIL;
        }
        if (!ServerPlayNetworking.canSend(player, VeilConfigPayload.TYPE)) {
            return InteractionResult.FAIL;
        }
        sendConfig(player, barrier, forceOpen);
        return InteractionResult.PASS;
    }

    private void sendConfig(ServerPlayer player, VeilBarrier barrier, boolean forceOpen) {
        ServerPlayNetworking.send(player, new VeilConfigPayload(
                barrier.id(),
                barrier.name(),
                barrier.radius(),
                barrier.height(),
                barrier.fogMargin(),
                barrier.fogDistance(),
                barrier.fogFadeTicks(),
                barrier.navigationRange(),
                barrier.boundaryVisible(),
                barrier.boundaryColor(),
                barrier.navigationColor(),
                barrier.securityMode(),
                barrier.beaconColor(),
                barrier.advanced().accessStart(),
                barrier.advanced().accessEnd(),
                barrier.advanced().boundaryDensity(),
                barrier.advanced().boundarySize(),
                barrier.advanced().navigationDensity(),
                barrier.advanced().navigationSize(),
                barrier.advanced().requireBeaconPower(),
                forceOpen,
                barrier.advanced().disableFog(),
                barrier.advanced().fogColor(),
                barrier.advanced().attackMode(),
                barrier.advanced().attackTargetX(),
                barrier.advanced().attackTargetY(),
                barrier.advanced().attackTargetZ(),
                barrier.advanced().strikeRadius(),
                barrier.advanced().absoluteBarrier(),
                barrier.advanced().reflectProjectiles(),
                String.join(", ", barrier.advanced().allowedPlayers().values())
        ));
    }

    public void tick() {
        tickCounter++;
        if (tickCounter % 20 == 0) {
            removeInvalidBarriers();
            tickTemporaryShieldDomes();
            synchronizeEnvironment();
            tickAttackModes();
            tickStrikeBeams();
            tickBeaconCores();
        }
        tickPendingStrikes();
        // 지연 블록 제거 - 틱당 최대 4096블록 처리 (서버 프리즈 방지)
        int clearBudget = 4096;
        while (clearBudget > 0 && !pendingClearTasks.isEmpty()) {
            Runnable task = pendingClearTasks.poll();
            if (task != null) {
                task.run();
                clearBudget -= 256; // 256블록 단위 추정
            }
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            tickPlayer(player);
        }

        if (tickCounter % 5 == 0) {
            emitNavigationParticles();
            emitVisibleBoundaries();
        }

        if (tickCounter % VeilConstants.MIRROR_INTERVAL_TICKS == 0) {
            VeilNetworking.sendMirrors(server, barriers.values(), pocketAssignments);
        }
        VeilShellSynchronizer.tick(server, barriers.values());
        platformGenerator.tick(server.getLevel(InteriorVeil.POCKET_LEVEL));
    }

    public void save() {
        VeilStore.save(server, barriers.values());
    }

    private InteractionResult createAt(ServerPlayer player, ServerLevel level, BlockPos position, InteractionHand hand) {
        if (!level.dimension().equals(Level.OVERWORLD)) {
            player.displayClientMessage(Component.translatable("message.interiorveil.overworld_only"), true);
            return InteractionResult.FAIL;
        }
        int beaconLevel = beaconLevel(level, position);
        if (beaconLevel == 0) {
            player.displayClientMessage(Component.translatable("message.interiorveil.beacon_inactive"), true);
            return InteractionResult.FAIL;
        }

        int maxGridX = barriers.values().stream()
                .mapToInt(VeilBarrier::getPocketX)
                .max()
                .orElse(0);
        int gridX = maxGridX + 10000;
        int gridZ = 0;

        int initialRadius = maxRadiusForBeaconLevel(beaconLevel);
        int initialHeight = (beaconLevel == 4) ? VeilConstants.MAX_HEIGHT : VeilConstants.DEFAULT_HEIGHT;

        VeilBarrier candidate = VeilBarrier.create(player.getUUID(), level.dimension(), position, gridX, gridZ)
                .withRadius(initialRadius)
                .withHeight(initialHeight);

        if (server.getLevel(InteriorVeil.POCKET_LEVEL) == null) {
            player.displayClientMessage(Component.translatable("message.interiorveil.no_pocket"), true);
            return InteractionResult.FAIL;
        }

        if (!moveBeaconToPocket(level, candidate)) {
            player.displayClientMessage(Component.translatable("message.interiorveil.no_pocket"), true);
            return InteractionResult.FAIL;
        }

        barriers.put(candidate.id(), candidate);
        platformGenerator.addTask(candidate.getPocketX(), candidate.getPocketZ(), candidate.centerY(), VeilConstants.MAX_RADIUS);
        VeilKeyBinding.bind(player.getItemInHand(hand), candidate);
        save();
        player.displayClientMessage(Component.translatable("message.interiorveil.created"), true);
        return InteractionResult.SUCCESS;
    }

    private InteractionResult bindExistingKey(ServerPlayer player, InteractionHand hand, VeilBarrier barrier) {
        if (!barrier.owner().equals(player.getUUID()) && !player.hasPermissions(2)) {
            player.displayClientMessage(Component.translatable("message.interiorveil.owner"), true);
            return InteractionResult.FAIL;
        }
        VeilKeyBinding.bind(player.getItemInHand(hand), barrier);
        player.displayClientMessage(Component.translatable("message.interiorveil.key_bound"), true);
        return InteractionResult.SUCCESS;
    }

    private InteractionResult removeAt(ServerPlayer player, ServerLevel level, BlockPos position, InteractionHand hand) {
        Optional<VeilBarrier> found = findAt(level, position);
        if (found.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!found.get().owner().equals(player.getUUID()) && !player.hasPermissions(2)) {
            player.displayClientMessage(Component.translatable("message.interiorveil.owner"), true);
            return InteractionResult.FAIL;
        }

        VeilBarrier barrier = found.get();
        PendingRemoval pending = pendingRemovals.get(player.getUUID());
        if (pending == null || !pending.barrierId().equals(barrier.id()) || pending.expiresAtTick() < tickCounter) {
            pendingRemovals.put(player.getUUID(), new PendingRemoval(barrier.id(), tickCounter + 100));
            player.displayClientMessage(Component.translatable("message.interiorveil.remove_confirm"), true);
            return InteractionResult.SUCCESS;
        }
        pendingRemovals.remove(player.getUUID());

        // 신호기 및 피라미드 아이템 지급
        InteriorVeil.giveItemToPlayer(player, new ItemStack(Blocks.BEACON));
        ServerLevel pocket = server.getLevel(InteriorVeil.POCKET_LEVEL);
        if (pocket != null) {
            for (int tier = 1; tier <= 4; tier++) {
                int py = barrier.centerY() - tier;
                for (int x = barrier.getPocketX() - tier; x <= barrier.getPocketX() + tier; x++) {
                    for (int z = barrier.getPocketZ() - tier; z <= barrier.getPocketZ() + tier; z++) {
                        BlockPos p = new BlockPos(x, py, z);
                        BlockState tState = pocket.getBlockState(p);
                        if (isBeaconBaseBlock(tState)) {
                            InteriorVeil.giveItemToPlayer(player, new ItemStack(tState.getBlock().asItem()));
                            pocket.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                }
            }
        }
        deleteBeaconFromSource(barrier);
        clearPocketDimension(barrier);
        removeBarrier(barrier);
        VeilKeyBinding.unbind(player.getItemInHand(hand), barrier.id());
        save();
        player.displayClientMessage(Component.translatable("message.interiorveil.removed"), true);
        return InteractionResult.SUCCESS;
    }

    private Optional<VeilBarrier> findAt(ServerLevel level, BlockPos position) {
        return barriers.values().stream()
                .filter(barrier -> {
                    if (level.dimension().equals(InteriorVeil.POCKET_LEVEL)) {
                        return position.getX() == barrier.getPocketX()
                                && position.getY() == barrier.centerY()
                                && position.getZ() == barrier.getPocketZ();
                    } else if (barrier.sourceKey().equals(level.dimension())) {
                        return barrier.center().equals(position);
                    }
                    return false;
                })
                .findFirst();
    }

    public void onPlayerJoin(ServerPlayer player) {
        tickPlayer(player);
        VeilNetworking.sendMirrors(server, barriers.values(), pocketAssignments);
    }

    private void tickPlayer(ServerPlayer player) {
        TransitionState transition = transitions.get(player.getUUID());
        if (transition != null) {
            tickTransition(player, transition);
            return;
        }

        if (player.level().dimension().equals(InteriorVeil.POCKET_LEVEL)) {
            tickPocketPlayer(player);
        } else {
            tickOutsidePlayer(player);
        }
    }

    private void tickOutsidePlayer(ServerPlayer player) {
        // 1. 오버월드 포스필드 렌더링 (오직 방어 모드일 때만 렌더링)
        if (tickCounter % 5 == 0) {
            java.util.List<dev.minse.interiorveil.network.ForcefieldStatePayload.DomeEntry> domeList = new java.util.ArrayList<>();
            for (VeilBarrier barrier : barriers.values()) {
                if (!barrier.sourceKey().location().equals(player.level().dimension().location())) continue;
                if (!barrier.boundaryVisible()) continue;
                if (!barrier.advanced().absoluteBarrier() && !pendingRemovals.containsKey(barrier.id())) continue;

                double maxDist = Math.max(256.0, barrier.radius() * 4.0);
                if (BarrierGeometry.horizontalDistanceSquared(
                        player.getX(), player.getZ(), barrier.centerX() + 0.5, barrier.centerZ() + 0.5
                ) <= maxDist * maxDist) {
                    int density = Math.max(64, barrier.advanced().boundaryDensity());
                    int color = barrier.boundaryColor() != 0 ? barrier.boundaryColor() : (pendingRemovals.containsKey(barrier.id()) ? 0xFFD700 : 0x33FFFF);
                    domeList.add(new dev.minse.interiorveil.network.ForcefieldStatePayload.DomeEntry(
                            barrier.centerX(),
                            barrier.centerY(),
                            barrier.centerZ(),
                            barrier.radius(),
                            color,
                            density
                    ));
                }
            }
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new dev.minse.interiorveil.network.ForcefieldStatePayload(domeList));
        }

        // 2. 오버월드 안개(Fog) 상태 계산 및 패킷 전송
        // 규칙: 키를 손에 들고 있지 않으면 결계 내부가 은폐 안개로 보이고, 키를 '손에 직접 들고 있어야만' 안개가 사라져 맑게 보임
        // 히스테리시스(완충 여유 구역 6.0m)를 적용하여 경계선에서의 상태 요동/깜빡임 100% 방지
        if (tickCounter % 5 == 0) {
            VeilBarrier insideBarrier = barriers.values().stream()
                    .filter(barrier -> barrier.sourceKey().location().equals(player.level().dimension().location()))
                    .filter(barrier -> {
                        double distSq = BarrierGeometry.horizontalDistanceSquared(
                                player.getX(), player.getZ(), barrier.centerX() + 0.5, barrier.centerZ() + 0.5
                        );
                        double r = barrier.radius() + barrier.fogMargin() + 4.0;
                        return distSq <= r * r;
                    })
                    .findFirst()
                    .orElse(null);

            boolean hasKey = insideBarrier != null && holdsKeyFor(player, insideBarrier);
            boolean showOverworldFog = insideBarrier != null && !insideBarrier.advanced().disableFog() && !hasKey;

            if (showOverworldFog) {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new dev.minse.interiorveil.network.FogStatePayload(
                        true,
                        insideBarrier.fogDistance(),
                        insideBarrier.fogFadeTicks(),
                        insideBarrier.advanced().fogColor()
                ));

                // 실시간 침입자 경보 (Intruder Alert System): 5초(100틱)마다 소유자 및 허용 플레이어에게 알림
                if (tickCounter % 100 == 0 && !isAttackFriendly(player, insideBarrier)) {
                    for (ServerPlayer notifyTarget : server.getPlayerList().getPlayers()) {
                        if (isAttackFriendly(notifyTarget, insideBarrier)) {
                            notifyTarget.displayClientMessage(
                                    Component.literal(String.format("§c⚠️ [침입자 경보] §e%s§7 님이 결계 외곽에 접근했습니다! §c[X: %d, Z: %d]",
                                            player.getGameProfile().name(), (int) player.getX(), (int) player.getZ())),
                                    true
                            );
                            notifyTarget.playNotifySound(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.PLAYERS, 1.5F, 0.6F);
                        }
                    }
                }
            } else {
                net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new dev.minse.interiorveil.network.FogStatePayload(
                        false,
                        32,
                        20,
                        0xFFFFFF
                ));
            }
        }

        // 3. 결계 진입 및 튕김 처리
        for (VeilBarrier barrier : barriers.values()) {
            if (!barrier.sourceKey().equals(player.level().dimension())) continue;

            // 🛡️ [5분 임시 방어 돔 특수 로직: 30초 동안 아군 진입 가능, 이후 완전 밀폐(나가기만 가능)]
            if (pendingRemovals.containsKey(barrier.id())) {
                PendingRemoval removal = pendingRemovals.get(barrier.id());
                long createdTick = removal.expiresAtTick() - 6000;
                long elapsedTicks = tickCounter - createdTick;
                boolean isLockdown = elapsedTicks > 600; // 30초(600틱) 이후 완전 밀폐

                double cx = barrier.centerX() + 0.5;
                double cz = barrier.centerZ() + 0.5;
                double prevDistSq = BarrierGeometry.horizontalDistanceSquared(player.xo, player.zo, cx, cz);
                double currDistSq = BarrierGeometry.horizontalDistanceSquared(player.getX(), player.getZ(), cx, cz);
                double r = barrier.radius();

                // 30초 정각 밀폐 사운드 및 공지 (주변 64m)
                if (elapsedTicks == 600 && currDistSq <= 64.0 * 64.0) {
                    player.displayClientMessage(
                            Component.literal("🔒 [방어 돔] 30초가 경과하여 돔이 완전 밀폐되었습니다! (이제 나가기만 가능합니다)")
                                    .withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD),
                            true
                    );
                    ServerLevel sl = (ServerLevel) player.level();
                    sl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.IRON_DOOR_CLOSE, SoundSource.PLAYERS, 1.5F, 0.8F);
                    sl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5F, 1.8F);
                }

                // 1) 30초 이전(대피 페이즈): 아군은 자유 진입/퇴장, 적대는 차단
                if (!isLockdown) {
                    int remainingSec = (int) Math.max(1, (600 - elapsedTicks) / 20);
                    if (currDistSq <= (r + 0.5) * (r + 0.5)) {
                        if (isAttackFriendly(player, barrier)) {
                            if (tickCounter % 20 == 0) {
                                player.displayClientMessage(
                                        Component.literal(String.format("🛡️ [방어 돔] 대피 모드 진행 중 (밀폐까지 %d초 남음)", remainingSec))
                                                .withStyle(net.minecraft.ChatFormatting.GOLD),
                                        true
                                );
                            }
                            continue; // 아군은 안전하게 입장 및 체류 허용
                        } else {
                            // 적대 플레이어는 진입 차단 및 튕김
                            double dx = player.getX() - cx;
                            double dz = player.getZ() - cz;
                            double len = Math.sqrt(dx * dx + dz * dz);
                            if (len < 0.01) { dx = 1.0; dz = 0.0; len = 1.0; }
                            player.setDeltaMovement((dx / len) * 1.3, 0.2, (dz / len) * 1.3);
                            player.hurtMarked = true;
                            return;
                        }
                    }
                    continue;
                }

                // 2) 30초 이후(밀폐 페이즈): "나가기만 가능, 들어오기는 누구도 절대 불가"
                if (currDistSq <= (r + 0.8) * (r + 0.8)) {
                    // 이전 틱에 이미 돔 내부 깊숙이(r - 0.8m) 있었고 밖으로 걸어나가는 중이라면 자유 탈출 허용!
                    if (prevDistSq < (r - 0.8) * (r - 0.8)) {
                        continue;
                    }

                    // 밖에서 안으로 들어가려 하거나 경계면에 접촉한 경우: 전원 밖으로 강력 튕겨냄!
                    double dx = player.getX() - cx;
                    double dz = player.getZ() - cz;
                    double len = Math.sqrt(dx * dx + dz * dz);
                    if (len < 0.01) { dx = 1.0; dz = 0.0; len = 1.0; }
                    player.setDeltaMovement((dx / len) * 1.4, 0.2, (dz / len) * 1.4);
                    player.hurtMarked = true;

                    if (tickCounter % 20 == 0) {
                        player.displayClientMessage(
                                Component.literal("⛔ [방어 돔] 완전 밀폐 구역입니다. 밖에서는 진입할 수 없습니다! (내부 탈출만 가능)")
                                        .withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD),
                                true
                        );
                        ServerLevel sl = (ServerLevel) player.level();
                        sl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 1.2F, 1.3F);
                        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(), 8, 0.3, 0.5, 0.3, 0.05);
                    }
                    return;
                }
                continue;
            }

            // 일반 신호기 결계 처리
            if (barrier.contains(player.getX(), player.getY(), player.getZ(), 0, false)) {
                if (canEnter(player, barrier)) {
                    beginTransition(player, barrier, TransitionState.Direction.ENTER);
                    return;
                } else if (barrier.advanced().absoluteBarrier()) {
                    if (holdsKeyFor(player, barrier) || isAttackFriendly(player, barrier)) {
                        continue; // 열쇠 소지자 또는 소유자/아군은 내부에서 튕겨나가지 않고 안전하게 체류 및 보호
                    }
                    double dx = player.getX() - barrier.centerX();
                    double dz = player.getZ() - barrier.centerZ();
                    double len = Math.sqrt(dx * dx + dz * dz);
                    if (len > 0) {
                        player.setDeltaMovement((dx / len) * 1.2, 0.2, (dz / len) * 1.2);
                        player.hurtMarked = true;
                    }
                    return;
                }
            }
        }
    }

    private void tickPocketPlayer(ServerPlayer player) {
        VeilBarrier barrier = assignedBarrier(player).orElse(null);
        if (barrier == null) {
            ServerLevel fallback = server.overworld();
            player.teleportTo(
                    fallback,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    Set.<Relative>of(),
                    player.getYRot(),
                    player.getXRot(),
                    false
            );
            return;
        }
        pocketAssignments.put(player.getUUID(), barrier.id());
        boolean outsideBoundary = !barrier.contains(
                player.getX(),
                player.getY(),
                player.getZ(),
                VeilConstants.HYSTERESIS,
                true
        );
        if (outsideBoundary) {
            beginTransition(player, barrier, TransitionState.Direction.EXIT);
        }

        // 포켓 차원 규칙: 결계 내부에서는 안개가 없고, 경계 밖(외부)에만 안개가 생김
        if (tickCounter % 5 == 0) {
            ServerPlayNetworking.send(player, new dev.minse.interiorveil.network.ForcefieldStatePayload(
                    barrier.boundaryVisible(),
                    barrier.getPocketX(),
                    barrier.getPocketZ(),
                    barrier.centerY(),
                    barrier.radius(),
                    barrier.boundaryColor(),
                    barrier.advanced().boundaryDensity()
            ));

            if (tickCounter % 5 == 0) {
                boolean inPocketInside = BarrierGeometry.horizontalDistanceSquared(
                        player.getX(), player.getZ(), barrier.getPocketX() + 0.5, barrier.getPocketZ() + 0.5
                ) <= Math.pow(barrier.radius() + barrier.fogMargin(), 2);

                // 포켓 차원 규칙: 내부에서는 무조건 안개 없음(키 필요 없음), 결계 경계 밖(외부)으로 나갈 때만 안개 발생
                if (!inPocketInside && !barrier.advanced().disableFog()) {
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new dev.minse.interiorveil.network.FogStatePayload(
                            true,
                            barrier.fogDistance(),
                            barrier.fogFadeTicks(),
                            barrier.advanced().fogColor()
                    ));
                } else {
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new dev.minse.interiorveil.network.FogStatePayload(
                            false,
                            32,
                            20,
                            0xFFFFFF
                    ));
                }
            }
        }
    }

    private Optional<VeilBarrier> assignedBarrier(ServerPlayer player) {
        UUID assignedId = pocketAssignments.get(player.getUUID());
        if (assignedId != null && barriers.containsKey(assignedId)) {
            return Optional.of(barriers.get(assignedId));
        }
        return barriers.values().stream()
                .min(Comparator.comparingDouble(barrier -> BarrierGeometry.horizontalDistanceSquared(
                        player.getX(),
                        player.getZ(),
                        barrier.getPocketX(),
                        barrier.getPocketZ()
                )));
    }

    private void beginTransition(ServerPlayer player, VeilBarrier barrier, TransitionState.Direction direction) {
        transitions.put(
                player.getUUID(),
                new TransitionState(barrier.id(), direction, VeilConstants.TRANSITION_TICKS)
        );
    }

    private void tickTransition(ServerPlayer player, TransitionState transition) {
        VeilBarrier barrier = barriers.get(transition.barrierId());
        if (barrier == null) {
            transitions.remove(player.getUUID());
            return;
        }
        if (transition.ticksRemaining() > 0) {
            transitions.put(player.getUUID(), transition.tick());
            return;
        }

        ServerLevel destination = transition.direction() == TransitionState.Direction.ENTER
                ? server.getLevel(InteriorVeil.POCKET_LEVEL)
                : server.getLevel(barrier.sourceKey());
        if (destination == null) {
            transitions.remove(player.getUUID());
            return;
        }

        double offsetX = barrier.getPocketX() - barrier.centerX();
        double offsetZ = barrier.getPocketZ() - barrier.centerZ();
        double targetX = player.getX();
        double targetZ = player.getZ();

        if (transition.direction() == TransitionState.Direction.ENTER) {
            targetX += offsetX;
            targetZ += offsetZ;
        } else {
            targetX -= offsetX;
            targetZ -= offsetZ;
        }

        Vec3 safe = safePosition(destination, targetX, player.getY(), targetZ);
        player.teleportTo(
                destination,
                safe.x,
                safe.y,
                safe.z,
                Set.<Relative>of(),
                player.getYRot(),
                player.getXRot(),
                false
        );

        // 애완동물(길들인 동물) 함께 이동
        ServerLevel sourceLevel = transition.direction() == TransitionState.Direction.ENTER
                ? server.getLevel(barrier.sourceKey())
                : server.getLevel(InteriorVeil.POCKET_LEVEL);
        if (sourceLevel != null) {
            teleportPets(player, sourceLevel, destination, safe.x, safe.y, safe.z);
        }

        if (transition.direction() == TransitionState.Direction.ENTER) {
            pocketAssignments.put(player.getUUID(), barrier.id());
            ArrayDeque<String> entries = recentEntries.computeIfAbsent(barrier.id(), ignored -> new ArrayDeque<String>());
            entries.addFirst(player.getGameProfile().name() + " @ " + player.level().getDayTime());
            while (entries.size() > 5) {
                entries.removeLast();
            }
        } else {
            pocketAssignments.remove(player.getUUID());
        }
        transitions.remove(player.getUUID());
    }

    static boolean isHoldingInHandKeyFor(ServerPlayer player, VeilBarrier barrier) {
        return keyMatches(player.getMainHandItem(), barrier) || keyMatches(player.getOffhandItem(), barrier);
    }

    static boolean holdsKeyFor(ServerPlayer player, VeilBarrier barrier) {
        // 인벤토리 전체(핫바, 메인 인벤토리, 오프핸드) 검색
        net.minecraft.world.entity.player.Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (keyMatches(inv.getItem(i), barrier)) {
                return true;
            }
        }
        return false;
    }

    /** InteriorVeil 이벤트 핸들러에서 개방적으로 호출할 수 있는 키 확인 */
    public boolean holdsKeyForBarrier(ServerPlayer player, VeilBarrier barrier) {
        return holdsKeyFor(player, barrier);
    }

    static boolean keyMatches(net.minecraft.world.item.ItemStack stack, VeilBarrier barrier) {
        return VeilKeyBinding.barrierId(stack)
                .filter(barrier.id()::equals)
                .isPresent()
                && VeilKeyBinding.revision(stack) == barrier.advanced().keyRevision();
    }

    public boolean canEnter(ServerPlayer player, VeilBarrier barrier) {
        if (barrier.advanced().absoluteBarrier()) {
            // 절대 방어막 모드에서는 포켓 차원으로 진입하지 않음
            return false;
        }
        // 손(메인핸드 또는 오프핸드)에 해당 결계의 열쇠를 직접 들고 있을 때만 포켓 차원 입장
        return keyMatches(player.getMainHandItem(), barrier) || keyMatches(player.getOffhandItem(), barrier);
    }

    private boolean isBarrierPowered(VeilBarrier barrier) {
        if (!barrier.advanced().requireBeaconPower()) {
            return true;
        }
        ServerLevel pocket = server.getLevel(InteriorVeil.POCKET_LEVEL);
        BlockPos pocketPos = new BlockPos(barrier.getPocketX(), barrier.centerY(), barrier.getPocketZ());
        return pocket != null && beaconLevel(pocket, pocketPos) > 0;
    }

    private static int beaconLevel(ServerLevel level, BlockPos position) {
        int result = 0;
        for (int layer = 1; layer <= 4; layer++) {
            int y = position.getY() - layer;
            boolean valid = true;
            for (int x = position.getX() - layer; x <= position.getX() + layer && valid; x++) {
                for (int z = position.getZ() - layer; z <= position.getZ() + layer; z++) {
                    if (!isBeaconBaseBlock(level.getBlockState(new BlockPos(x, y, z)))) {
                        valid = false;
                        break;
                    }
                }
            }
            if (!valid) {
                break;
            }
            result = layer;
        }
        return result;
    }

    private static int maxRadiusForBeaconLevel(int level) {
        return switch (level) {
            case 1 -> 70;
            case 2 -> 80;
            case 3 -> 96;
            case 4 -> 128;
            default -> 50;
        };
    }

    private void removeInvalidBarriers() {
        ServerLevel pocket = server.getLevel(InteriorVeil.POCKET_LEVEL);
        if (pocket == null) {
            return;
        }
        java.util.List<VeilBarrier> invalid = barriers.values().stream()
                .filter(barrier -> {
                    // 5분 임시 방어 돔은 신호기가 없으므로 5분 만료 전까지 삭제 검사에서 제외
                    if (pendingRemovals.containsKey(barrier.id())) {
                        return false;
                    }
                    ServerLevel source = server.getLevel(barrier.sourceKey());
                    if (source == null) {
                        return false; // 차원이 아직 로드되지 않은 경우 삭제하지 않음
                    }
                    BlockPos pocketPos = new BlockPos(barrier.getPocketX(), barrier.centerY(), barrier.getPocketZ());
                    BlockPos sourcePos = barrier.center();

                    // 방어 모드(absoluteBarrier)인 경우: 오버월드 또는 포켓 차원 중 하나라도 신호기가 있으면 유효
                    if (barrier.advanced().absoluteBarrier()) {
                        boolean sourceLoaded = source.isLoaded(sourcePos);
                        boolean pocketLoaded = pocket.isLoaded(pocketPos);
                        // 둘 다 아직 청크가 로드되지 않은 상태라면 삭제를 유예
                        if (!sourceLoaded && !pocketLoaded) {
                            return false;
                        }
                        boolean hasSourceBeacon = sourceLoaded && source.getBlockState(sourcePos).is(Blocks.BEACON);
                        boolean hasPocketBeacon = pocketLoaded && pocket.getBlockState(pocketPos).is(Blocks.BEACON);
                        if (hasSourceBeacon || hasPocketBeacon) {
                            return false; // 유효함
                        }
                        // 두 차원 청크가 모두 로드되었는데 신호기가 둘 다 없다면 삭제
                        return sourceLoaded && pocketLoaded;
                    } else {
                        // 일반 결계인 경우: 포켓 차원 청크가 로드되었을 때만 검사
                        if (!pocket.isLoaded(pocketPos)) {
                            return false;
                        }
                        return !pocket.getBlockState(pocketPos).is(Blocks.BEACON);
                    }
                })
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        invalid.forEach(barrier -> {
            deleteBeaconFromSource(barrier);
            clearPocketDimensionAsync(barrier);
            removeBarrier(barrier);
            absoluteBarrierCache.remove(barrier.id());
        });
        if (!invalid.isEmpty()) {
            save();
        }
    }

    private void tickTemporaryShieldDomes() {
        java.util.List<UUID> expired = new java.util.ArrayList<>();
        for (Map.Entry<UUID, PendingRemoval> entry : pendingRemovals.entrySet()) {
            if (barriers.containsKey(entry.getKey()) && entry.getValue().expiresAtTick() <= tickCounter) {
                expired.add(entry.getKey());
            }
        }
        for (UUID id : expired) {
            VeilBarrier barrier = barriers.remove(id);
            if (barrier != null) {
                absoluteBarrierCache.remove(id);
                pendingRemovals.remove(id);
                ServerLevel source = server.getLevel(barrier.sourceKey());
                if (source != null) {
                    source.playSound(null, barrier.centerX() + 0.5, barrier.centerY(), barrier.centerZ() + 0.5, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 2.0F, 0.8F);
                }
            }
        }
    }

    private void removeBarrier(VeilBarrier barrier) {
        ServerLevel source = server.getLevel(barrier.sourceKey());
        if (source != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player.level().dimension().equals(InteriorVeil.POCKET_LEVEL)
                        && barrier.id().equals(pocketAssignments.get(player.getUUID()))) {
                    double offsetX = barrier.getPocketX() - barrier.centerX();
                    double offsetZ = barrier.getPocketZ() - barrier.centerZ();
                    Vec3 safe = safePosition(source, player.getX() - offsetX, player.getY(), player.getZ() - offsetZ);
                    player.teleportTo(
                            source,
                            safe.x,
                            safe.y,
                            safe.z,
                            Set.<Relative>of(),
                            player.getYRot(),
                            player.getXRot(),
                            false
                    );
                }
            }
        }
        barriers.remove(barrier.id());
        updateChunkForceload(barrier, false);
        pocketAssignments.values().removeIf(barrier.id()::equals);
        transitions.values().removeIf(transition -> transition.barrierId().equals(barrier.id()));
    }

    /** 신호기 파괴 등으로 결계 자체를 삭제하고 저장할 때 사용 (InteriorVeil에서 호출) */
    public void removeBarrierAndSave(VeilBarrier barrier) {
        removeBarrier(barrier);
        absoluteBarrierCache.remove(barrier.id());
        save();
    }

    private void migrateVisibleBeacons() {
        ServerLevel pocket = server.getLevel(InteriorVeil.POCKET_LEVEL);
        if (pocket == null) {
            return;
        }
        for (VeilBarrier barrier : barriers.values()) {
            if (barrier.advanced().absoluteBarrier()) {
                continue; // 절대 방어막은 오버월드 신호기 유지
            }
            ServerLevel source = server.getLevel(barrier.sourceKey());
            BlockPos pocketPos = new BlockPos(barrier.getPocketX(), barrier.centerY(), barrier.getPocketZ());
            if (source == null || pocket.getBlockState(pocketPos).is(Blocks.BEACON)) {
                continue;
            }
            if (source.getBlockState(barrier.center()).is(Blocks.BEACON)
                    && moveBeaconToPocket(source, barrier)) {
                InteriorVeil.LOGGER.info(
                        "Moved legacy visible beacon for veil {} into the pocket dimension",
                        barrier.id()
                );
            }
        }
    }

    private boolean moveBeaconToPocket(ServerLevel source, VeilBarrier barrier) {
        ServerLevel pocket = server.getLevel(InteriorVeil.POCKET_LEVEL);
        BlockPos sourcePos = barrier.center();
        if (pocket == null || !source.getBlockState(sourcePos).is(Blocks.BEACON)) {
            return false;
        }

        int offsetX = barrier.getPocketX() - barrier.centerX();
        int offsetZ = barrier.getPocketZ() - barrier.centerZ();
        BlockPos pocketPos = sourcePos.offset(offsetX, 0, offsetZ);

        // 1. 신호기 블록 포켓 차원으로 복사
        BlockState beaconState = source.getBlockState(sourcePos);
        BlockEntity sourceEntity = source.getBlockEntity(sourcePos);
        CompoundTag data = sourceEntity == null
                ? null
                : sourceEntity.saveWithFullMetadata(source.registryAccess());
        pocket.setBlock(pocketPos, beaconState, 2 | 16);
        if (data != null) {
            BlockEntity copy = BlockEntity.loadStatic(pocketPos, beaconState, data, pocket.registryAccess());
            if (copy != null) {
                pocket.setBlockEntity(copy);
            }
        }

        // 2. 신호기 아래 피라미드 블록(최대 4레이어) 포켓 차원으로 이동 및 오버월드에서 완전 제거
        for (int layer = 1; layer <= 4; layer++) {
            int y = sourcePos.getY() - layer;
            for (int x = sourcePos.getX() - layer; x <= sourcePos.getX() + layer; x++) {
                for (int z = sourcePos.getZ() - layer; z <= sourcePos.getZ() + layer; z++) {
                    BlockPos pSource = new BlockPos(x, y, z);
                    BlockState pState = source.getBlockState(pSource);
                    if (isBeaconBaseBlock(pState)) {
                        BlockPos pPocket = pSource.offset(offsetX, 0, offsetZ);
                        pocket.setBlock(pPocket, pState, 2 | 16);
                        if (!barrier.advanced().absoluteBarrier()) {
                            source.setBlock(pSource, Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                }
            }
        }

        // 3. 일반 결계인 경우 오버월드 신호기 제거 (피라미드 이동 완료 후 제거)
        if (!barrier.advanced().absoluteBarrier()) {
            source.setBlock(sourcePos, Blocks.AIR.defaultBlockState(), 3);
        }

        return true;
    }

    private void clearPocketDimension(VeilBarrier barrier) {
        ServerLevel pocket = server.getLevel(InteriorVeil.POCKET_LEVEL);
        if (pocket == null) {
            return;
        }
        int radius = barrier.radius();
        int pocketX = barrier.getPocketX();
        int pocketZ = barrier.getPocketZ();
        int centerY = barrier.centerY();
        // 피라미드 4레이어(centerY-4)까지 포함한 Y 범위
        int minY = centerY - barrier.height() / 2 - 5;
        int maxY = centerY + barrier.height() / 2;

        for (int y = minY; y <= maxY; y++) {
            for (int x = pocketX - radius; x <= pocketX + radius; x++) {
                for (int z = pocketZ - radius; z <= pocketZ + radius; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!pocket.getBlockState(pos).isAir()) {
                        pocket.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    /** 서버 프리즈 방지를 위한 분할 처리 버전. clearPocketDimension 의 async 래퍼. */
    private void clearPocketDimensionAsync(VeilBarrier barrier) {
        ServerLevel pocket = server.getLevel(InteriorVeil.POCKET_LEVEL);
        if (pocket == null) return;
        int radius = barrier.radius();
        int pocketX = barrier.getPocketX();
        int pocketZ = barrier.getPocketZ();
        int centerY = barrier.centerY();
        int minY = centerY - barrier.height() / 2 - 5;
        int maxY = centerY + barrier.height() / 2;

        // Y 레이어 단위로 분할하여 pendingClearTasks에 추가
        for (int y = minY; y <= maxY; y++) {
            final int finalY = y;
            pendingClearTasks.add(() -> {
                for (int x = pocketX - radius; x <= pocketX + radius; x++) {
                    for (int z = pocketZ - radius; z <= pocketZ + radius; z++) {
                        BlockPos pos = new BlockPos(x, finalY, z);
                        if (!pocket.getBlockState(pos).isAir()) {
                            pocket.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                }
            });
        }
    }

    private void copyBeaconToSource(VeilBarrier barrier) {
        ServerLevel source = server.getLevel(barrier.sourceKey());
        ServerLevel pocket = server.getLevel(InteriorVeil.POCKET_LEVEL);
        BlockPos sourcePos = barrier.center();
        if (source == null || pocket == null) {
            return;
        }

        int offsetX = barrier.getPocketX() - barrier.centerX();
        int offsetZ = barrier.getPocketZ() - barrier.centerZ();
        BlockPos pocketPos = sourcePos.offset(offsetX, 0, offsetZ);

        if (pocket.getBlockState(pocketPos).is(Blocks.BEACON)) {
            BlockState beaconState = pocket.getBlockState(pocketPos);
            BlockEntity pocketEntity = pocket.getBlockEntity(pocketPos);
            CompoundTag data = pocketEntity == null ? null : pocketEntity.saveWithFullMetadata(pocket.registryAccess());
            source.setBlock(sourcePos, beaconState, 2 | 16);
            if (data != null) {
                BlockEntity copy = BlockEntity.loadStatic(sourcePos, beaconState, data, source.registryAccess());
                if (copy != null) {
                    source.setBlockEntity(copy);
                }
            }
        }

        for (int layer = 1; layer <= 4; layer++) {
            int y = sourcePos.getY() - layer;
            for (int x = sourcePos.getX() - layer; x <= sourcePos.getX() + layer; x++) {
                for (int z = sourcePos.getZ() - layer; z <= sourcePos.getZ() + layer; z++) {
                    BlockPos pPocket = new BlockPos(x + offsetX, y, z + offsetZ);
                    BlockState pState = pocket.getBlockState(pPocket);
                    if (isBeaconBaseBlock(pState)) {
                        BlockPos pSource = new BlockPos(x, y, z);
                        source.setBlock(pSource, pState, 2 | 16);
                    }
                }
            }
        }
    }

    public void deleteBeaconFromSource(VeilBarrier barrier) {
        ServerLevel source = server.getLevel(barrier.sourceKey());
        if (source == null) return;
        BlockPos sourcePos = barrier.center();
        if (source.getBlockState(sourcePos).is(Blocks.BEACON)) {
            source.setBlock(sourcePos, Blocks.AIR.defaultBlockState(), 3);
        }
        for (int layer = 1; layer <= 4; layer++) {
            int y = sourcePos.getY() - layer;
            for (int x = sourcePos.getX() - layer; x <= sourcePos.getX() + layer; x++) {
                for (int z = sourcePos.getZ() - layer; z <= sourcePos.getZ() + layer; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (isBeaconBaseBlock(source.getBlockState(p))) {
                        source.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }
    }

    /**
     * 바닐라 및 모드 신호기 베이스 블록(철, 금, 다이아, 에메랄드, 네더라이트, 구리, 자수정, 스컬크 등) 여부 판정.
     */
    private static boolean isBeaconBaseBlock(BlockState state) {
        if (state.is(net.minecraft.tags.BlockTags.BEACON_BASE_BLOCKS)) {
            return true;
        }
        return state.is(Blocks.IRON_BLOCK)
                || state.is(Blocks.GOLD_BLOCK)
                || state.is(Blocks.DIAMOND_BLOCK)
                || state.is(Blocks.EMERALD_BLOCK)
                || state.is(Blocks.NETHERITE_BLOCK)
                || state.is(Blocks.COPPER_BLOCK)
                || state.is(Blocks.WAXED_COPPER_BLOCK)
                || state.is(Blocks.CUT_COPPER)
                || state.is(Blocks.LIGHTNING_ROD)
                || state.is(Blocks.AMETHYST_BLOCK)
                || state.is(Blocks.AMETHYST_CLUSTER)
                || state.is(Blocks.SCULK)
                || state.is(Blocks.SCULK_CATALYST);
    }

    /**
     * 신호기 피라미드 베이스에 스컬크 코어(스컬크, 스컬크 촉매 등)가 장착되어 있는지 검사.
     */
    public boolean hasSculkCore(VeilBarrier barrier) {
        ServerLevel source = server.getLevel(barrier.sourceKey());
        ServerLevel pocket = server.getLevel(InteriorVeil.POCKET_LEVEL);
        return checkBaseBlock(source, barrier.center(), state -> state.is(Blocks.SCULK) || state.is(Blocks.SCULK_CATALYST) || state.is(Blocks.SCULK_SHRIEKER))
                || (pocket != null && checkBaseBlock(pocket, new BlockPos(barrier.getPocketX(), barrier.centerY(), barrier.getPocketZ()), state -> state.is(Blocks.SCULK) || state.is(Blocks.SCULK_CATALYST) || state.is(Blocks.SCULK_SHRIEKER)));
    }

    /**
     * 신호기 피라미드 베이스에 자수정 코어(자수정 블록, 자수정 군집)가 장착되어 있는지 검사.
     */
    public boolean hasAmethystCore(VeilBarrier barrier) {
        ServerLevel source = server.getLevel(barrier.sourceKey());
        ServerLevel pocket = server.getLevel(InteriorVeil.POCKET_LEVEL);
        return checkBaseBlock(source, barrier.center(), state -> state.is(Blocks.AMETHYST_BLOCK) || state.is(Blocks.AMETHYST_CLUSTER))
                || (pocket != null && checkBaseBlock(pocket, new BlockPos(barrier.getPocketX(), barrier.centerY(), barrier.getPocketZ()), state -> state.is(Blocks.AMETHYST_BLOCK) || state.is(Blocks.AMETHYST_CLUSTER)));
    }

    /**
     * 신호기 피라미드 베이스에 구리 코어(구리 블록 계열, 피뢰침)가 장착되어 있는지 검사.
     */
    public boolean hasCopperCore(VeilBarrier barrier) {
        ServerLevel source = server.getLevel(barrier.sourceKey());
        ServerLevel pocket = server.getLevel(InteriorVeil.POCKET_LEVEL);
        return checkBaseBlock(source, barrier.center(), state -> state.is(Blocks.COPPER_BLOCK) || state.is(Blocks.WAXED_COPPER_BLOCK) || state.is(Blocks.LIGHTNING_ROD) || state.is(Blocks.CUT_COPPER))
                || (pocket != null && checkBaseBlock(pocket, new BlockPos(barrier.getPocketX(), barrier.centerY(), barrier.getPocketZ()), state -> state.is(Blocks.COPPER_BLOCK) || state.is(Blocks.WAXED_COPPER_BLOCK) || state.is(Blocks.LIGHTNING_ROD) || state.is(Blocks.CUT_COPPER)));
    }

    private static boolean checkBaseBlock(ServerLevel level, BlockPos beaconPos, java.util.function.Predicate<BlockState> predicate) {
        if (level == null) return false;
        for (int layer = 1; layer <= 4; layer++) {
            int y = beaconPos.getY() - layer;
            for (int x = beaconPos.getX() - layer; x <= beaconPos.getX() + layer; x++) {
                for (int z = beaconPos.getZ() - layer; z <= beaconPos.getZ() + layer; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (level.isLoaded(p) && predicate.test(level.getBlockState(p))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void synchronizeEnvironment() {
        ServerLevel pocket = server.getLevel(InteriorVeil.POCKET_LEVEL);
        ServerLevel source = server.overworld();
        if (pocket == null || barriers.isEmpty()) {
            return;
        }
        pocket.setDayTime(source.getDayTime());
        pocket.setWeatherParameters(0, 40, source.isRaining(), source.isThundering());
    }

    private void tickAttackModes() {
        for (VeilBarrier barrier : barriers.values()) {
            if (!barrier.advanced().attackMode() || !isBarrierPowered(barrier)) {
                continue;
            }
            ServerLevel source = server.getLevel(barrier.sourceKey());
            if (source == null) {
                continue;
            }
            Vec3 center = Vec3.atCenterOf(barrier.center());
            double attackRadius = barrier.radius() + 100.0;
            AABB area = new AABB(center, center).inflate(attackRadius);

            LivingEntity closestTarget = null;
            double closestDistSq = Double.MAX_VALUE;

            for (LivingEntity target : source.getEntitiesOfClass(LivingEntity.class, area,
                    entity -> entity.isAlive()
                            && BarrierGeometry.horizontalDistanceSquared(
                                    entity.getX(), entity.getZ(), center.x, center.z
                            ) <= attackRadius * attackRadius
                            && isTargetableHostile(entity, barrier))) {
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
                damageTarget(source, target, 2.0F);

                double dSq = target.distanceToSqr(center);
                if (dSq < closestDistSq) {
                    closestDistSq = dSq;
                    closestTarget = target;
                }
            }

            // 자동 요격 센트리 (Auto Sentry Turret): 1초마다 가장 가까운 타겟에게 펄스 요격 레이저 발사
            if (closestTarget != null && tickCounter % 20 == 0) {
                damageTarget(source, closestTarget, 10.0F);
                source.playSound(null, BlockPos.containing(center), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 2.0F, 1.8F);
                source.playSound(null, closestTarget.blockPosition(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.WEATHER, 1.5F, 1.5F);

                // 센트리 레이저 궤적 파티클
                Vec3 from = center.add(0, 1.5, 0);
                Vec3 to = closestTarget.getEyePosition();
                Vec3 dir = to.subtract(from);
                double len = dir.length();
                if (len > 0) {
                    Vec3 unit = dir.scale(1.0 / len);
                    for (double d = 0; d < len; d += 0.8) {
                        Vec3 p = from.add(unit.scale(d));
                        source.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.02);
                    }
                }

                // Create / 레드스톤 호환 출력: 신호기 위치 갱신 (Create Redstone Link / 대포 자동 격발기 트리거)
                BlockPos beaconPos = barrier.center();
                source.updateNeighborsAt(beaconPos, Blocks.BEACON);
            }
        }
    }

    /**
     * 신호기 코어 모듈(자수정 코어 등) 틱 효과 처리.
     */
    private void tickBeaconCores() {
        for (VeilBarrier barrier : barriers.values()) {
            if (pendingRemovals.containsKey(barrier.id())) {
                // 임시 방어 돔: 내부 아군에게 저항 II 및 재생 II 방어막 버프 부여
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (barrier.sourceKey().equals(player.level().dimension())
                            && barrier.contains(player.getX(), player.getY(), player.getZ(), 0, false)
                            && isAttackFriendly(player, barrier)) {
                        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 60, 1, false, false, true));
                        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 1, false, false, true));
                    }
                }
            }
            if (!isBarrierPowered(barrier)) continue;
            if (hasAmethystCore(barrier)) {
                // 자수정 코어: 결계 내부의 모든 아군 플레이어에게 재생 II 및 포만감, 흡수 버프 부여
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    boolean insidePocket = player.level().dimension().equals(InteriorVeil.POCKET_LEVEL)
                            && barrier.id().equals(pocketAssignments.get(player.getUUID()));
                    boolean insideOverworld = barrier.sourceKey().equals(player.level().dimension())
                            && barrier.contains(player.getX(), player.getY(), player.getZ(), 0, false);
                    if ((insidePocket || insideOverworld) && isAttackFriendly(player, barrier)) {
                        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 80, 1, false, false, true));
                        player.addEffect(new MobEffectInstance(MobEffects.SATURATION, 40, 0, false, false, false));
                        player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 80, 0, false, false, true));
                    }
                }
            }
        }
    }

    public void fireStrikeFromCommand(ServerPlayer player, int x, int y, int z) {
        fireStrikeFromCommand(player, x, y, z, 0, StrikeFormation.SINGLE);
    }

    public void fireStrikeFromCommand(ServerPlayer player, int x, int y, int z, int strikeType) {
        fireStrikeFromCommand(player, x, y, z, strikeType, StrikeFormation.SINGLE);
    }

    public void fireStrikeFromCommand(ServerPlayer player, int x, int y, int z, int strikeType, StrikeFormation formation) {
        if (formation == null) formation = StrikeFormation.SINGLE;
        final StrikeFormation finalFormation = formation;
        VeilBarrier barrier = barriers.values().stream()
                .filter(b -> b.owner().equals(player.getUUID()) || player.hasPermissions(2))
                .min(Comparator.comparingDouble(b -> BarrierGeometry.horizontalDistanceSquared(
                        player.getX(), player.getZ(), b.centerX(), b.centerZ()
                )))
                .orElse(null);

        if (barrier == null) {
            player.sendSystemMessage(Component.literal("§c귀속되거나 소유한 결계 신호기가 없습니다."));
            return;
        }

        // 공격 모드 활성화 및 목표 설정
        barriers.put(barrier.id(), barrier.withSettings(
                barrier.name(), barrier.radius(), barrier.height(), barrier.fogMargin(),
                barrier.fogDistance(), barrier.fogFadeTicks(), barrier.navigationRange(),
                barrier.boundaryVisible(), barrier.boundaryColor(), barrier.navigationColor(),
                barrier.securityMode(), barrier.beaconColor(),
                new VeilAdvancedSettings(
                        barrier.advanced().keyRevision(), barrier.advanced().allowedPlayers(),
                        barrier.advanced().accessStart(), barrier.advanced().accessEnd(),
                        barrier.advanced().boundaryDensity(), barrier.advanced().boundarySize(),
                        barrier.advanced().navigationDensity(), barrier.advanced().navigationSize(),
                        barrier.advanced().requireBeaconPower(), barrier.advanced().disableFog(),
                        barrier.advanced().fogColor(), true,
                        x, y, z, barrier.advanced().strikeRadius(),
                        barrier.advanced().absoluteBarrier(), barrier.advanced().reflectProjectiles()
                )
        ));
        barrier = barriers.get(barrier.id());

        fireCoordinateLaser(player, barrier, x + "," + y + "," + z + "," + barrier.advanced().strikeRadius() + "," + strikeType + "," + finalFormation.ordinal());
    }

    private void fireCoordinateLaser(ServerPlayer player, VeilBarrier barrier, String value) {
        if (!barrier.advanced().attackMode()) {
            player.displayClientMessage(Component.translatable("message.interiorveil.attack_disabled"), true);
            return;
        }
        String[] parts = value.split(",", -1);
        if (parts.length < 3) {
            return;
        }
        int x;
        int y;
        int z;
        int strikeType = 0; // 0: HE_THERMAL, 1: EMP_PULSE, 2: SUPPLY_POD
        int formationIndex = 0; // 0: SINGLE, 1: CROSS_5, 2: GRID_9, 3: CIRCLE_9, 4: X_CROSS_5, 5: LINE_5, 6: DIAMOND_9
        try {
            x = Integer.parseInt(parts[0]);
            y = Integer.parseInt(parts[1]);
            z = Integer.parseInt(parts[2]);
            if (parts.length >= 4) {
                int radius = Integer.parseInt(parts[3]);
                if (parts.length >= 5) {
                    strikeType = Integer.parseInt(parts[4]);
                }
                if (parts.length >= 6) {
                    formationIndex = Integer.parseInt(parts[5]);
                }
                barriers.put(barrier.id(), barrier.withSettings(
                        barrier.name(), barrier.radius(), barrier.height(), barrier.fogMargin(),
                        barrier.fogDistance(), barrier.fogFadeTicks(), barrier.navigationRange(),
                        barrier.boundaryVisible(), barrier.boundaryColor(), barrier.navigationColor(),
                        barrier.securityMode(), barrier.beaconColor(),
                        new VeilAdvancedSettings(
                                barrier.advanced().keyRevision(), barrier.advanced().allowedPlayers(),
                                barrier.advanced().accessStart(), barrier.advanced().accessEnd(),
                                barrier.advanced().boundaryDensity(), barrier.advanced().boundarySize(),
                                barrier.advanced().navigationDensity(), barrier.advanced().navigationSize(),
                                barrier.advanced().requireBeaconPower(), barrier.advanced().disableFog(),
                                barrier.advanced().fogColor(), barrier.advanced().attackMode(),
                                x, y, z, radius, barrier.advanced().absoluteBarrier(), barrier.advanced().reflectProjectiles()
                        )
                ));
                barrier = barriers.get(barrier.id());
            }
        } catch (NumberFormatException ignored) {
            return;
        }
        ServerLevel source = server.getLevel(barrier.sourceKey());
        if (source == null || (barrier.advanced().requireBeaconPower() && !isBarrierPowered(barrier))) {
            player.displayClientMessage(Component.translatable("message.interiorveil.beacon_inactive"), true);
            return;
        }
        Vec3 origin = Vec3.atCenterOf(barrier.center()).add(0.0, 1.0, 0.0);
        Vec3 mainTarget = new Vec3(x + 0.5, y + 0.5, z + 0.5);
        double distance = origin.distanceTo(mainTarget);
        if (distance > 1024.0 || distance < 1.0 || y < source.getMinY() || y >= source.getMaxY()) {
            player.displayClientMessage(Component.translatable("message.interiorveil.laser_range"), true);
            return;
        }

        // 결계 반경 + 16블럭 안쪽은 폭격 금지
        double horizontalDist = BarrierGeometry.horizontalDistanceSquared(
                x + 0.5, z + 0.5, barrier.centerX() + 0.5, barrier.centerZ() + 0.5);
        if (horizontalDist <= Math.pow(barrier.radius() + 16.0, 2)) {
            player.displayClientMessage(Component.translatable("message.interiorveil.laser_too_close"), true);
            return;
        }

        // 1. 발사 쿨다운 검사 (기본 7초, 구리 코어 장착 시 3초로 가속)
        long cooldownDuration = hasCopperCore(barrier) ? 3000L : 7000L;
        long now = System.currentTimeMillis();
        Long lastTime = strikeCooldowns.get(barrier.id());
        if (lastTime != null && now - lastTime < cooldownDuration) {
            long remSec = (cooldownDuration - (now - lastTime) + 999L) / 1000L;
            player.displayClientMessage(
                    Component.literal(String.format("§c⚠ 궤도 함포 재장전 및 냉각 중입니다! (남은 시간: %d초%s)", remSec, hasCopperCore(barrier) ? " [구리 코어 가속]" : "")),
                    true
            );
            return;
        }

        // 2. 동시 대기 폭격 개수 제한 (서버/클라이언트 과부하 방지: 최대 18발)
        if (pendingStrikes.size() >= 18) {
            player.displayClientMessage(
                    Component.literal("§c⚠ 현재 대기 중인 궤도 폭격이 너무 많습니다! (잠시 후 다시 시도하세요)"),
                    true
            );
            return;
        }

        strikeCooldowns.put(barrier.id(), now);

        StrikeFormation formation = StrikeFormation.byIndex(formationIndex);
        int spacing = Math.max(14, (int) (barrier.advanced().strikeRadius() * 1.5));
        java.util.List<int[]> offsets = formation.getOffsets(spacing);

        String typeLabel = switch (strikeType) {
            case 1 -> "⚡ EMP 펄스";
            case 2 -> "📦 궤도 보급 포드";
            case 3 -> "❄️ 극저온 동결";
            case 4 -> "🕳️ 중력 특이점";
            case 5 -> "☣️ 나노 낙진";
            case 6 -> "🛡️ 방어막 포드";
            default -> "💥 고폭 열폭풍";
        };
        String formLabel = formation.getDisplayName();
        player.displayClientMessage(
                Component.literal(String.format("🛰️ 궤도 폭격 발사 승인: [%s | %s] [X: %d, Y: %d, Z: %d] (총 %d발 연쇄 투하)",
                        typeLabel, formLabel, x, y, z, offsets.size())),
                true
        );

        // 5초(100틱) 후 폭격 예약 (각 타격 지점마다 3틱 시차를 두어 웅장한 연쇄 폭격 실행)
        for (int i = 0; i < offsets.size(); i++) {
            int[] off = offsets.get(i);
            int tx = x + off[0];
            int tz = z + off[1];
            int ty = source.getHeight(Heightmap.Types.MOTION_BLOCKING, tx, tz);
            if (ty <= source.getMinY()) ty = y;

            Vec3 rawTarget = new Vec3(tx + 0.5, ty + 0.5, tz + 0.5);
            Vec3 subTarget = adjustTargetToBarrierDomeSurface(source, rawTarget);
            int executeTick = tickCounter + 100 + (i * 3);
            boolean isPrimary = (i == 0); // 0번째만 대표 카운트다운 및 도달 타이틀 담당
            pendingStrikes.add(new PendingStrike(source.dimension(), origin, subTarget, barrier.id(), executeTick, strikeType, isPrimary, player.getUUID()));
        }

        // 발사 즉시 첫 번째 '5' 카운트다운 타이틀 전송 (1회만 전송)
        net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket initAnim =
                new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(0, 20, 5);
        net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket initTitle =
                new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                        Component.literal("5").withStyle(net.minecraft.ChatFormatting.RED, net.minecraft.ChatFormatting.BOLD)
                );
        net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket initSubtitle =
                new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                        Component.literal("⚠ " + formation.getDisplayName() + " 카운트다운 ⚠").withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD)
                );
        for (ServerPlayer p : source.players()) {
            double distSq = BarrierGeometry.horizontalDistanceSquared(p.getX(), p.getZ(), mainTarget.x, mainTarget.z);
            if (distSq <= 256.0 * 256.0 || holdsKeyFor(p, barrier)) {
                p.connection.send(initAnim);
                p.connection.send(initSubtitle);
                p.connection.send(initTitle);
                p.playNotifySound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(), net.minecraft.sounds.SoundSource.PLAYERS, 1.2F, 1.0F);
            }
        }
    }

    private void tickPendingStrikes() {
        java.util.Iterator<PendingStrike> it = pendingStrikes.iterator();
        while (it.hasNext()) {
            PendingStrike strike = it.next();
            ServerLevel level = server.getLevel(strike.dimension());
            if (level == null) {
                pendingStrikes.remove(strike);
                continue;
            }

            VeilBarrier barrier = barriers.get(strike.barrierId());

            if (tickCounter < strike.executeAtTick()) {
                int ticksLeft = strike.executeAtTick() - tickCounter;

                // 대표(isPrimary) 폭격만 매 초(20틱)마다 화면 중앙에 거대한 빨간색 카운트다운 타이틀 전송 (4, 3, 2, 1)
                if (strike.isPrimary() && ticksLeft < 100 && ticksLeft % 20 == 0 && ticksLeft > 0) {
                    int seconds = ticksLeft / 20;
                    net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket anim =
                            new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(0, 20, 5);
                    net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket title =
                            new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                                    Component.literal(String.valueOf(seconds)).withStyle(
                                            seconds <= 2 ? net.minecraft.ChatFormatting.DARK_RED : net.minecraft.ChatFormatting.RED,
                                            net.minecraft.ChatFormatting.BOLD
                                    )
                            );
                    net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket subtitle =
                            new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                                    Component.literal("⚠ 폭격 카운트다운 ⚠").withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD)
                            );

                    for (ServerPlayer player : level.players()) {
                        double distSq = BarrierGeometry.horizontalDistanceSquared(player.getX(), player.getZ(), strike.target().x, strike.target().z);
                        if (distSq <= 256.0 * 256.0 || (barrier != null && holdsKeyFor(player, barrier))) {
                            player.connection.send(anim);
                            player.connection.send(subtitle);
                            player.connection.send(title);
                            player.playNotifySound(
                                    net.minecraft.sounds.SoundEvents.NOTE_BLOCK_PLING.value(),
                                    net.minecraft.sounds.SoundSource.PLAYERS,
                                    1.2F,
                                    1.0F + (5 - seconds) * 0.25F
                            );
                        }
                    }
                }

                // 경고음 및 파티클 (Telegraphing) - 사운드는 대표 폭격만, 레이저 파티클은 전 탄환 위치에 생성
                if (tickCounter % 5 == 0) {
                    if (strike.isPrimary()) {
                        level.playSound(null, strike.target().x, strike.target().y, strike.target().z,
                                net.minecraft.sounds.SoundEvents.GUARDIAN_ATTACK,
                                net.minecraft.sounds.SoundSource.HOSTILE, 1.0F, 1.5F + (tickCounter % 10) * 0.1F);
                    }
                    
                    DustParticleOptions warningParticle = new DustParticleOptions(0xFF0000, 2.0F);
                    for (int i = 0; i < 20; i++) {
                        double dy = i * 2.0;
                        level.sendParticles(warningParticle, strike.target().x, strike.target().y + dy, strike.target().z, 2, 0.1, 0.1, 0.1, 0);
                    }
                }
                continue;
            }

            pendingStrikes.remove(strike);
            
            // 폭격 도달 알림 타이틀 (대표 폭격 1회만 전송)
            if (strike.isPrimary()) {
                net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket anim =
                        new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(0, 30, 10);
                net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket title =
                        new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                                Component.literal("💥 폭격 도달 💥").withStyle(net.minecraft.ChatFormatting.DARK_RED, net.minecraft.ChatFormatting.BOLD)
                        );
                for (ServerPlayer player : level.players()) {
                    double distSq = BarrierGeometry.horizontalDistanceSquared(player.getX(), player.getZ(), strike.target().x, strike.target().z);
                    if (distSq <= 256.0 * 256.0 || (barrier != null && holdsKeyFor(player, barrier))) {
                        player.connection.send(anim);
                        player.connection.send(title);
                    }
                }
            }

            // 실제 폭격 로직 실행
            Vec3 origin = strike.origin();
            Vec3 rawTarget = strike.target();
            Vec3 target = adjustTargetToBarrierDomeSurface(level, rawTarget);
            boolean hitDomeSurface = (target.y > rawTarget.y + 0.1);
            if (barrier == null) continue;

            // 🛡️ 결계/방어 돔 상단 외벽에 폭격이 충돌하여 요격된 경우의 특수 방어막 연출
            if (hitDomeSurface) {
                BlockPos cPos = BlockPos.containing(target);
                level.playSound(null, cPos, SoundEvents.SHIELD_BLOCK.value(), SoundSource.BLOCKS, 8.0F, 1.2F);
                level.playSound(null, cPos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 6.0F, 1.6F);
                for (int i = 0; i < 30; i++) {
                    double angle = i * Math.PI * 2 / 30;
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.x, target.y, target.z, 2, Math.cos(angle) * 2.5, 0.4, Math.sin(angle) * 2.5, 0.15);
                    level.sendParticles(ParticleTypes.END_ROD, target.x, target.y + 0.2, target.z, 1, 0.1, 0.1, 0.1, 0.05);
                }
                for (ServerPlayer sp : level.players()) {
                    if (sp.distanceToSqr(target) <= 128.0 * 128.0) {
                        sp.displayClientMessage(
                                Component.literal(String.format("🛡️ [결계 돔 방어막] 궤도 폭격이 결계 외벽 표면(고도 Y: %d)에서 완벽하게 요격되었습니다!", (int) target.y))
                                        .withStyle(net.minecraft.ChatFormatting.AQUA, net.minecraft.ChatFormatting.BOLD),
                                true
                        );
                    }
                }
            }

            int strikeType = strike.strikeType();
            int bombRadius = barrier.advanced().strikeRadius();

            int kills = 0;
            float totalDamage = 0;
            int debuffedCount = 0;

            // 공격형 탄종(고폭탄, EMP, 동결, 특이점, 낙진)일 때만 관통 레이저 및 착탄 폭발 피해 적용 (보급포드 2 및 방어막포드 6 제외)
            if (strikeType != 2 && strikeType != 6) {
                AABB path = new AABB(origin, target).inflate(3.0);
                Set<UUID> hit = new java.util.HashSet<>();
                for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, path,
                        candidate -> candidate.isAlive() && !isAttackFriendly(candidate, barrier))) {
                    if (distanceToSegmentSquared(entity.position(), origin, target) <= 6.25) {
                        hit.add(entity.getUUID());
                        entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0, false, false));
                        damageTarget(level, entity, 12.0F);
                    }
                }
                AABB impact = new AABB(target, target).inflate(5.0);
                for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, impact,
                        candidate -> candidate.isAlive() && !isAttackFriendly(candidate, barrier))) {
                    if (hit.add(entity.getUUID())) {
                        entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 100, 0, false, false));
                        damageTarget(level, entity, 16.0F);
                    }
                }
            }

            if (strikeType == 1) {
                // [탄종 1: EMP 전자기 펄스탄] - 지형 파괴 없음, 광역 마비 및 전기 방전, 겉날개 무력화
                // * 결계 내부(오버월드 결계 영역 및 포켓 차원)에 있는 모든 대상은 완벽하게 보호됨!
                double empRadius = Math.max(48.0, bombRadius * 3.0);
                AABB empArea = new AABB(target, target).inflate(empRadius);
                for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, empArea,
                        candidate -> candidate.isAlive()
                                && !isProtectedFromThermalShockwave(candidate, barrier)
                                && isTargetableHostile(candidate, barrier))) {
                    boolean wasAlive = entity.isAlive();
                    entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 200, 5, false, false));
                    entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 2, false, false));
                    entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
                    damageTarget(level, entity, 8.0F);
                    totalDamage += 8.0F;
                    debuffedCount++;
                    if (wasAlive && entity.isDeadOrDying()) kills++;
                    if (entity instanceof ServerPlayer sp) {
                        sp.stopFallFlying();
                        sp.displayClientMessage(
                                Component.literal("⚡ 강력한 EMP 전자기 펄스에 피격되어 시스템이 일시 마비되었습니다!")
                                        .withStyle(net.minecraft.ChatFormatting.AQUA, net.minecraft.ChatFormatting.BOLD),
                                true
                        );
                    }
                }

                // EMP 푸른 전기 빔 (color: 0x00E5FF, 지속시간 600틱 = 30초)
                int beamColor = 0x00E5FF;
                sendStrikeBeam(level, target, 600, beamColor);

                // EMP 번개 굉음 및 대규모 전기 스파크 파티클 (상공 200블럭 및 지상)
                BlockPos centerPos = BlockPos.containing(target);
                level.playSound(null, centerPos, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 10.0F, 1.2F);
                level.playSound(null, centerPos, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 8.0F, 1.4F);
                for (int i = 0; i < 60; i++) {
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.x, target.y + 200, target.z, 25, 12.0, 4.0, 12.0, 0.4);
                }
                for (int i = 0; i < 30; i++) {
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.x, target.y + 1, target.z, 15, 6.0, 3.0, 6.0, 0.2);
                }
            } else if (strikeType == 2) {
                // [탄종 2: 궤도 보급 포드 투하] - 전술 보급품 상자 낙하
                BlockPos landPos = BlockPos.containing(target);
                if (level.getBlockState(landPos).canBeReplaced()) {
                    level.setBlock(landPos, Blocks.CHEST.defaultBlockState(), 3);
                    BlockEntity be = level.getBlockEntity(landPos);
                    if (be instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
                        chest.setItem(0, new ItemStack(VeilItems.VEIL_KEY));
                        chest.setItem(4, new ItemStack(Blocks.EMERALD_BLOCK, 4));
                        chest.setItem(11, new ItemStack(net.minecraft.world.item.Items.DIAMOND, 8));
                        chest.setItem(13, new ItemStack(net.minecraft.world.item.Items.NETHERITE_SCRAP, 2));
                        chest.setItem(15, new ItemStack(net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE, 2));
                        chest.setItem(22, new ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING, 1));
                        chest.setItem(26, new ItemStack(net.minecraft.world.item.Items.ENDER_PEARL, 16));
                    }
                }

                level.playSound(null, landPos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 5.0F, 0.8F);
                level.playSound(null, landPos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 6.0F, 1.5F);
                level.sendParticles(ParticleTypes.FIREWORK, target.x, target.y + 1, target.z, 50, 2.0, 2.0, 2.0, 0.15);
                level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, target.x, target.y + 1, target.z, 40, 1.5, 3.0, 1.5, 0.05);
            } else if (strikeType == 3) {
                // [탄종 3: ❄️ 극저온 동결탄 (Cryo)] - 수분 결빙 및 극저온 빙결 제압
                double cryoRadius = Math.max(48.0, bombRadius * 2.8);
                AABB cryoArea = new AABB(target, target).inflate(cryoRadius);
                for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, cryoArea,
                        candidate -> candidate.isAlive()
                                && !isProtectedFromThermalShockwave(candidate, barrier)
                                && isTargetableHostile(candidate, barrier))) {
                    boolean wasAlive = entity.isAlive();
                    entity.setTicksFrozen(360);
                    entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 240, 4, false, false));
                    entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 240, 2, false, false));
                    entity.hurtServer(level, level.damageSources().freeze(), 14.0F);
                    totalDamage += 14.0F;
                    debuffedCount++;
                    if (wasAlive && entity.isDeadOrDying()) kills++;
                }

                // 주변 물 결빙
                int fRad = Math.min(24, (int) (bombRadius * 1.2));
                BlockPos.MutableBlockPos mPos = new BlockPos.MutableBlockPos();
                for (int dx = -fRad; dx <= fRad; dx++) {
                    for (int dz = -fRad; dz <= fRad; dz++) {
                        if (dx * dx + dz * dz > fRad * fRad) continue;
                        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) target.x + dx, (int) target.z + dz);
                        mPos.set((int) target.x + dx, surfaceY - 1, (int) target.z + dz);
                        if (level.getBlockState(mPos).is(Blocks.WATER)) {
                            level.setBlock(mPos, Blocks.FROSTED_ICE.defaultBlockState(), 3);
                        }
                    }
                }

                sendStrikeBeam(level, target, 600, 0x80D8FF);
                BlockPos cPos = BlockPos.containing(target);
                level.playSound(null, cPos, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 8.0F, 0.7F);
                level.playSound(null, cPos, SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 6.0F, 1.2F);
                for (int i = 0; i < 40; i++) {
                    level.sendParticles(ParticleTypes.SNOWFLAKE, target.x, target.y + 1, target.z, 20, 8.0, 3.0, 8.0, 0.05);
                }
            } else if (strikeType == 4) {
                // [탄종 4: 🕳️ 중력 특이점 탄 (Singularity)] - 반경 48m 내 적을 중심점으로 강력 흡입 후 압축 폭발
                double singRadius = 48.0;
                AABB singArea = new AABB(target, target).inflate(singRadius);
                for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, singArea,
                        candidate -> candidate.isAlive()
                                && !isProtectedFromThermalShockwave(candidate, barrier)
                                && isTargetableHostile(candidate, barrier))) {
                    boolean wasAlive = entity.isAlive();
                    Vec3 dir = target.subtract(entity.position());
                    double len = Math.max(0.5, dir.length());
                    entity.setDeltaMovement(dir.scale(1.8 / len).add(0, 0.5, 0));
                    entity.hurtMarked = true;
                    entity.hurtServer(level, level.damageSources().magic(), 26.0F);
                    entity.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 40, 1, false, false));
                    entity.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0, false, false));
                    totalDamage += 26.0F;
                    debuffedCount++;
                    if (wasAlive && entity.isDeadOrDying()) kills++;
                }

                sendStrikeBeam(level, target, 600, 0x9400D3);
                BlockPos cPos = BlockPos.containing(target);
                level.playSound(null, cPos, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 10.0F, 0.5F);
                level.playSound(null, cPos, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 8.0F, 1.4F);
                for (int i = 0; i < 50; i++) {
                    level.sendParticles(ParticleTypes.PORTAL, target.x, target.y + 1, target.z, 30, 6.0, 3.0, 6.0, 0.5);
                }
            } else if (strikeType == 5) {
                // [탄종 5: ☣️ 나노 독소 / 방사능 낙진탄 (Nanite Fallout)] - 지속 독소 안개 지대
                double nanoRadius = Math.max(40.0, bombRadius * 2.5);
                AABB nanoArea = new AABB(target, target).inflate(nanoRadius);
                for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, nanoArea,
                        candidate -> candidate.isAlive()
                                && !isProtectedFromThermalShockwave(candidate, barrier)
                                && isTargetableHostile(candidate, barrier))) {
                    boolean wasAlive = entity.isAlive();
                    entity.addEffect(new MobEffectInstance(MobEffects.WITHER, 300, 1, false, false));
                    entity.addEffect(new MobEffectInstance(MobEffects.POISON, 300, 1, false, false));
                    entity.addEffect(new MobEffectInstance(MobEffects.HUNGER, 300, 2, false, false));
                    entity.hurtServer(level, level.damageSources().magic(), 18.0F);
                    totalDamage += 18.0F;
                    debuffedCount++;
                    if (wasAlive && entity.isDeadOrDying()) kills++;
                }

                net.minecraft.world.entity.AreaEffectCloud cloud = new net.minecraft.world.entity.AreaEffectCloud(level, target.x, target.y + 0.5, target.z);
                cloud.setRadius((float) (bombRadius * 1.2));
                cloud.setDuration(400); // 20초 지속
                cloud.setRadiusPerTick(-0.02F);
                cloud.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1));
                cloud.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1));
                level.addFreshEntity(cloud);

                sendStrikeBeam(level, target, 600, 0x00FF66);
                BlockPos cPos = BlockPos.containing(target);
                level.playSound(null, cPos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 8.0F, 1.1F);
                level.playSound(null, cPos, SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 6.0F, 0.8F);
            } else if (strikeType == 6) {
                // [탄종 6: 🛡️ 궤도 드롭 방어막 포드 (Deployable Shield Pod)] - 착탄 지점에 5분간 임시 방어 돔 소환
                BlockPos podCenter = BlockPos.containing(target);
                spawnDeployableShieldPod(level, podCenter, barrier.owner());
                sendStrikeBeam(level, target, 6000, 0xFFD700);
                level.playSound(null, podCenter, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 10.0F, 1.5F);
                level.playSound(null, podCenter, SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 5.0F, 1.2F);
            } else {
                // [탄종 0: 고폭 열폭풍탄 - 기본값]
                destroyBombRadius(level, target, bombRadius);

                int craterBottomY = Math.max(level.getMinY(), (int) target.y - bombRadius);
                int beamColor = 0xFF2222;
                sendStrikeBeam(level, target, 2400, beamColor);

                emitLaserColumnExplosion(level, target, craterBottomY);
                int[] stats = emitThermalShockwave(level, target, barrier, bombRadius);
                totalDamage += stats[0];
                kills += stats[1];
                debuffedCount += stats[2];
            }

            // 대표 폭격 시 발사자에게 전투 피해 분석 리포트 HUD 패킷 전송
            if (strike.isPrimary() && strike.initiator() != null) {
                ServerPlayer initiator = server.getPlayerList().getPlayer(strike.initiator());
                if (initiator != null) {
                    ServerPlayNetworking.send(initiator, new dev.minse.interiorveil.network.BattleReportPayload(
                            strikeType, kills, totalDamage, debuffedCount, (int) target.x, (int) target.y, (int) target.z
                    ));
                }
            }
        }
    }

    private void sendStrikeBeam(ServerLevel level, Vec3 target, int durationTicks, int color) {
        dev.minse.interiorveil.network.StrikeBeamPayload beamPayload = new dev.minse.interiorveil.network.StrikeBeamPayload(
                target.x, (int) target.y, target.z, durationTicks, color
        );
        for (ServerPlayer player : level.players()) {
            ServerPlayNetworking.send(player, beamPayload);
        }
    }

    private void spawnDeployableShieldPod(ServerLevel level, BlockPos center, UUID owner) {
        UUID tempId = UUID.randomUUID();
        VeilBarrier tempBarrier = new VeilBarrier(
                tempId,
                owner,
                level.dimension().location().toString(),
                center.getX(),
                center.getY(),
                center.getZ(),
                16,
                center.getY() - 16,
                center.getY() + 16,
                VeilConstants.SHELL_DEPTH,
                "🛡️ 5분 전술 방어 돔",
                4,
                32,
                20,
                64,
                true,
                0xFFD700,
                0xFFD700,
                false,
                0xFFD700,
                new VeilAdvancedSettings(
                        1, Map.of(), 0, 24000, 200, 200, 140, 100,
                        false, false, 0xFFD700, false, 0, 0, 0, 0,
                        true, true
                ),
                4,
                center.getX(),
                center.getZ()
        );
        barriers.put(tempId, tempBarrier);
        absoluteBarrierCache.add(tempId);
        pendingRemovals.put(tempId, new PendingRemoval(tempId, tickCounter + 6000));

        // 착탄 지점 주변 플레이어에게 30초 대피 모드 안내
        for (ServerPlayer sp : level.players()) {
            if (sp.distanceToSqr(center.getX() + 0.5, center.getY(), center.getZ() + 0.5) <= 64.0 * 64.0) {
                sp.displayClientMessage(
                        Component.literal("🛡️ [전술 방어 돔 전개] 30초간 아군 대피/진입 가능! 30초 후 완전 밀폐(탈출만 가능)됩니다.")
                                .withStyle(net.minecraft.ChatFormatting.YELLOW, net.minecraft.ChatFormatting.BOLD),
                        true
                );
            }
        }
    }

    private void tickStrikeBeams() {
        // 실제 물리 블록을 설치하지 않으므로 잔여 블록 제거 로직 불필요
    }

    /**
     * 폭격 충돌 지점 기준 구형 반경 내 블럭을 파괴한다.
     * 공기, 유체, 흑요석 등 폭발 저항이 높은 블럭은 제외한다.
     */
    private static void destroyBombRadius(ServerLevel level, Vec3 center, int radius) {
        int cx = (int) Math.floor(center.x);
        int cy = (int) Math.floor(center.y);
        int cz = (int) Math.floor(center.z);
        int radiusSq = radius * radius;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radiusSq) continue;
                    pos.set(cx + dx, cy + dy, cz + dz);
                    if (pos.getY() < level.getMinY() || pos.getY() >= level.getMaxY()) continue;
                    BlockState state = level.getBlockState(pos);
                    // 공기, 유체(물/용암), 폭발 저항 3600 이상(흑요석 등) 제외
                    if (state.isAir()
                            || !state.getFluidState().isEmpty()
                            || state.getBlock().getExplosionResistance() >= 3600.0F) {
                        continue;
                    }
                    // 🛡️ 결계 및 방어막 포드 돔 내부 블럭은 폭탄/폭격에 의해 절대 파괴되지 않음
                    if (isBlockProtectedByBarrier(level, pos)) {
                        continue;
                    }
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    /**
     * 전술 스캔 패드 아이템 우클릭 시 호출:
     * 바닐라 지도 유무나 크기와 상관없이 현재 플레이어 위치 및 귀속 결계를 기준으로 512x512 광역 지형을 즉시 스캔하여 전술 지도 GUI를 팝업한다.
     */
    public void openTacticalMapFromItem(ServerPlayer player, ItemStack stack) {
        if (!ServerPlayNetworking.canSend(player, VeilTargetMapPayload.TYPE)) {
            return;
        }

        // 1. 연동할 결계 탐색 (소유한 결계, 또는 가장 가까운 결계, 또는 첫 번째 결계)
        VeilBarrier targetBarrier = null;
        Optional<UUID> boundId = VeilKeyBinding.barrierId(stack);
        if (boundId.isPresent() && barriers.containsKey(boundId.get())) {
            targetBarrier = barriers.get(boundId.get());
        } else {
            // 플레이어가 소유한 결계 탐색
            targetBarrier = barriers.values().stream()
                    .filter(b -> b.owner().equals(player.getUUID()) || player.hasPermissions(2))
                    .min(Comparator.comparingDouble(b -> BarrierGeometry.horizontalDistanceSquared(
                            player.getX(), player.getZ(), b.centerX(), b.centerZ()
                    )))
                    .orElse(null);

            if (targetBarrier == null && !barriers.isEmpty()) {
                targetBarrier = barriers.values().iterator().next();
            }
        }

        ServerLevel source = server.getLevel(targetBarrier != null ? targetBarrier.sourceKey() : player.level().dimension());
        if (source == null) {
            source = server.overworld();
        }
        if (source == null) {
            return;
        }

        int scanCenterX = targetBarrier != null ? targetBarrier.centerX() : (int) Math.floor(player.getX());
        int scanCenterZ = targetBarrier != null ? targetBarrier.centerZ() : (int) Math.floor(player.getZ());
        UUID barrierId = targetBarrier != null ? targetBarrier.id() : new UUID(0L, 0L);

        // 2. 맵 크기 상관없이 광역 128x128 고해상도 지형 즉시 스캔 (cellSize = 4 -> 512x512 블록 광역 스캔)
        int resolution = 128;
        int cellSize = 4;
        int[] heights = new int[resolution * resolution];
        int[] colors = new int[resolution * resolution];
        int startX = scanCenterX - resolution * cellSize / 2;
        int startZ = scanCenterZ - resolution * cellSize / 2;

        for (int mapZ = 0; mapZ < resolution; mapZ++) {
            for (int mapX = 0; mapX < resolution; mapX++) {
                int worldX = startX + mapX * cellSize + cellSize / 2;
                int worldZ = startZ + mapZ * cellSize + cellSize / 2;
                int index = mapZ * resolution + mapX;

                if (!source.hasChunk(worldX >> 4, worldZ >> 4)) {
                    heights[index] = source.getSeaLevel();
                    colors[index] = 0xFF14171E; // 미로드 구역
                    continue;
                }

                int surfaceY = source.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worldX, worldZ);
                BlockPos surface = new BlockPos(worldX, Math.max(source.getMinY(), surfaceY - 1), worldZ);
                int color = 0;
                for (int scanY = surface.getY(); scanY >= Math.max(source.getMinY(), surface.getY() - 8); scanY--) {
                    BlockPos scanPos = new BlockPos(worldX, scanY, worldZ);
                    int c = source.getBlockState(scanPos).getMapColor(source, scanPos).col;
                    if (c != 0) {
                        color = c;
                        break;
                    }
                }
                if (color == 0) {
                    color = 0x916339;
                }
                int shade = Math.max(-28, Math.min(28, (surfaceY - source.getSeaLevel()) / 3));
                colors[index] = 0xFF000000 | shadeColor(color, shade);
                heights[index] = surfaceY;
            }
        }

        player.displayClientMessage(
                Component.literal("📡 전술 스캔 패드: 광역 지형(512x512) 스캔 완료")
                        .withStyle(net.minecraft.ChatFormatting.GREEN, net.minecraft.ChatFormatting.BOLD),
                true
        );

        int[] sculkPings = getSculkPings(source, targetBarrier);
        ServerPlayNetworking.send(player, new VeilTargetMapPayload(
                barrierId, scanCenterX, scanCenterZ, cellSize, resolution, heights, colors, sculkPings
        ));
    }

    private void sendTargetMap(ServerPlayer player, VeilBarrier barrier) {
        if (!ServerPlayNetworking.canSend(player, VeilTargetMapPayload.TYPE)) {
            return;
        }
        ServerLevel source = server.getLevel(barrier.sourceKey());
        ServerLevel pocket = server.getLevel(InteriorVeil.POCKET_LEVEL);
        if (source == null) {
            return;
        }

        // 1. 손에 든 지도 또는 신호기 주변 6블록 이내의 아이템 액자에 걸린 지도 탐색
        net.minecraft.world.item.ItemStack mapStack = net.minecraft.world.item.ItemStack.EMPTY;
        if (player.getMainHandItem().is(net.minecraft.world.item.Items.FILLED_MAP)) {
            mapStack = player.getMainHandItem();
        } else if (player.getOffhandItem().is(net.minecraft.world.item.Items.FILLED_MAP)) {
            mapStack = player.getOffhandItem();
        } else {
            // 포켓 차원 및 오버월드 신호기 주변 액자 검색
            net.minecraft.world.phys.AABB searchBox = new net.minecraft.world.phys.AABB(barrier.center()).inflate(6.0);
            java.util.List<net.minecraft.world.entity.decoration.ItemFrame> frames = new java.util.ArrayList<>();
            if (pocket != null) {
                frames.addAll(pocket.getEntitiesOfClass(net.minecraft.world.entity.decoration.ItemFrame.class, searchBox));
            }
            frames.addAll(source.getEntitiesOfClass(net.minecraft.world.entity.decoration.ItemFrame.class, searchBox));

            for (net.minecraft.world.entity.decoration.ItemFrame frame : frames) {
                if (frame.getItem().is(net.minecraft.world.item.Items.FILLED_MAP)) {
                    mapStack = frame.getItem();
                    break;
                }
            }
        }

        // 2. 바닐라 지도 데이터가 확인되면 128x128 바닐라 맵 데이터 로드
        if (!mapStack.isEmpty()) {
            net.minecraft.world.level.saveddata.maps.MapId mapId = mapStack.get(net.minecraft.core.component.DataComponents.MAP_ID);
            if (mapId != null) {
                net.minecraft.world.level.saveddata.maps.MapItemSavedData mapData = source.getMapData(mapId);
                if (mapData == null && server.overworld() != null) {
                    mapData = server.overworld().getMapData(mapId);
                }
                if (mapData != null) {
                    int resolution = 128;
                    int cellSize = 1 << mapData.scale;
                    int[] heights = new int[resolution * resolution];
                    int[] colors = new int[resolution * resolution];
                    for (int i = 0; i < mapData.colors.length && i < colors.length; i++) {
                        byte packed = mapData.colors[i];
                        if (packed == 0) {
                            colors[i] = 0xFF14171E; // 미탐험 구역
                        } else {
                            int rgb = net.minecraft.world.level.material.MapColor.getColorFromPackedId(packed);
                            colors[i] = 0xFF000000 | (rgb & 0xFFFFFF);
                        }
                        heights[i] = 64;
                    }
                    player.displayClientMessage(Component.translatable("message.interiorveil.map_loaded"), true);
                    int[] sculkPings = getSculkPings(source, barrier);
                    ServerPlayNetworking.send(player, new VeilTargetMapPayload(
                            barrier.id(), mapData.centerX, mapData.centerZ, cellSize, resolution, heights, colors, sculkPings
                    ));
                    return;
                }
            }
        }

        // 3. 지도가 없는 경우 512x512 고해상도 광역 지형 스캔
        int resolution = 128;
        int cellSize = 4;
        int[] heights = new int[resolution * resolution];
        int[] colors = new int[resolution * resolution];
        int startX = barrier.centerX() - resolution * cellSize / 2;
        int startZ = barrier.centerZ() - resolution * cellSize / 2;
        for (int mapZ = 0; mapZ < resolution; mapZ++) {
            for (int mapX = 0; mapX < resolution; mapX++) {
                int worldX = startX + mapX * cellSize + cellSize / 2;
                int worldZ = startZ + mapZ * cellSize + cellSize / 2;
                int index = mapZ * resolution + mapX;
                if (!source.hasChunk(worldX >> 4, worldZ >> 4)) {
                    heights[index] = source.getSeaLevel();
                    colors[index] = 0xFF14171E;
                    continue;
                }
                int surfaceY = source.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worldX, worldZ);
                BlockPos surface = new BlockPos(worldX, Math.max(source.getMinY(), surfaceY - 1), worldZ);
                int color = 0;
                for (int scanY = surface.getY(); scanY >= Math.max(source.getMinY(), surface.getY() - 8); scanY--) {
                    BlockPos scanPos = new BlockPos(worldX, scanY, worldZ);
                    int c = source.getBlockState(scanPos).getMapColor(source, scanPos).col;
                    if (c != 0) {
                        color = c;
                        break;
                    }
                }
                if (color == 0) {
                    color = 0x916339;
                }
                int shade = Math.max(-28, Math.min(28, (surfaceY - source.getSeaLevel()) / 3));
                colors[index] = 0xFF000000 | shadeColor(color, shade);
                heights[index] = surfaceY;
            }
        }
        int[] sculkPings = getSculkPings(source, barrier);
        ServerPlayNetworking.send(player, new VeilTargetMapPayload(
                barrier.id(), barrier.centerX(), barrier.centerZ(), cellSize, resolution, heights, colors, sculkPings
        ));
    }

    private int[] getSculkPings(ServerLevel source, VeilBarrier barrier) {
        if (barrier == null || source == null || !hasSculkCore(barrier)) {
            return new int[0];
        }
        AABB sculkArea = new AABB(barrier.center()).inflate(128.0);
        java.util.List<LivingEntity> enemies = source.getEntitiesOfClass(LivingEntity.class, sculkArea,
                e -> e.isAlive() && isTargetableHostile(e, barrier));
        int[] pings = new int[enemies.size() * 2];
        for (int i = 0; i < enemies.size(); i++) {
            pings[i * 2] = enemies.get(i).getBlockX();
            pings[i * 2 + 1] = enemies.get(i).getBlockZ();
        }
        return pings;
    }

    private static int shadeColor(int color, int amount) {
        int red = Math.max(0, Math.min(255, ((color >> 16) & 0xFF) + amount));
        int green = Math.max(0, Math.min(255, ((color >> 8) & 0xFF) + amount));
        int blue = Math.max(0, Math.min(255, (color & 0xFF) + amount));
        return red << 16 | green << 8 | blue;
    }

    public static boolean isAttackFriendly(LivingEntity entity, VeilBarrier barrier) {
        if (entity instanceof ServerPlayer player) {
            if (player.getUUID().equals(barrier.owner())
                    || barrier.advanced().allowedPlayers().containsKey(player.getUUID())
                    || player.hasPermissions(2)) {
                return true;
            }

            // 마인크래프트 스코어보드 팀(Team) 일괄 권한 검사 (예: team:red 또는 팀 이름)
            net.minecraft.world.scores.PlayerTeam team = player.getTeam();
            if (team != null) {
                String teamName = team.getName().toLowerCase();
                for (String allowedName : barrier.advanced().allowedPlayers().values()) {
                    String clean = allowedName.trim().toLowerCase();
                    if (clean.equals("team:" + teamName) || clean.equals(teamName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 신호기 감지 및 자동 요격 대상 판정:
     * 1. 비인가 플레이어 (소유자, 허용 목록, 동맹 팀이 아닌 외부 플레이어)
     * 2. 적대적 몬스터 (Enemy / Monster 인터페이스: 좀비, 스켈레톤, 크리퍼, 엔더맨, 일리저, 드라운드, 워든 등)
     * 
     * * 가축, 동물(Animal), 길들인 펫, 주민(Villager), 골렘(IronGolem), 수중생물 등은 일체 감지/공격하지 않음!
     */
    private static boolean isTargetableHostile(LivingEntity entity, VeilBarrier barrier) {
        if (entity == null || !entity.isAlive() || entity.isSpectator()) {
            return false;
        }

        // 1. 플레이어: 소유자/허용목록/팀이 아닌 침입자 플레이어만 타겟팅
        if (entity instanceof ServerPlayer player) {
            return !isAttackFriendly(player, barrier);
        }

        // 2. 적대적 몬스터 및 유해 생물(박쥐 포함)
        if (entity instanceof net.minecraft.world.entity.monster.Enemy
                || entity instanceof net.minecraft.world.entity.ambient.Bat) {
            return true;
        }

        // 3. 그 외 가축(Animal), 길들인 펫, 주민(Villager), 골렘(IronGolem), 수중생물 등 비적대적 생명체는 일체 감지하지 않음
        return false;
    }

    private static void damageTarget(ServerLevel level, LivingEntity target, float amount) {
        boolean wasAlive = target.isAlive();
        target.hurtServer(level, level.damageSources().magic(), amount);
        if (wasAlive && target.isDeadOrDying()) {
            emitSafeExplosion(level, target.position());
        }
    }

    private static void emitSafeExplosion(ServerLevel level, Vec3 position) {
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, position.x, position.y + 0.5, position.z,
                1, 0.0, 0.0, 0.0, 0.0);
        level.playSound(null, BlockPos.containing(position), SoundEvents.GENERIC_EXPLODE.value(),
                SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    private static void emitLaserColumnExplosion(ServerLevel level, Vec3 target, int craterBottomY) {
        BlockPos centerPos = BlockPos.containing(target);

        // 1. 4중 입체 대폭발 사운드 레이어 (중저음 대폭발 + 벼락 굉음 + 궤도 에너지 방출음 + 음파 충격음)
        level.playSound(null, centerPos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 10.0F, 0.5F);
        level.playSound(null, centerPos, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 8.0F, 0.7F);
        level.playSound(null, centerPos, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 6.0F, 1.1F);
        level.playSound(null, centerPos, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.BLOCKS, 5.0F, 0.6F);

        // 2. 수직 레이저 기둥 궤도를 타고 하늘(Y:320)부터 바닥까지 쏟아지는 연쇄 폭발
        int topY = Math.min(320, level.getMaxY());
        for (int y = craterBottomY; y <= topY; y += 3) {
            level.sendParticles(ParticleTypes.EXPLOSION, target.x, y, target.z, 4, 1.2, 0.8, 1.2, 0.05);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.x, y, target.z, 6, 0.8, 0.8, 0.8, 0.15);
            level.sendParticles(ParticleTypes.FLAME, target.x, y, target.z, 5, 0.6, 0.6, 0.6, 0.1);
            if (y % 8 == 0) {
                level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, target.x, y, target.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }

        // 3. 지상 착탄 지점 대규모 연쇄 폭발 구름 & 화염 파편 & 피어오르는 연기
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, target.x, target.y + 1, target.z, 8, 4.0, 1.5, 4.0, 0.0);
        level.sendParticles(ParticleTypes.LAVA, target.x, target.y + 1, target.z, 60, 6.0, 2.5, 6.0, 0.3);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, target.x, target.y + 2, target.z, 50, 4.0, 3.0, 4.0, 0.08);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, target.x, target.y + 1, target.z, 40, 5.0, 2.0, 5.0, 0.05);
    }

    /**
     * 폭격 충돌 시 사방으로 확산되는 광역 열폭풍(Thermal Shockwave) 발생.
     * 결계 내부(오버월드 결계 영역 및 포켓 차원)에 있는 플레이어 및 엔티티는 모든 피해와 넉백이 완벽하게 면역된다.
     */
    private int[] emitThermalShockwave(ServerLevel level, Vec3 target, VeilBarrier strikeBarrier, int bombRadius) {
        int kills = 0;
        int totalDamage = 0;
        int debuffedCount = 0;

        // Zone 2, 3 범위를 3배 이상으로 대폭 확장 (기본 bombRadius=20 기준 thermalRadius = 192m)
        double zone2End = bombRadius * 4.0; // 20m ~ 80m (폭 60m로 3배 확장)
        double thermalRadius = Math.max(192.0, bombRadius * 9.6); // 80m ~ 192m 광역 범위
        double thermalRadiusSq = thermalRadius * thermalRadius;
        AABB area = new AABB(
                target.x - thermalRadius, target.y - thermalRadius * 0.5, target.z - thermalRadius,
                target.x + thermalRadius, target.y + thermalRadius, target.z + thermalRadius
        );

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive)) {
            // 결계 내부 보호 판정
            if (isProtectedFromThermalShockwave(entity, strikeBarrier)) {
                if (entity instanceof ServerPlayer player) {
                    player.displayClientMessage(
                            Component.literal("🛡 결계가 전술 폭격의 초대형 열폭풍을 완벽하게 차단했습니다.")
                                    .withStyle(net.minecraft.ChatFormatting.AQUA, net.minecraft.ChatFormatting.BOLD),
                            true
                    );
                }
                continue;
            }

            double distSq = entity.distanceToSqr(target);
            if (distSq > thermalRadiusSq) continue;

            double dist = Math.sqrt(distSq);
            float damage;
            int fireTicks;
            double pushPower;
            int debuffDuration;

            if (dist <= bombRadius) {
                // Zone 1: 폭심지 그라운드 제로 (0 ~ bombRadius, 0~20m) -> 즉사급 직격 괴멸 피해
                float frac = (float) (1.0 - (dist / (bombRadius + 0.01)));
                damage = 150.0F + frac * 100.0F; // 150 ~ 250 괴멸 피해
                fireTicks = 600; // 30초 발화
                pushPower = 3.8;
                debuffDuration = 360;
                entity.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, debuffDuration, 1, false, false));
            } else if (dist <= zone2End) {
                // Zone 2: 중열폭풍 고위험 구역 (bombRadius ~ 4 * bombRadius, 20~80m: 3배 확장) -> 치명상 및 중화상
                float frac = (float) (1.0 - ((dist - bombRadius) / (zone2End - bombRadius + 0.01)));
                damage = 40.0F + frac * 75.0F; // 40 ~ 115 치명 피해
                fireTicks = (int) (180 + frac * 220); // 9~20초 발화
                pushPower = 1.8 + frac * 1.8;
                debuffDuration = 240;
            } else {
                // Zone 3: 초대형 광역 열폭풍 파동 구역 (zone2End ~ thermalRadius, 80~192m: 3배 확장) -> 거리비례 감쇄 화염 피해
                float frac = (float) (1.0 - ((dist - zone2End) / (thermalRadius - zone2End + 0.01)));
                damage = 8.0F + frac * 32.0F; // 8 ~ 40 화염 충격 피해
                fireTicks = (int) (60 + frac * 120); // 3~9초 발화
                pushPower = 0.5 + frac * 1.3;
                debuffDuration = (int) (80 + frac * 120);
            }

            boolean wasAlive = entity.isAlive();
            // 1. 거리별 차등 폭발/열폭풍 피해
            entity.hurtServer(level, level.damageSources().explosion(null, null), damage);
            totalDamage += (int) damage;
            debuffedCount++;
            if (wasAlive && entity.isDeadOrDying()) kills++;

            // 2. 거리별 차등 발화 효과
            entity.setRemainingFireTicks(Math.max(entity.getRemainingFireTicks(), fireTicks));

            // 3. 거리별 차등 열폭풍 넉백
            Vec3 diff = entity.position().subtract(target);
            double hDist = Math.max(0.1, Math.sqrt(diff.x * diff.x + diff.z * diff.z));
            entity.push(
                    (diff.x / hDist) * pushPower,
                    Math.min(1.6, 0.4 + pushPower * 0.3),
                    (diff.z / hDist) * pushPower
            );
            entity.hurtMarked = true;

            // 4. 거리별 차등 디버프 (실명, 어둠, 구속)
            entity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, debuffDuration, 0, false, false));
            entity.addEffect(new MobEffectInstance(MobEffects.DARKNESS, debuffDuration + 40, 0, false, false));
            entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, debuffDuration, 1, false, false));
        }

        // 5. 지표면 지열 발화 (착탄 주변 광역 블럭에 불 부착)
        int fireRadius = Math.min(100, bombRadius * 5);
        int fireRadiusSq = fireRadius * fireRadius;
        int cx = (int) Math.floor(target.x);
        int cy = (int) Math.floor(target.y);
        int cz = (int) Math.floor(target.z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -fireRadius; dx <= fireRadius; dx += 2) {
            for (int dz = -fireRadius; dz <= fireRadius; dz += 2) {
                if (dx * dx + dz * dz > fireRadiusSq) continue;
                if (Math.random() < 0.25) {
                    int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, cx + dx, cz + dz);
                    pos.set(cx + dx, surfaceY, cz + dz);
                    if (level.getBlockState(pos).isAir() && level.getBlockState(pos.below()).isSolidRender()) {
                        // 결계 내부 영역인지 확인
                        boolean inBarrier = false;
                        for (VeilBarrier b : barriers.values()) {
                            if (b.sourceKey().equals(level.dimension()) && b.contains(pos.getX(), pos.getY(), pos.getZ(), 0, false)) {
                                inBarrier = true;
                                break;
                            }
                        }
                        if (!inBarrier) {
                            level.setBlock(pos, Blocks.FIRE.defaultBlockState(), 3);
                        }
                    }
                }
            }
        }

        return new int[]{totalDamage, kills, debuffedCount};
    }

    /**
     * 열폭풍 보호 판정: 오직 결계 내부(포켓 차원 또는 오버월드 결계 구역 내부)에 서 있는 대상만 보호된다.
     * 결계 밖의 야외에 서 있다면 플레이어나 오너라도 열폭풍 피해를 그대로 입는다.
     */
    private boolean isProtectedFromThermalShockwave(LivingEntity entity, VeilBarrier strikeBarrier) {
        // 1. 포켓 차원에 있으면 오버월드 폭격 피해 면역
        if (entity.level().dimension().equals(InteriorVeil.POCKET_LEVEL)) {
            return true;
        }

        // 2. 오버월드 결계(어떤 결계든)의 물리적 내부 영역에 위치하고 있는 경우에만 면역
        for (VeilBarrier b : barriers.values()) {
            if (b.sourceKey().equals(entity.level().dimension())) {
                if (b.contains(entity.getX(), entity.getY(), entity.getZ(), 0, false)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static double distanceToSegmentSquared(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 segment = end.subtract(start);
        double lengthSquared = segment.lengthSqr();
        if (lengthSquared == 0.0) {
            return point.distanceToSqr(start);
        }
        double progress = Math.max(0.0, Math.min(1.0, point.subtract(start).dot(segment) / lengthSquared));
        return point.distanceToSqr(start.add(segment.scale(progress)));
    }

    private void emitVisibleBoundaries() {
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            boolean insidePocket = viewer.level().dimension().equals(InteriorVeil.POCKET_LEVEL);
            VeilBarrier barrier = insidePocket
                    ? assignedBarrier(viewer).orElse(null)
                    : barriers.values().stream()
                        .filter(candidate -> candidate.sourceKey().equals(viewer.level().dimension()))
                        .filter(candidate -> holdsKeyFor(viewer, candidate))
                        .filter(candidate -> candidate.mirrors(viewer.getX(), viewer.getY(), viewer.getZ(), false))
                        .findFirst()
                        .orElse(null);
            if (barrier == null || (!insidePocket && !holdsKeyFor(viewer, barrier))) {
                continue;
            }
            if (insidePocket) {
                if (barrier.boundaryVisible()) {
                    emitInteriorBoundary(viewer, barrier);
                }
                continue;
            }
            double angleOffset = (tickCounter % 120) * Math.PI / 60.0;
            for (int index = 0; index < 24; index++) {
                double angle = angleOffset + index * Math.PI * 2.0 / 24.0;
                double x = barrier.centerX() + 0.5 + Math.cos(angle) * barrier.radius();
                double z = barrier.centerZ() + 0.5 + Math.sin(angle) * barrier.radius();
                double y = Math.max(barrier.minY(), Math.min(barrier.maxY(), viewer.getY() + (index % 3) - 1));
                viewer.level().sendParticles(
                        viewer,
                        ParticleTypes.PORTAL,
                        true,
                        false,
                        x,
                        y,
                        z,
                        1,
                        0.05,
                        0.15,
                        0.05,
                        0.0
                );
            }
        }
    }

    private void emitInteriorBoundary(ServerPlayer viewer, VeilBarrier barrier) {
        DustParticleOptions boundaryParticle = new DustParticleOptions(
                barrier.boundaryColor(),
                barrier.advanced().boundarySize()
        );
        double angleOffset = (tickCounter % 160) * Math.PI / 80.0;
        int pointsPerRing = barrier.advanced().boundaryDensity();
        for (int index = 0; index < pointsPerRing; index++) {
            double angle = angleOffset + index * Math.PI * 2.0 / pointsPerRing;
            double x = barrier.getPocketX() + 0.5 + Math.cos(angle) * barrier.radius();
            double z = barrier.getPocketZ() + 0.5 + Math.sin(angle) * barrier.radius();
            double y = viewer.level().getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    (int) Math.floor(x),
                    (int) Math.floor(z)
            ) + 0.08;
            viewer.level().sendParticles(
                    viewer,
                    boundaryParticle,
                    true,
                    false,
                    x,
                    y,
                    z,
                    1,
                    0.015,
                    0.015,
                    0.015,
                    0.0
            );
        }
    }
    private void emitNavigationParticles() {
        for (ServerPlayer viewer : server.getPlayerList().getPlayers()) {
            if (viewer.level().dimension().equals(InteriorVeil.POCKET_LEVEL)) {
                continue;
            }

            VeilBarrier barrier = barriers.values().stream()
                    .filter(candidate -> candidate.sourceKey().equals(viewer.level().dimension()))
                    .filter(candidate -> holdsKeyFor(viewer, candidate))
                    .filter(candidate -> BarrierGeometry.horizontalDistanceSquared(
                            viewer.getX(),
                            viewer.getZ(),
                            candidate.centerX() + 0.5,
                            candidate.centerZ() + 0.5
                    ) <= (double) candidate.navigationRange() * candidate.navigationRange())
                    .min(Comparator.comparingDouble(candidate -> BarrierGeometry.horizontalDistanceSquared(
                            viewer.getX(),
                            viewer.getZ(),
                            candidate.centerX() + 0.5,
                            candidate.centerZ() + 0.5
                    )))
                    .orElse(null);
            if (barrier == null) {
                continue;
            }

            double deltaX = barrier.centerX() + 0.5 - viewer.getX();
            double deltaZ = barrier.centerZ() + 0.5 - viewer.getZ();
            double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
            if (distance < 3.0) {
                continue;
            }
            viewer.displayClientMessage(
                    Component.translatable("message.interiorveil.navigation", barrier.name(), Math.round(distance)),
                    true
            );
            double pathLength = Math.min(20.0, distance - 3.0);
            double unitX = deltaX / distance;
            double unitZ = deltaZ / distance;
            double flow = (tickCounter % 80) / 80.0;
            DustParticleOptions navigationParticle = new DustParticleOptions(
                    barrier.navigationColor(),
                    barrier.advanced().navigationSize()
            );
            int navigationPoints = barrier.advanced().navigationDensity();
            for (int index = 0; index < navigationPoints; index++) {
                double progress = (flow + index / (double) navigationPoints) % 1.0;
                double step = 3.0 + pathLength * progress;
                double y = viewer.getY() + 0.45 + Math.sin((tickCounter + index * 13) * 0.12) * 0.22;
                viewer.level().sendParticles(
                        viewer,
                        navigationParticle,
                        true,
                        false,
                        viewer.getX() + unitX * step,
                        y,
                        viewer.getZ() + unitZ * step,
                        1,
                        0.01,
                        0.01,
                        0.01,
                        0.0
                );
            }
        }
    }

    private static Vec3 safePosition(ServerLevel level, double x, double y, double z) {
        BlockPos origin = BlockPos.containing(x, y, z);
        for (int radius = 0; radius <= 8; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    for (int dy = 6; dy >= -4; dy--) {
                        BlockPos feet = origin.offset(dx, dy, dz);
                        if (level.getBlockState(feet).isAir()
                                && level.getBlockState(feet.above()).isAir()
                                && !level.getBlockState(feet.below()).isAir()) {
                            return Vec3.atBottomCenterOf(feet);
                        }
                    }
                }
            }
        }
        return new Vec3(x, y, z);
    }

    private record PendingRemoval(UUID barrierId, int expiresAtTick) {
    }

    private record PendingStrike(net.minecraft.resources.ResourceKey<Level> dimension, Vec3 origin, Vec3 target, UUID barrierId, int executeAtTick, int strikeType, boolean isPrimary, UUID initiator) {
    }

    private static void teleportPets(ServerPlayer player, ServerLevel source, ServerLevel destination, double x, double y, double z) {
        java.util.List<net.minecraft.world.entity.Entity> pets = source.getEntities(player, player.getBoundingBox().inflate(32.0), entity -> {
            if (entity instanceof net.minecraft.world.entity.Leashable leashable && player.equals(leashable.getLeashHolder())) {
                return true;
            }
            if (entity instanceof net.minecraft.world.entity.OwnableEntity ownable && player.equals(ownable.getOwner())) {
                if (entity instanceof net.minecraft.world.entity.TamableAnimal tamable && tamable.isInSittingPose()) {
                    return false;
                }
                return true;
            }
            return false;
        });
        for (net.minecraft.world.entity.Entity pet : pets) {
            pet.teleportTo(
                    destination,
                    x,
                    y,
                    z,
                    Set.of(),
                    pet.getYRot(),
                    pet.getXRot(),
                    false
            );
        }
    }

    public static boolean isProtectedByAbsoluteBarrier(Level level, Vec3 pos) {
        if (!level.dimension().equals(Level.OVERWORLD) || InteriorVeil.manager == null) {
            return false;
        }
        for (VeilBarrier barrier : InteriorVeil.manager.barriers.values()) {
            if (!barrier.sourceKey().equals(Level.OVERWORLD)) continue;
            double dx = pos.x - barrier.centerX();
            double dz = pos.z - barrier.centerZ();
            if (dx * dx + dz * dz <= barrier.radius() * barrier.radius()) {
                if (barrier.advanced().absoluteBarrier()) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isBlockProtectedByBarrier(Level level, BlockPos pos) {
        if (InteriorVeil.manager == null) {
            return false;
        }
        for (VeilBarrier barrier : InteriorVeil.manager.barriers.values()) {
            if (!barrier.sourceKey().equals(level.dimension())) continue;
            double dx = pos.getX() + 0.5 - (barrier.centerX() + 0.5);
            double dy = pos.getY() + 0.5 - barrier.centerY();
            double dz = pos.getZ() + 0.5 - (barrier.centerZ() + 0.5);
            double r = barrier.radius();
            // 구형 또는 반구형 돔 영역 내부 판정
            if ((dx * dx + dz * dz) <= r * r && dy >= -r && dy <= r) {
                return true;
            }
        }
        return false;
    }

    /**
     * 궤도 폭격 타겟 좌표가 결계 또는 방어 돔 영역 내부인 경우,
     * 폭격이 결계 내부로 파고들지 못하도록 결계 돔 상단 표면(Dome Ceiling Surface) 높이로 착탄 좌표를 보정한다.
     */
    public Vec3 adjustTargetToBarrierDomeSurface(ServerLevel level, Vec3 target) {
        if (target == null) return target;
        for (VeilBarrier barrier : barriers.values()) {
            if (!barrier.sourceKey().equals(level.dimension())) continue;
            double cx = barrier.centerX() + 0.5;
            double cz = barrier.centerZ() + 0.5;
            double dx = target.x - cx;
            double dz = target.z - cz;
            double hDistSq = dx * dx + dz * dz;
            double r = barrier.radius();
            if (hDistSq < r * r) {
                double domeCeilingY = barrier.centerY() + Math.sqrt(r * r - hDistSq);
                if (target.y < domeCeilingY) {
                    return new Vec3(target.x, domeCeilingY + 0.2, target.z);
                }
            }
        }
        return target;
    }

    public static VeilBarrier getBlockingBarrierForProjectile(net.minecraft.world.entity.projectile.Projectile projectile, Vec3 pos) {
        if (!projectile.level().dimension().equals(Level.OVERWORLD) || InteriorVeil.manager == null) {
            return null;
        }
        Vec3 delta = projectile.getDeltaMovement();
        Vec3 nextPos = pos.add(delta);

        for (VeilBarrier barrier : InteriorVeil.manager.barriers.values()) {
            if (!barrier.sourceKey().equals(Level.OVERWORLD)) continue;
            if (!barrier.advanced().absoluteBarrier()) continue;

            double dx = pos.x - barrier.centerX();
            double dz = pos.z - barrier.centerZ();
            double distSq = dx * dx + dz * dz;

            double ndx = nextPos.x - barrier.centerX();
            double ndz = nextPos.z - barrier.centerZ();
            double nextDistSq = ndx * ndx + ndz * ndz;

            double radiusSq = barrier.radius() * barrier.radius();

            // 현재 위치 또는 다음 틱 이동 위치가 결계 내부로 들어가는 경우
            if (distSq <= radiusSq || nextDistSq <= radiusSq) {
                // 내부에서 외부로 쏘는 투사체(안에서 밖으로 나가는 방향)는 허용
                double len = Math.sqrt(distSq);
                if (len > 0) {
                    double nx = dx / len;
                    double nz = dz / len;
                    double dot = delta.x * nx + delta.z * nz;
                    if (dot > 0 && distSq < (barrier.radius() - 2) * (barrier.radius() - 2)) {
                        // 결계 안쪽 깊은 곳에서 바깥으로 쏘는 경우는 밖으로 통과 허용
                        continue;
                    }
                }
                return barrier;
            }
        }
        return null;
    }
}
