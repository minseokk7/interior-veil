package dev.minse.interiorveil.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/**
 * 결계 모드 조작 키지정 (Minecraft 설정 -> 조작 -> 키 지정).
 * - Iris/Sodium 셰이더 토글(K), Xaero 월드맵(M) 등과의 단축키 충돌을 완벽히 방지.
 */
public final class VeilKeyBindings {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            ResourceLocation.fromNamespaceAndPath("interiorveil", "keys")
    );

    public static KeyMapping ORBITAL_STRIKE;
    public static KeyMapping OPEN_CONFIG;
    public static KeyMapping OPEN_TACTICAL_MAP;

    private VeilKeyBindings() {
    }

    public static void register() {
        // 궤도 폭격: 기본값 V (Xaero 지도 화면 전용)
        ORBITAL_STRIKE = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.interiorveil.orbital_strike",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_V,
                CATEGORY
        ));

        // 결계 설정: 기본값 O (Iris 셰이더 K키 충돌 방지)
        OPEN_CONFIG = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.interiorveil.open_config",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                CATEGORY
        ));

        // 전술 스캔 지도: 기본값 J (Xaero 맵 M키 충돌 방지)
        OPEN_TACTICAL_MAP = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.interiorveil.open_tactical_map",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                CATEGORY
        ));
    }
}
