package dev.minse.interiorveil.client.mixin;

import dev.minse.interiorveil.client.VeilCameraShake;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 전술 궤도 폭격 충격파 발생 시 카메라 앵글(Pitch, Yaw)을 자연스럽게 흔들어 진동을 구현하는 믹스인.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    public abstract float getXRot();

    @Shadow
    public abstract float getYRot();

    @Inject(method = "setup", at = @At("TAIL"))
    private void interiorveil$applyCameraShake(BlockGetter area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        float[] shake = VeilCameraShake.getShakeOffsets(tickDelta);
        if (shake[0] != 0.0F || shake[1] != 0.0F) {
            this.setRotation(this.getYRot() + shake[1], this.getXRot() + shake[0]);
        }
    }
}
