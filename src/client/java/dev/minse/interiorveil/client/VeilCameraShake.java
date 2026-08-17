package dev.minse.interiorveil.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/**
 * 전술 궤도 폭격 및 열폭풍 충격파 발생 시 거리에 비례하여 작동하는 지진파 카메라 진동 시스템.
 */
public final class VeilCameraShake {
    private static double intensity = 0.0;
    private static double durationTicks = 0.0;
    private static double maxDurationTicks = 0.0;

    private VeilCameraShake() {
    }

    /**
     * 폭격 충격파 발생 시 화면 진동 트리거.
     * @param impactPos 착탄 월드 좌표
     * @param maxRadius 최대 진동 감지 반경 (예: 256m)
     * @param baseIntensity 최대 강도 (예: 1.5)
     * @param durationTicks 진동 지속 틱 (예: 40틱 = 2초)
     */
    public static void trigger(Vec3 impactPos, double maxRadius, double baseIntensity, double durationTicks) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        double dist = client.player.position().distanceTo(impactPos);
        if (dist > maxRadius) return;

        // 거리에 따른 감쇠 (가까울수록 강력)
        double falloff = 1.0 - (dist / maxRadius);
        double calculatedIntensity = baseIntensity * falloff * falloff;

        if (calculatedIntensity > intensity) {
            intensity = calculatedIntensity;
            VeilCameraShake.durationTicks = durationTicks;
            VeilCameraShake.maxDurationTicks = durationTicks;
        }
    }

    /**
     * 클라이언트 틱마다 진동 감쇠
     */
    public static void clientTick() {
        if (durationTicks > 0) {
            durationTicks--;
            if (durationTicks <= 0) {
                intensity = 0.0;
            }
        }
    }

    /**
     * 현재 프레임의 피치/요/롤 흔들림 오프셋 반환 [pitch, yaw, roll]
     */
    public static float[] getShakeOffsets(float partialTicks) {
        if (durationTicks <= 0 || intensity <= 0.001) {
            return new float[]{0.0F, 0.0F, 0.0F};
        }

        double progress = durationTicks / Math.max(1.0, maxDurationTicks);
        double currentIntensity = intensity * progress;

        long time = System.currentTimeMillis();
        float pitch = (float) (Math.sin(time * 0.05) * Math.cos(time * 0.033) * currentIntensity * 4.5);
        float yaw = (float) (Math.cos(time * 0.04) * Math.sin(time * 0.027) * currentIntensity * 3.5);
        float roll = (float) (Math.sin(time * 0.06) * currentIntensity * 3.0);

        return new float[]{pitch, yaw, roll};
    }

    public static void clear() {
        intensity = 0.0;
        durationTicks = 0.0;
    }
}
