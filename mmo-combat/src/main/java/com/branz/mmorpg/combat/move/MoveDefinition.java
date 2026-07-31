package com.branz.mmorpg.combat.move;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.combat.input.DirectionSnapshot;
import com.branz.mmorpg.combat.input.SemanticInput;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable server-authoritative move definition compiled from one content snapshot. */
public record MoveDefinition(
        DefinitionId id,
        String family,
        InputBranch input,
        PhaseDurations phases,
        int commitTick,
        ResourceCost costs,
        Movement movement,
        List<Hitbox> hitboxes,
        Outputs outputs,
        CancelWindows cancels,
        String interruptResistance,
        String presentationArchetype,
        CombatProfiles profiles) {
    public MoveDefinition {
        Objects.requireNonNull(id, "id");
        family = requireText(family, "family");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(phases, "phases");
        Objects.requireNonNull(costs, "costs");
        Objects.requireNonNull(movement, "movement");
        hitboxes = List.copyOf(Objects.requireNonNull(hitboxes, "hitboxes"));
        if (hitboxes.isEmpty()) {
            throw new IllegalArgumentException("move requires at least one hitbox");
        }
        Objects.requireNonNull(outputs, "outputs");
        Objects.requireNonNull(cancels, "cancels");
        interruptResistance = requireText(interruptResistance, "interruptResistance");
        presentationArchetype = requireText(presentationArchetype, "presentationArchetype");
        Objects.requireNonNull(profiles, "profiles");
        if (commitTick < 0 || commitTick >= phases.totalTicks()) {
            throw new IllegalArgumentException("commitTick must be inside the action timeline");
        }
        int activeStart = phases.windupTicks();
        int activeEnd = activeStart + phases.activeTicks() - 1;
        for (Hitbox hitbox : hitboxes) {
            if (hitbox.tick() < activeStart || hitbox.tick() > activeEnd) {
                throw new IllegalArgumentException("hitbox tick must be inside the active phase");
            }
        }
        cancels.validate(phases.totalTicks());
    }

    public record InputBranch(SemanticInput action, DirectionSnapshot direction, String branch) {
        public InputBranch {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(direction, "direction");
            branch = requireText(branch, "branch");
        }
    }

    public record PhaseDurations(int windupTicks, int activeTicks, int recoveryTicks) {
        public PhaseDurations {
            if (windupTicks < 0
                    || windupTicks > 40
                    || activeTicks < 1
                    || activeTicks > 40
                    || recoveryTicks < 0
                    || recoveryTicks > 40) {
                throw new IllegalArgumentException("phase ticks exceed the authored bounds");
            }
        }

        public int totalTicks() {
            return Math.addExact(Math.addExact(windupTicks, activeTicks), recoveryTicks);
        }
    }

    public record ResourceCost(int stamina, int mana, int health, int setupStamina) {
        public ResourceCost {
            if (stamina < 0 || mana < 0 || health < 0 || setupStamina < 0) {
                throw new IllegalArgumentException("resource costs must not be negative");
            }
            if (setupStamina > stamina) {
                throw new IllegalArgumentException("setup stamina cannot exceed total stamina");
            }
        }
    }

    public record Movement(String curve, double facingTurnDegrees) {
        public Movement {
            curve = requireText(curve, "curve");
            if (!Double.isFinite(facingTurnDegrees)
                    || facingTurnDegrees < 0
                    || facingTurnDegrees > 35) {
                throw new IllegalArgumentException("facing turn must be between 0 and 35 degrees");
            }
        }
    }

    public record Hitbox(
            int tick,
            HitboxShape shape,
            double range,
            double angleDegrees,
            double height,
            int maxTargets,
            String hitGroup,
            Optional<ProjectileDefinition> projectile) {
        public Hitbox {
            Objects.requireNonNull(shape, "shape");
            hitGroup = requireText(hitGroup, "hitGroup");
            Objects.requireNonNull(projectile, "projectile");
            if (tick < 0
                    || !Double.isFinite(range)
                    || range <= 0
                    || !Double.isFinite(angleDegrees)
                    || angleDegrees < 0
                    || angleDegrees > 360
                    || !Double.isFinite(height)
                    || height <= 0
                    || maxTargets < 1
                    || maxTargets > 8) {
                throw new IllegalArgumentException("invalid hitbox bounds");
            }
            if ((shape == HitboxShape.PROJECTILE) != projectile.isPresent()) {
                throw new IllegalArgumentException(
                        "PROJECTILE hitbox requires projectile fields and other shapes forbid them");
            }
            if (projectile.isPresent()
                    && maxTargets != projectile.orElseThrow().pierceCount() + 1) {
                throw new IllegalArgumentException(
                        "PROJECTILE max_targets must equal pierce_count plus one");
            }
        }

        public Hitbox(
                int tick,
                HitboxShape shape,
                double range,
                double angleDegrees,
                double height,
                int maxTargets,
                String hitGroup) {
            this(tick, shape, range, angleDegrees, height, maxTargets, hitGroup, Optional.empty());
        }
    }

    public record ProjectileDefinition(
            double speed,
            double gravityPerTick,
            double dragPerTick,
            double collisionRadius,
            int lifetimeTicks,
            int pierceCount,
            DefinitionId ammoCategory) {
        public ProjectileDefinition {
            Objects.requireNonNull(ammoCategory, "ammoCategory");
            if (!Double.isFinite(speed)
                    || speed <= 0
                    || speed > 8
                    || !Double.isFinite(gravityPerTick)
                    || gravityPerTick < 0
                    || gravityPerTick > 1
                    || !Double.isFinite(dragPerTick)
                    || dragPerTick <= 0
                    || dragPerTick > 1
                    || !Double.isFinite(collisionRadius)
                    || collisionRadius <= 0
                    || collisionRadius > 2
                    || lifetimeTicks < 1
                    || lifetimeTicks > 400
                    || pierceCount < 0
                    || pierceCount > 7) {
                throw new IllegalArgumentException("invalid projectile hitbox fields");
            }
        }
    }

    public enum HitboxShape {
        ARC,
        CAPSULE,
        BOX,
        SPHERE,
        RAY,
        PROJECTILE
    }

    public record Outputs(
            PhysicalDamageType physicalType,
            double moveCoefficient,
            int posture,
            int guardPressure) {
        public Outputs {
            Objects.requireNonNull(physicalType, "physicalType");
            if (!Double.isFinite(moveCoefficient)
                    || moveCoefficient <= 0
                    || posture < 0
                    || guardPressure < 0) {
                throw new IllegalArgumentException("invalid move outputs");
            }
        }
    }

    public enum PhysicalDamageType {
        SLASH,
        PIERCE,
        BLUNT
    }

    public record CancelWindows(int dodgeFromTick, List<ChainWindow> chainWindows) {
        public CancelWindows {
            if (dodgeFromTick < 0) {
                throw new IllegalArgumentException("dodge cancel tick must not be negative");
            }
            chainWindows = List.copyOf(Objects.requireNonNull(chainWindows, "chainWindows"));
        }

        private void validate(int totalTicks) {
            if (dodgeFromTick >= totalTicks) {
                throw new IllegalArgumentException("dodge cancel tick must be inside the timeline");
            }
            for (ChainWindow window : chainWindows) {
                if (window.fromTick() >= totalTicks || window.toTick() >= totalTicks) {
                    throw new IllegalArgumentException("chain window must be inside the timeline");
                }
            }
        }
    }

    public record ChainWindow(int fromTick, int toTick, String branch) {
        public ChainWindow {
            branch = requireText(branch, "branch");
            if (fromTick < 0 || toTick < fromTick) {
                throw new IllegalArgumentException("invalid chain window");
            }
        }
    }

    public record CombatProfiles(double pveMultiplier, double pvpMultiplier) {
        public CombatProfiles {
            if (!Double.isFinite(pveMultiplier)
                    || pveMultiplier <= 0
                    || !Double.isFinite(pvpMultiplier)
                    || pvpMultiplier <= 0) {
                throw new IllegalArgumentException("profile multipliers must be positive");
            }
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
