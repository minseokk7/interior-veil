package dev.minse.interiorveil.client;

import dev.minse.interiorveil.network.VeilConfigPayload;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.UUID;

/**
 * 궤도 폭격 발사 시 화면 상단/우측에 실시간으로 표시되는 SF 사이버네틱 전술 HUD 오버레이.
 */
public final class VeilTacticalHudRenderer {

    private VeilTacticalHudRenderer() {
    }

    public static void register() {
        HudRenderCallback.EVENT.register(VeilTacticalHudRenderer::renderHud);
    }

    private static void renderHud(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;

        VeilConfigPayload currentConfig = VeilConfigClientState.current();
        UUID barrierId = currentConfig != null ? currentConfig.barrierId() : null;

        VeilStrikeTargetTracker.TargetEntry activeEntry = VeilStrikeTargetTracker.getActiveTarget(barrierId);
        if (activeEntry == null) return;

        long remSec = activeEntry.getRemainingSeconds();
        boolean isPinned = activeEntry.status() == VeilStrikeTargetTracker.Status.PINNED;

        int screenWidth = client.getWindow().getGuiScaledWidth();
        int panelWidth = 190;
        int panelHeight = 44;
        int x = screenWidth - panelWidth - 12;
        int y = 12;

        // 사이버네틱 글래스모피즘 반투명 배경
        graphics.fill(x, y, x + panelWidth, y + panelHeight, 0xD00A0E17);
        // 상단/측면 네온 악센트 라인
        int themeColor = isPinned ? 0xFFFFB300 : 0xFFFF2244;
        graphics.fill(x, y, x + panelWidth, y + 2, themeColor | 0xFF000000);
        graphics.fill(x, y, x + 2, y + panelHeight, themeColor | 0xFF000000);

        // 1. 헤더 레이블
        String headerTitle = isPinned ? "§6🛰️ ORBITAL TARGET LOCKED" : "§c💥 ORBITAL STRIKE INBOUND";
        graphics.drawString(client.font, headerTitle, x + 8, y + 6, 0xFFFFFFFF, true);

        // 2. 타겟 좌표 및 상태
        String targetInfo = String.format("§7TARGET: §f[%d, %d] §7| R: §e%dm", activeEntry.targetX(), activeEntry.targetZ(), activeEntry.strikeRadius());
        graphics.drawString(client.font, targetInfo, x + 8, y + 18, 0xFFFFFFFF, false);

        // 3. 프로그레스/카운트다운 바
        if (isPinned) {
            String subInfo = "§e[V] 키를 눌러 폭격 발사 승인";
            graphics.drawString(client.font, subInfo, x + 8, y + 30, 0xFFE0E0E0, false);
        } else {
            String timeText = String.format("§c⏱ 지속 시간: §e%d:%02d §7(잔류 빔)", remSec / 60, remSec % 60);
            graphics.drawString(client.font, timeText, x + 8, y + 30, 0xFFFFFFFF, false);

            // 게이지 바 렌더링
            int barWidth = panelWidth - 16;
            int barX = x + 8;
            int barY = y + panelHeight - 4;
            graphics.fill(barX, barY, barX + barWidth, barY + 2, 0x55444444);
            float progress = Math.max(0.0f, Math.min(1.0f, remSec / 120.0f));
            graphics.fill(barX, barY, barX + (int) (barWidth * progress), barY + 2, 0xFFFF2244);
        }
    }
}
