package com.branz.mmorpg.core.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.api.stat.ModifierSource;
import com.branz.mmorpg.api.status.CrowdControlCategory;
import com.branz.mmorpg.api.status.OfflinePolicy;
import com.branz.mmorpg.api.status.StackPolicy;
import com.branz.mmorpg.api.status.StatusApplication;
import com.branz.mmorpg.api.status.StatusCategory;
import com.branz.mmorpg.api.status.StatusDefinition;
import com.branz.mmorpg.api.status.StatusInstance;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StatusContainerTest {

    private static final Instant NOW = Instant.parse("2026-07-25T12:00:00Z");
    private static final ModifierSource CASTER_A =
            ModifierSource.of(ModifierSource.SourceType.SKILL, "player-a");
    private static final ModifierSource CASTER_B =
            ModifierSource.of(ModifierSource.SourceType.SKILL, "player-b");

    private final Map<ContentId, StatusDefinition> catalog = BuiltInStatuses.all();

    @Test
    void appliesAndExpiresOnItsOwnSchedule() {
        StatusContainer container = new StatusContainer();

        StatusApplication applied = container.apply(catalog.get(BuiltInStatuses.BURN),
                CASTER_A, null, 0.0, NOW);

        assertEquals(StatusApplication.Outcome.APPLIED, applied.outcome());
        assertTrue(container.has(BuiltInStatuses.BURN));

        assertTrue(container.advance(NOW.plusSeconds(5)).expired().isEmpty());
        assertEquals(1, container.advance(NOW.plusSeconds(7)).expired().size());
        assertFalse(container.has(BuiltInStatuses.BURN));
    }

    @Test
    void uniqueRejectsASecondApplication() {
        StatusDefinition unique = new StatusDefinition(ContentId.parse("branz:mark"), "Mark",
                StatusCategory.NEUTRAL, StackPolicy.UNIQUE, 1, Duration.ofSeconds(10),
                Duration.ZERO, 0.0, List.of(), Set.of(), CrowdControlCategory.NONE,
                OfflinePolicy.TICK_DOWN);
        StatusContainer container = new StatusContainer();
        container.apply(unique, CASTER_A, null, 0.0, NOW);

        StatusApplication second = container.apply(unique, CASTER_B, null, 0.0, NOW.plusSeconds(1));

        assertEquals(StatusApplication.Outcome.REJECTED_WEAKER, second.outcome());
        assertFalse(second.applied());
        assertEquals(1, container.size());
    }

    @Test
    void addStackRefreshGrowsToTheCapThenOnlyRefreshes() {
        StatusContainer container = new StatusContainer();
        StatusDefinition burn = catalog.get(BuiltInStatuses.BURN);

        assertEquals(StatusApplication.Outcome.APPLIED,
                container.apply(burn, CASTER_A, null, 0.0, NOW).outcome());
        assertEquals(StatusApplication.Outcome.STACKED,
                container.apply(burn, CASTER_A, null, 0.0, NOW.plusSeconds(1)).outcome());
        assertEquals(StatusApplication.Outcome.STACKED,
                container.apply(burn, CASTER_A, null, 0.0, NOW.plusSeconds(2)).outcome());
        assertEquals(3, container.stacksOf(BuiltInStatuses.BURN));

        StatusApplication atCap = container.apply(burn, CASTER_A, null, 0.0, NOW.plusSeconds(3));

        assertEquals(StatusApplication.Outcome.REFRESHED, atCap.outcome());
        assertEquals(3, container.stacksOf(BuiltInStatuses.BURN), "never exceeds the cap");
        assertEquals(1, container.size(), "still one instance");
    }

    @Test
    void independentStacksKeepTheirOwnSource() {
        StatusContainer container = new StatusContainer();
        StatusDefinition bleed = catalog.get(BuiltInStatuses.BLEED);

        container.apply(bleed, CASTER_A, null, 0.0, NOW);
        container.apply(bleed, CASTER_B, null, 0.0, NOW);

        assertEquals(2, container.size());
        assertEquals(Set.of(CASTER_A, CASTER_B),
                container.active().stream().map(StatusInstance::source)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void replaceWeakerKeepsTheStrongerRemainingDuration() {
        StatusContainer container = new StatusContainer();
        StatusDefinition stun = catalog.get(BuiltInStatuses.STUN);
        container.apply(stun, CASTER_A, Duration.ofSeconds(5), 0.0, NOW);

        StatusApplication weaker = container.apply(stun, CASTER_B, Duration.ofSeconds(1), 0.0, NOW);
        assertEquals(StatusApplication.Outcome.REJECTED_WEAKER, weaker.outcome());

        StatusApplication stronger = container.apply(stun, CASTER_B, Duration.ofSeconds(9), 0.0, NOW);
        assertEquals(StatusApplication.Outcome.REPLACED, stronger.outcome());
        assertEquals(1, container.size());
        assertEquals(CASTER_B, container.active().get(0).source());
    }

    @Test
    void refreshNeverShortensAnExistingDuration() {
        StatusContainer container = new StatusContainer();
        StatusDefinition regen = catalog.get(BuiltInStatuses.REGENERATION);
        container.apply(regen, CASTER_A, Duration.ofSeconds(30), 0.0, NOW);

        container.apply(regen, CASTER_B, Duration.ofSeconds(2), 0.0, NOW);

        assertEquals(30_000L, container.active().get(0).remainingMillis(NOW),
                "a short refresh must not cut a long buff short");
    }

    @Test
    void crowdControlResistanceIsAppliedExactlyOnce() {
        StatusContainer container = new StatusContainer();
        StatusDefinition stun = catalog.get(BuiltInStatuses.STUN);

        container.apply(stun, CASTER_A, Duration.ofSeconds(10), 0.60, NOW);

        assertEquals(4_000L, container.active().get(0).remainingMillis(NOW),
                "10s at 60% resistance is 4s, not 10s compounded per tick");
    }

    @Test
    void resistanceDoesNotShortenNonCrowdControl() {
        StatusContainer container = new StatusContainer();

        container.apply(catalog.get(BuiltInStatuses.BURN), CASTER_A, Duration.ofSeconds(10), 0.60, NOW);

        assertEquals(10_000L, container.active().get(0).remainingMillis(NOW));
    }

    @Test
    void immunityRejectsTheWholeCrowdControlClass() {
        StatusContainer container = new StatusContainer();
        container.grantImmunity(CrowdControlCategory.STUN);

        StatusApplication rejected = container.apply(catalog.get(BuiltInStatuses.STUN),
                CASTER_A, null, 0.0, NOW);

        assertEquals(StatusApplication.Outcome.REJECTED_IMMUNE, rejected.outcome());
        assertNull(rejected.instance());
        assertEquals(0, container.size());

        // an unrelated class still lands
        assertTrue(container.apply(catalog.get(BuiltInStatuses.SLOW), CASTER_A, null, 0.0, NOW)
                .applied());
    }

    @Test
    void immunityToOneStatusDoesNotBlockOthers() {
        StatusContainer container = new StatusContainer();
        container.grantImmunity(BuiltInStatuses.BURN);

        assertEquals(StatusApplication.Outcome.REJECTED_IMMUNE,
                container.apply(catalog.get(BuiltInStatuses.BURN), CASTER_A, null, 0.0, NOW).outcome());
        assertTrue(container.apply(catalog.get(BuiltInStatuses.POISON), CASTER_A, null, 0.0, NOW)
                .applied());
    }

    @Test
    void periodicTicksComeDueOnTheirInterval() {
        StatusContainer container = new StatusContainer();
        StatusDefinition burn = catalog.get(BuiltInStatuses.BURN);
        container.apply(burn, CASTER_A, null, 0.0, NOW);

        assertTrue(container.advance(NOW.plusSeconds(1)).ticked().isEmpty());

        StatusTickResult due = container.advance(NOW.plusSeconds(2));
        assertEquals(1, due.ticked().size());

        container.tickDelivered(due.ticked().get(0), burn.periodicInterval(), NOW.plusSeconds(2));
        assertTrue(container.advance(NOW.plusSeconds(3)).ticked().isEmpty(), "not due again yet");
        assertEquals(1, container.advance(NOW.plusSeconds(4)).ticked().size());
    }

    @Test
    void cleanseRemovesOnlyMatchingStatuses() {
        StatusContainer container = new StatusContainer();
        container.apply(catalog.get(BuiltInStatuses.POISON), CASTER_A, null, 0.0, NOW);
        container.apply(catalog.get(BuiltInStatuses.BURN), CASTER_A, null, 0.0, NOW);
        container.apply(catalog.get(BuiltInStatuses.REGENERATION), CASTER_A, null, 0.0, NOW);

        List<StatusInstance> cleansed =
                container.cleanse(StatusCategory.NEGATIVE, "poison", catalog::get);

        assertEquals(1, cleansed.size());
        assertEquals(BuiltInStatuses.POISON, cleansed.get(0).definitionId());
        assertTrue(container.has(BuiltInStatuses.BURN), "a debuff without the tag survives");
        assertTrue(container.has(BuiltInStatuses.REGENERATION), "a buff is not cleansed");
    }

    @Test
    void cleanseCannotStripBuffsWhenRestrictedToDebuffs() {
        StatusContainer container = new StatusContainer();
        container.apply(catalog.get(BuiltInStatuses.SHIELD), CASTER_A, null, 0.0, NOW);

        assertTrue(container.cleanse(StatusCategory.NEGATIVE, null, catalog::get).isEmpty());
        assertTrue(container.has(BuiltInStatuses.SHIELD));
    }

    @Test
    void deathClearsEverything() {
        StatusContainer container = new StatusContainer();
        container.apply(catalog.get(BuiltInStatuses.BURN), CASTER_A, null, 0.0, NOW);
        container.apply(catalog.get(BuiltInStatuses.SHIELD), CASTER_A, null, 0.0, NOW);

        assertEquals(2, container.clear().size());
        assertEquals(0, container.size());
    }

    @Test
    void contentValidationRejectsImpossibleDefinitions() {
        assertThrows(MMOException.class, () -> new StatusDefinition(
                ContentId.parse("branz:bad"), "Bad", StatusCategory.NEGATIVE,
                StackPolicy.ADD_STACK_REFRESH, 0, Duration.ofSeconds(1), Duration.ZERO, 0.0,
                List.of(), Set.of(), CrowdControlCategory.NONE, OfflinePolicy.TICK_DOWN));

        assertThrows(MMOException.class, () -> new StatusDefinition(
                ContentId.parse("branz:forever_stun"), "Forever", StatusCategory.NEGATIVE,
                StackPolicy.REPLACE_WEAKER, 1, Duration.ZERO, Duration.ZERO, 0.0,
                List.of(), Set.of(), CrowdControlCategory.STUN, OfflinePolicy.TICK_DOWN));

        assertThrows(MMOException.class, () -> new StatusDefinition(
                ContentId.parse("branz:bad_unique"), "Bad", StatusCategory.NEGATIVE,
                StackPolicy.UNIQUE, 3, Duration.ofSeconds(1), Duration.ZERO, 0.0,
                List.of(), Set.of(), CrowdControlCategory.NONE, OfflinePolicy.TICK_DOWN));
    }

    @Test
    void allTenRequiredStatusesExist() {
        assertEquals(10, catalog.size());
        for (ContentId required : List.of(BuiltInStatuses.BURN, BuiltInStatuses.BLEED,
                BuiltInStatuses.POISON, BuiltInStatuses.SLOW, BuiltInStatuses.ROOT,
                BuiltInStatuses.STUN, BuiltInStatuses.SILENCE, BuiltInStatuses.SHIELD,
                BuiltInStatuses.REGENERATION, BuiltInStatuses.VULNERABILITY)) {
            assertTrue(catalog.containsKey(required), required + " is required at launch");
        }
    }
}
