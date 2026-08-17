package dev.minse.interiorveil;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

public final class VeilSecurity {

    public static void register(VeilManager manager) {
        // 1. 서버 사이드 블록 파괴 검사 (소유자/열쇠 소지자/OP는 자유롭게 교체 허용, 침입자만 차단)
        PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
            if (level.isClientSide()) return true;
            if (isProtectedBlock(manager, player, level, pos)) {
                if (player instanceof ServerPlayer sp) {
                    sp.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("§c⛔ 결계 신호기 피라미드는 소유자 또는 인가된 플레이어만 수정할 수 있습니다!"),
                            true
                    );
                }
                return false; // 파괴 차단
            }
            return true; // 허용
        });

        // 2. 공격 블록 콜백 (클라이언트는 통과시키고, 서버에서만 비인가 침입자 공격 차단)
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (level.isClientSide()) return InteractionResult.PASS;
            if (isProtectedBlock(manager, player, level, pos)) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        // 3. 블록 설치 검사 (소유자/열쇠 소지자/OP는 자유롭게 설치/교체 허용, 침입자만 차단)
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (level.isClientSide()) return InteractionResult.PASS;
            BlockPos pos = hitResult.getBlockPos();
            BlockPos placePos = pos.relative(hitResult.getDirection());
            if (isProtectedBlock(manager, player, level, pos) || isProtectedBlock(manager, player, level, placePos)) {
                // 신호기 자체를 우클릭하는 것은 허용 (설정 메뉴 열기 위해)
                if (level.getBlockState(pos).is(Blocks.BEACON)) {
                    return InteractionResult.PASS;
                }
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
    }

    private static boolean isProtectedBlock(VeilManager manager, Player player, Level level, BlockPos pos) {
        if (manager == null) return false;
        if (level.dimension().equals(InteriorVeil.POCKET_LEVEL)) return false;

        for (VeilBarrier barrier : manager.barriers()) {
            if (barrier.sourceKey().equals(level.dimension()) && barrier.advanced().absoluteBarrier()) {
                BlockPos center = barrier.center();
                // 중심(신호기) 또는 아래 4개 층(피라미드 9x9 반경) 내에 포함되는지 확인
                if (pos.getY() <= center.getY() && pos.getY() >= center.getY() - 4) {
                    int layer = center.getY() - pos.getY();
                    int maxDist = Math.max(1, layer);
                    if (Math.abs(pos.getX() - center.getX()) <= maxDist && Math.abs(pos.getZ() - center.getZ()) <= maxDist) {
                        // 소유자, 열쇠 소지자, OP 관리자, 아군은 보호 해제 -> 블록 캐기 및 설치/교체 100% 허용!
                        if (player instanceof ServerPlayer serverPlayer) {
                            if (serverPlayer.getUUID().equals(barrier.owner())
                                    || manager.holdsKeyForBarrier(serverPlayer, barrier)
                                    || VeilManager.isAttackFriendly(serverPlayer, barrier)
                                    || serverPlayer.hasPermissions(2)) {
                                return false; // 보호 해제 (수정 허용)
                            }
                        }
                        return true; // 비인가 침입자는 차단
                    }
                }
            }
        }
        return false;
    }
}
