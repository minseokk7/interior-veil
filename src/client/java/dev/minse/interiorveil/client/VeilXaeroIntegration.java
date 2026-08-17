package dev.minse.interiorveil.client;

import dev.minse.interiorveil.network.ForcefieldStatePayload;
import dev.minse.interiorveil.network.VeilAdminActionPayload;
import dev.minse.interiorveil.network.VeilConfigPayload;
import java.lang.reflect.Field;
import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Xaero's World Map 및 Minimap 화면에서 결계 영역 시각화 및 다이렉트 궤도 폭격을 지원하는 연동 모듈.
 */
public final class VeilXaeroIntegration {
    private static boolean registered = false;

    private VeilXaeroIntegration() {
    }

    private static int selectedStrikeType = 0; // 0: 고폭탄, 1: EMP탄, 2: 보급포드
    private static dev.minse.interiorveil.StrikeFormation selectedFormation = dev.minse.interiorveil.StrikeFormation.SINGLE;

    private static String getStrikeTypeButtonText() {
        return switch (selectedStrikeType) {
            case 1 -> "§b⚡ EMP탄";
            case 2 -> "§a📦 보급포드";
            default -> "§c💥 고폭탄";
        };
    }

    private static String getFormationButtonText() {
        return "§e" + selectedFormation.getDisplayName();
    }

    public static void initialize() {
        if (registered) return;
        registered = true;

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            String className = screen.getClass().getName();
            // Xaero's World Map 화면 감지 (xaero.map.gui.GuiMap 등)
            if (className.contains("xaero.map.gui.GuiMap") || className.contains("GuiMap") || className.contains("WorldMap")) {
                String strikeKeyName = VeilKeyBindings.ORBITAL_STRIKE != null
                        ? VeilKeyBindings.ORBITAL_STRIKE.getTranslatedKeyMessage().getString()
                        : "V";

                // 탄종 선택 토글 버튼 (클릭 시 고폭탄 -> EMP탄 -> 보급포드 순환)
                Button typeButton = Button.builder(
                        Component.literal(getStrikeTypeButtonText()),
                        btn -> {
                            selectedStrikeType = (selectedStrikeType + 1) % 3;
                            btn.setMessage(Component.literal(getStrikeTypeButtonText()));
                            if (client.player != null) {
                                String label = selectedStrikeType == 1 ? "⚡ EMP 전자기 펄스탄" : (selectedStrikeType == 2 ? "📦 궤도 보급 포드" : "💥 고폭 열폭풍탄");
                                client.player.displayClientMessage(Component.literal("§6[탄종 변경] §f" + label + "§7 선택됨"), true);
                            }
                        }
                ).bounds(scaledWidth - 325, 6, 95, 20).build();
                Screens.getButtons(screen).add(typeButton);

                // 진형 선택 토글 버튼 (클릭 시 단일 -> 십자 -> 3x3 격자 -> 원형 -> X자 -> 선형 -> 다이아몬드 순환)
                Button formationButton = Button.builder(
                        Component.literal(getFormationButtonText()),
                        btn -> {
                            selectedFormation = dev.minse.interiorveil.StrikeFormation.byIndex((selectedFormation.ordinal() + 1) % dev.minse.interiorveil.StrikeFormation.values().length);
                            btn.setMessage(Component.literal(getFormationButtonText()));
                            if (client.player != null) {
                                client.player.displayClientMessage(Component.literal("§6[진형 변경] §e" + selectedFormation.getDisplayName() + "§7 선택됨"), true);
                            }
                        }
                ).bounds(scaledWidth - 225, 6, 105, 20).build();
                Screens.getButtons(screen).add(formationButton);

                // Xaero 지도 화면 우측 상단에 [🚀 궤도 폭격] 버튼 주입
                Screens.getButtons(screen).add(Button.builder(
                        Component.literal(String.format("§c🚀 궤도 폭격 (%s)", strikeKeyName)),
                        button -> fireStrikeAtXaeroCursor(screen, true)
                ).bounds(scaledWidth - 115, 6, 105, 20).build());

                // Xaero 지도 화면에서 좌클릭 시 목표 지점 조준 핀 고정
                net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.afterMouseClick(screen).register((scr, event, doubleClick) -> {
                    if (event.button() == 0) {
                        pinTargetAtXaeroCursor(scr);
                    }
                    return false;
                });

                // Xaero 지도 화면에서 설정된 폭격 키를 누르면 찍어둔 조준점으로 궤도 폭격만 단독 발사 (텔레포트 원천 차단)
                net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents.allowKeyPress(screen).register((scr, event) -> {
                    boolean isStrikeKey = false;
                    if (VeilKeyBindings.ORBITAL_STRIKE != null && VeilKeyBindings.ORBITAL_STRIKE.matches(event)) {
                        isStrikeKey = true;
                    } else if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_V) {
                        isStrikeKey = true;
                    }

                    // B 키를 누르면 지도 화면에서 즉시 탄종 빠른 순환 전환!
                    if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_B) {
                        selectedStrikeType = (selectedStrikeType + 1) % 3;
                        typeButton.setMessage(Component.literal(getStrikeTypeButtonText()));
                        if (client.player != null) {
                            String label = selectedStrikeType == 1 ? "⚡ EMP 전자기 펄스탄" : (selectedStrikeType == 2 ? "📦 궤도 보급 포드" : "💥 고폭 열폭풍탄");
                            client.player.displayClientMessage(Component.literal("§6[탄종 변경] §f" + label + "§7 선택됨"), true);
                        }
                        return false;
                    }

                    // F 키를 누르면 지도 화면에서 즉시 진형 빠른 순환 전환!
                    if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_F) {
                        selectedFormation = dev.minse.interiorveil.StrikeFormation.byIndex((selectedFormation.ordinal() + 1) % dev.minse.interiorveil.StrikeFormation.values().length);
                        formationButton.setMessage(Component.literal(getFormationButtonText()));
                        if (client.player != null) {
                            client.player.displayClientMessage(Component.literal("§6[진형 변경] §e" + selectedFormation.getDisplayName() + "§7 선택됨"), true);
                        }
                        return false;
                    }

                    if (isStrikeKey) {
                        fireStrikeAtXaeroCursor(scr, false); // 폭격 즉시 발사!
                        return false; // Xaero의 텔레포트/웨이포인트 기본 동작 실행을 100% 원천 차단
                    }
                    return true;
                });

                // Xaero 지도 화면에 결계(Veil Barrier) 영역 오버레이 실시간 렌더링
                ScreenEvents.afterRender(screen).register((scr, guiGraphics, mouseX, mouseY, tickDelta) -> {
                    renderVeilBarriersOnXaeroMap(scr, guiGraphics, mouseX, mouseY);
                });
            }
        });
    }

    /**
     * 폭격 전: Xaero 지도에서 마우스 클릭 시 해당 지점을 조준 핀으로 고정한다.
     */
    public static void pinTargetAtXaeroCursor(Screen screen) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        VeilConfigPayload current = VeilConfigClientState.current();
        UUID barrierId = current != null ? current.barrierId() : new UUID(0L, 0L);

        int[] worldCoords = extractXaeroWorldCoordinates(screen);
        int targetX = worldCoords[0];
        int targetZ = worldCoords[1];
        int targetY = client.player.getBlockY();
        if (client.level != null) {
            targetY = client.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, targetX, targetZ);
        }

        int strikeRadius = current != null ? current.strikeRadius() : 20;
        VeilStrikeTargetTracker.pinTarget(barrierId, targetX, targetY, targetZ, strikeRadius);

        // 조준 완료 액션바 알림
        client.player.displayClientMessage(
                Component.literal(String.format("§e🎯 [X: %d, Z: %d] 목표 지점 조준 완료! §7(V키로 폭격 발사)", targetX, targetZ)),
                true
        );
    }

    /**
     * Xaero 지도 화면에서 마우스 커서가 가리키는 실제 월드 좌표(X, Z)를 추출하여 즉시 궤도 폭격을 발사한다.
     */
    public static void fireStrikeAtXaeroCursor(Screen screen, boolean fromButton) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        VeilConfigPayload current = VeilConfigClientState.current();
        UUID barrierId = current != null ? current.barrierId() : new UUID(0L, 0L);

        // 1. 이미 좌클릭 등으로 지정된 조준 핀이 있다면 마우스 커서 위치를 완전히 무시하고 그 목표 좌표로만 고정 발사!
        VeilStrikeTargetTracker.TargetEntry activeEntry = VeilStrikeTargetTracker.getActiveTarget(barrierId);
        int targetX;
        int targetY;
        int targetZ;

        if (activeEntry != null) {
            targetX = activeEntry.targetX();
            targetY = activeEntry.targetY();
            targetZ = activeEntry.targetZ();
        } else {
            // 조준 핀이 없는 경우에만 현재 마우스 커서 좌표 추출
            int[] worldCoords = extractXaeroWorldCoordinates(screen);
            targetX = worldCoords[0];
            targetZ = worldCoords[1];
            targetY = client.player.getBlockY();
            if (client.level != null) {
                targetY = client.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, targetX, targetZ);
            }
        }

        if (VeilStrikeTargetTracker.isOnCooldown()) {
            long rem = VeilStrikeTargetTracker.getRemainingCooldownSeconds();
            client.player.displayClientMessage(
                    Component.literal(String.format("§c⚠ 궤도 함포 재장전 및 냉각 중입니다! (남은 시간: %d초)", rem)),
                    true
            );
            return;
        }

        int strikeRadius = current != null ? current.strikeRadius() : 20;

        // 2분 만료 카운트다운 시작 (FIRED 상태 전환)
        VeilStrikeTargetTracker.recordStrike(barrierId, targetX, targetY, targetZ, strikeRadius);

        // 2. 서버로 궤도 폭격 발사 패킷 전송 (탄종 및 진형 포함)
        ClientPlayNetworking.send(new VeilAdminActionPayload(
                barrierId, "laser_fire", targetX + "," + targetY + "," + targetZ + "," + strikeRadius + "," + selectedStrikeType + "," + selectedFormation.ordinal()
        ));

        // 3. 발사 사운드 및 클라이언트 알림
        if (client.level != null) {
            client.level.playLocalSound(
                    client.player.getX(), client.player.getY(), client.player.getZ(),
                    SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0F, 1.2F, false
            );
        }

        String typeLabel = selectedStrikeType == 1 ? "⚡ EMP 펄스" : (selectedStrikeType == 2 ? "📦 궤도 보급 포드" : "💥 고폭 열폭풍");
        client.player.displayClientMessage(
                Component.literal(String.format("§c🛰️ [%s] 목표 [X: %d, Y: %d, Z: %d]으로 발사 승인!", typeLabel, targetX, targetY, targetZ)),
                true
        );
    }

    /**
     * Xaero World Map 화면 위에 활성화된 결계들의 보호 구역 원형 및 네온 링, 라벨을 렌더링한다.
     */
    private static void renderVeilBarriersOnXaeroMap(Screen screen, GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        // 현재 클라이언트가 알고 있는 결계 정보 탐색
        ForcefieldStatePayload state = VeilForcefieldRenderer.getCurrentState();
        VeilConfigPayload currentConfig = VeilConfigClientState.current();
        if (state == null && currentConfig == null) return;

        double originX = state != null ? state.centerX() : (client.player.getX());
        double originZ = state != null ? state.centerZ() : (client.player.getZ());
        int radius = state != null ? (int) state.radius() : (currentConfig != null ? currentConfig.radius() : 30);
        int beaconColor = state != null ? state.color() : (currentConfig != null ? (currentConfig.beaconColor() & 0xFFFFFF) : 0x00E5FF);

        double[] mapTransform = getXaeroMapTransform(screen);
        double camX = mapTransform[0];
        double camZ = mapTransform[1];
        double effectiveScale = mapTransform[2];

        // Xaero 화면 좌표계로 투영 (화면 중심 기준)
        double screenCenterX = (screen.width / 2.0) + (originX - camX) * effectiveScale;
        double screenCenterY = (screen.height / 2.0) + (originZ - camZ) * effectiveScale;
        double screenRadius = radius * effectiveScale;

        // 화면 밖이면 렌더링 생략
        if (screenCenterX + screenRadius < 0 || screenCenterX - screenRadius > screen.width
                || screenCenterY + screenRadius < 0 || screenCenterY - screenRadius > screen.height) {
            return;
        }

        // 1. 반투명 결계 내부 보호 영역 채우기
        int fillAlpha = 0x28; // 약 16% 투명도
        int fillColor = (fillAlpha << 24) | (beaconColor != 0 ? beaconColor : 0x00E5FF);
        drawCircleFilled(guiGraphics, (int) Math.round(screenCenterX), (int) Math.round(screenCenterY), (int) Math.round(screenRadius), fillColor);

        // 2. 결계 네온 외곽 링 테두리 렌더링
        int ringAlpha = 0xCC; // 약 80% 불투명도
        int ringColor = (ringAlpha << 24) | (beaconColor != 0 ? beaconColor : 0x00E5FF);
        drawCircleOutline(guiGraphics, (int) Math.round(screenCenterX), (int) Math.round(screenCenterY), (int) Math.round(screenRadius), ringColor);

        // 3. 결계 중앙 마커 및 라벨
        int scX = (int) Math.round(screenCenterX);
        int scY = (int) Math.round(screenCenterY);
        guiGraphics.fill(scX - 2, scY - 2, scX + 3, scY + 3, 0xFFFFFFFF);
        String label = currentConfig != null
                ? String.format("§b🛡️ 결계 #%s (R: %d)", currentConfig.barrierId().toString().substring(0, 4), radius)
                : String.format("§b🛡️ 결계 구역 (R: %d)", radius);
        int labelWidth = client.font.width(label);
        guiGraphics.drawString(client.font, label, scX - labelWidth / 2, scY - 14, 0xFFFFFFFF, true);

        // 4. 최근 폭격 타겟 지점 마커 렌더링 (2분 자동 만료 시스템 연동)
        VeilStrikeTargetTracker.TargetEntry activeEntry = VeilStrikeTargetTracker.getActiveTarget(currentConfig != null ? currentConfig.barrierId() : null);
        if (activeEntry != null) {
            double targetScreenX = screen.width / 2.0 + (activeEntry.targetX() - camX) * effectiveScale;
            double targetScreenY = screen.height / 2.0 + (activeEntry.targetZ() - camZ) * effectiveScale;
            double targetRadiusScreen = activeEntry.strikeRadius() * effectiveScale;

            int alpha = (int) (activeEntry.getFadeAlpha() * 255);
            int strikeColor = (Math.max(40, alpha) << 24) | 0xFF2222;

            // 붉은 조준 십자선
            int tsX = (int) Math.round(targetScreenX);
            int tsY = (int) Math.round(targetScreenY);
            guiGraphics.fill(tsX - 7, tsY, tsX + 8, tsY + 1, strikeColor);
            guiGraphics.fill(tsX, tsY - 7, tsX + 1, tsY + 8, strikeColor);

            // 폭격 범위 링
            drawCircleOutline(guiGraphics, tsX, tsY, (int) Math.round(targetRadiusScreen), (Math.max(30, alpha / 2) << 24) | 0xFF3333);

            // 조준/폭격 라벨
            String statusStr;
            if (activeEntry.status() == VeilStrikeTargetTracker.Status.PINNED) {
                statusStr = String.format("§e🎯 [조준 목표] [%d, %d] §7(V키 발사)", activeEntry.targetX(), activeEntry.targetZ());
            } else {
                long remSec = activeEntry.getRemainingSeconds();
                statusStr = String.format("§c💥 [폭격 진행] §7| §e⏱ %d:%02d 후 소멸", remSec / 60, remSec % 60);
            }
            int tWidth = client.font.width(statusStr);
            guiGraphics.drawString(client.font, statusStr, tsX - tWidth / 2, tsY - 14, 0xFFFFFFFF, true);
        }

        // 5. 마우스 커서가 결계 내부를 가리키고 있다면 네온 HUD 툴팁 표시
        double distSq = Math.pow(mouseX - screenCenterX, 2) + Math.pow(mouseY - screenCenterY, 2);
        if (distSq <= Math.pow(screenRadius, 2)) {
            int tipX = Math.min(mouseX + 12, screen.width - 150);
            int tipY = Math.max(10, mouseY - 20);
            guiGraphics.fill(tipX - 4, tipY - 4, tipX + 148, tipY + 36, 0xEE111827);
            guiGraphics.fill(tipX - 4, tipY - 4, tipX + 148, tipY - 3, 0xFF00E5FF);
            guiGraphics.drawString(client.font, "§b🛡️ [결계 보호 구역]", tipX, tipY, 0xFFFFFFFF, true);
            guiGraphics.drawString(client.font, "§7반지름: §e" + radius + "m §a(열폭풍 면역)", tipX, tipY + 12, 0xFFFFFFFF, true);
            guiGraphics.drawString(client.font, "§a✔ 엔티티 및 블록 완전 보호", tipX, tipY + 24, 0xFFFFFFFF, true);
        }
    }

    /**
     * 원형 채우기 렌더링
     */
    private static void drawCircleFilled(GuiGraphics graphics, int cx, int cy, int r, int color) {
        if (r <= 0) return;
        for (int y = -r; y <= r; y++) {
            int dx = (int) Math.sqrt(r * r - y * y);
            graphics.fill(cx - dx, cy + y, cx + dx + 1, cy + y + 1, color);
        }
    }

    /**
     * 원형 테두리 링 렌더링
     */
    private static void drawCircleOutline(GuiGraphics graphics, int cx, int cy, int r, int color) {
        if (r <= 0) return;
        int numSegments = Math.max(24, Math.min(72, r * 2));
        for (int i = 0; i < numSegments; i++) {
            double angle = 2.0 * Math.PI * i / numSegments;
            int px = cx + (int) Math.round(Math.cos(angle) * r);
            int py = cy + (int) Math.round(Math.sin(angle) * r);
            graphics.fill(px - 1, py - 1, px + 2, py + 2, color);
        }
    }

    /**
     * Xaero GuiMap 내부의 카메라 위치와 유효 스케일을 추출한다 [camX, camZ, effectiveScale].
     */
    private static double[] getXaeroMapTransform(Screen screen) {
        Minecraft client = Minecraft.getInstance();
        double defaultCamX = client.player != null ? client.player.getX() : 0.0;
        double defaultCamZ = client.player != null ? client.player.getZ() : 0.0;
        double defaultScale = 1.0;
        double defaultScreenScale = client.getWindow() != null ? client.getWindow().getGuiScale() : 1.0;

        try {
            Class<?> clazz = screen.getClass();
            while (clazz != null && clazz != Object.class) {
                for (Field field : clazz.getDeclaredFields()) {
                    field.setAccessible(true);
                    String name = field.getName();
                    if (name.equals("cameraX")) {
                        Object val = field.get(screen);
                        if (val instanceof Number num) defaultCamX = num.doubleValue();
                    } else if (name.equals("cameraZ")) {
                        Object val = field.get(screen);
                        if (val instanceof Number num) defaultCamZ = num.doubleValue();
                    } else if (name.equals("scale")) {
                        Object val = field.get(screen);
                        if (val instanceof Number num && num.doubleValue() > 0.0001) defaultScale = num.doubleValue();
                    } else if (name.equals("screenScale")) {
                        Object val = field.get(screen);
                        if (val instanceof Number num && num.doubleValue() > 0.0001) defaultScreenScale = num.doubleValue();
                    }
                }
                clazz = clazz.getSuperclass();
            }
        } catch (Throwable ignored) {
        }

        if (defaultScreenScale <= 0.001) defaultScreenScale = 1.0;
        double effectiveScale = defaultScale / defaultScreenScale;

        return new double[]{defaultCamX, defaultCamZ, effectiveScale};
    }

    /**
     * Xaero GuiMap 내부의 mouseBlockPosX / mouseBlockPosZ 필드 또는 카메라 오프셋으로 실제 월드 좌표를 얻는다.
     */
    private static int[] extractXaeroWorldCoordinates(Screen screen) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return new int[]{0, 0};

        try {
            Class<?> clazz = screen.getClass();
            while (clazz != null && clazz != Object.class) {
                try {
                    Field fX = clazz.getDeclaredField("mouseBlockPosX");
                    Field fZ = clazz.getDeclaredField("mouseBlockPosZ");
                    fX.setAccessible(true);
                    fZ.setAccessible(true);
                    Object valX = fX.get(screen);
                    Object valZ = fZ.get(screen);
                    if (valX instanceof Number numX && valZ instanceof Number numZ) {
                        return new int[]{numX.intValue(), numZ.intValue()};
                    }
                } catch (NoSuchFieldException ignored) {}
                clazz = clazz.getSuperclass();
            }
        } catch (Throwable ignored) {
        }

        // 역산 폴백
        double[] transform = getXaeroMapTransform(screen);
        double camX = transform[0];
        double camZ = transform[1];
        double effectiveScale = transform[2];
        if (effectiveScale <= 0.00001) effectiveScale = 1.0;

        double mouseX = client.mouseHandler.xpos() * (double) client.getWindow().getGuiScaledWidth() / (double) client.getWindow().getScreenWidth();
        double mouseY = client.mouseHandler.ypos() * (double) client.getWindow().getGuiScaledHeight() / (double) client.getWindow().getScreenHeight();

        double relX = (mouseX - screen.width / 2.0) / effectiveScale;
        double relZ = (mouseY - screen.height / 2.0) / effectiveScale;

        return new int[]{(int) Math.round(camX + relX), (int) Math.round(camZ + relZ)};
    }
}
