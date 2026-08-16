package dev.minse.interiorveil;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;

public final class VeilSecurity {

    public static void register(VeilManager manager) {
        // 방어막이 쳐져 있는 오버월드의 신호기/피라미드 위치 보호 (블록 파괴 방지 - 침입자 차단)
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (isProtectedBlock(manager, player, level, pos)) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        // 방어막이 쳐져 있는 오버월드의 신호기/피라미드 위치 보호 (블록 설치 방지 - 침입자 차단)
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            BlockPos pos = hitResult.getBlockPos();
            BlockPos placePos = pos.relative(hitResult.getDirection());
            if (isProtectedBlock(manager, player, level, pos) || isProtectedBlock(manager, player, level, placePos)) {
                // 신호기 자체를 우클릭하는 것은 허용 (설정 메뉴를 열기 위해)
                if (level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.BEACON)) {
                    return InteractionResult.PASS;
                }
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });
    }

    private static boolean isProtectedBlock(VeilManager manager, net.minecraft.world.entity.player.Player player, Level level, BlockPos pos) {
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
                        // 오너, 키 소지자, 관리자는 보호 해제 (블록 조작 허용)
                        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                            if (serverPlayer.getUUID().equals(barrier.owner())
                                    || manager.holdsKeyForBarrier(serverPlayer, barrier)
                                    || serverPlayer.hasPermissions(2)) {
                                return false;
                            }
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
