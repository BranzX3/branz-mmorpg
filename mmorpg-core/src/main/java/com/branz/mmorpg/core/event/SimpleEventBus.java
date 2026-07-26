package com.branz.mmorpg.core.event;

import com.branz.mmorpg.api.event.DomainEvent;
import com.branz.mmorpg.api.event.EventBus;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * In-process {@link EventBus}.
 *
 * <p>Subscribers are held in copy-on-write lists: subscription happens at
 * startup and publication happens constantly, so reads must be free and writes
 * may be expensive.
 *
 * <p>Delivery walks the event's whole type hierarchy, so a consumer can
 * subscribe to an interface and receive every implementation.
 */
public final class SimpleEventBus implements EventBus {

    private final Map<Class<?>, List<Consumer<? super DomainEvent>>> subscribers = new ConcurrentHashMap<>();
    private final Consumer<Throwable> failureHandler;

    public SimpleEventBus() {
        this(throwable -> { });
    }

    public SimpleEventBus(Consumer<Throwable> failureHandler) {
        this.failureHandler = Objects.requireNonNull(failureHandler, "failureHandler");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends DomainEvent> void subscribe(Class<T> eventType, Consumer<T> subscriber) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(subscriber, "subscriber");
        subscribers.computeIfAbsent(eventType, key -> new CopyOnWriteArrayList<>())
                .add((Consumer<? super DomainEvent>) subscriber);
    }

    @Override
    public void publish(DomainEvent event) {
        Objects.requireNonNull(event, "event");
        for (Class<?> type : hierarchyOf(event.getClass())) {
            List<Consumer<? super DomainEvent>> listeners = subscribers.get(type);
            if (listeners == null) {
                continue;
            }
            for (Consumer<? super DomainEvent> listener : listeners) {
                try {
                    listener.accept(event);
                } catch (RuntimeException failure) {
                    // The action already committed. One broken consumer must not
                    // silence the others or fail the gameplay that caused it.
                    failureHandler.accept(failure);
                }
            }
        }
    }

    public int subscriberCount(Class<? extends DomainEvent> eventType) {
        List<Consumer<? super DomainEvent>> listeners = subscribers.get(eventType);
        return listeners == null ? 0 : listeners.size();
    }

    private static List<Class<?>> hierarchyOf(Class<?> type) {
        List<Class<?>> types = new java.util.ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class;
             current = current.getSuperclass()) {
            types.add(current);
            types.addAll(List.of(current.getInterfaces()));
        }
        return types;
    }
}
