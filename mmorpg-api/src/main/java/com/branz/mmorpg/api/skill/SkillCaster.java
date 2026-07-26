package com.branz.mmorpg.api.skill;

import java.util.Map;
import java.util.UUID;

/** Narrow runtime port through which the skill engine reserves resources. */
public interface SkillCaster {

    UUID id();

    boolean alive();

    boolean silenced();

    boolean stunned();

    /** Resolved cooldown-recovery fraction, already clamped by the attribute system. */
    double cooldownRecovery();

    /** Atomically spends all costs or spends nothing. */
    boolean spend(Map<ResourceType, Double> costs);

    /** Refunds previously committed costs after an allowed interruption. */
    void refund(Map<ResourceType, Double> costs, double fraction);
}
