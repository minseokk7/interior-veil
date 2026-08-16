package dev.minse.interiorveil.mixin;

import dev.minse.interiorveil.InteriorVeil;
import dev.minse.interiorveil.VeilBarrier;
import dev.minse.interiorveil.VeilManager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Projectile.class)
public abstract class ProjectileMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onTick(CallbackInfo ci) {
        Projectile projectile = (Projectile) (Object) this;
        if (projectile.level().isClientSide() || InteriorVeil.manager == null) {
            return;
        }
        if (!projectile.level().dimension().equals(net.minecraft.world.level.Level.OVERWORLD)) {
            return;
        }
        Vec3 pos = projectile.position();
        VeilBarrier blockingBarrier = VeilManager.getBlockingBarrierForProjectile(projectile, pos);
        if (blockingBarrier == null) {
            return;
        }

        // absoluteBarrier 결계에서는 반사 또는 소멸 처리
        boolean shouldReflect = blockingBarrier.advanced().reflectProjectiles() || blockingBarrier.advanced().absoluteBarrier();
        if (shouldReflect) {
            Vec3 delta = projectile.getDeltaMovement();
            double dx = pos.x - blockingBarrier.centerX();
            double dz = pos.z - blockingBarrier.centerZ();
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len > 0) {
                double nx = dx / len;
                double nz = dz / len;
                double dot = delta.x * nx + delta.z * nz;
                if (dot < 0) { // 결계 내부로 향하는 경우 반사
                    Vec3 reflected = new Vec3(
                            delta.x - 2 * dot * nx,
                            delta.y,
                            delta.z - 2 * dot * nz
                    );
                    projectile.setDeltaMovement(reflected);
                    projectile.hasImpulse = true;
                    projectile.hurtMarked = true;

                    // 경계 바깥으로 밀어내기
                    double r = blockingBarrier.radius() + 0.2;
                    double outX = blockingBarrier.centerX() + nx * r;
                    double outZ = blockingBarrier.centerZ() + nz * r;
                    projectile.setPos(outX, pos.y, outZ);

                    // 반사 사운드 & 파티클 피드백
                    projectile.level().playSound(
                            null,
                            outX, pos.y, outZ,
                            net.minecraft.sounds.SoundEvents.SHIELD_BLOCK,
                            net.minecraft.sounds.SoundSource.BLOCKS,
                            1.5F,
                            1.2F
                    );
                    if (projectile.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                        serverLevel.sendParticles(
                                net.minecraft.core.particles.ParticleTypes.CRIT,
                                outX, pos.y, outZ,
                                12, 0.2, 0.2, 0.2, 0.1
                        );
                    }
                }
            }
        } else {
            // 기본 동작: 즉시 소멸
            projectile.discard();
            ci.cancel();
        }
    }
}
