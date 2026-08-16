package dev.minse.interiorveil;

public final class BarrierGeometry {
    private BarrierGeometry() {
    }

    public static double horizontalDistanceSquared(double x, double z, double centerX, double centerZ) {
        double dx = x - centerX;
        double dz = z - centerZ;
        return dx * dx + dz * dz;
    }

    public static boolean insideEllipsoid(
            double x, double y, double z,
            double centerX, double centerY, double centerZ,
            double radius
    ) {
        if (radius <= 0) radius = 1.0;

        double dx = (x - centerX) / radius;
        double dy = (y - centerY) / radius;
        double dz = (z - centerZ) / radius;

        return (dx * dx + dy * dy + dz * dz) <= 1.0;
    }

    public static boolean ellipsoidsOverlap(
            double centerAX, double centerAY, double centerAZ, double radiusA,
            double centerBX, double centerBY, double centerBZ, double radiusB
    ) {
        double combinedRadius = radiusA + radiusB;

        if (combinedRadius <= 0) combinedRadius = 1.0;

        double dx = (centerAX - centerBX) / combinedRadius;
        double dy = (centerAY - centerBY) / combinedRadius;
        double dz = (centerAZ - centerBZ) / combinedRadius;

        return (dx * dx + dy * dy + dz * dz) < 1.0;
    }
}

