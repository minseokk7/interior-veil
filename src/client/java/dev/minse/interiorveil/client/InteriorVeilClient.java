package dev.minse.interiorveil.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.minse.interiorveil.network.BeaconColorPayload;
import dev.minse.interiorveil.network.FogStatePayload;
import dev.minse.interiorveil.network.MirrorFramePayload;
import dev.minse.interiorveil.network.VeilAdminActionPayload;
import dev.minse.interiorveil.network.VeilConfigPayload;
import dev.minse.interiorveil.network.VeilOverviewPayload;
import dev.minse.interiorveil.network.VeilTargetMapPayload;
import java.util.UUID;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.network.chat.Component;

public final class InteriorVeilClient implements ClientModInitializer {
    private static final int[] BEACON_PRESETS = {
            0xFFFFFF, 0x33FFFF, 0xFFD23F, 0xB66CFF, 0x55FF55, 0xFF5555, 0x3399FF, 0xFF66CC
    };

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(
                MirrorFramePayload.TYPE,
                (payload, context) -> MirrorPlayers.accept(context.client(), payload)
        );
        ClientPlayNetworking.registerGlobalReceiver(
                FogStatePayload.TYPE,
                (payload, context) -> context.client().execute(() -> VeilFogState.accept(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(
                dev.minse.interiorveil.network.ForcefieldStatePayload.TYPE,
                (payload, context) -> context.client().execute(() -> VeilForcefieldRenderer.accept(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(
                VeilConfigPayload.TYPE,
                (payload, context) -> {
                    VeilConfigClientState.accept(payload);
                    if (payload.forceOpen()) {
                        context.client().execute(() -> {
                            context.client().setScreen(new VeilConfigScreen(payload));
                        });
                    }
                }
        );
        ClientPlayNetworking.registerGlobalReceiver(
                BeaconColorPayload.TYPE,
                (payload, context) -> VeilBeaconColorState.accept(payload)
        );
        ClientPlayNetworking.registerGlobalReceiver(
                VeilOverviewPayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> context.client().setScreen(new VeilOverviewScreen(payload.veils()))
                )
        );
        ClientPlayNetworking.registerGlobalReceiver(
                VeilTargetMapPayload.TYPE,
                (payload, context) -> context.client().execute(
                        () -> context.client().setScreen(new VeilTargetMapScreen(payload))
                )
        );
        ClientPlayNetworking.registerGlobalReceiver(
                dev.minse.interiorveil.network.StrikeBeamPayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    VeilStrikeBeamRenderer.addBeam(
                            payload.targetX(), payload.targetY(), payload.targetZ(), payload.durationTicks(), payload.color()
                    );
                    VeilStrikeTargetTracker.recordStrike(null, (int) payload.targetX(), (int) payload.targetY(), (int) payload.targetZ(), 20);
                })
        );
        ClientPlayNetworking.registerGlobalReceiver(
                dev.minse.interiorveil.network.BattleReportPayload.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    VeilTacticalHudRenderer.setBattleReport(payload);
                })
        );
        VeilKeyBindings.register();
        VeilXaeroIntegration.initialize();
        VeilTacticalHudRenderer.register();
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (!(screen instanceof BeaconScreen) || VeilConfigClientState.current() == null) {
                return;
            }
            Screens.getButtons(screen).add(Button.builder(
                    Component.translatable("screen.interiorveil.config.open"),
                    button -> client.setScreen(new VeilConfigScreen(VeilConfigClientState.current()))
            ).bounds(scaledWidth / 2 + 120, scaledHeight / 2 - 22, 110, 20).build());
            Screens.getButtons(screen).add(Button.builder(
                    beaconColorMessage(VeilConfigClientState.current()),
                    button -> client.setScreen(new VeilBeaconColorScreen(VeilConfigClientState.current()))
            ).bounds(scaledWidth / 2 + 120, scaledHeight / 2 + 2, 110, 20).build());
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            MirrorPlayers.clear();
            VeilFogState.clear();
            VeilConfigClientState.clear();
            VeilBeaconColorState.clear();
            VeilForcefieldRenderer.clear();
            VeilStrikeBeamRenderer.clear();
            VeilCameraShake.clear();
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            VeilFogState.tick(client);
            VeilStrikeBeamRenderer.clientTick();
            VeilCameraShake.clientTick();

            // 단축키 입력 처리
            while (VeilKeyBindings.OPEN_CONFIG.consumeClick()) {
                // 서버에 결계 설정 화면 요청 (서버가 오너 권한 검증 및 최신 데이터로 화면 팝업 응답)
                ClientPlayNetworking.send(new VeilAdminActionPayload(
                        new UUID(0L, 0L), "open_config_screen", ""
                ));
            }
        });
        WorldRenderEvents.END_EXTRACTION.register(MirrorPlayers::extractRenderStates);
        WorldRenderEvents.END_MAIN.register(context -> {
            VeilStrikeBeamRenderer.render(context);
            VeilForcefieldRenderer.render(context);
        });
    }

    private static Component beaconColorMessage(VeilConfigPayload payload) {
        return Component.translatable(
                "screen.interiorveil.config.beacon_button",
                String.format("#%06X", payload.beaconColor() & 0xFFFFFF)
        );
    }
}
