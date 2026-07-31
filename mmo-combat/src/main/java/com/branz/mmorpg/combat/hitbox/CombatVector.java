package com.branz.mmorpg.combat.hitbox;

public record CombatVector(double x, double y, double z) {
    public CombatVector {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("combat vector components must be finite");
        }
    }

    public CombatVector subtract(CombatVector other) {
        return new CombatVector(x - other.x, y - other.y, z - other.z);
    }

    public CombatVector add(CombatVector other) {
        return new CombatVector(x + other.x, y + other.y, z + other.z);
    }

    public CombatVector multiply(double scalar) {
        if (!Double.isFinite(scalar)) {
            throw new IllegalArgumentException("combat vector scalar must be finite");
        }
        return new CombatVector(x * scalar, y * scalar, z * scalar);
    }

    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public CombatVector normalized() {
        double length = length();
        if (length == 0) {
            throw new IllegalArgumentException("direction must not be zero");
        }
        return multiply(1.0 / length);
    }

    public double horizontalLength() {
        return Math.hypot(x, z);
    }

    public CombatVector normalizedHorizontal() {
        double length = horizontalLength();
        if (length == 0) {
            throw new IllegalArgumentException("horizontal direction must not be zero");
        }
        return new CombatVector(x / length, 0, z / length);
    }
}
