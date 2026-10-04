package dev.farmprofit;

import net.fabricmc.fabric.api.event.Event;

/**
 * Registers a listener on a Fabric event we only have as an Object (found by name at runtime).
 * Must go through the public Event type: the object's real class is Fabric-internal and not public,
 * so calling "register" on it by reflection is refused by Java (that silently broke menu overlays before).
 */
final class Events {
    @SuppressWarnings("unchecked")
    static void register(Object event, Object listener) {
        ((Event<Object>) event).register(listener);
    }

    private Events() {}
}
