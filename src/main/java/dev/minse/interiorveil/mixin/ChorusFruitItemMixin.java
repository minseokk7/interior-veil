package dev.minse.interiorveil.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.minse.interiorveil.InteriorVeil;
import net.minecraft.network.chat.Component;

@Mixin(Item.class)
public abstract class ChorusFruitItemMixin {
    @Inject(method = "finishUsingItem", at = @At("HEAD"), cancellable = true)
    private void onFinishUsingItem(ItemStack stack, Level level, LivingEntity entityLiving, CallbackInfoReturnable<ItemStack> cir) {
        if (stack.is(Items.CHORUS_FRUIT) && level.dimension().equals(InteriorVeil.POCKET_LEVEL)) {
            ItemStack result = stack.copy();
            if (entityLiving instanceof net.minecraft.world.entity.player.Player player) {
                if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                    serverPlayer.displayClientMessage(Component.translatable("message.interiorveil.teleport_blocked"), true);
                }
                if (!player.getAbilities().instabuild) {
                    result.shrink(1);
                }
            } else {
                result.shrink(1);
            }
            cir.setReturnValue(result);
            cir.cancel();
        }
    }
}
