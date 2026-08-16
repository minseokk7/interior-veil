package dev.minse.interiorveil.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeaconBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(Block.class)
public abstract class BeaconBlockMixin {
    @Inject(method = "setPlacedBy", at = @At("TAIL"))
    private void interiorveil$autoBuildPyramid(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack, CallbackInfo ci) {
        if (!((Object) this instanceof BeaconBlock)) {
            return;
        }
        if (level.isClientSide() || !(placer instanceof ServerPlayer player)) {
            return;
        }

        // 1. Count total valid base blocks in player inventory
        int totalValidBlocks = 0;
        List<ItemStack> validStacks = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack itemStack = player.getInventory().getItem(i);
            if (!itemStack.isEmpty() && itemStack.getItem() instanceof BlockItem blockItem) {
                if (blockItem.getBlock().defaultBlockState().is(BlockTags.BEACON_BASE_BLOCKS)) {
                    totalValidBlocks += itemStack.getCount();
                    validStacks.add(itemStack);
                }
            }
        }

        // 2. Determine highest fulfillable tier (Pure math)
        int targetTier = 0;
        if (totalValidBlocks >= 164) targetTier = 4;
        else if (totalValidBlocks >= 83) targetTier = 3;
        else if (totalValidBlocks >= 34) targetTier = 2;
        else if (totalValidBlocks >= 9) targetTier = 1;

        if (targetTier == 0) return;

        // 3. Elevate beacon position so it rests ON the original floor (doesn't dig)
        BlockPos newBeaconPos = pos.above(targetTier);
        
        // Remove the original beacon that was just placed
        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);

        // 4. Consume blocks and place the pyramid
        for (int tier = 1; tier <= targetTier; tier++) {
            int y = newBeaconPos.getY() - tier;
            for (int x = newBeaconPos.getX() - tier; x <= newBeaconPos.getX() + tier; x++) {
                for (int z = newBeaconPos.getZ() - tier; z <= newBeaconPos.getZ() + tier; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    
                    // Skip if it already has a base block
                    if (level.getBlockState(p).is(BlockTags.BEACON_BASE_BLOCKS)) {
                        continue;
                    }

                    // Find a valid stack to consume from
                    ItemStack stackToConsume = null;
                    for (ItemStack validStack : validStacks) {
                        if (!validStack.isEmpty()) {
                            stackToConsume = validStack;
                            break;
                        }
                    }
                    
                    if (stackToConsume != null) {
                        Block blockToPlace = ((BlockItem) stackToConsume.getItem()).getBlock();
                        stackToConsume.shrink(1);
                        
                        // Break existing block naturally (drops items)
                        level.destroyBlock(p, true);
                        
                        // Place new block
                        level.setBlock(p, blockToPlace.defaultBlockState(), 3);
                    }
                }
            }
        }

        // 5. Place the beacon at the elevated position
        level.setBlock(newBeaconPos, state, 3);
        // Copy original block entity data? No, BeaconBlock doesn't have custom data on placement usually
        // But if it had a custom name, we'd copy it. For simplicity, just placing the state is fine.
    }

}
