package dev.minse.interiorveil.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.minse.interiorveil.client.VeilFogState;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {

    @Inject(
            method = "computeFogColor",
            at = @At("RETURN"),
            cancellable = true
    )
    private void interiorveil$modifyFogColor(
            Camera camera,
            float f,
            ClientLevel clientLevel,
            int i,
            float g,
            boolean bl,
            CallbackInfoReturnable<Vector4f> cir
    ) {
        float strength = VeilFogState.strength();
        if (strength <= 0.001F) {
            return;
        }

        Vector4f original = cir.getReturnValue();
        if (original == null) {
            return;
        }

        int color = VeilFogState.color();
        Vector4f targetColor = new Vector4f(
                ((color >> 16) & 0xFF) / 255.0F,
                ((color >> 8) & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F,
                1.0F
        );
        cir.setReturnValue(new Vector4f(original).lerp(targetColor, strength));
    }

    @Inject(
            method = "setupFog",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/client/renderer/fog/FogData;renderDistanceEnd:F",
                    shift = At.Shift.AFTER
            )
    )
    private void interiorveil$applySpatialFogData(
            Camera camera,
            int i,
            boolean bl,
            net.minecraft.client.DeltaTracker deltaTracker,
            float f,
            ClientLevel clientLevel,
            CallbackInfoReturnable<Vector4f> cir,
            @Local FogData fogData
    ) {
        float strength = VeilFogState.strength();
        if (strength > 0.001F && fogData != null) {
            float targetFogEnd = VeilFogState.distance();
            float targetFogStart = Math.max(0.5F, targetFogEnd * 0.3F);

            fogData.renderDistanceStart = lerp(fogData.renderDistanceStart, targetFogStart, strength);
            fogData.renderDistanceEnd = lerp(fogData.renderDistanceEnd, targetFogEnd, strength);
            fogData.environmentalStart = lerp(fogData.environmentalStart, targetFogStart, strength);
            fogData.environmentalEnd = lerp(fogData.environmentalEnd, targetFogEnd, strength);
            fogData.skyEnd = lerp(fogData.skyEnd, targetFogEnd, strength);
            fogData.cloudEnd = lerp(fogData.cloudEnd, targetFogEnd, strength);
        }
    }

    private static float lerp(float start, float end, float amount) {
        return start + (end - start) * amount;
    }
}
