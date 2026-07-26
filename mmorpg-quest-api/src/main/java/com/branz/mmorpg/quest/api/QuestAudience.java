package com.branz.mmorpg.quest.api;

import java.util.List;
import java.util.UUID;

public interface QuestAudience {
    void line(UUID playerId, String speakerKey, String textKey, String portrait);
    void choices(UUID playerId, UUID sessionId, long sequence, List<ChoiceView> choices);
    void tracker(UUID playerId, String titleKey, List<String> objectiveLines);
    void journal(UUID playerId, List<JournalEntry> entries);
    void subtitle(UUID playerId, String textKey);
    void sound(UUID playerId, String soundKey);
    void clear(UUID playerId, UUID sessionId);

    record ChoiceView(String id, String textKey, boolean enabled, String disabledReasonKey) {}
    record JournalEntry(String questId, String titleKey, String state, String stage) {}
}
