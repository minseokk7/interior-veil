package dev.minse.interiorveil.client.mixin;

import dev.minse.interiorveil.VeilItems;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 타겟팅 레이저 조준 시 바닐라 망원경(Spyglass) 줌 및 스코프 메커니즘을 활성화하는 플레이어 믹스인.
 */
@Mixin(Player.class)
public abstract class PlayerMixin {

    @Inject(method = "isScoping", at = @At("HEAD"), cancellable = true)
    private void interiorveil$isScoping(CallbackInfoReturnable<Boolean> cir) {
        Player player = (Player) (Object) this;
        if (player.isUsingItem() && player.getUseItem().is(VeilItems.TARGETING_LASER)) {
            cir.setReturnValue(true);
        }
    }
}
