package dev.minse.interiorveil;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 실시간 광역 지형 스캔 및 전술 폭격 관제 패드 아이템.
 * 우클릭 시 맵 크기나 바닐라 지도 유무에 상관없이 주변 512x512 이상의 광역 지형을 즉시 스캔하여 전술 지도 GUI를 팝업한다.
 */
public final class TacticalMapItem extends Item {
    public TacticalMapItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            if (InteriorVeil.manager != null) {
                InteriorVeil.manager.openTacticalMapFromItem(serverPlayer, stack);
                return InteractionResult.SUCCESS;
            } else {
                player.displayClientMessage(
                        Component.literal("§c결계 관리자를 불러올 수 없습니다."),
                        true
                );
            }
        }
        return InteractionResult.SUCCESS;
    }
}
