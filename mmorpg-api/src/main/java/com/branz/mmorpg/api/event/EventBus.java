package com.branz.mmorpg.api.event;

import java.util.function.Consumer;

/**
 * Publishes domain events to in-process subscribers.
 *
 * <p>Deliberately synchronous and platform-independent: an event is published
 * after its transaction committed, on the thread that committed it, so a
 * consumer sees a consistent world. Anything a consumer wants to do off-thread
 * is its own business, and its own scheduler call.
 *
 * <p>A throwing subscriber must never prevent the remaining subscribers from
 * receiving the event, nor fail the gameplay action that produced it: the action
 * already happened, and refusing to tell everyone about it cannot undo that.
 */
public interface EventBus {

    <T extends DomainEvent> void subscribe(Class<T> eventType, Consumer<T> subscriber);

    void publish(DomainEvent event);
}
