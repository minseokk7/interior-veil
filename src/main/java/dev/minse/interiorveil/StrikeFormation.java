package dev.minse.interiorveil;

import java.util.ArrayList;
import java.util.List;

public enum StrikeFormation {
    SINGLE("🎯 단일 정밀 (1발)", 1),
    CROSS_5("➕ 십자 융단 (5발)", 5),
    GRID_9("⬛ 3x3 격자 (9발)", 9),
    CIRCLE_9("⭕ 원형 포위 (9발)", 9),
    X_CROSS_5("⚡ X자 교차 (5발)", 5),
    LINE_5("➖ 선형 융단 (5발)", 5),
    DIAMOND_9("🔷 다이아몬드 (9발)", 9);

    private final String displayName;
    private final int count;

    StrikeFormation(String displayName, int count) {
        this.displayName = displayName;
        this.count = count;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getCount() {
        return count;
    }

    public static StrikeFormation byIndex(int index) {
        StrikeFormation[] values = values();
        if (index < 0 || index >= values.length) {
            return SINGLE;
        }
        return values[index];
    }

    public static StrikeFormation byName(String name) {
        if (name == null) return SINGLE;
        String clean = name.toLowerCase().trim();
        return switch (clean) {
            case "cross", "cross5", "cross_5", "십자" -> CROSS_5;
            case "grid", "grid9", "grid_9", "3x3", "격자" -> GRID_9;
            case "circle", "circle9", "circle_9", "원형", "원" -> CIRCLE_9;
            case "xcross", "x_cross", "x", "x자" -> X_CROSS_5;
            case "line", "line5", "line_5", "선형", "일렬" -> LINE_5;
            case "diamond", "diamond9", "diamond_9", "다이아", "마름모" -> DIAMOND_9;
            default -> SINGLE;
        };
    }

    /**
     * 중심점 기준 상대 (dx, dz) 오프셋 목록을 반환한다.
     * @param spacing 폭격 지점 간의 간격 (블럭 단위, 기본 bombRadius * 1.5)
     */
    public List<int[]> getOffsets(int spacing) {
        List<int[]> list = new ArrayList<>();
        int s = Math.max(12, spacing);

        switch (this) {
            case SINGLE -> list.add(new int[]{0, 0});

            case CROSS_5 -> {
                list.add(new int[]{0, 0});
                list.add(new int[]{0, -s});
                list.add(new int[]{0, s});
                list.add(new int[]{-s, 0});
                list.add(new int[]{s, 0});
            }

            case GRID_9 -> {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        list.add(new int[]{dx * s, dz * s});
                    }
                }
            }

            case CIRCLE_9 -> {
                list.add(new int[]{0, 0});
                int outerRadius = (int) (s * 1.4);
                for (int i = 0; i < 8; i++) {
                    double angle = i * Math.PI / 4.0;
                    int ox = (int) Math.round(Math.cos(angle) * outerRadius);
                    int oz = (int) Math.round(Math.sin(angle) * outerRadius);
                    list.add(new int[]{ox, oz});
                }
            }

            case X_CROSS_5 -> {
                list.add(new int[]{0, 0});
                list.add(new int[]{-s, -s});
                list.add(new int[]{s, -s});
                list.add(new int[]{-s, s});
                list.add(new int[]{s, s});
            }

            case LINE_5 -> {
                list.add(new int[]{-s * 2, 0});
                list.add(new int[]{-s, 0});
                list.add(new int[]{0, 0});
                list.add(new int[]{s, 0});
                list.add(new int[]{s * 2, 0});
            }

            case DIAMOND_9 -> {
                list.add(new int[]{0, 0});
                // 1차 다이아몬드 (4발)
                list.add(new int[]{0, -s});
                list.add(new int[]{0, s});
                list.add(new int[]{-s, 0});
                list.add(new int[]{s, 0});
                // 2차 외곽 다이아몬드 (4발)
                int s2 = (int) (s * 1.8);
                list.add(new int[]{0, -s2});
                list.add(new int[]{0, s2});
                list.add(new int[]{-s2, 0});
                list.add(new int[]{s2, 0});
            }
        }
        return list;
    }
}
