package com.todo.desktop.domain.event;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class EventBus {

    private final Map<Class<? extends DomainEvent>, Set<Consumer<DomainEvent>>> listeners = new ConcurrentHashMap<>();
    private final Map<Class<? extends DomainEvent>, Map<Consumer<?>, Consumer<DomainEvent>>> adapters = new ConcurrentHashMap<>();

    public <T extends DomainEvent> void register(Class<T> type, Consumer<? super T> consumer) {
        Consumer<DomainEvent> adapter = event -> consumer.accept(type.cast(event));
        listeners.computeIfAbsent(type, ignored -> ConcurrentHashMap.newKeySet()).add(adapter);
        adapters.computeIfAbsent(type, ignored -> new ConcurrentHashMap<>()).put(consumer, adapter);
    }

    public <T extends DomainEvent> void unregister(Class<T> type, Consumer<? super T> consumer) {
        Map<Consumer<?>, Consumer<DomainEvent>> adapterMap = adapters.get(type);
        if (adapterMap == null) {
            return;
        }
        Consumer<DomainEvent> adapter = adapterMap.remove(consumer);
        if (adapter == null) {
            return;
        }
        Set<Consumer<DomainEvent>> consumerSet = listeners.get(type);
        if (consumerSet != null) {
            consumerSet.remove(adapter);
            if (consumerSet.isEmpty()) {
                listeners.remove(type);
            }
        }
        if (adapterMap.isEmpty()) {
            adapters.remove(type);
        }
    }

    public void publish(DomainEvent event) {
        listeners.getOrDefault(event.getClass(), Set.of()).forEach(listener -> listener.accept(event));
    }
}
