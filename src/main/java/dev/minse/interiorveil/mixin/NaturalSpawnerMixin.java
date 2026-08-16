package dev.minse.interiorveil.mixin;

import dev.minse.interiorveil.InteriorVeil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 포켓 차원에서 자연 몹 생성을 완전히 차단하는 Mixin.
 * spawnForChunk 진입 시점에 포켓 차원인지 확인하고 즉시 취소한다.
 */
@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {

    @Inject(
            method = "spawnForChunk",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void interiorveil$blockPocketSpawns(
            ServerLevel level,
            LevelChunk chunk,
            NaturalSpawner.SpawnState spawnState,
            List<?> spawnCategories,
            CallbackInfo ci
    ) {
        // 포켓 차원이면 몹 생성을 전부 취소
        if (level.dimension().equals(InteriorVeil.POCKET_LEVEL)) {
            ci.cancel();
        }
    }
}

