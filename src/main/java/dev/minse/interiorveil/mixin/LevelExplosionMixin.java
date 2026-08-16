package dev.minse.interiorveil.mixin;

import dev.minse.interiorveil.InteriorVeil;
import dev.minse.interiorveil.VeilManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 절대 방어막 영역 내 폭발(TNT, 크리퍼 등) 차단 Mixin.
 * MC 1.21.10 Level.explode 실제 시그니처:
 *   explode(Entity, double x, double y, double z, float power, Level.ExplosionInteraction)
 */
@Mixin(Level.class)
public class LevelExplosionMixin {

    @Inject(
            method = "explode(Lnet/minecraft/world/entity/Entity;DDDFLnet/minecraft/world/level/Level$ExplosionInteraction;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void interiorveil$blockExplosionInBarrier(
            Entity source,
            double x, double y, double z,
            float power,
            Level.ExplosionInteraction interaction,
            CallbackInfo ci
    ) {
        Level level = (Level) (Object) this;
        if (level.isClientSide() || InteriorVeil.manager == null) {
            return;
        }
        // isProtectedByAbsoluteBarrier가 오버월드 체크 포함
        if (VeilManager.isProtectedByAbsoluteBarrier(level, new Vec3(x, y, z))) {
            ci.cancel();
        }
    }
}
