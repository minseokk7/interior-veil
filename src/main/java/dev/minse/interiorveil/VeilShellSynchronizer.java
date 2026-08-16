package dev.minse.interiorveil;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

final class VeilShellSynchronizer {
    private static final Map<UUID, Long> CURSORS = new HashMap<>();

    private VeilShellSynchronizer() {
    }

    static void tick(MinecraftServer server, Collection<VeilBarrier> barriers) {
        ServerLevel pocket = server.getLevel(InteriorVeil.POCKET_LEVEL);
        if (pocket == null || barriers.isEmpty()) {
            return;
        }

        Set<UUID> active = new HashSet<>();
        int perBarrierBudget = Math.max(16, VeilConstants.SHELL_SYNC_BUDGET / barriers.size());
        for (VeilBarrier barrier : barriers) {
            active.add(barrier.id());
            ServerLevel source = server.getLevel(barrier.sourceKey());
            if (source != null) {
                syncBarrier(source, pocket, barrier, perBarrierBudget);
            }
        }
        CURSORS.keySet().removeIf(id -> !active.contains(id));
    }

    private static void syncBarrier(ServerLevel source, ServerLevel pocket, VeilBarrier barrier, int budget) {
        int outerRadius = VeilConstants.MAX_RADIUS + barrier.shellDepth();
        int wallRadius = outerRadius + 2;
        int diameter = wallRadius * 2 + 1;
        int height = barrier.maxY() - barrier.minY() + 1;
        long total = (long) diameter * diameter * height;
        long cursor = CURSORS.getOrDefault(barrier.id(), 0L);
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        int copied = 0;
        int scanned = 0;
        int maxScans = budget * 16;

        while (copied < budget && scanned < maxScans) {
            long index = cursor++ % total;
            scanned++;
            int yIndex = (int) (index % height);
            long horizontal = index / height;
            int zOffset = (int) (horizontal % diameter) - wallRadius;
            int xOffset = (int) (horizontal / diameter) - wallRadius;
            double radialSquared = (double) xOffset * xOffset + (double) zOffset * zOffset;
            int innerRadius = VeilConstants.MAX_RADIUS;
            if (radialSquared < (double) innerRadius * innerRadius
                    || radialSquared > (double) wallRadius * wallRadius) {
                continue;
            }

            BlockPos pocketPos = new BlockPos(
                    barrier.getPocketX() + xOffset,
                    barrier.minY() + yIndex,
                    barrier.getPocketZ() + zOffset
            );

            if (radialSquared > (double) outerRadius * outerRadius) {
                if (pocket.hasChunkAt(pocketPos) && pocket.getBlockState(pocketPos).getBlock() != net.minecraft.world.level.block.Blocks.AIR) {
                    pocket.setBlock(pocketPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2 | 16);
                }
                copied++;
                continue;
            }

            BlockPos sourcePos = new BlockPos(
                    barrier.centerX() + xOffset,
                    barrier.minY() + yIndex,
                    barrier.centerZ() + zOffset
            );
            
            if (!source.hasChunkAt(sourcePos) || !pocket.hasChunkAt(pocketPos)) {
                continue;
            }
            copyBlock(source, pocket, sourcePos, pocketPos);
            copied++;
        }
        CURSORS.put(barrier.id(), cursor % total);
    }

    private static void copyBlock(ServerLevel source, ServerLevel pocket, BlockPos sourcePos, BlockPos pocketPos) {
        BlockState sourceState = source.getBlockState(sourcePos);
        BlockState pocketState = pocket.getBlockState(pocketPos);
        BlockEntity sourceEntity = source.getBlockEntity(sourcePos);
        BlockEntity pocketEntity = pocket.getBlockEntity(pocketPos);
        boolean sameEntityData = sourceEntity == null && pocketEntity == null;
        if (sourceState.equals(pocketState) && sameEntityData) {
            return;
        }

        pocket.setBlock(pocketPos, sourceState, 2 | 16);
        if (sourceEntity != null) {
            CompoundTag data = sourceEntity.saveWithFullMetadata(source.registryAccess());
            BlockEntity copy = BlockEntity.loadStatic(pocketPos, sourceState, data, pocket.registryAccess());
            if (copy != null) {
                pocket.setBlockEntity(copy);
            }
        }
    }
}
