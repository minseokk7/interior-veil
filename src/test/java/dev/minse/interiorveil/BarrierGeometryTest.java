package dev.minse.interiorveil;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BarrierGeometryTest {
    @Test
    void sphereIncludesCircularBoundaryAndVerticalBounds() {
        assertTrue(BarrierGeometry.insideEllipsoid(3, 15, 4, 0, 15, 0, 5));
        assertTrue(BarrierGeometry.insideEllipsoid(0, 10, 0, 0, 15, 0, 5));
        assertFalse(BarrierGeometry.insideEllipsoid(0, 9, 0, 0, 15, 0, 5));
        assertFalse(BarrierGeometry.insideEllipsoid(0, 21, 0, 0, 15, 0, 5));
    }

    @Test
    void overlapLogicConsidersCombinedRadii() {
        assertTrue(BarrierGeometry.ellipsoidsOverlap(0, 0, 0, 5, 4, 0, 4, 5));
        assertFalse(BarrierGeometry.ellipsoidsOverlap(0, 0, 0, 5, 10, 0, 0, 4));
        assertTrue(BarrierGeometry.ellipsoidsOverlap(0, 0, 0, 5, 0, 9, 0, 5));
        assertFalse(BarrierGeometry.ellipsoidsOverlap(0, 0, 0, 5, 0, 11, 0, 5));
    }
}

