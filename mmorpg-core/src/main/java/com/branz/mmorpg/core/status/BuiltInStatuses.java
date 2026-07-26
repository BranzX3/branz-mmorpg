package com.branz.mmorpg.core.status;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.stat.AttributeModifier;
import com.branz.mmorpg.api.stat.AttributeType;
import com.branz.mmorpg.api.stat.ModifierSource;
import com.branz.mmorpg.api.status.CrowdControlCategory;
import com.branz.mmorpg.api.status.OfflinePolicy;
import com.branz.mmorpg.api.status.StackPolicy;
import com.branz.mmorpg.api.status.StatusCategory;
import com.branz.mmorpg.api.status.StatusDefinition;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The ten statuses CORE_MMO_SPECIFICATION §C3 requires at launch.
 *
 * <p>These are shipped as code for now because the content loader only
 * understands materials; they move to YAML unchanged when it learns the status
 * type. Their shape is already the definition record, so that move is a parser
 * change and not a redesign.
 *
 * <p>Values here are starting points, not balance decisions.
 */
public final class BuiltInStatuses {

    private static final ModifierSource ENGINE =
            ModifierSource.of(ModifierSource.SourceType.STATUS, "builtin");

    public static final ContentId BURN = ContentId.parse("branz:burn");
    public static final ContentId BLEED = ContentId.parse("branz:bleed");
    public static final ContentId POISON = ContentId.parse("branz:poison");
    public static final ContentId SLOW = ContentId.parse("branz:slow");
    public static final ContentId ROOT = ContentId.parse("branz:root");
    public static final ContentId STUN = ContentId.parse("branz:stun");
    public static final ContentId SILENCE = ContentId.parse("branz:silence");
    public static final ContentId SHIELD = ContentId.parse("branz:shield");
    public static final ContentId REGENERATION = ContentId.parse("branz:regeneration");
    public static final ContentId VULNERABILITY = ContentId.parse("branz:vulnerability");

    private BuiltInStatuses() {
    }

    /** Every built-in definition, by ID. */
    public static Map<ContentId, StatusDefinition> all() {
        Map<ContentId, StatusDefinition> catalog = new LinkedHashMap<>();
        for (StatusDefinition definition : List.of(
                burn(), bleed(), poison(), slow(), root(), stun(), silence(),
                shield(), regeneration(), vulnerability())) {
            catalog.put(definition.id(), definition);
        }
        return Map.copyOf(catalog);
    }

    public static StatusDefinition burn() {
        return damageOverTime(BURN, "Burn", 4.0, Duration.ofSeconds(6),
                Duration.ofSeconds(2), 3, Set.of("magic", "fire"));
    }

    public static StatusDefinition bleed() {
        // Independent stacks: three attackers each keep credit for their own bleed.
        return new StatusDefinition(BLEED, "Bleed", StatusCategory.NEGATIVE,
                StackPolicy.INDEPENDENT_STACKS, 5, Duration.ofSeconds(8), Duration.ofSeconds(2),
                3.0, List.of(), Set.of("physical", "bleed"), CrowdControlCategory.NONE,
                OfflinePolicy.TICK_DOWN);
    }

    public static StatusDefinition poison() {
        return damageOverTime(POISON, "Poison", 2.0, Duration.ofSeconds(12),
                Duration.ofSeconds(3), 5, Set.of("poison"));
    }

    public static StatusDefinition slow() {
        return new StatusDefinition(SLOW, "Slow", StatusCategory.NEGATIVE,
                StackPolicy.REPLACE_WEAKER, 1, Duration.ofSeconds(4), Duration.ZERO, 0.0,
                List.of(AttributeModifier.percent("slow", AttributeType.MOVEMENT_SPEED, -0.30, ENGINE)),
                Set.of("movement"), CrowdControlCategory.SLOW, OfflinePolicy.TICK_DOWN);
    }

    public static StatusDefinition root() {
        return new StatusDefinition(ROOT, "Root", StatusCategory.NEGATIVE,
                StackPolicy.REFRESH_DURATION, 1, Duration.ofSeconds(2), Duration.ZERO, 0.0,
                List.of(), Set.of("movement"), CrowdControlCategory.ROOT, OfflinePolicy.TICK_DOWN);
    }

    public static StatusDefinition stun() {
        return new StatusDefinition(STUN, "Stun", StatusCategory.NEGATIVE,
                StackPolicy.REPLACE_WEAKER, 1, Duration.ofMillis(1500), Duration.ZERO, 0.0,
                List.of(), Set.of("control"), CrowdControlCategory.STUN, OfflinePolicy.TICK_DOWN);
    }

    public static StatusDefinition silence() {
        return new StatusDefinition(SILENCE, "Silence", StatusCategory.NEGATIVE,
                StackPolicy.REPLACE_WEAKER, 1, Duration.ofSeconds(3), Duration.ZERO, 0.0,
                List.of(), Set.of("control", "magic"), CrowdControlCategory.SILENCE,
                OfflinePolicy.TICK_DOWN);
    }

    public static StatusDefinition shield() {
        // Paused offline: an absorb shield the player paid for should not drain
        // away during a disconnect.
        return new StatusDefinition(SHIELD, "Shield", StatusCategory.POSITIVE,
                StackPolicy.REPLACE_WEAKER, 1, Duration.ofSeconds(10), Duration.ZERO, 50.0,
                List.of(), Set.of("absorb"), CrowdControlCategory.NONE, OfflinePolicy.PAUSE);
    }

    public static StatusDefinition regeneration() {
        return new StatusDefinition(REGENERATION, "Regeneration", StatusCategory.POSITIVE,
                StackPolicy.REFRESH_DURATION, 1, Duration.ofSeconds(10), Duration.ofSeconds(2),
                5.0, List.of(), Set.of("heal"), CrowdControlCategory.NONE, OfflinePolicy.PAUSE);
    }

    public static StatusDefinition vulnerability() {
        return new StatusDefinition(VULNERABILITY, "Vulnerability", StatusCategory.NEGATIVE,
                StackPolicy.ADD_STACK_REFRESH, 3, Duration.ofSeconds(6), Duration.ZERO, 0.0,
                List.of(AttributeModifier.percent("vuln", AttributeType.DEFENSE, -0.10, ENGINE)),
                Set.of("magic"), CrowdControlCategory.NONE, OfflinePolicy.TICK_DOWN);
    }

    private static StatusDefinition damageOverTime(ContentId id, String name, double potency,
                                                   Duration duration, Duration interval,
                                                   int maxStacks, Set<String> tags) {
        return new StatusDefinition(id, name, StatusCategory.NEGATIVE,
                StackPolicy.ADD_STACK_REFRESH, maxStacks, duration, interval, potency,
                List.of(), tags, CrowdControlCategory.NONE, OfflinePolicy.TICK_DOWN);
    }
}
