package dev.minse.interiorveil.client;

import dev.minse.interiorveil.network.FogStatePayload;
import net.minecraft.client.Minecraft;

public final class VeilFogState {
    private static volatile boolean targetActive;
    private static volatile float strength;
    private static volatile float distance = 12.0F;
    private static volatile int fadeTicks = 20;
    private static volatile int color = 0xFFFFFF;

    private VeilFogState() {
    }

    static void accept(FogStatePayload payload) {
        targetActive = payload.active();
        distance = payload.distance();
        fadeTicks = Math.max(1, payload.fadeTicks());
        color = payload.color() & 0xFFFFFF;
    }

    static void tick(Minecraft client) {
        float target = targetActive ? 1.0F : 0.0F;
        float step = 1.0F / Math.max(1, fadeTicks);
        if (strength < target) {
            strength = Math.min(target, strength + step);
        } else if (strength > target) {
            strength = Math.max(target, strength - step);
        }
    }

    static void clear() {
        targetActive = false;
        strength = 0.0F;
        distance = 12.0F;
        fadeTicks = 20;
        color = 0xFFFFFF;
    }

    public static float strength() {
        return strength;
    }

    public static float distance() {
        return distance;
    }

    public static int color() {
        return color;
    }
}
