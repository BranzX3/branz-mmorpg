package com.branz.mmorpg.combat.input;

/** Ownership decision for one physical primary-attack ingress observation. */
public record PrimaryAttackIngressDecision(
        boolean mmoOwned, boolean beginDraw, boolean routePrimary) {
    public PrimaryAttackIngressDecision {
        if (beginDraw && !routePrimary) {
            throw new IllegalArgumentException("beginDraw requires routePrimary");
        }
        if (routePrimary && !mmoOwned) {
            throw new IllegalArgumentException("routePrimary requires MMO ownership");
        }
    }
}
