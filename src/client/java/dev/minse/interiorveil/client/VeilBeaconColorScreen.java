package dev.minse.interiorveil.client;

import dev.minse.interiorveil.network.VeilConfigPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class VeilBeaconColorScreen extends Screen {
    private static final int[] PRESETS = {
            0xFFFFFF, 0x33FFFF, 0xFFD23F, 0xB66CFF, 0x55FF55, 0xFF5555, 0x3399FF, 0xFF66CC
    };

    private VeilConfigPayload current;
    private EditBox colorBox;
    private Component status = Component.empty();

    public VeilBeaconColorScreen(VeilConfigPayload payload) {
        super(Component.translatable("screen.interiorveil.beacon_color.title"));
        this.current = payload;
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        colorBox = new EditBox(this.font, center - 100, 72, 200, 22, Component.empty());
        colorBox.setMaxLength(7);
        colorBox.setValue(String.format("#%06X", current.beaconColor() & 0xFFFFFF));
        addRenderableWidget(colorBox);
        for (int index = 0; index < PRESETS.length; index++) {
            int color = PRESETS[index];
            int x = center - 100 + (index % 4) * 51;
            int y = 108 + (index / 4) * 24;
            addRenderableWidget(Button.builder(Component.literal(String.format("%06X", color)), button ->
                    colorBox.setValue(String.format("#%06X", color))
            ).bounds(x, y, 48, 20).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.interiorveil.config.save"), button -> save())
                .bounds(center - 100, 164, 98, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), button -> onClose())
                .bounds(center + 2, 164, 98, 20).build());
    }

    private void save() {
        try {
            String text = colorBox.getValue().strip();
            if (text.startsWith("#")) {
                text = text.substring(1);
            }
            if (!text.matches("[0-9a-fA-F]{6}")) {
                throw new IllegalArgumentException();
            }
            int color = Integer.parseInt(text, 16);
            current = new VeilConfigPayload(
                    current.barrierId(), current.name(), current.radius(), current.height(), current.fogMargin(),
                    current.fogDistance(), current.fogFadeTicks(), current.navigationRange(), current.boundaryVisible(),
                    current.boundaryColor(), current.navigationColor(), current.securityMode(), color,
                    current.accessStart(), current.accessEnd(), current.boundaryDensity(), current.boundarySize(),
                    current.navigationDensity(), current.navigationSize(), current.requireBeaconPower(),
                    false, current.disableFog(), current.fogColor(),
                    current.attackMode(), current.perimeterDefense(), current.attackTargetX(), current.attackTargetY(), current.attackTargetZ(),
                    current.strikeRadius(), current.absoluteBarrier(), current.reflectProjectiles(), current.allowedPlayers()
            );
            ClientPlayNetworking.send(current);
            VeilConfigClientState.accept(current);
            onClose();
        } catch (IllegalArgumentException ignored) {
            status = Component.translatable("screen.interiorveil.config.invalid_color");
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        int windowWidth = 260;
        int windowHeight = 220;
        int left = (this.width - windowWidth) / 2;
        int top = (this.height - windowHeight) / 2;

        int neonColor = 0xFF00F0FF;

        // 외곽 은은한 네온 글로우
        graphics.fill(left - 3, top - 3, left + windowWidth + 3, top + windowHeight + 3, 0x1500F0FF);
        graphics.fill(left - 1, top - 1, left + windowWidth + 1, top + windowHeight + 1, 0x3000F0FF);

        // 어두운 글래스 반투명 배경
        graphics.fill(left, top, left + windowWidth, top + windowHeight, 0xEE0A0E17);

        // 네온 테두리 선
        graphics.fill(left, top, left + windowWidth, top + 1, neonColor);
        graphics.fill(left, top + windowHeight - 1, left + windowWidth, top + windowHeight, neonColor);
        graphics.fill(left, top, left + 1, top + windowHeight, neonColor);
        graphics.fill(left + windowWidth - 1, top, left + windowWidth, top + windowHeight, neonColor);

        // 내부 빛 반사 느낌 (미세한 밝은 선)
        graphics.fill(left, top, left + windowWidth, top + 1, 0x40FFFFFF);
        graphics.fill(left, top, left + 1, top + windowHeight, 0x40FFFFFF);

        // 타이틀바 구분선
        graphics.fill(left + 10, top + 35, left + windowWidth - 10, top + 36, 0x3000F0FF);

        super.render(graphics, mouseX, mouseY, delta);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, top + 6, neonColor);
        graphics.drawCenteredString(this.font, Component.translatable("screen.interiorveil.beacon_color.help"), this.width / 2, top + 42, 0xFFAAAAAA);
        graphics.drawCenteredString(this.font, status, this.width / 2, top + windowHeight - 14, 0xFFFF5555);
    }
}
