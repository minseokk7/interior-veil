package dev.minse.interiorveil.mixin;

import dev.minse.interiorveil.VeilManager;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 방어 모드가 켜진 오버월드 결계 내부에서 자연 몹 스폰(적대적/자연 스폰)을 완전히 차단하는 Mixin.
 * 단, 플레이어가 직접 사용하는 스폰 알, 동물 번식, 커맨드는 정상 허용한다.
 */
@Mixin(Mob.class)
public abstract class MobMixin {

    @Inject(method = "checkSpawnRules", at = @At("HEAD"), cancellable = true)
    private void interiorveil$preventSpawnInAbsoluteBarrier(LevelAccessor level, EntitySpawnReason spawnReason, CallbackInfoReturnable<Boolean> cir) {
        if (level instanceof Level l && !l.isClientSide()) {
            if (spawnReason == EntitySpawnReason.SPAWN_ITEM_USE || spawnReason == EntitySpawnReason.BREEDING || spawnReason == EntitySpawnReason.COMMAND) {
                return;
            }
            Mob mob = (Mob) (Object) this;
            if (VeilManager.isProtectedByAbsoluteBarrier(l, mob.position())) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "checkSpawnObstruction", at = @At("HEAD"), cancellable = true)
    private void interiorveil$preventObstructionSpawnInAbsoluteBarrier(net.minecraft.world.level.LevelReader level, CallbackInfoReturnable<Boolean> cir) {
        if (level instanceof Level l && !l.isClientSide()) {
            Mob mob = (Mob) (Object) this;
            if (VeilManager.isProtectedByAbsoluteBarrier(l, mob.position())) {
                cir.setReturnValue(false);
            }
        }
    }
}
