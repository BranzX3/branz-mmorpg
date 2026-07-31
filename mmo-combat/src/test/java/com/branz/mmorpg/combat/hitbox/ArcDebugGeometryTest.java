package com.branz.mmorpg.combat.hitbox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class ArcDebugGeometryTest {
    private final ArcDebugGeometry geometry = new ArcDebugGeometry();

    @Test
    void outlineContainsDeterministicArcAndThreeRadials() {
        ArcHitboxQuery query =
                new ArcHitboxQuery(
                        new CombatVector(10, 5, 20), new CombatVector(0, 0, 1), 2, 90, -0.5, 2, 4);

        List<CombatVector> points = geometry.outline(query, 2, 2);

        assertEquals(9, points.size());
        assertVector(10 + Math.sqrt(2), 5, 20 + Math.sqrt(2), points.get(0));
        assertVector(10, 5, 22, points.get(1));
        assertVector(10 - Math.sqrt(2), 5, 20 + Math.sqrt(2), points.get(2));
        assertVector(10, 5, 21, points.get(5));
        assertVector(10, 5, 22, points.get(6));
    }

    @Test
    void invalidDebugDensityIsRejected() {
        ArcHitboxQuery query =
                new ArcHitboxQuery(
                        new CombatVector(0, 0, 0), new CombatVector(0, 0, 1), 2, 90, -0.5, 2, 4);

        assertThrows(IllegalArgumentException.class, () -> geometry.outline(query, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> geometry.outline(query, 8, 17));
    }

    private static void assertVector(
            double expectedX, double expectedY, double expectedZ, CombatVector actual) {
        assertEquals(expectedX, actual.x(), 1.0e-9);
        assertEquals(expectedY, actual.y(), 1.0e-9);
        assertEquals(expectedZ, actual.z(), 1.0e-9);
    }
}
