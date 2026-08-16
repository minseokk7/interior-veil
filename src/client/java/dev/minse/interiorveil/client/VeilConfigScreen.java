package dev.minse.interiorveil.client;

import dev.minse.interiorveil.VeilConstants;
import dev.minse.interiorveil.network.VeilAdminActionPayload;
import dev.minse.interiorveil.network.VeilConfigPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class VeilConfigScreen extends Screen {
    private static final int GENERAL = 0;
    private static final int VISUAL = 1;
    private static final int ACCESS = 2;
    private static final int ATTACK = 3;
    private static final int GUARD = 4;

    private VeilConfigPayload current;
    private int page;
    private EditBox first;
    private EditBox second;
    private EditBox third;
    private EditBox fourth;
    private EditBox fifth;
    private EditBox sixth;
    private EditBox seventh;
    private EditBox eighth;
    private boolean boundaryVisible;
    private boolean securityMode;
    private boolean requireBeaconPower;
    private boolean attackMode;
    private boolean absoluteBarrier;
    private boolean reflectProjectiles;
    private boolean disableFog;
    private Button securityButton;
    private Button boundaryButton;
    private Button powerButton;
    private Button attackButton;
    private Component status = Component.empty();

    public VeilConfigScreen(VeilConfigPayload payload) {
        super(Component.translatable("screen.interiorveil.config.title"));
        this.current = payload;
        this.boundaryVisible = payload.boundaryVisible();
        this.securityMode = payload.securityMode();
        this.requireBeaconPower = payload.requireBeaconPower();
        this.attackMode = payload.attackMode();
        this.absoluteBarrier = payload.absoluteBarrier();
        this.reflectProjectiles = payload.reflectProjectiles();
        this.disableFog = payload.disableFog();
    }

    @Override
    protected void init() {
        int top = (this.height - 275) / 2;
        int left = this.width / 2 - 155;
        int right = this.width / 2 + 5;
        addRenderableWidget(tab("screen.interiorveil.config.tab_general", GENERAL, left, top + 18));
        addRenderableWidget(tab("screen.interiorveil.config.tab_visual", VISUAL, left + 63, top + 18));
        addRenderableWidget(tab("screen.interiorveil.config.tab_access", ACCESS, left + 126, top + 18));
        addRenderableWidget(tab("screen.interiorveil.config.tab_attack", ATTACK, left + 189, top + 18));
        addRenderableWidget(tab("screen.interiorveil.config.tab_guard", GUARD, left + 252, top + 18));
        if (page == GENERAL) {
            initGeneral(left, right, top);
        } else if (page == VISUAL) {
            initVisual(left, right, top);
        } else if (page == ACCESS) {
            initAccess(left, right, top);
        } else if (page == ATTACK) {
            initAttack(left, right, top);
        } else {
            initGuard(left, right, top);
        }
    }

    private Button tab(String key, int targetPage, int x, int y) {
        return Button.builder(Component.translatable(key), button -> {
            page = targetPage;
            rebuildWidgets();
        }).bounds(x, y, 59, 18).build();
    }

    private void initGeneral(int left, int right, int top) {
        first = field(left, top + 52, current.name(), false, 32);
        second = field(right, top + 52, Integer.toString(current.radius()), true, 3);
        third = field(left, top + 90, Integer.toString(current.height()), true, 3);
        fourth = field(right, top + 90, Integer.toString(current.fogMargin()), true, 3);
        fifth = field(left, top + 128, Integer.toString(current.fogDistance()), true, 2);
        sixth = field(right, top + 128, Integer.toString(current.fogFadeTicks()), true, 3);
        seventh = field(left, top + 166, Integer.toString(current.navigationRange()), true, 3);
        securityButton = addRenderableWidget(toggle(right, top + 166, securityMessage(), button -> {
            securityMode = !securityMode;
            button.setMessage(securityMessage());
        }, "screen.interiorveil.config.security_tip"));
        boundaryButton = addRenderableWidget(toggle(left, top + 204, boundaryMessage(), button -> {
            boundaryVisible = !boundaryVisible;
            button.setMessage(boundaryMessage());
        }, "screen.interiorveil.config.boundary_tip"));
        powerButton = addRenderableWidget(toggle(right, top + 204, powerMessage(), button -> {
            requireBeaconPower = !requireBeaconPower;
            button.setMessage(powerMessage());
        }, "screen.interiorveil.config.power_tip"));
        footer(left, right, top, this::saveGeneral, this::resetGeneral);
    }

    private void initVisual(int left, int right, int top) {
        first = field(left, top + 52, hex(current.boundaryColor()), false, 7);
        second = field(right, top + 52, hex(current.navigationColor()), false, 7);
        third = field(left, top + 90, hex(current.beaconColor()), false, 7);
        fourth = field(right, top + 90, Integer.toString(current.boundaryDensity()), true, 3);
        fifth = field(left, top + 128, Float.toString(current.boundarySize()), false, 4);
        sixth = field(right, top + 128, Integer.toString(current.navigationDensity()), true, 2);
        seventh = field(left, top + 166, Float.toString(current.navigationSize()), false, 4);
        eighth = field(right, top + 166, hex(current.fogColor()), false, 7);
        addRenderableWidget(toggle(left, top + 204, fogMessage(), button -> {
            disableFog = !disableFog;
            button.setMessage(fogMessage());
        }, "screen.interiorveil.config.fog_tip"));
        footer(left, right, top, this::saveVisual, this::resetVisual);
    }

    private void initAccess(int left, int right, int top) {
        first = field(left, top + 52, Integer.toString(current.accessStart()), true, 5);
        second = field(right, top + 52, Integer.toString(current.accessEnd()), true, 5);
        third = field(left, top + 116, "", false, 16);
        addRenderableWidget(actionButton(right, top + 116, "screen.interiorveil.config.allow", "allow"));
        addRenderableWidget(actionButton(right, top + 140, "screen.interiorveil.config.deny", "deny"));
        addRenderableWidget(Button.builder(Component.translatable("screen.interiorveil.config.revoke_keys"), button -> {
            ClientPlayNetworking.send(new VeilAdminActionPayload(current.barrierId(), "revoke_keys", ""));
            status = Component.translatable("screen.interiorveil.config.keys_revoked");
        }).bounds(left, top + 166, 310, 20).tooltip(Tooltip.create(Component.translatable("screen.interiorveil.config.revoke_tip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.interiorveil.config.overview"), button ->
                ClientPlayNetworking.send(new VeilAdminActionPayload(current.barrierId(), "overview", ""))
        ).bounds(left, top + 204, 310, 18).build());
        footer(left, right, top, this::saveAccess, () -> {
            first.setValue("0");
            second.setValue("0");
        });
    }

    private void initAttack(int left, int right, int top) {
        first = field(left, top + 52, 100, Integer.toString(current.attackTargetX()), false, 11);
        second = field(left + 105, top + 52, 100, Integer.toString(current.attackTargetY()), false, 11);
        third = field(left + 210, top + 52, 100, Integer.toString(current.attackTargetZ()), false, 11);
        first.setFilter(text -> text.matches("-?\\d*"));
        second.setFilter(text -> text.matches("-?\\d*"));
        third.setFilter(text -> text.matches("-?\\d*"));

        attackButton = addRenderableWidget(toggle(left, top + 86, attackMessage(), button -> {
            attackMode = !attackMode;
            button.setMessage(attackMessage());
        }, "screen.interiorveil.config.attack_tip"));

        fourth = field(right, top + 86, Integer.toString(current.strikeRadius()), true, 3);

        addRenderableWidget(Button.builder(Component.translatable("screen.interiorveil.config.attack_map"), button -> {
            if (saveAttack(false)) {
                ClientPlayNetworking.send(new VeilAdminActionPayload(current.barrierId(), "map_request", ""));
            }
        }).bounds(left, top + 120, 310, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("screen.interiorveil.config.attack_fire"), button ->
                saveAttack(true)
        ).bounds(left, top + 146, 310, 20).tooltip(Tooltip.create(Component.translatable(
                "screen.interiorveil.config.attack_fire_tip"
        ))).build());

        footer(left, right, top, () -> saveAttack(false), this::resetAttack);
    }

    private void initGuard(int left, int right, int top) {
        addRenderableWidget(toggle(left, top + 52, absoluteBarrierMessage(), button -> {
            absoluteBarrier = !absoluteBarrier;
            button.setMessage(absoluteBarrierMessage());
        }, "screen.interiorveil.config.absolute_barrier_tip")).setWidth(310);
        addRenderableWidget(toggle(left, top + 78, reflectProjectilesMessage(), button -> {
            reflectProjectiles = !reflectProjectiles;
            button.setMessage(reflectProjectilesMessage());
        }, "screen.interiorveil.config.reflect_tip")).setWidth(310);
        footer(left, right, top, this::saveGuard, this::resetGuard);
    }

    private Button actionButton(int x, int y, String key, String action) {
        return Button.builder(Component.translatable(key), button -> {
            String playerName = third.getValue().strip();
            if (!playerName.isEmpty()) {
                ClientPlayNetworking.send(new VeilAdminActionPayload(current.barrierId(), action, playerName));
                status = Component.translatable("screen.interiorveil.config.access_sent");
            }
        }).bounds(x, y, 150, 20).build();
    }

    private void footer(int left, int right, int top, Runnable save, Runnable reset) {
        addRenderableWidget(Button.builder(Component.translatable("screen.interiorveil.config.save"), button -> save.run())
                .bounds(left, top + 242, 95, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.interiorveil.config.defaults"), button -> reset.run())
                .bounds(left + 100, top + 242, 105, 20).tooltip(Tooltip.create(Component.translatable("screen.interiorveil.config.defaults_tip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> onClose())
                .bounds(right + 55, top + 242, 95, 20).build());
    }

    private Button toggle(int x, int y, Component message, Button.OnPress press, String tooltipKey) {
        return Button.builder(message, press)
                .bounds(x, y, 150, 20)
                .tooltip(Tooltip.create(Component.translatable(tooltipKey)))
                .build();
    }

    private EditBox field(int x, int y, String value, boolean numeric, int maxLength) {
        return field(x, y, 150, value, numeric, maxLength);
    }

    private EditBox field(int x, int y, int width, String value, boolean numeric, int maxLength) {
        EditBox box = new EditBox(this.font, x, y, width, 20, Component.empty());
        box.setMaxLength(maxLength);
        if (numeric) {
            box.setFilter(text -> text.matches("\\d*"));
        }
        box.setValue(value);
        return addRenderableWidget(box);
    }

    private void saveGeneral() {
        try {
            send(new VeilConfigPayload(
                    current.barrierId(), first.getValue(),
                    integer(second, VeilConstants.MIN_RADIUS, VeilConstants.MAX_RADIUS),
                    integer(third, VeilConstants.MIN_HEIGHT, VeilConstants.MAX_HEIGHT),
                    integer(fourth, VeilConstants.MIN_FOG_MARGIN, VeilConstants.MAX_FOG_MARGIN),
                    integer(fifth, VeilConstants.MIN_FOG_DISTANCE, VeilConstants.MAX_FOG_DISTANCE),
                    integer(sixth, VeilConstants.MIN_FOG_FADE_TICKS, VeilConstants.MAX_FOG_FADE_TICKS),
                    integer(seventh, VeilConstants.MIN_NAVIGATION_RANGE, VeilConstants.MAX_NAVIGATION_RANGE),
                    boundaryVisible, current.boundaryColor(), current.navigationColor(), securityMode,
                    current.beaconColor(), current.accessStart(), current.accessEnd(), current.boundaryDensity(),
                    current.boundarySize(), current.navigationDensity(), current.navigationSize(), requireBeaconPower, false,
                    current.disableFog(), current.fogColor(), attackMode, current.attackTargetX(), current.attackTargetY(),
                    current.attackTargetZ(), current.strikeRadius(), absoluteBarrier, reflectProjectiles, current.allowedPlayers()
            ));
        } catch (IllegalArgumentException ignored) {
            invalid();
        }
    }

    private void saveVisual() {
        try {
            send(new VeilConfigPayload(
                    current.barrierId(), current.name(), current.radius(), current.height(), current.fogMargin(),
                    current.fogDistance(), current.fogFadeTicks(), current.navigationRange(), boundaryVisible,
                    color(first), color(second), securityMode, color(third), current.accessStart(), current.accessEnd(),
                    integer(fourth, 24, 192), decimal(fifth, 0.25F, 3.0F), integer(sixth, 1, 12),
                    decimal(seventh, 0.25F, 2.0F), requireBeaconPower, false, disableFog, color(eighth), attackMode,
                    current.attackTargetX(), current.attackTargetY(), current.attackTargetZ(), current.strikeRadius(),
                    absoluteBarrier, reflectProjectiles, current.allowedPlayers()
            ));
        } catch (IllegalArgumentException ignored) {
            invalid();
        }
    }

    private void saveAccess() {
        try {
            send(new VeilConfigPayload(
                    current.barrierId(), current.name(), current.radius(), current.height(), current.fogMargin(),
                    current.fogDistance(), current.fogFadeTicks(), current.navigationRange(), boundaryVisible,
                    current.boundaryColor(), current.navigationColor(), securityMode, current.beaconColor(),
                    integer(first, 0, 23999), integer(second, 0, 23999), current.boundaryDensity(),
                    current.boundarySize(), current.navigationDensity(), current.navigationSize(),
                    requireBeaconPower, false, current.disableFog(), current.fogColor(), attackMode,
                    current.attackTargetX(), current.attackTargetY(), current.attackTargetZ(), current.strikeRadius(),
                    absoluteBarrier, reflectProjectiles, current.allowedPlayers()
            ));
        } catch (IllegalArgumentException ignored) {
            invalid();
        }
    }

    private boolean saveAttack(boolean fire) {
        try {
            int targetX = Integer.parseInt(first.getValue());
            int targetY = Integer.parseInt(second.getValue());
            int targetZ = Integer.parseInt(third.getValue());
            int radius = integer(fourth, 1, 64);
            send(new VeilConfigPayload(
                    current.barrierId(), current.name(), current.radius(), current.height(), current.fogMargin(),
                    current.fogDistance(), current.fogFadeTicks(), current.navigationRange(), boundaryVisible,
                    current.boundaryColor(), current.navigationColor(), securityMode, current.beaconColor(),
                    current.accessStart(), current.accessEnd(), current.boundaryDensity(), current.boundarySize(),
                    current.navigationDensity(), current.navigationSize(), requireBeaconPower, false, current.disableFog(), current.fogColor(),
                    attackMode, targetX, targetY, targetZ, radius, absoluteBarrier, reflectProjectiles, current.allowedPlayers()
            ));
            if (fire) {
                ClientPlayNetworking.send(new VeilAdminActionPayload(
                        current.barrierId(), "laser_fire", targetX + "," + targetY + "," + targetZ + "," + radius
                ));
            }
            return true;
        } catch (IllegalArgumentException ignored) {
            invalid();
            return false;
        }
    }

    private void saveGuard() {
        send(new VeilConfigPayload(
                current.barrierId(), current.name(), current.radius(), current.height(), current.fogMargin(),
                current.fogDistance(), current.fogFadeTicks(), current.navigationRange(), boundaryVisible,
                current.boundaryColor(), current.navigationColor(), securityMode, current.beaconColor(),
                current.accessStart(), current.accessEnd(), current.boundaryDensity(), current.boundarySize(),
                current.navigationDensity(), current.navigationSize(), requireBeaconPower, false, current.disableFog(), current.fogColor(),
                attackMode, current.attackTargetX(), current.attackTargetY(),
                current.attackTargetZ(), current.strikeRadius(), absoluteBarrier, reflectProjectiles, current.allowedPlayers()
        ));
    }

    private void resetGuard() {
        absoluteBarrier = current.absoluteBarrier();
        reflectProjectiles = current.reflectProjectiles();
        rebuildWidgets();
    }

    private void send(VeilConfigPayload payload) {
        ClientPlayNetworking.send(payload);
        current = payload;
        VeilConfigClientState.accept(payload);
        status = Component.translatable("screen.interiorveil.config.sent");
    }

    private void resetGeneral() {
        first.setValue(current.name());
        second.setValue(Integer.toString(VeilConstants.DEFAULT_RADIUS));
        third.setValue(Integer.toString(VeilConstants.DEFAULT_HEIGHT));
        fourth.setValue(Integer.toString(VeilConstants.AMBIENT_FOG_MARGIN));
        fifth.setValue(Integer.toString(VeilConstants.DEFAULT_FOG_DISTANCE));
        sixth.setValue(Integer.toString(VeilConstants.DEFAULT_FOG_FADE_TICKS));
        seventh.setValue(Integer.toString(VeilConstants.NAVIGATION_RANGE));
        securityMode = false;
        boundaryVisible = true;
        requireBeaconPower = false;
        securityButton.setMessage(securityMessage());
        boundaryButton.setMessage(boundaryMessage());
        powerButton.setMessage(powerMessage());
    }

    private void resetVisual() {
        first.setValue(hex(VeilConstants.DEFAULT_BOUNDARY_COLOR));
        second.setValue(hex(VeilConstants.DEFAULT_NAVIGATION_COLOR));
        third.setValue("#FFFFFF");
        fourth.setValue("96");
        fifth.setValue("1.25");
        sixth.setValue("3");
        seventh.setValue("0.75");
        eighth.setValue("#B8C2CC");
        disableFog = false;
        rebuildWidgets();
    }

    private void resetAttack() {
        first.setValue(Integer.toString(current.attackTargetX()));
        second.setValue(Integer.toString(current.attackTargetY()));
        third.setValue(Integer.toString(current.attackTargetZ()));
        attackMode = current.attackMode();
        absoluteBarrier = current.absoluteBarrier();
        reflectProjectiles = current.reflectProjectiles();
        if (attackButton != null) attackButton.setMessage(attackMessage());
    }

    private static int integer(EditBox box, int minimum, int maximum) {
        int value = Integer.parseInt(box.getValue());
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException();
        }
        return value;
    }

    private static float decimal(EditBox box, float minimum, float maximum) {
        float value = Float.parseFloat(box.getValue());
        if (!Float.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException();
        }
        return value;
    }

    private static int color(EditBox box) {
        String value = box.getValue().strip();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        if (!value.matches("[0-9a-fA-F]{6}")) {
            throw new IllegalArgumentException();
        }
        return Integer.parseInt(value, 16);
    }

    private static String hex(int color) {
        return String.format("#%06X", color & 0xFFFFFF);
    }

    private void invalid() {
        status = Component.translatable("screen.interiorveil.config.invalid");
    }

    private Component securityMessage() {
        return Component.translatable(securityMode
                ? "screen.interiorveil.config.security_on" : "screen.interiorveil.config.security_off");
    }

    private Component boundaryMessage() {
        return Component.translatable(boundaryVisible
                ? "screen.interiorveil.config.boundary_on" : "screen.interiorveil.config.boundary_off");
    }

    private Component powerMessage() {
        return Component.translatable(requireBeaconPower
                ? "screen.interiorveil.config.power_on" : "screen.interiorveil.config.power_off");
    }

    private Component attackMessage() {
        return Component.translatable(attackMode
                ? "screen.interiorveil.config.attack_on" : "screen.interiorveil.config.attack_off");
    }

    private Component absoluteBarrierMessage() {
        return Component.translatable(absoluteBarrier
                ? "screen.interiorveil.config.absolute_barrier_on" : "screen.interiorveil.config.absolute_barrier_off");
    }

    private Component reflectProjectilesMessage() {
        return Component.translatable(reflectProjectiles
                ? "screen.interiorveil.config.reflect_on" : "screen.interiorveil.config.reflect_off");
    }

    private Component fogMessage() {
        return Component.translatable(disableFog
                ? "screen.interiorveil.config.fog_off" : "screen.interiorveil.config.fog_on");
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        
        int windowWidth = 340;
        int windowHeight = 275;
        int left = (this.width - windowWidth) / 2;
        int top = (this.height - windowHeight) / 2;
        
        // Glassmorphism 반투명 다크 그라데이션 배경
        graphics.fillGradient(left, top, left + windowWidth, top + windowHeight, 0xE0050A0F, 0xD00A101A);
        
        // Neon Cyan 테두리
        int neonColor = 0xFF00F0FF;
        graphics.fill(left - 1, top - 1, left + windowWidth + 1, top, neonColor); // Top
        graphics.fill(left - 1, top + windowHeight, left + windowWidth + 1, top + windowHeight + 1, neonColor); // Bottom
        graphics.fill(left - 1, top, left, top + windowHeight, neonColor); // Left
        graphics.fill(left + windowWidth, top, left + windowWidth + 1, top + windowHeight, neonColor); // Right
        
        // 내부 빛 반사 느낌 (미세한 밝은 선)
        graphics.fill(left, top, left + windowWidth, top + 1, 0x40FFFFFF);
        graphics.fill(left, top, left + 1, top + windowHeight, 0x40FFFFFF);
        
        // 타이틀바 구분선
        graphics.fill(left + 10, top + 35, left + windowWidth - 10, top + 36, 0x3000F0FF);

        super.render(graphics, mouseX, mouseY, delta);
        
        graphics.drawCenteredString(this.font, this.title, this.width / 2, top + 6, neonColor);
        
        int contentLeft = this.width / 2 - 155;
        int contentRight = this.width / 2 + 5;
        if (page == GENERAL) {
            labelsGeneral(graphics, contentLeft, contentRight, top);
        } else if (page == VISUAL) {
            labelsVisual(graphics, contentLeft, contentRight, top);
        } else if (page == ACCESS) {
            labelsAccess(graphics, contentLeft, contentRight, top);
        } else if (page == ATTACK) {
            labelsAttack(graphics, contentLeft, top);
        } else {
            labelsGuard(graphics, contentLeft, top);
        }
        graphics.drawCenteredString(this.font, status, this.width / 2, top + windowHeight - 12, 0xFFFFD23F);
    }

    private void labelsGeneral(GuiGraphics graphics, int left, int right, int top) {
        label(graphics, "screen.interiorveil.config.name", left, top + 38);
        label(graphics, "screen.interiorveil.config.radius", right, top + 38);
        label(graphics, "screen.interiorveil.config.height", left, top + 76);
        label(graphics, "screen.interiorveil.config.fog_margin", right, top + 76);
        label(graphics, "screen.interiorveil.config.fog_distance", left, top + 114);
        label(graphics, "screen.interiorveil.config.fade_ticks", right, top + 114);
        label(graphics, "screen.interiorveil.config.navigation_range", left, top + 152);
    }

    private void labelsVisual(GuiGraphics graphics, int left, int right, int top) {
        label(graphics, "screen.interiorveil.config.boundary_hex", left, top + 38);
        label(graphics, "screen.interiorveil.config.navigation_hex", right, top + 38);
        label(graphics, "screen.interiorveil.config.beacon_hex", left, top + 76);
        label(graphics, "screen.interiorveil.config.boundary_density", right, top + 76);
        label(graphics, "screen.interiorveil.config.boundary_size", left, top + 114);
        label(graphics, "screen.interiorveil.config.navigation_density", right, top + 114);
        label(graphics, "screen.interiorveil.config.navigation_size", left, top + 152);
        label(graphics, "screen.interiorveil.config.fog_hex", right, top + 152);
    }

    private void labelsAccess(GuiGraphics graphics, int left, int right, int top) {
        label(graphics, "screen.interiorveil.config.access_start", left, top + 38);
        label(graphics, "screen.interiorveil.config.access_end", right, top + 38);
        label(graphics, "screen.interiorveil.config.allowed", left, top + 76);
        graphics.drawString(this.font, current.allowedPlayers().isBlank() ? "-" : current.allowedPlayers(), left, top + 88, 0xFFFFFFFF, false);
        label(graphics, "screen.interiorveil.config.player_name", left, top + 104);
    }

    private void labelsAttack(GuiGraphics graphics, int left, int top) {
        label(graphics, "screen.interiorveil.config.attack_x", left, top + 38);
        label(graphics, "screen.interiorveil.config.attack_y", left + 105, top + 38);
        label(graphics, "screen.interiorveil.config.attack_z", left + 210, top + 38);
        label(graphics, "screen.interiorveil.config.strike_radius", left + 160, top + 74);
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.interiorveil.config.attack_info"),
                this.width / 2, top + 172, 0xFFFFD85A);
    }

    private void labelsGuard(GuiGraphics graphics, int left, int top) {
        label(graphics, "screen.interiorveil.config.guard_title", left, top + 38);
    }

    private int colorOrDefault(EditBox box, int fallback) {
        try {
            return color(box);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private void label(GuiGraphics graphics, String key, int x, int y) {
        graphics.drawString(this.font, Component.translatable(key), x, y, 0xFFCFCFCF, false);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }
}
