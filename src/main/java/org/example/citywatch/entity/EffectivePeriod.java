package org.example.citywatch.entity;

import java.util.*;
import java.time.*;

/** Immutable shared value used by CityWatch. */
public final class EffectivePeriod {
    private final Optional<Instant> start;
    private final Optional<Instant> end;

    public EffectivePeriod(Optional<Instant> start, Optional<Instant> end) {
        if (start.isPresent() && end.isPresent() && end.get().isBefore(start.get())) {
            throw new IllegalArgumentException("End precedes start");
        }
        this.start = Objects.requireNonNull(start);
        this.end = Objects.requireNonNull(end);
    }

    public Optional<Instant> getStart() { return start; }

    public Optional<Instant> getEnd() { return end; }

}
