package dev.minse.interiorveil.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 궤도 폭격 조준(Pin) 및 발사(Fire) 생명주기를 관리하는 매니저.
 * - 폭격 전: 지도에 조준 마커가 계속 찍혀있음 (PINNED 상태).
 * - 폭격 발사: B키나 발사 버튼을 누른 순간부터 2분(120초) 타이머 시작 (FIRED 상태).
 * - 폭격 후 2분 경과: 지도에서 마커가 자동으로 깔끔하게 지워짐.
 */
public final class VeilStrikeTargetTracker {
    public static final long TARGET_LIFETIME_MS = 120_000L; // 2분 (120초)

    public enum Status {
        PINNED, // 폭격 전 조준 고정 상태 (타이머 무한 대기)
        FIRED   // 폭격 발사 후 카운트다운 진행 상태 (2분 후 만료)
    }

    public record TargetEntry(
            int targetX,
            int targetY,
            int targetZ,
            int strikeRadius,
            Status status,
            long fireTimestampMs
    ) {
        public boolean isExpired() {
            if (status == Status.PINNED) return false;
            return System.currentTimeMillis() - fireTimestampMs >= TARGET_LIFETIME_MS;
        }

        public long getRemainingSeconds() {
            if (status == Status.PINNED) return 120L;
            long remainingMs = TARGET_LIFETIME_MS - (System.currentTimeMillis() - fireTimestampMs);
            return Math.max(0L, remainingMs / 1000L);
        }

        public float getFadeAlpha() {
            if (status == Status.PINNED) return 1.0f;
            long elapsed = System.currentTimeMillis() - fireTimestampMs;
            if (elapsed >= TARGET_LIFETIME_MS) return 0.0f;
            long remaining = TARGET_LIFETIME_MS - elapsed;
            if (remaining < 10_000L) {
                return (float) remaining / 10_000.0f;
            }
            return 1.0f;
        }
    }

    private static final Map<UUID, TargetEntry> TARGETS = new ConcurrentHashMap<>();
    private static TargetEntry latestTarget = null;

    private VeilStrikeTargetTracker() {
    }

    /**
     * 폭격 전: 목표 지점을 지도에 조준 핀으로 고정 (타이머 대기).
     */
    public static void pinTarget(UUID barrierId, int x, int y, int z, int strikeRadius) {
        TargetEntry entry = new TargetEntry(x, y, z, strikeRadius, Status.PINNED, 0L);
        if (barrierId != null) {
            TARGETS.put(barrierId, entry);
        }
        latestTarget = entry;
    }

    /**
     * 폭격 발사: B키 또는 발사 버튼을 누른 순간부터 2분 카운트다운 시작!
     */
    public static void recordStrike(UUID barrierId, int x, int y, int z, int strikeRadius) {
        TargetEntry entry = new TargetEntry(x, y, z, strikeRadius, Status.FIRED, System.currentTimeMillis());
        if (barrierId != null) {
            TARGETS.put(barrierId, entry);
        }
        latestTarget = entry;
    }

    /**
     * 활성 타겟 엔트리 반환 (2분 만료 시 자동 소멸).
     */
    public static TargetEntry getActiveTarget(UUID barrierId) {
        if (barrierId != null) {
            TargetEntry entry = TARGETS.get(barrierId);
            if (entry != null) {
                if (entry.isExpired()) {
                    TARGETS.remove(barrierId);
                    return null;
                }
                return entry;
            }
        }
        if (latestTarget != null) {
            if (latestTarget.isExpired()) {
                latestTarget = null;
                return null;
            }
            return latestTarget;
        }
        return null;
    }

    public static void clear() {
        TARGETS.clear();
        latestTarget = null;
    }
}
