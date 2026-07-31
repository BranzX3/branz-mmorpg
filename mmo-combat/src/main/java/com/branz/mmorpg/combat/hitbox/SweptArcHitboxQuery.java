package com.branz.mmorpg.combat.hitbox;

import java.util.Objects;

/** Prior/current authoritative ARC transforms for one server-tick sweep. */
public record SweptArcHitboxQuery(ArcHitboxQuery previous, ArcHitboxQuery current) {
    public SweptArcHitboxQuery {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(current, "current");
        if (Double.compare(previous.range(), current.range()) != 0
                || Double.compare(previous.angleDegrees(), current.angleDegrees()) != 0
                || Double.compare(previous.verticalMinimum(), current.verticalMinimum()) != 0
                || Double.compare(previous.verticalMaximum(), current.verticalMaximum()) != 0
                || previous.maximumTargets() != current.maximumTargets()) {
            throw new IllegalArgumentException("swept ARC shape parameters must remain constant");
        }
    }
}
