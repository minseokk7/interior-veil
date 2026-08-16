package dev.minse.interiorveil.client;

import dev.minse.interiorveil.network.VeilAdminActionPayload;
import dev.minse.interiorveil.network.VeilOverviewPayload.VeilSummary;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

public final class VeilOverviewScreen extends Screen {
    private final List<VeilSummary> veils;
    private int page = 0;
    private static final int ITEMS_PER_PAGE = 7;

    public VeilOverviewScreen(List<VeilSummary> veils) {
        super(Component.translatable("screen.interiorveil.overview.title"));
        this.veils = veils;
    }

    @Override
    protected void init() {
        clearWidgets();
        int y = 42;
        int startIndex = page * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, veils.size());

        for (int i = startIndex; i < endIndex; i++) {
            VeilSummary veil = veils.get(i);
            int currentY = y;
            addRenderableWidget(Button.builder(Component.translatable("screen.interiorveil.overview.teleport"), button -> teleportTo(veil.id()))
                    .bounds(this.width / 2 + 60, currentY - 4, 40, 20)
                    .build());
            addRenderableWidget(Button.builder(Component.translatable("screen.interiorveil.overview.delete"), button -> deleteVeil(veil.id()))
                    .bounds(this.width / 2 + 105, currentY - 4, 40, 20)
                    .build());
            y += 24;
        }

        int buttonY = this.height - 28;
        if (page > 0) {
            addRenderableWidget(Button.builder(Component.translatable("screen.interiorveil.overview.prev"), button -> {
                page--;
                init();
            }).bounds(this.width / 2 - 160, buttonY, 50, 20).build());
        }
        if (endIndex < veils.size()) {
            addRenderableWidget(Button.builder(Component.translatable("screen.interiorveil.overview.next"), button -> {
                page++;
                init();
            }).bounds(this.width / 2 + 110, buttonY, 50, 20).build());
        }

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(this.width / 2 - 75, buttonY, 150, 20)
                .build());
    }

    private void teleportTo(UUID veilId) {
        ClientPlayNetworking.send(new VeilAdminActionPayload(veilId, "teleport", veilId.toString()));
        onClose();
    }

    private void deleteVeil(UUID veilId) {
        ClientPlayNetworking.send(new VeilAdminActionPayload(veilId, "delete_veil", veilId.toString()));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        int windowWidth = 340;
        int windowHeight = this.height - 20;
        int wLeft = (this.width - windowWidth) / 2;
        int wTop = 10;

        // Glassmorphism 반투명 다크 그라데이션 배경
        graphics.fillGradient(wLeft, wTop, wLeft + windowWidth, wTop + windowHeight, 0xE0050A0F, 0xD00A101A);

        // Neon Cyan 테두리
        int neonColor = 0xFF00F0FF;
        graphics.fill(wLeft - 1, wTop - 1, wLeft + windowWidth + 1, wTop, neonColor);
        graphics.fill(wLeft - 1, wTop + windowHeight, wLeft + windowWidth + 1, wTop + windowHeight + 1, neonColor);
        graphics.fill(wLeft - 1, wTop, wLeft, wTop + windowHeight, neonColor);
        graphics.fill(wLeft + windowWidth, wTop, wLeft + windowWidth + 1, wTop + windowHeight, neonColor);

        // 내부 빛 반사 느낌
        graphics.fill(wLeft, wTop, wLeft + windowWidth, wTop + 1, 0x40FFFFFF);
        graphics.fill(wLeft, wTop, wLeft + 1, wTop + windowHeight, 0x40FFFFFF);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, neonColor);

        if (veils.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("screen.interiorveil.overview.empty"), this.width / 2, 60, 0xFFE8E8E8);
        } else {
            int y = 42;
            int startIndex = page * ITEMS_PER_PAGE;
            int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, veils.size());

            for (int i = startIndex; i < endIndex; i++) {
                VeilSummary veil = veils.get(i);
                String info = "%s | %d, %d, %d | Lv.%d | %s%s".formatted(
                        veil.name(),
                        veil.x(), veil.y(), veil.z(),
                        veil.level(),
                        veil.secure() ? "LOCK" : "OPEN",
                        veil.recent().isEmpty() ? "" : " | 최근: " + veil.recent()
                );
                graphics.drawString(this.font, info, this.width / 2 - 150, y, 0xFFE8E8E8, false);
                y += 24;
            }
            graphics.drawCenteredString(this.font, (page + 1) + " / " + Math.max(1, (veils.size() + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE), this.width / 2, this.height - 40, 0xFFAAAAAA);
        }
        super.render(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
