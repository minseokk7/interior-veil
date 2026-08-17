package dev.minse.interiorveil.item;

import dev.minse.interiorveil.InteriorVeil;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 전술 3D 타겟팅 레이저 조준기 (Tactical Laser Designator).
 * - 우클릭 유지(Hold Right-Click): 망원경 줌인(Spyglass View)으로 전환되어 원거리 지형을 정밀 관측/조준.
 * - 조준 1초(20틱) 유지 시: 목표 지점 거리(Distance) 및 좌표(Coords) 실시간 락온 완료 (비프음 피드백).
 * - 우클릭 해제(Release): 락온된 목표 좌표로 궤도 레이저 폭격 투하 (자수정 조각 1개 소모).
 */
public class TargetingLaserItem extends Item {
    private static final int LOCK_ON_TICKS = 20; // 1초 조준 시 락온 완료
    private static final double MAX_TARGET_RANGE = 300.0D; // 최대 조준 거리 300블록

    public TargetingLaserItem(Properties properties) {
        super(properties);
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.SPYGLASS;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand usedHand) {
        player.playSound(SoundEvents.SPYGLASS_USE, 1.0F, 1.0F);
        player.awardStat(Stats.ITEM_USED.get(this));
        return ItemUtils.startUsingInstantly(level, player, usedHand);
    }

    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack, int remainingUseDuration) {
        if (!level.isClientSide() && livingEntity instanceof ServerPlayer player) {
            int useTicks = getUseDuration(stack, livingEntity) - remainingUseDuration;
            
            // 시선 방향 정밀 레이캐스팅 (최대 300블록)
            Vec3 eyePos = player.getEyePosition(1.0F);
            Vec3 look = player.getViewVector(1.0F);
            Vec3 end = eyePos.add(look.scale(MAX_TARGET_RANGE));
            BlockHitResult hitResult = level.clip(new ClipContext(
                    eyePos, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player
            ));

            if (hitResult.getType() == HitResult.Type.BLOCK) {
                BlockPos targetPos = hitResult.getBlockPos();
                double dist = Math.sqrt(player.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5));
                int progress = Math.min(LOCK_ON_TICKS, useTicks);

                if (progress < LOCK_ON_TICKS) {
                    // 조준 및 거리 측정 진행 중
                    int percent = (progress * 100) / LOCK_ON_TICKS;
                    player.displayClientMessage(
                            Component.literal("§e🔭 [조준 중] §fX: " + targetPos.getX() + " Y: " + targetPos.getY() + " Z: " + targetPos.getZ()
                                    + " §7(" + String.format("%.1f", dist) + "m) §a" + percent + "%"),
                            true
                    );
                    if (useTicks % 5 == 0) {
                        player.playNotifySound(SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.6F, 1.4F + (progress * 0.03F));
                    }
                } else {
                    // 락온 완료 (손을 떼면 폭격 발사)
                    if (useTicks == LOCK_ON_TICKS) {
                        player.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0F, 2.0F);
                        player.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.8F);
                    }
                    player.displayClientMessage(
                            Component.literal("§c🎯 [TARGET LOCKED] §fX: " + targetPos.getX() + " Y: " + targetPos.getY() + " Z: " + targetPos.getZ()
                                    + " §7(" + String.format("%.1f", dist) + "m) §6★ 우클릭을 떼면 레이저 폭격 발사 ★"),
                            true
                    );
                }
            } else {
                player.displayClientMessage(
                        Component.literal("§7🔭 [조준 중] §8유효한 지형이나 블록을 조준하세요..."),
                        true
                );
            }
        }
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int timeCharged) {
        if (!level.isClientSide() && livingEntity instanceof ServerPlayer player) {
            int useTicks = getUseDuration(stack, livingEntity) - timeCharged;
            player.playSound(SoundEvents.SPYGLASS_STOP_USING, 1.0F, 1.0F);

            // 1초(20틱) 이상 조준하여 락온된 경우에만 폭격 실행
            if (useTicks >= LOCK_ON_TICKS) {
                Vec3 eyePos = player.getEyePosition(1.0F);
                Vec3 look = player.getViewVector(1.0F);
                Vec3 end = eyePos.add(look.scale(MAX_TARGET_RANGE));
                BlockHitResult hitResult = level.clip(new ClipContext(
                        eyePos, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player
                ));

                if (hitResult.getType() == HitResult.Type.BLOCK) {
                    // 자수정 조각 확인
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
                        player.displayClientMessage(Component.translatable("message.interiorveil.no_amethyst"), true);
                        player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1.0F, 0.5F);
                        return false;
                    }

                    BlockPos targetPos = hitResult.getBlockPos();
                    if (InteriorVeil.manager != null) {
                        boolean success = InteriorVeil.manager.applyLaserStrike(player, targetPos);
                        if (success) {
                            if (!player.isCreative() && amethystSlot != -1) {
                                player.getInventory().getItem(amethystSlot).shrink(1);
                            }
                            player.playNotifySound(SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS, 1.0F, 1.4F);
                            player.displayClientMessage(
                                    Component.literal("§a🛰️ [FIRE!] §e좌표 (" + targetPos.getX() + ", " + targetPos.getY() + ", " + targetPos.getZ() + ")로 궤도 레이저 폭격이 발사되었습니다!"),
                                    true
                            );
                            return true;
                        }
                    }
                } else {
                    player.displayClientMessage(Component.literal("§c유효한 타겟을 찾을 수 없습니다."), true);
                }
            } else {
                player.displayClientMessage(Component.literal("§7조준이 취소되었습니다. (1초 이상 조준 유지 시 락온)"), true);
            }
        }
        return false;
    }
}
