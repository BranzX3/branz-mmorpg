package com.branz.mmorpg.quest.core;

import com.branz.mmorpg.quest.api.ObjectiveDefinition;
import com.branz.mmorpg.quest.api.ObjectiveProgress;
import com.branz.mmorpg.quest.api.QuestEvent;
import com.branz.mmorpg.quest.api.QuestGamePort;
import java.util.UUID;

public final class ObjectiveEngine {
    public boolean query(ObjectiveDefinition definition) {
        return definition.type() == ObjectiveDefinition.Type.POSSESS
                || definition.type() == ObjectiveDefinition.Type.CONSUME
                || definition.type() == ObjectiveDefinition.Type.REACH_MASTERY;
    }

    public ObjectiveProgress initial(ObjectiveDefinition definition) {
        return new ObjectiveProgress(0, definition.targetAmount(), java.util.Map.of());
    }

    public ObjectiveProgress reduce(
            UUID progressOwner, ObjectiveDefinition definition, ObjectiveProgress before,
            QuestEvent event, QuestGamePort game) {
        if (before.complete() || !credit(progressOwner, definition, event)
                || (!query(definition) && !source(definition, event))
                || !matches(definition, event)) return before;
        long current;
        if (definition.type() == ObjectiveDefinition.Type.POSSESS
                || definition.type() == ObjectiveDefinition.Type.CONSUME) {
            current = game.itemQuantity(progressOwner, definition.targetId().orElseThrow());
        } else if (definition.type() == ObjectiveDefinition.Type.REACH_MASTERY) {
            current = game.masteryLevel(progressOwner, definition.targetId().orElseThrow());
        } else {
            current = Math.addExact(before.current(), Math.max(1, event.amount()));
        }
        return new ObjectiveProgress(
                Math.min(before.target(), current), before.target(), before.data());
    }

    public QuestEvent.Type eventType(ObjectiveDefinition.Type type) {
        return switch (type) {
            case TALK -> QuestEvent.Type.NPC_TALKED;
            case KILL -> QuestEvent.Type.MOB_KILLED;
            case DEFEAT_BOSS -> QuestEvent.Type.BOSS_DEFEATED;
            case COLLECT, POSSESS, CONSUME -> QuestEvent.Type.ITEM_ACQUIRED;
            case INTERACT -> QuestEvent.Type.WORLD_OBJECT_INTERACTED;
            case ENTER_REGION -> QuestEvent.Type.REGION_ENTERED;
            case USE_SKILL -> QuestEvent.Type.SKILL_USED;
            case CRAFT -> QuestEvent.Type.CRAFT_COMPLETED;
            case REACH_MASTERY -> QuestEvent.Type.MASTERY_CHANGED;
            case WAIT -> QuestEvent.Type.TIMER_ELAPSED;
            case CHOOSE -> QuestEvent.Type.DIALOGUE_CHOICE;
        };
    }

    private boolean matches(ObjectiveDefinition definition, QuestEvent event) {
        // Query objectives represent current canonical game state. Re-evaluate
        // them whenever their owner receives a quest event, including the event
        // that activates a new stage, so already-owned items/mastery are not
        // stranded waiting for an unrelated duplicate acquisition event.
        if (query(definition)) {
            return true;
        }
        if (event.type() != eventType(definition.type())) return false;
        return definition.targetId().isEmpty()
                || definition.targetId().equals(event.targetId());
    }

    private static boolean source(ObjectiveDefinition definition, QuestEvent event) {
        return definition.acceptedSources().isEmpty()
                || definition.acceptedSources().contains(event.source());
    }

    private static boolean credit(
            UUID owner, ObjectiveDefinition definition, QuestEvent event) {
        return switch (definition.creditPolicy()) {
            case PERSONAL -> owner.equals(event.playerId());
            case PARTY_IN_RANGE, PARTY_SHARED ->
                    owner.equals(event.playerId())
                            || event.partyInRangeSnapshot().contains(owner);
            case ENCOUNTER_ELIGIBLE -> event.encounterEligible()
                    && (owner.equals(event.playerId())
                    || event.partyInRangeSnapshot().contains(owner));
        };
    }
}
