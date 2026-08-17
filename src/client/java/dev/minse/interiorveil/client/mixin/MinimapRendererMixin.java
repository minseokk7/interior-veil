package dev.minse.interiorveil.client.mixin;

import dev.minse.interiorveil.client.VeilForcefieldRenderer;
import dev.minse.interiorveil.client.VeilStrikeTargetTracker;
import dev.minse.interiorveil.network.ForcefieldStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Xaero Minimap 화면 렌더링 시 결계 영역 및 조준 핀 오버레이를 미니맵 상단에 렌더링하는 믹스인.
 */
@Pseudo
@Mixin(targets = "xaero.common.minimap.render.MinimapRenderer", remap = false)
public abstract class MinimapRendererMixin {

    @Inject(method = "renderOutsidePip", at = @At("TAIL"), require = 0)
    private void interiorveil$renderOnMinimap(
            Object session, int x, int y, int width, int height, double scale, float size, int halfSize, float partial, GuiGraphics guiGraphics, CallbackInfo ci
    ) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || guiGraphics == null) return;

        ForcefieldStatePayload state = VeilForcefieldRenderer.getCurrentState();
        if (state == null) return;

        double playerX = client.player.getX();
        double playerZ = client.player.getZ();

        double dx = state.centerX() - playerX;
        double dz = state.centerZ() - playerZ;

        // 미니맵 중심 좌표 (x + halfSize, y + halfSize)
        int mapCenterX = x + halfSize;
        int mapCenterY = y + halfSize;

        // 스케일에 따른 미니맵 상의 상대 픽셀 위치
        double renderScale = scale > 0.001 ? scale : 1.0;
        int barrierScreenX = mapCenterX + (int) Math.round(dx * renderScale);
        int barrierScreenY = mapCenterY + (int) Math.round(dz * renderScale);
        int screenRadius = (int) Math.round(state.radius() * renderScale);

        // 미니맵 경계 내에 있을 때만 렌더링
        if (Math.abs(barrierScreenX - mapCenterX) <= halfSize + screenRadius
                && Math.abs(barrierScreenY - mapCenterY) <= halfSize + screenRadius) {
            int color = (0x88 << 24) | (state.color() != 0 ? state.color() : 0x00E5FF);
            // 원형 테두리 링
            if (screenRadius > 0) {
                int segments = Math.max(16, Math.min(48, screenRadius * 2));
                for (int i = 0; i < segments; i++) {
                    double angle = 2.0 * Math.PI * i / segments;
                    int px = barrierScreenX + (int) Math.round(Math.cos(angle) * screenRadius);
                    int py = barrierScreenY + (int) Math.round(Math.sin(angle) * screenRadius);
                    if (Math.abs(px - mapCenterX) <= halfSize && Math.abs(py - mapCenterY) <= halfSize) {
                        guiGraphics.fill(px - 1, py - 1, px + 1, py + 1, color);
                    }
                }
            }
        }

        // 폭격 조준 핀 오버레이
        VeilStrikeTargetTracker.TargetEntry activeEntry = VeilStrikeTargetTracker.getActiveTarget(null);
        if (activeEntry != null) {
            double tx = activeEntry.targetX() - playerX;
            double tz = activeEntry.targetZ() - playerZ;
            int targetScreenX = mapCenterX + (int) Math.round(tx * renderScale);
            int targetScreenY = mapCenterY + (int) Math.round(tz * renderScale);

            if (Math.abs(targetScreenX - mapCenterX) <= halfSize && Math.abs(targetScreenY - mapCenterY) <= halfSize) {
                int strikeColor = 0xFFFF2222;
                guiGraphics.fill(targetScreenX - 3, targetScreenY, targetScreenX + 4, targetScreenY + 1, strikeColor);
                guiGraphics.fill(targetScreenX, targetScreenY - 3, targetScreenX + 1, targetScreenY + 4, strikeColor);
            }
        }
    }
}
