package dev.minse.interiorveil;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record VeilAdvancedSettings(
        int keyRevision,
        Map<UUID, String> allowedPlayers,
        int accessStart,
        int accessEnd,
        int boundaryDensity,
        float boundarySize,
        int navigationDensity,
        float navigationSize,
        boolean requireBeaconPower,
        boolean disableFog,
        int fogColor,
        boolean attackMode,
        int attackTargetX,
        int attackTargetY,
        int attackTargetZ,
        int strikeRadius,
        boolean absoluteBarrier,
        boolean reflectProjectiles
) {
    public VeilAdvancedSettings {
        keyRevision = Math.max(1, keyRevision);
        allowedPlayers = allowedPlayers == null ? Map.of() : Map.copyOf(allowedPlayers);
        accessStart = clamp(accessStart, 0, 23999);
        accessEnd = clamp(accessEnd, 0, 23999);
        boundaryDensity = clamp(boundaryDensity, 24, 192);
        boundarySize = clamp(boundarySize, 0.25F, 3.0F);
        navigationDensity = clamp(navigationDensity, 1, 12);
        navigationSize = clamp(navigationSize, 0.25F, 2.0F);
        fogColor &= 0xFFFFFF;
        strikeRadius = clamp(strikeRadius, 1, 64);
    }

    public static VeilAdvancedSettings defaults() {
        return new VeilAdvancedSettings(1, Map.of(), 0, 0, 96, 1.25F, 3, 0.75F, false, false, 0xB8C2CC,
                false, 0, 64, 0, 20, false, false);
    }

    public VeilAdvancedSettings withAccess(UUID playerId, String playerName, boolean allowed) {
        LinkedHashMap<UUID, String> updated = new LinkedHashMap<>(allowedPlayers);
        if (allowed) {
            updated.put(playerId, playerName);
        } else {
            updated.remove(playerId);
        }
        return new VeilAdvancedSettings(
                keyRevision,
                updated,
                accessStart,
                accessEnd,
                boundaryDensity,
                boundarySize,
                navigationDensity,
                navigationSize,
                requireBeaconPower,
                disableFog,
                fogColor,
                attackMode,
                attackTargetX,
                attackTargetY,
                attackTargetZ,
                strikeRadius,
                absoluteBarrier,
                reflectProjectiles
        );
    }

    public VeilAdvancedSettings revokeAllKeys() {
        return new VeilAdvancedSettings(
                keyRevision + 1,
                allowedPlayers,
                accessStart,
                accessEnd,
                boundaryDensity,
                boundarySize,
                navigationDensity,
                navigationSize,
                requireBeaconPower,
                disableFog,
                fogColor,
                attackMode,
                attackTargetX,
                attackTargetY,
                attackTargetZ,
                strikeRadius,
                absoluteBarrier,
                reflectProjectiles
        );
    }

    public boolean isAccessTime(long dayTime) {
        if (accessStart == accessEnd) {
            return true;
        }
        int time = (int) Math.floorMod(dayTime, 24000L);
        return accessStart < accessEnd
                ? time >= accessStart && time < accessEnd
                : time >= accessStart || time < accessEnd;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
