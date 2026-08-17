package dev.minse.interiorveil.client;

import dev.minse.interiorveil.network.VeilAdminActionPayload;
import dev.minse.interiorveil.network.VeilConfigPayload;
import dev.minse.interiorveil.network.VeilTargetMapPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Xaero's World Map 스타일의 부드러운 줌/패닝 및 실시간 좌표 HUD를 탑재한 전술 관제 지도 GUI.
 */
public final class VeilTargetMapScreen extends Screen {
    private final VeilTargetMapPayload map;
    private VeilConfigPayload current;
    private int selectedX;
    private int selectedY;
    private int selectedZ;
    private int mapLeft;
    private int mapTop;
    private int mapSize;
    private Button fireButton;

    // Xaero 스타일 줌 및 패닝
    private float zoom = 1.0f;
    private float panOffsetX = 0.0f;
    private float panOffsetZ = 0.0f;

    public VeilTargetMapScreen(VeilTargetMapPayload map) {
        super(Component.translatable("screen.interiorveil.target_map.title"));
        this.map = map;
        this.current = VeilConfigClientState.current();

        VeilStrikeTargetTracker.TargetEntry activeEntry = VeilStrikeTargetTracker.getActiveTarget(map.barrierId());
        if (activeEntry != null) {
            this.selectedX = activeEntry.targetX();
            this.selectedY = activeEntry.targetY();
            this.selectedZ = activeEntry.targetZ();
        } else if (current != null && (current.attackTargetX() != 0 || current.attackTargetZ() != 0)) {
            this.selectedX = current.attackTargetX();
            this.selectedY = current.attackTargetY();
            this.selectedZ = current.attackTargetZ();
        } else {
            // 초기 기본값: 결계/스캔 지형 중심
            this.selectedX = map.centerX();
            this.selectedZ = map.centerZ();
            int midIdx = (map.resolution() / 2) * map.resolution() + (map.resolution() / 2);
            this.selectedY = (midIdx >= 0 && midIdx < map.heights().length) ? map.heights()[midIdx] : 64;
        }
    }

    private int strikeType = 0; // 0: 고폭, 1: EMP, 2: 보급
    private dev.minse.interiorveil.StrikeFormation strikeFormation = dev.minse.interiorveil.StrikeFormation.SINGLE;
    private Button typeButton;
    private Button formationButton;

    private String getTypeButtonText() {
        return switch (strikeType) {
            case 1 -> "§b⚡ EMP탄";
            case 2 -> "§a📦 보급포드";
            default -> "§c💥 고폭탄";
        };
    }

    private String getFormationButtonText() {
        return "§e" + strikeFormation.getDisplayName();
    }

    @Override
    protected void init() {
        mapSize = Math.min(384, this.height - 82);
        mapLeft = this.width / 2 - mapSize / 2;
        mapTop = 24;
        int buttonY = mapTop + mapSize + 8;

        typeButton = addRenderableWidget(Button.builder(Component.literal(getTypeButtonText()), btn -> {
            strikeType = (strikeType + 1) % 3;
            btn.setMessage(Component.literal(getTypeButtonText()));
        }).bounds(this.width / 2 - 215, buttonY, 75, 20).build());

        formationButton = addRenderableWidget(Button.builder(Component.literal(getFormationButtonText()), btn -> {
            strikeFormation = dev.minse.interiorveil.StrikeFormation.byIndex((strikeFormation.ordinal() + 1) % dev.minse.interiorveil.StrikeFormation.values().length);
            btn.setMessage(Component.literal(getFormationButtonText()));
        }).bounds(this.width / 2 - 135, buttonY, 100, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("screen.interiorveil.target_map.save"), button -> save(false))
                .bounds(this.width / 2 - 30, buttonY, 50, 20).build());
        fireButton = addRenderableWidget(Button.builder(Component.translatable("screen.interiorveil.target_map.fire"), button -> save(true))
                .bounds(this.width / 2 + 25, buttonY, 60, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(this.width / 2 + 90, buttonY, 50, 20).build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= mapLeft && mouseX < mapLeft + mapSize && mouseY >= mapTop && mouseY < mapTop + mapSize) {
            zoom = Math.max(0.75f, Math.min(4.0f, zoom + (float) verticalAmount * 0.25f));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (event.button() == 1 || event.button() == 2) {
            // 우클릭 드래그 또는 마우스 휠 클릭 드래그로 패닝 이동
            panOffsetX += (float) dragX;
            panOffsetZ += (float) dragY;
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_B) {
            strikeType = (strikeType + 1) % 3;
            if (typeButton != null) {
                typeButton.setMessage(Component.literal(getTypeButtonText()));
            }
            return true;
        }
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_F) {
            strikeFormation = dev.minse.interiorveil.StrikeFormation.byIndex((strikeFormation.ordinal() + 1) % dev.minse.interiorveil.StrikeFormation.values().length);
            if (formationButton != null) {
                formationButton.setMessage(Component.literal(getFormationButtonText()));
            }
            return true;
        }
        if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_V || event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_G) {
            save(true);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && event.x() >= mapLeft && event.x() < mapLeft + mapSize
                && event.y() >= mapTop && event.y() < mapTop + mapSize) {
            // 렌더링 공식과 100% 동일한 정밀 역변환
            float renderCellPixel = ((float) mapSize / map.resolution()) * zoom;
            float renderOriginX = mapLeft + (mapSize / 2.0f) - ((map.resolution() / 2.0f) * renderCellPixel) + panOffsetX;
            float renderOriginZ = mapTop + (mapSize / 2.0f) - ((map.resolution() / 2.0f) * renderCellPixel) + panOffsetZ;

            int mapX = (int) Math.floor((event.x() - renderOriginX) / renderCellPixel);
            int mapZ = (int) Math.floor((event.y() - renderOriginZ) / renderCellPixel);

            if (mapX >= 0 && mapX < map.resolution() && mapZ >= 0 && mapZ < map.resolution()) {
                int halfRange = map.resolution() * map.cellSize() / 2;
                selectedX = map.centerX() - halfRange + mapX * map.cellSize() + map.cellSize() / 2;
                selectedZ = map.centerZ() - halfRange + mapZ * map.cellSize() + map.cellSize() / 2;
                selectedY = map.heights()[mapZ * map.resolution() + mapX];
                int radius = current != null ? current.strikeRadius() : 20;
                VeilStrikeTargetTracker.pinTarget(map.barrierId(), selectedX, selectedY, selectedZ, radius);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void save(boolean fire) {
        if (current != null && current.barrierId().equals(map.barrierId())) {
            current = new VeilConfigPayload(
                    current.barrierId(), current.name(), current.radius(), current.height(), current.fogMargin(),
                    current.fogDistance(), current.fogFadeTicks(), current.navigationRange(), current.boundaryVisible(),
                    current.boundaryColor(), current.navigationColor(), current.securityMode(), current.beaconColor(),
                    current.accessStart(), current.accessEnd(), current.boundaryDensity(), current.boundarySize(),
                    current.navigationDensity(), current.navigationSize(), current.requireBeaconPower(),
                    false, current.disableFog(), current.fogColor(),
                    fire || current.attackMode(), selectedX, selectedY, selectedZ, current.strikeRadius(),
                    current.absoluteBarrier(), current.reflectProjectiles(), current.allowedPlayers());
            ClientPlayNetworking.send(current);
            VeilConfigClientState.accept(current);
        }
        if (fire) {
            int radius = (current != null) ? current.strikeRadius() : 20;
            VeilStrikeTargetTracker.recordStrike(map.barrierId(), selectedX, selectedY, selectedZ, radius);
            ClientPlayNetworking.send(new VeilAdminActionPayload(
                    map.barrierId(), "laser_fire", selectedX + "," + selectedY + "," + selectedZ + "," + radius + "," + strikeType + "," + strikeFormation.ordinal()
            ));
        }
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        int windowWidth = mapSize + 16;
        int windowHeight = mapSize + 52;
        int windowLeft = this.width / 2 - windowWidth / 2;
        int windowTop = mapTop - 16;

        int neonColor = 0xFF00F0FF;

        // 외곽 은은한 네온 글로우
        graphics.fill(windowLeft - 3, windowTop - 3, windowLeft + windowWidth + 3, windowTop + windowHeight + 3, 0x1500F0FF);
        graphics.fill(windowLeft - 1, windowTop - 1, windowLeft + windowWidth + 1, windowTop + windowHeight + 1, 0x3000F0FF);

        // 어두운 글래스 반투명 배경
        graphics.fill(windowLeft, windowTop, windowLeft + windowWidth, windowTop + windowHeight, 0xEE0A0E17);

        // 네온 테두리 선
        graphics.fill(windowLeft, windowTop, windowLeft + windowWidth, windowTop + 1, neonColor);
        graphics.fill(windowLeft, windowTop + windowHeight - 1, windowLeft + windowWidth, windowTop + windowHeight, neonColor);
        graphics.fill(windowLeft, windowTop, windowLeft + 1, windowTop + windowHeight, neonColor);
        graphics.fill(windowLeft + windowWidth - 1, windowTop, windowLeft + windowWidth, windowTop + windowHeight, neonColor);

        // 내부 빛 반사
        graphics.fill(windowLeft, windowTop, windowLeft + windowWidth, windowTop + 1, 0x40FFFFFF);
        graphics.fill(windowLeft, windowTop, windowLeft + 1, windowTop + windowHeight, 0x40FFFFFF);

        super.render(graphics, mouseX, mouseY, delta);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, neonColor);

        // 지도 렌더링 영역 가위질
        graphics.enableScissor(mapLeft, mapTop, mapLeft + mapSize, mapTop + mapSize);

        float renderCellPixel = ((float) mapSize / map.resolution()) * zoom;
        float renderOriginX = mapLeft + (mapSize / 2.0f) - ((map.resolution() / 2.0f) * renderCellPixel) + panOffsetX;
        float renderOriginZ = mapTop + (mapSize / 2.0f) - ((map.resolution() / 2.0f) * renderCellPixel) + panOffsetZ;

        for (int z = 0; z < map.resolution(); z++) {
            for (int x = 0; x < map.resolution(); x++) {
                int color = map.colors()[z * map.resolution() + x];
                int pixelX = (int) (renderOriginX + x * renderCellPixel);
                int pixelZ = (int) (renderOriginZ + z * renderCellPixel);
                int nextPixelX = (int) (renderOriginX + (x + 1) * renderCellPixel);
                int nextPixelZ = (int) (renderOriginZ + (z + 1) * renderCellPixel);

                if (nextPixelX < mapLeft || pixelX > mapLeft + mapSize || nextPixelZ < mapTop || pixelZ > mapTop + mapSize) {
                    continue;
                }

                graphics.fill(pixelX, pixelZ, Math.max(pixelX + 1, nextPixelX), Math.max(pixelZ + 1, nextPixelZ), color);
            }
        }

        // 결계 반경 원형 표시 (파란색)
        int centerPixelX = (int) (renderOriginX + (map.resolution() / 2.0f) * renderCellPixel);
        int centerPixelZ = (int) (renderOriginZ + (map.resolution() / 2.0f) * renderCellPixel);
        int veilRadius = current != null ? current.radius() : 32;
        int radiusPixels = (int) (veilRadius * renderCellPixel / map.cellSize());
        drawCircle(graphics, centerPixelX, centerPixelZ, radiusPixels, 0xAA33FFFF);

        // 타겟 마커 검사 (2분 만료 시스템 및 7종 진형 다중 마커 연동)
        VeilStrikeTargetTracker.TargetEntry activeEntry = VeilStrikeTargetTracker.getActiveTarget(map.barrierId());
        int targetXToDraw = activeEntry != null ? activeEntry.targetX() : selectedX;
        int targetZToDraw = activeEntry != null ? activeEntry.targetZ() : selectedZ;
        boolean shouldDrawTarget = (activeEntry != null) || (selectedX != 0 || selectedZ != 0);

        if (shouldDrawTarget) {
            int halfRange = map.resolution() * map.cellSize() / 2;
            int alpha = activeEntry != null ? (int) (activeEntry.getFadeAlpha() * 255) : 255;
            int strikeRadius = activeEntry != null ? activeEntry.strikeRadius() : (current != null ? current.strikeRadius() : 20);
            int strikeRadiusPixels = (int) (strikeRadius * renderCellPixel / map.cellSize());

            int markerColorRgb = switch (strikeType) {
                case 1 -> 0x00E5FF; // EMP (Cyan)
                case 2 -> 0x55FF55; // Supply (Green)
                default -> 0xFF2222; // HE (Red)
            };
            int primaryMarkerColor = (Math.max(50, alpha) << 24) | markerColorRgb;
            int subMarkerColor = (Math.max(35, alpha * 2 / 3) << 24) | markerColorRgb;
            int circleColor = (Math.max(25, alpha / 3) << 24) | markerColorRgb;

            // 선택된 진형의 오프셋 목록 가져오기 (기본 간격: 폭격 반경 * 1.5)
            int spacing = (int) (strikeRadius * 1.5);
            java.util.List<int[]> offsets = strikeFormation.getOffsets(spacing);

            for (int i = 0; i < offsets.size(); i++) {
                int[] offset = offsets.get(i);
                int ptX = targetXToDraw + offset[0];
                int ptZ = targetZToDraw + offset[1];

                double targetMapX = (ptX - (map.centerX() - halfRange)) / (double) map.cellSize();
                double targetMapZ = (ptZ - (map.centerZ() - halfRange)) / (double) map.cellSize();
                int px = (int) (renderOriginX + targetMapX * renderCellPixel);
                int pz = (int) (renderOriginZ + targetMapZ * renderCellPixel);

                if (i == 0) {
                    // 중심점: 메인 굵은 조준 십자선 & 폭격 예상 반경 원형 링
                    graphics.fill(px - 6, pz, px + 7, pz + 1, primaryMarkerColor);
                    graphics.fill(px, pz - 6, px + 1, pz + 7, primaryMarkerColor);
                    drawCircle(graphics, px, pz, strikeRadiusPixels, circleColor);
                } else {
                    // 보조 타격점: 서브 조준 십자선 & 반경 원
                    graphics.fill(px - 3, pz, px + 4, pz + 1, subMarkerColor);
                    graphics.fill(px, pz - 3, px + 1, pz + 4, subMarkerColor);
                    drawCircle(graphics, px, pz, (int) (strikeRadiusPixels * 0.75), circleColor);
                }
            }
        }

        graphics.disableScissor();

        // 지도 외곽 네온 테두리
        graphics.fill(mapLeft - 1, mapTop - 1, mapLeft + mapSize + 1, mapTop, 0xFF00F0FF);
        graphics.fill(mapLeft - 1, mapTop + mapSize, mapLeft + mapSize + 1, mapTop + mapSize + 1, 0xFF00F0FF);
        graphics.fill(mapLeft - 1, mapTop, mapLeft, mapTop + mapSize, 0xFF00F0FF);
        graphics.fill(mapLeft + mapSize, mapTop, mapLeft + mapSize + 1, mapTop + mapSize, 0xFF00F0FF);

        // 하단 선택된 좌표 & 2분 타이머 텍스트
        if (activeEntry != null) {
            long remSec = activeEntry.getRemainingSeconds();
            String timerText = String.format("§c💥 폭격 조준점 [%d, %d, %d] §7| §e⏱ %d분 %02d초 후 자동 소멸",
                    activeEntry.targetX(), activeEntry.targetY(), activeEntry.targetZ(), remSec / 60, remSec % 60);
            graphics.drawCenteredString(this.font, Component.literal(timerText), this.width / 2, mapTop + mapSize + 30, 0xFFFF5555);
        } else {
            Component targetText = Component.translatable("screen.interiorveil.target_map.target", selectedX, selectedY, selectedZ);
            graphics.drawCenteredString(this.font, targetText, this.width / 2, mapTop + mapSize + 30, 0xFFFFD23F);
        }

        // 조작 도움말
        String guide = String.format("§7[휠: 줌 (%.1fx) | 우클릭 드래그: 이동 | /veil strike %d %d]", zoom, selectedX, selectedZ);
        graphics.drawCenteredString(this.font, Component.literal(guide), this.width / 2, mapTop + mapSize + 44, 0xFF88AAFF);
    }

    private void drawCircle(GuiGraphics graphics, int centerX, int centerZ, int radius, int color) {
        int sides = 32;
        for (int i = 0; i < sides; i++) {
            double a1 = i * 2.0 * Math.PI / sides;
            double a2 = (i + 1) * 2.0 * Math.PI / sides;
            int x1 = (int) (centerX + Math.cos(a1) * radius);
            int z1 = (int) (centerZ + Math.sin(a1) * radius);
            int x2 = (int) (centerX + Math.cos(a2) * radius);
            int z2 = (int) (centerZ + Math.sin(a2) * radius);
            graphics.fill(Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2) + 1, Math.max(z1, z2) + 1, color);
        }
    }
}
