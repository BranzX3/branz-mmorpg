package com.branz.mmorpg.quest.api;

import java.util.function.Consumer;

public interface QuestEventSource {
    AutoCloseable subscribe(QuestEvent.Type type, Consumer<QuestEvent> listener);
}
