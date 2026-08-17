package dev.minse.interiorveil.client.mixin;

import dev.minse.interiorveil.VeilItems;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1000블록 초장거리 타겟팅 레이저 조준 시 20배율 초정밀 스나이퍼 망원 줌(FOV)을 적용하는 믹스인.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void interiorveil$applyTargetingLaserSuperZoom(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        if (this.minecraft.player != null && this.minecraft.player.isUsingItem() && this.minecraft.player.getUseItem().is(VeilItems.TARGETING_LASER)) {
            double currentFov = cir.getReturnValue();
            // isScoping()으로 적용된 10배율 줌에서 추가 2배 확대 -> 총 20배율 초정밀 줌
            cir.setReturnValue(currentFov * 0.5);
        }
    }
}
