package dev.minse.interiorveil.mixin;

import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.minse.interiorveil.InteriorVeil;

@Mixin(ThrownEnderpearl.class)
public abstract class EnderPearlEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void onTick(CallbackInfo ci) {
        ThrownEnderpearl pearl = (ThrownEnderpearl) (Object) this;
        Level level = pearl.level();
        if (level.dimension().equals(InteriorVeil.POCKET_LEVEL)) {
            // 결계 내부에서 던진 엔더 진주가 떨어지면 그냥 삭제되도록 하거나, 범위를 벗어나면 삭제
            // 간단하게: 포켓 차원에서는 엔더 진주 틱 처리를 취소하고 즉시 아이템을 소멸시킨다?
            // 아니면 범위를 벗어나면 소멸시키게 할까?
            // 보안상 아예 텔레포트를 막는 것이 가장 안전함.
            pearl.discard();
            ci.cancel();
        }
    }
}
