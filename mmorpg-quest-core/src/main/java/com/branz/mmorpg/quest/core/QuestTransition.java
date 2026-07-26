package com.branz.mmorpg.quest.core;

import com.branz.mmorpg.quest.api.PendingQuestOperation;
import com.branz.mmorpg.quest.api.QuestProgress;
import java.util.List;

public record QuestTransition(
        boolean changed,
        QuestProgress progress,
        List<PendingQuestOperation> operations) {
    public QuestTransition {
        operations = List.copyOf(operations);
    }
}
