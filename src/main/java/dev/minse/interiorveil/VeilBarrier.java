package dev.minse.interiorveil;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public record VeilBarrier(
        UUID id,
        UUID owner,
        String sourceDimension,
        int centerX,
        int centerY,
        int centerZ,
        int radius,
        int minY,
        int maxY,
        int shellDepth,
        String name,
        int fogMargin,
        int fogDistance,
        int fogFadeTicks,
        int navigationRange,
        boolean boundaryVisible,
        int boundaryColor,
        int navigationColor,
        boolean securityMode,
        int beaconColor,
        VeilAdvancedSettings advanced,
        int settingsVersion,
        Integer pocketX,
        Integer pocketZ
) {
    public VeilBarrier {
        if (radius < 4) {
            throw new IllegalArgumentException("radius must be at least 4");
        }
        if (minY > maxY) {
            throw new IllegalArgumentException("minY must not exceed maxY");
        }
        if (shellDepth < 8) {
            throw new IllegalArgumentException("shellDepth must be at least 8");
        }
        ResourceLocation.parse(sourceDimension);
    }

    public static VeilBarrier create(UUID owner, ResourceKey<Level> dimension, BlockPos beaconPos, int gridX, int gridZ) {
        return new VeilBarrier(
                UUID.randomUUID(),
                owner,
                dimension.location().toString(),
                beaconPos.getX(),
                beaconPos.getY(),
                beaconPos.getZ(),
                VeilConstants.DEFAULT_RADIUS,
                beaconPos.getY() - VeilConstants.DEFAULT_HEIGHT / 2,
                beaconPos.getY() + VeilConstants.DEFAULT_HEIGHT / 2,
                VeilConstants.SHELL_DEPTH,
                "결계 " + beaconPos.getX() + ", " + beaconPos.getZ(),
                VeilConstants.AMBIENT_FOG_MARGIN,
                VeilConstants.DEFAULT_FOG_DISTANCE,
                VeilConstants.DEFAULT_FOG_FADE_TICKS,
                VeilConstants.NAVIGATION_RANGE,
                true,
                VeilConstants.DEFAULT_BOUNDARY_COLOR,
                VeilConstants.DEFAULT_NAVIGATION_COLOR,
                false,
                0xFFFFFF,
                VeilAdvancedSettings.defaults(),
                4,
                gridX,
                gridZ
        );
    }

    public int getPocketX() {
        return pocketX != null ? pocketX : centerX;
    }

    public int getPocketZ() {
        return pocketZ != null ? pocketZ : centerZ;
    }

    public VeilBarrier normalized() {
        if (settingsVersion >= 3 && advanced != null) {
            return this;
        }
        if (settingsVersion >= 1) {
            return withSettings(
                    name,
                    radius,
                    maxY - minY,
                    fogMargin,
                    fogDistance,
                    fogFadeTicks,
                    navigationRange,
                    boundaryVisible,
                    boundaryColor,
                    navigationColor,
                    settingsVersion >= 2 && securityMode,
                    0xFFFFFF,
                    VeilAdvancedSettings.defaults()
            );
        }
        return withSettings(
                name == null || name.isBlank() ? "결계 " + centerX + ", " + centerZ : name,
                radius,
                maxY - minY,
                VeilConstants.AMBIENT_FOG_MARGIN,
                VeilConstants.DEFAULT_FOG_DISTANCE,
                VeilConstants.DEFAULT_FOG_FADE_TICKS,
                VeilConstants.NAVIGATION_RANGE,
                true,
                VeilConstants.DEFAULT_BOUNDARY_COLOR,
                VeilConstants.DEFAULT_NAVIGATION_COLOR,
                false,
                0xFFFFFF,
                VeilAdvancedSettings.defaults()
        );
    }

    public VeilBarrier withSettings(
            String newName,
            int newRadius,
            int newHeight,
            int newFogMargin,
            int newFogDistance,
            int newFogFadeTicks,
            int newNavigationRange,
            boolean newBoundaryVisible,
            int newBoundaryColor,
            int newNavigationColor,
            boolean newSecurityMode,
            int newBeaconColor,
            VeilAdvancedSettings newAdvanced
    ) {
        int radiusValue = clamp(newRadius, VeilConstants.MIN_RADIUS, VeilConstants.MAX_RADIUS);
        int heightValue = clamp(newHeight, VeilConstants.MIN_HEIGHT, VeilConstants.MAX_HEIGHT);
        String nameValue = newName == null || newName.isBlank()
                ? "결계 " + centerX + ", " + centerZ
                : newName.strip().substring(0, Math.min(32, newName.strip().length()));
        return new VeilBarrier(
                id,
                owner,
                sourceDimension,
                centerX,
                centerY,
                centerZ,
                radiusValue,
                centerY - heightValue / 2,
                centerY + heightValue / 2,
                shellDepth,
                nameValue,
                clamp(newFogMargin, VeilConstants.MIN_FOG_MARGIN, VeilConstants.MAX_FOG_MARGIN),
                clamp(newFogDistance, VeilConstants.MIN_FOG_DISTANCE, VeilConstants.MAX_FOG_DISTANCE),
                clamp(newFogFadeTicks, VeilConstants.MIN_FOG_FADE_TICKS, VeilConstants.MAX_FOG_FADE_TICKS),
                clamp(newNavigationRange, VeilConstants.MIN_NAVIGATION_RANGE, VeilConstants.MAX_NAVIGATION_RANGE),
                newBoundaryVisible,
                newBoundaryColor & 0xFFFFFF,
                newNavigationColor & 0xFFFFFF,
                newSecurityMode,
                newBeaconColor & 0xFFFFFF,
                newAdvanced == null ? VeilAdvancedSettings.defaults() : newAdvanced,
                4,
                getPocketX(),
                getPocketZ()
        );
    }

    public VeilBarrier withAdvanced(VeilAdvancedSettings newAdvanced) {
        return withSettings(
                name,
                radius,
                height(),
                fogMargin,
                fogDistance,
                fogFadeTicks,
                navigationRange,
                boundaryVisible,
                boundaryColor,
                navigationColor,
                securityMode,
                beaconColor,
                newAdvanced
        );
    }

    public VeilBarrier withRadius(int newRadius) {
        return withSettings(
                name,
                newRadius,
                height(),
                fogMargin,
                fogDistance,
                fogFadeTicks,
                navigationRange,
                boundaryVisible,
                boundaryColor,
                navigationColor,
                securityMode,
                beaconColor,
                advanced
        );
    }

    public VeilBarrier withHeight(int newHeight) {
        return withSettings(
                name,
                radius,
                newHeight,
                fogMargin,
                fogDistance,
                fogFadeTicks,
                navigationRange,
                boundaryVisible,
                boundaryColor,
                navigationColor,
                securityMode,
                beaconColor,
                advanced
        );
    }

    public int height() {
        return maxY - minY;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public ResourceKey<Level> sourceKey() {
        return ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(sourceDimension));
    }

    public BlockPos center() {
        return new BlockPos(centerX, centerY, centerZ);
    }

    public boolean contains(double x, double y, double z, double margin, boolean isPocket) {
        double targetX = isPocket ? getPocketX() + 0.5 : centerX + 0.5;
        double targetZ = isPocket ? getPocketZ() + 0.5 : centerZ + 0.5;
        return BarrierGeometry.insideEllipsoid(
                x,
                y,
                z,
                targetX,
                centerY + 0.5,
                targetZ,
                radius + margin
        );
    }

    public boolean mirrors(double x, double y, double z, boolean isPocket) {
        return contains(x, y, z, shellDepth, isPocket);
    }

    public boolean overlaps(VeilBarrier other, double margin) {
        return BarrierGeometry.ellipsoidsOverlap(
                centerX + 0.5, centerY + 0.5, centerZ + 0.5, radius + margin,
                other.centerX() + 0.5, other.centerY() + 0.5, other.centerZ() + 0.5, other.radius() + margin
        );
    }
    
    public boolean overlaps(VeilBarrier other) {
        return overlaps(other, VeilConstants.HYSTERESIS);
    }
}
