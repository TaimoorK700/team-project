package org.example.citywatch.entity;

import java.util.*;
import java.time.*;

/** Immutable shared value used by CityWatch. */
public final class FieldChange {
    private final Optional<String> previousValue;
    private final Optional<String> newValue;

    public FieldChange(Optional<String> previousValue, Optional<String> newValue) {
        if (previousValue.equals(newValue)) throw new IllegalArgumentException("Values did not change");
        this.previousValue = Objects.requireNonNull(previousValue);
        this.newValue = Objects.requireNonNull(newValue);
    }

    public Optional<String> getPreviousValue() { return previousValue; }

    public Optional<String> getNewValue() { return newValue; }

}
