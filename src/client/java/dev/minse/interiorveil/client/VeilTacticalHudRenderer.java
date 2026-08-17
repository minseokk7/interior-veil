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

    private static dev.minse.interiorveil.network.BattleReportPayload lastReport = null;
    private static long reportExpiryTime = 0;

    public static void setBattleReport(dev.minse.interiorveil.network.BattleReportPayload report) {
        lastReport = report;
        reportExpiryTime = System.currentTimeMillis() + 5000L;
    }

    public static void register() {
        HudRenderCallback.EVENT.register(VeilTacticalHudRenderer::renderHud);
    }

    private static void renderHud(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.options.hideGui) return;

        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        int currentY = 12;

        // 0. 타겟팅 레이저 조준경 (Spyglass Scope) 전술 오버레이
        if (client.player.isUsingItem() && client.player.getUseItem().is(dev.minse.interiorveil.VeilItems.TARGETING_LASER)) {
            renderTargetingScopeOverlay(graphics, client, screenWidth, screenHeight);
        }

        // 1. 전투 피해 분석 (BDA) 리포트 HUD 카드 (5초간 표시)
        long now = System.currentTimeMillis();
        if (lastReport != null && now < reportExpiryTime) {
            float fade = Math.min(1.0f, (reportExpiryTime - now) / 1000.0f);
            int bdaWidth = 220;
            int bdaHeight = 56;
            int bdaX = screenWidth - bdaWidth - 12;
            int bdaY = currentY;

            int alpha = (int) (fade * 240);
            graphics.fill(bdaX, bdaY, bdaX + bdaWidth, bdaY + bdaHeight, (alpha << 24) | 0x0A0E17);
            // 네온 시안 악센트 라인
            graphics.fill(bdaX, bdaY, bdaX + bdaWidth, bdaY + 2, (alpha << 24) | 0x00E5FF);
            graphics.fill(bdaX, bdaY, bdaX + 2, bdaY + bdaHeight, (alpha << 24) | 0x00E5FF);

            String strikeName = switch (lastReport.strikeType()) {
                case 1 -> "⚡ EMP 전자기 펄스";
                case 2 -> "📦 궤도 보급 포드";
                case 3 -> "❄️ 극저온 동결탄";
                case 4 -> "🕳️ 중력 특이점 탄";
                case 5 -> "☣️ 나노 낙진탄";
                case 6 -> "🛡️ 궤도 방어막 포드";
                default -> "💥 고폭 열폭풍탄";
            };

            graphics.drawString(client.font, "§b🎯 BDA // 전투 피해 분석 리포트", bdaX + 8, bdaY + 6, 0xFFFFFFFF, true);
            graphics.drawString(client.font, String.format("§7탄종: §e%s §7[%d, %d]", strikeName, lastReport.x(), lastReport.z()), bdaX + 8, bdaY + 18, 0xFFFFFFFF, false);
            graphics.drawString(client.font, String.format("§a💀 처치: §f%d마리  §c💥 피해: §f%.0f HP", lastReport.kills(), lastReport.totalDamage()), bdaX + 8, bdaY + 30, 0xFFFFFFFF, false);
            graphics.drawString(client.font, String.format("§b⚡ 상태이상 제압: §f%d개체", lastReport.debuffedCount()), bdaX + 8, bdaY + 42, 0xFFE0E0E0, false);

            currentY += bdaHeight + 8;
        }

        // 2. 오르비탈 타겟 락온 / 카운트다운 HUD 카드
        VeilConfigPayload currentConfig = VeilConfigClientState.current();
        UUID barrierId = currentConfig != null ? currentConfig.barrierId() : null;

        VeilStrikeTargetTracker.TargetEntry activeEntry = VeilStrikeTargetTracker.getActiveTarget(barrierId);
        if (activeEntry == null) return;

        long remSec = activeEntry.getRemainingSeconds();
        boolean isPinned = activeEntry.status() == VeilStrikeTargetTracker.Status.PINNED;

        int panelWidth = 190;
        int panelHeight = 44;
        int x = screenWidth - panelWidth - 12;
        int y = currentY;

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

    private static void renderTargetingScopeOverlay(GuiGraphics graphics, Minecraft client, int width, int height) {
        int centerX = width / 2;
        int centerY = height / 2;
        int useTicks = 72000 - client.player.getUseItemRemainingTicks();
        int lockOnTicks = 20;
        float progress = Math.min(1.0f, (float) useTicks / lockOnTicks);
        boolean isLocked = useTicks >= lockOnTicks;

        int colorTheme = isLocked ? 0xFFFF3344 : 0xFF00E5FF;

        // 1. 조준 십자선 (Crosshair lines)
        int crosshairSize = 28;
        int gap = 8;
        // 좌/우
        graphics.fill(centerX - crosshairSize, centerY, centerX - gap, centerY + 1, colorTheme);
        graphics.fill(centerX + gap, centerY, centerX + crosshairSize, centerY + 1, colorTheme);
        // 상/하
        graphics.fill(centerX, centerY - crosshairSize, centerX + 1, centerY - gap, colorTheme);
        graphics.fill(centerX, centerY + gap, centerX + 1, centerY + crosshairSize, colorTheme);

        // 2. 외곽 브래킷 (Targeting Brackets)
        int bracketDist = 36;
        int bracketLen = 10;
        // 좌상단
        graphics.fill(centerX - bracketDist, centerY - bracketDist, centerX - bracketDist + bracketLen, centerY - bracketDist + 1, colorTheme);
        graphics.fill(centerX - bracketDist, centerY - bracketDist, centerX - bracketDist + 1, centerY - bracketDist + bracketLen, colorTheme);
        // 우상단
        graphics.fill(centerX + bracketDist - bracketLen, centerY - bracketDist, centerX + bracketDist, centerY - bracketDist + 1, colorTheme);
        graphics.fill(centerX + bracketDist, centerY - bracketDist, centerX + bracketDist + 1, centerY - bracketDist + bracketLen, colorTheme);
        // 좌하단
        graphics.fill(centerX - bracketDist, centerY + bracketDist, centerX - bracketDist + bracketLen, centerY + bracketDist + 1, colorTheme);
        graphics.fill(centerX - bracketDist, centerY + bracketDist - bracketLen, centerX - bracketDist + 1, centerY + bracketDist, colorTheme);
        // 우하단
        graphics.fill(centerX + bracketDist - bracketLen, centerY + bracketDist, centerX + bracketDist, centerY + bracketDist + 1, colorTheme);
        graphics.fill(centerX + bracketDist, centerY + bracketDist - bracketLen, centerX + bracketDist + 1, centerY + bracketDist, colorTheme);

        // 3. 충전 게이지 바 (하단)
        int barW = 80;
        int barH = 4;
        int barX = centerX - barW / 2;
        int barY = centerY + 46;
        graphics.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0x88000000);
        graphics.fill(barX, barY, barX + (int) (barW * progress), barY + barH, isLocked ? 0xFFFF2244 : 0xFF00FFCC);

        // 4. 중앙 상태 레이블
        String statusText = isLocked ? "§c🎯 [LOCK ON - RELEASE TO FIRE]" : String.format("§b🔭 ACQUIRING... %d%%", (int) (progress * 100));
        int textW = client.font.width(statusText);
        graphics.drawString(client.font, statusText, centerX - textW / 2, centerY - 48, 0xFFFFFFFF, true);
    }
}
