package com.branz.mmorpg.quest.core;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.quest.api.ConditionDefinition;
import com.branz.mmorpg.quest.api.QuestGamePort;
import com.branz.mmorpg.quest.api.QuestProgress;
import com.branz.mmorpg.quest.api.QuestState;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

public final class ConditionEngine {
    public enum Result { TRUE, FALSE, UNAVAILABLE, ERROR }

    public Result evaluate(ConditionDefinition condition, UUID playerId,
                           Map<ContentId, QuestProgress> progress,
                           QuestGamePort game, Instant now) {
        try {
            return switch (condition.type()) {
                case ALL -> combine(condition, playerId, progress, game, now, true);
                case ANY -> combine(condition, playerId, progress, game, now, false);
                case NOT -> negate(evaluate(condition.children().getFirst(),
                        playerId, progress, game, now));
                case QUEST_STATE -> {
                    ContentId id = ContentId.parse(condition.values().get("quest"));
                    QuestState expected = QuestState.valueOf(condition.values().get("state"));
                    yield bool(progress.containsKey(id)
                            && progress.get(id).state() == expected);
                }
                case FLAG -> {
                    ContentId id = ContentId.parse(condition.values().get("quest"));
                    QuestProgress value = progress.get(id);
                    yield bool(value != null && condition.values().getOrDefault(
                            "value", "true").equals(value.flags().get(
                                    condition.values().get("flag"))));
                }
                case ITEM_POSSESSION -> bool(game.itemQuantity(playerId,
                        ContentId.parse(condition.values().get("item")))
                        >= condition.numbers().getOrDefault("amount", 1L));
                case MASTERY_LEVEL -> bool(game.masteryLevel(playerId,
                        ContentId.parse(condition.values().get("mastery")))
                        >= condition.numbers().getOrDefault("level", 1L));
                case PERMISSION -> bool(game.hasPermission(
                        playerId, condition.values().get("permission")));
                case PARTY_SIZE -> bool(game.partySize(playerId)
                        >= condition.numbers().getOrDefault("minimum", 1L));
                case CONTENT_UNLOCK -> bool(game.contentUnlocked(
                        playerId, ContentId.parse(condition.values().get("content"))));
                case TIME_WINDOW -> {
                    long from = condition.numbers().getOrDefault("from_epoch_ms", 0L);
                    long to = condition.numbers().getOrDefault("to_epoch_ms", Long.MAX_VALUE);
                    yield bool(now.toEpochMilli() >= from && now.toEpochMilli() <= to);
                }
                case PLAYER_WORLD_REGION -> bool(game.inRegion(playerId,
                        ContentId.parse(condition.values().get("region"))));
            };
        } catch (RuntimeException failure) {
            return Result.ERROR;
        }
    }

    private Result combine(ConditionDefinition condition, UUID playerId,
                           Map<ContentId, QuestProgress> progress,
                           QuestGamePort game, Instant now, boolean all) {
        boolean unavailable = false;
        for (ConditionDefinition child : condition.children()) {
            Result result = evaluate(child, playerId, progress, game, now);
            if (result == Result.ERROR) return Result.ERROR;
            if (result == Result.UNAVAILABLE) unavailable = true;
            if (all && result == Result.FALSE) return Result.FALSE;
            if (!all && result == Result.TRUE) return Result.TRUE;
        }
        if (unavailable) return condition.unavailableAsFalse()
                ? Result.FALSE : Result.UNAVAILABLE;
        return all ? Result.TRUE : Result.FALSE;
    }

    private static Result negate(Result value) {
        return value == Result.TRUE ? Result.FALSE
                : value == Result.FALSE ? Result.TRUE : value;
    }
    private static Result bool(boolean value) { return value ? Result.TRUE : Result.FALSE; }
}
