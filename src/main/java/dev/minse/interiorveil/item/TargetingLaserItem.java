package dev.minse.interiorveil.item;

import dev.minse.interiorveil.InteriorVeil;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class TargetingLaserItem extends Item {
    public TargetingLaserItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        
        Vec3 eyePos = player.getEyePosition(1.0F);
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eyePos.add(look.x * 200.0D, look.y * 200.0D, look.z * 200.0D);
        BlockHitResult hitResult = level.clip(new ClipContext(eyePos, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        
        if (hitResult.getType() != HitResult.Type.BLOCK) {
            return InteractionResult.PASS;
        }
        
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            boolean hasAmethyst = false;
            int amethystSlot = -1;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (player.getInventory().getItem(i).is(Items.AMETHYST_SHARD)) {
                    hasAmethyst = true;
                    amethystSlot = i;
                    break;
                }
            }
            if (!hasAmethyst && !player.isCreative()) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.interiorveil.no_amethyst"), true);
                return InteractionResult.FAIL;
            }
            
            BlockPos targetPos = hitResult.getBlockPos();
            if (InteriorVeil.manager != null) {
                boolean success = InteriorVeil.manager.applyLaserStrike(serverPlayer, targetPos);
                if (success) {
                    if (!player.isCreative() && amethystSlot != -1) {
                        player.getInventory().getItem(amethystSlot).shrink(1);
                    }
                    level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
                }
            }
        }
        
        return InteractionResult.SUCCESS;
    }
}
