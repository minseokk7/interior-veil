package dev.minse.interiorveil.client.mixin;

import dev.minse.interiorveil.client.VeilKeyBindings;
import dev.minse.interiorveil.client.VeilXaeroIntegration;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Xaero's World Map 화면에서:
 * 1. 마우스 좌클릭 시 목표 지점 조준 핀(Pin)을 즉시 고정.
 * 2. V키 입력 시 마우스 커서 위치와 상관없이 '찍어둔 조준 핀 지점'으로만 궤도 폭격 발사.
 */
@Pseudo
@Mixin(targets = "xaero.map.gui.GuiMap", remap = true)
public abstract class GuiMapMixin {

    @Inject(method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z", at = @At("HEAD"), require = 0)
    private void interiorveil$onXaeroMapClick(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (event.button() == 0) {
            Screen screen = (Screen) (Object) this;
            VeilXaeroIntegration.pinTargetAtXaeroCursor(screen);
        }
    }

    @Inject(method = "keyPressed(Lnet/minecraft/client/input/KeyEvent;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void interiorveil$interceptXaeroStrikeKey(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
        boolean isStrikeKey = false;
        if (VeilKeyBindings.ORBITAL_STRIKE != null && VeilKeyBindings.ORBITAL_STRIKE.matches(event)) {
            isStrikeKey = true;
        } else if (event.key() == GLFW.GLFW_KEY_V) {
            isStrikeKey = true;
        }

        if (isStrikeKey) {
            Screen screen = (Screen) (Object) this;
            VeilXaeroIntegration.fireStrikeAtXaeroCursor(screen, false);
            cir.setReturnValue(true); // Xaero의 자체 텔레포트 및 모든 키 핸들러 실행을 완벽하게 차단!
        }
    }
}
