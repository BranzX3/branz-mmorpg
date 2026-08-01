package com.branz.mmorpg.combat.action;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.move.MoveDefinition;
import java.util.Optional;

public record CombatResources(
        int maximumHealth,
        int health,
        int maximumStamina,
        int stamina,
        int maximumMana,
        int mana,
        int reservedHealth,
        int reservedStamina,
        int reservedMana) {
    public CombatResources {
        if (maximumHealth < 1
                || health < 1
                || health > maximumHealth
                || maximumStamina < 0
                || stamina < 0
                || stamina > maximumStamina
                || maximumMana < 0
                || mana < 0
                || mana > maximumMana
                || reservedHealth < 0
                || reservedStamina < 0
                || reservedMana < 0
                || reservedHealth >= health
                || reservedStamina > stamina
                || reservedMana > mana) {
            throw new IllegalArgumentException("invalid combat resource ledger");
        }
    }

    public static CombatResources full(int health, int stamina, int mana) {
        return new CombatResources(health, health, stamina, stamina, mana, mana, 0, 0, 0);
    }

    public CombatResources restoreStamina(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("stamina restore must not be negative");
        }
        return new CombatResources(
                maximumHealth,
                health,
                maximumStamina,
                Math.min(maximumStamina, stamina + amount),
                maximumMana,
                mana,
                reservedHealth,
                reservedStamina,
                reservedMana);
    }

    public CombatResources restoreMana(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("mana restore must not be negative");
        }
        return new CombatResources(
                maximumHealth,
                health,
                maximumStamina,
                stamina,
                maximumMana,
                Math.min(maximumMana, mana + amount),
                reservedHealth,
                reservedStamina,
                reservedMana);
    }

    public Result<CombatResources, ActionTimelineErrorCode> reserveMana(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("mana reservation must not be negative");
        }
        if (mana - reservedMana < amount) {
            return Result.failure(
                    ActionTimelineErrorCode.NO_MANA, "Spell mana cost cannot be reserved.");
        }
        return Result.success(
                new CombatResources(
                        maximumHealth,
                        health,
                        maximumStamina,
                        stamina,
                        maximumMana,
                        mana,
                        reservedHealth,
                        reservedStamina,
                        reservedMana + amount));
    }

    public CombatResources commitReservedMana(int amount) {
        if (amount < 0 || amount > reservedMana) {
            throw new IllegalArgumentException("invalid committed mana reservation");
        }
        return new CombatResources(
                maximumHealth,
                health,
                maximumStamina,
                stamina,
                maximumMana,
                mana - amount,
                reservedHealth,
                reservedStamina,
                reservedMana - amount);
    }

    public CombatResources releaseReservedMana(int amount) {
        if (amount < 0 || amount > reservedMana) {
            throw new IllegalArgumentException("invalid released mana reservation");
        }
        return new CombatResources(
                maximumHealth,
                health,
                maximumStamina,
                stamina,
                maximumMana,
                mana,
                reservedHealth,
                reservedStamina,
                reservedMana - amount);
    }

    public int availableStamina() {
        return stamina - reservedStamina;
    }

    public Optional<CombatResources> spendStamina(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("stamina spend must not be negative");
        }
        if (amount > availableStamina()) {
            return Optional.empty();
        }
        return Optional.of(
                new CombatResources(
                        maximumHealth,
                        health,
                        maximumStamina,
                        stamina - amount,
                        maximumMana,
                        mana,
                        reservedHealth,
                        reservedStamina,
                        reservedMana));
    }

    public Optional<CombatResources> spendMana(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("mana spend must not be negative");
        }
        if (amount > mana - reservedMana) {
            return Optional.empty();
        }
        return Optional.of(
                new CombatResources(
                        maximumHealth,
                        health,
                        maximumStamina,
                        stamina,
                        maximumMana,
                        mana - amount,
                        reservedHealth,
                        reservedStamina,
                        reservedMana));
    }

    Result<CombatResources, ActionTimelineErrorCode> reserve(MoveDefinition.ResourceCost cost) {
        if (stamina - reservedStamina < cost.stamina()) {
            return Result.failure(
                    ActionTimelineErrorCode.NO_STAMINA, "Move stamina cost cannot be reserved.");
        }
        if (mana - reservedMana < cost.mana()) {
            return Result.failure(
                    ActionTimelineErrorCode.NO_MANA, "Move mana cost cannot be reserved.");
        }
        if (health - reservedHealth - cost.health() < 1) {
            return Result.failure(
                    ActionTimelineErrorCode.HEALTH_COST_LETHAL,
                    "Move health cost would reduce the actor below one HP.");
        }
        return Result.success(
                new CombatResources(
                        maximumHealth,
                        health,
                        maximumStamina,
                        stamina,
                        maximumMana,
                        mana,
                        reservedHealth + cost.health(),
                        reservedStamina + cost.stamina(),
                        reservedMana + cost.mana()));
    }

    CombatResources commit(MoveDefinition.ResourceCost cost) {
        return new CombatResources(
                maximumHealth,
                health - cost.health(),
                maximumStamina,
                stamina - cost.stamina(),
                maximumMana,
                mana - cost.mana(),
                reservedHealth - cost.health(),
                reservedStamina - cost.stamina(),
                reservedMana - cost.mana());
    }

    CombatResources cancelBeforeCommit(MoveDefinition.ResourceCost cost) {
        return new CombatResources(
                maximumHealth,
                health,
                maximumStamina,
                stamina - cost.setupStamina(),
                maximumMana,
                mana,
                reservedHealth - cost.health(),
                reservedStamina - cost.stamina(),
                reservedMana - cost.mana());
    }
}
