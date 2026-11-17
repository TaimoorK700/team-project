package org.example.citywatch.entity;

import java.util.*;
import java.time.*;

/** Immutable shared value used by CityWatch. */
public final class TransitSubscription {
    private final Type type;
    private final String serviceId;
    private final String displayName;
    private final Set<String> associatedStopIds;

    public TransitSubscription(Type type, String serviceId, String displayName, Set<String> associatedStopIds) {
        associatedStopIds.forEach(Checks::text);
        this.type = Objects.requireNonNull(type);
        this.serviceId = Checks.text(serviceId);
        this.displayName = Checks.text(displayName);
        this.associatedStopIds = Set.copyOf(associatedStopIds);
    }

    public Type getType() { return type; }

    public String getServiceId() { return serviceId; }

    public String getDisplayName() { return displayName; }

    public Set<String> getAssociatedStopIds() { return associatedStopIds; }

    public enum Type { ROUTE, STATION }
    /** Identity excludes labels and mappings, which may change. */
    @Override public boolean equals(Object other) {
        if (!(other instanceof TransitSubscription)) return false;
        TransitSubscription s = (TransitSubscription) other;
        return type == s.type && serviceId.equals(s.serviceId);
    }
    @Override public int hashCode() { return Objects.hash(type, serviceId); }

}
