package com.branz.mmorpg.core.fixture;

import com.branz.mmorpg.api.combat.Combatant;
import com.branz.mmorpg.api.combat.WorldPoint;
import com.branz.mmorpg.api.stat.AttributeSnapshot;
import com.branz.mmorpg.api.stat.AttributeType;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** Mutable {@link Combatant} fixture, so combat tests need no server. */
public final class TestCombatant implements Combatant {

    /** Shared default world, so two fixtures can reach each other. */
    public static final UUID WORLD = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private final UUID id = UUID.randomUUID();
    private final Map<AttributeType, Double> attributes = new EnumMap<>(AttributeType.class);
    private double health = 100.0;
    private double shield;
    private WorldPoint position = new WorldPoint(WORLD, 0, 0, 0);
    private boolean invulnerable;
    private boolean safeZone;
    private boolean playerControlled = true;
    private String allegiance = "neutral-" + UUID.randomUUID();

    public static TestCombatant player() {
        return new TestCombatant();
    }

    public static TestCombatant mob() {
        TestCombatant mob = new TestCombatant();
        mob.playerControlled = false;
        return mob;
    }

    public TestCombatant with(AttributeType attribute, double value) {
        attributes.put(attribute, value);
        return this;
    }

    public TestCombatant health(double value) {
        this.health = value;
        return this;
    }

    public TestCombatant shield(double value) {
        this.shield = value;
        return this;
    }

    public TestCombatant at(double x, double y, double z) {
        this.position = new WorldPoint(position.worldId(), x, y, z);
        return this;
    }

    public TestCombatant inWorld(UUID worldId) {
        this.position = new WorldPoint(worldId, position.x(), position.y(), position.z());
        return this;
    }

    public TestCombatant invulnerable(boolean value) {
        this.invulnerable = value;
        return this;
    }

    public TestCombatant safeZone(boolean value) {
        this.safeZone = value;
        return this;
    }

    public TestCombatant allegiance(String value) {
        this.allegiance = value;
        return this;
    }

    public double shieldRemaining() {
        return shield;
    }

    @Override
    public UUID id() {
        return id;
    }

    @Override
    public AttributeSnapshot attributes() {
        Map<AttributeType, Double> resolved = new EnumMap<>(AttributeType.class);
        for (AttributeType attribute : AttributeType.values()) {
            resolved.put(attribute, attributes.getOrDefault(attribute, attribute.defaultValue()));
        }
        return new AttributeSnapshot(resolved);
    }

    @Override
    public double currentHealth() {
        return health;
    }

    @Override
    public double applyHealthLoss(double amount) {
        double removed = Math.min(Math.max(0.0, amount), Math.max(0.0, health));
        health -= removed;
        return removed;
    }

    @Override
    public double absorb(double amount) {
        double absorbed = Math.min(shield, Math.max(0.0, amount));
        shield -= absorbed;
        return absorbed;
    }

    @Override
    public WorldPoint position() {
        return position;
    }

    @Override
    public boolean alive() {
        return health > 0.0;
    }

    @Override
    public boolean invulnerable() {
        return invulnerable;
    }

    @Override
    public boolean inSafeZone() {
        return safeZone;
    }

    @Override
    public boolean playerControlled() {
        return playerControlled;
    }

    @Override
    public String allegiance() {
        return allegiance;
    }
}
