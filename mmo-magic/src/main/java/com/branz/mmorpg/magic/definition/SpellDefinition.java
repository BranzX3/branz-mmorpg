package com.branz.mmorpg.magic.definition;

import com.branz.mmorpg.api.identity.DefinitionId;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable data-driven spell contract compiled from one content snapshot. */
public record SpellDefinition(
        DefinitionId id,
        DefinitionId artId,
        SpellCastType castType,
        SpellTargetType targetType,
        SpellDeliveryType deliveryType,
        Requirements requirements,
        int manaCost,
        Phases phases,
        Interruption interruption,
        Optional<Projectile> projectile,
        Optional<Direct> direct,
        Optional<Channel> channel,
        Optional<Zone> zone,
        Optional<Imbuement> imbuement,
        Output output,
        String presentationArchetype,
        CombatProfiles profiles) {
    public SpellDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(artId, "artId");
        Objects.requireNonNull(castType, "castType");
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(deliveryType, "deliveryType");
        Objects.requireNonNull(requirements, "requirements");
        if (manaCost < 0) {
            throw new IllegalArgumentException("manaCost must not be negative");
        }
        Objects.requireNonNull(phases, "phases");
        Objects.requireNonNull(interruption, "interruption");
        Objects.requireNonNull(projectile, "projectile");
        Objects.requireNonNull(direct, "direct");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(imbuement, "imbuement");
        Objects.requireNonNull(output, "output");
        presentationArchetype = requireText(presentationArchetype, "presentationArchetype");
        Objects.requireNonNull(profiles, "profiles");
        if ((deliveryType == SpellDeliveryType.PROJECTILE) != projectile.isPresent()) {
            throw new IllegalArgumentException(
                    "PROJECTILE delivery requires projectile fields and other deliveries forbid them");
        }
        if ((deliveryType == SpellDeliveryType.DIRECT) != direct.isPresent()) {
            throw new IllegalArgumentException(
                    "DIRECT delivery requires direct fields and other deliveries forbid them");
        }
        if ((castType == SpellCastType.CHANNEL) != channel.isPresent()) {
            throw new IllegalArgumentException(
                    "CHANNEL cast requires channel fields and other casts forbid them");
        }
        if (castType == SpellCastType.CHANNEL && deliveryType != SpellDeliveryType.BEAM) {
            throw new IllegalArgumentException("V1 CHANNEL casts require BEAM delivery");
        }
        if ((deliveryType == SpellDeliveryType.ZONE) != zone.isPresent()) {
            throw new IllegalArgumentException(
                    "ZONE delivery requires zone fields and other deliveries forbid them");
        }
        if ((deliveryType == SpellDeliveryType.IMBUE) != imbuement.isPresent()) {
            throw new IllegalArgumentException(
                    "IMBUE delivery requires imbuement fields and other deliveries forbid them");
        }
    }

    public record Requirements(Set<String> catalystTags, int attunement) {
        public Requirements {
            catalystTags = Set.copyOf(Objects.requireNonNull(catalystTags, "catalystTags"));
            if (catalystTags.isEmpty()
                    || catalystTags.stream().anyMatch(tag -> tag == null || tag.isBlank())
                    || attunement < 0) {
                throw new IllegalArgumentException("invalid spell requirements");
            }
        }
    }

    public record Phases(
            int windupTicks, int minimumChargeTicks, int maximumChargeTicks, int recoveryTicks) {
        public Phases {
            if (windupTicks < 0
                    || minimumChargeTicks < 0
                    || maximumChargeTicks < minimumChargeTicks
                    || recoveryTicks < 0) {
                throw new IllegalArgumentException("invalid spell phase timings");
            }
        }
    }

    public record Interruption(
            boolean movement,
            boolean damage,
            boolean flinch,
            boolean stagger,
            boolean silence,
            boolean weaponSwap) {}

    public record Projectile(
            double speed,
            double gravityPerTick,
            double dragPerTick,
            double collisionRadius,
            int lifetimeTicks,
            int pierceCount,
            String hitGroup) {
        public Projectile {
            hitGroup = requireText(hitGroup, "hitGroup");
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
                throw new IllegalArgumentException("invalid spell projectile fields");
            }
        }
    }

    public record Direct(double range, int maximumTargets) {
        public Direct {
            if (!Double.isFinite(range) || range <= 0 || range > 32 || maximumTargets != 1) {
                throw new IllegalArgumentException("invalid direct-delivery fields");
            }
        }
    }

    public record Channel(
            int pulseIntervalTicks,
            int maximumPulses,
            int manaPerPulse,
            double range,
            int maximumTargetsPerPulse) {
        public Channel {
            if (pulseIntervalTicks < 1
                    || pulseIntervalTicks > 100
                    || maximumPulses < 1
                    || maximumPulses > 100
                    || manaPerPulse < 0
                    || !Double.isFinite(range)
                    || range <= 0
                    || range > 32
                    || maximumTargetsPerPulse != 1) {
                throw new IllegalArgumentException("invalid channel fields");
            }
        }
    }

    public record Zone(
            double placementRange,
            double radius,
            int durationTicks,
            int pulseIntervalTicks,
            int maximumTargetsPerPulse) {
        public Zone {
            if (!Double.isFinite(placementRange)
                    || placementRange <= 0
                    || placementRange > 32
                    || !Double.isFinite(radius)
                    || radius <= 0
                    || radius > 12
                    || durationTicks < 1
                    || durationTicks > 400
                    || pulseIntervalTicks < 1
                    || pulseIntervalTicks > durationTicks
                    || maximumTargetsPerPulse < 1
                    || maximumTargetsPerPulse > 16) {
                throw new IllegalArgumentException("invalid zone fields");
            }
        }
    }

    public record Imbuement(int durationTicks, int maximumCharges, double powerCoefficient) {
        public Imbuement {
            if (durationTicks < 1
                    || durationTicks > 1200
                    || maximumCharges < 1
                    || maximumCharges > 32
                    || !Double.isFinite(powerCoefficient)
                    || powerCoefficient <= 0
                    || powerCoefficient > 2) {
                throw new IllegalArgumentException("invalid imbuement fields");
            }
        }
    }

    public record Output(
            ArcaneSchool arcaneSchool, double powerCoefficient, int posture, int guardPressure) {
        public Output {
            Objects.requireNonNull(arcaneSchool, "arcaneSchool");
            if (!Double.isFinite(powerCoefficient)
                    || powerCoefficient <= 0
                    || posture < 0
                    || guardPressure < 0) {
                throw new IllegalArgumentException("invalid spell output");
            }
        }
    }

    public record CombatProfiles(double pveMultiplier, double pvpMultiplier) {
        public CombatProfiles {
            if (!Double.isFinite(pveMultiplier)
                    || pveMultiplier <= 0
                    || !Double.isFinite(pvpMultiplier)
                    || pvpMultiplier <= 0) {
                throw new IllegalArgumentException("spell profile multipliers must be positive");
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
