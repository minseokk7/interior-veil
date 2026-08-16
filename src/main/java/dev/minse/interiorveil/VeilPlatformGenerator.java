package dev.minse.interiorveil;

import java.util.LinkedList;
import java.util.Queue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

public class VeilPlatformGenerator {
    private static final int BLOCKS_PER_TICK = 4096;
    
    private static class PlatformTask {
        final int pocketX;
        final int pocketZ;
        final int centerY;
        final int radius;
        int currentRadius;
        int currentXOffset;

        PlatformTask(int pocketX, int pocketZ, int centerY, int radius) {
            this.pocketX = pocketX;
            this.pocketZ = pocketZ;
            this.centerY = centerY;
            this.radius = radius;
            this.currentRadius = 0;
            this.currentXOffset = 0;
        }
    }

    private final Queue<PlatformTask> tasks = new LinkedList<>();

    public void addTask(int pocketX, int pocketZ, int centerY, int radius) {
        tasks.offer(new PlatformTask(pocketX, pocketZ, centerY, radius));
    }

    public void tick(ServerLevel pocket) {
        if (pocket == null || tasks.isEmpty()) return;

        int blocksPlaced = 0;
        PlatformTask task = tasks.peek();

        while (task != null && blocksPlaced < BLOCKS_PER_TICK) {
            boolean moved = true;
            while (moved && blocksPlaced < BLOCKS_PER_TICK) {
                if (task.currentRadius > task.radius) {
                    moved = false; // Task complete
                    break;
                }

                int r2 = task.currentRadius * task.currentRadius;
                int inner_r2 = task.currentRadius == 0 ? -1 : (task.currentRadius - 1) * (task.currentRadius - 1);
                int x2 = task.currentXOffset * task.currentXOffset;
                int maxZ = (int) Math.sqrt(r2 - x2);

                for (int zOffset = -maxZ; zOffset <= maxZ; zOffset++) {
                    int z2 = zOffset * zOffset;
                    if (x2 + z2 <= r2 && x2 + z2 > inner_r2) {
                        for (int layer = -11; layer <= -5; layer++) {
                            BlockPos pos = new BlockPos(
                                task.pocketX + task.currentXOffset, 
                                task.centerY + layer, 
                                task.pocketZ + zOffset
                            );
                            if (layer == -11) {
                                pocket.setBlock(pos, Blocks.BEDROCK.defaultBlockState(), 2 | 16);
                            } else {
                                pocket.setBlock(pos, Blocks.DIRT.defaultBlockState(), 2 | 16);
                            }
                            blocksPlaced++;
                        }
                    }
                }

                task.currentXOffset++;
                if (task.currentXOffset > task.currentRadius) {
                    task.currentRadius++;
                    task.currentXOffset = -task.currentRadius;
                }
            }

            if (!moved) {
                tasks.poll();
                task = tasks.peek();
            }
        }
    }
}
