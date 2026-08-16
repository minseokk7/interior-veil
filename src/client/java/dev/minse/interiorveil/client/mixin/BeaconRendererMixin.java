package dev.minse.interiorveil.client.mixin;

import dev.minse.interiorveil.InteriorVeil;
import dev.minse.interiorveil.client.VeilBeaconColorState;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.blockentity.state.BeaconRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BeaconRenderer.class)
abstract class BeaconRendererMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void interiorveil$applyRgbBeam(
            BlockEntity blockEntity,
            BeaconRenderState state,
            float partialTick,
            Vec3 cameraPosition,
            @Nullable ModelFeatureRenderer.CrumblingOverlay overlay,
            CallbackInfo callback
    ) {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null && client.level.dimension().equals(InteriorVeil.POCKET_LEVEL)) {
            if (VeilBeaconColorState.hasPosition() && !VeilBeaconColorState.isAssignedPosition(blockEntity.getBlockPos())) {
                state.sections = List.of();
                return;
            }
        }

        int color = VeilBeaconColorState.colorAt(blockEntity.getBlockPos());
        if (color >= 0) {
            state.sections = List.of(new BeaconRenderState.Section(0xFF000000 | color, 256));
        }
    }
}
