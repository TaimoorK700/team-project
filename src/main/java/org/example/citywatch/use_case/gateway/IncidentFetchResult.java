package org.example.citywatch.use_case.gateway;

import java.util.*;
import java.time.*;
import org.example.citywatch.entity.*;

/** Only COMPLETE results may replace a cached snapshot or trigger change detection. */
public final class IncidentFetchResult {
    public enum Outcome { COMPLETE, INCOMPLETE, FAILED }
    private final IncidentSource source;
    private final Outcome outcome;
    private final Optional<IncidentSnapshot> snapshot;
    private final Optional<String> message;
    private IncidentFetchResult(IncidentSource source, Outcome outcome,
                                Optional<IncidentSnapshot> snapshot, Optional<String> message) {
        this.source = Objects.requireNonNull(source);
        this.outcome = outcome;
        this.snapshot = snapshot;
        this.message = message;
    }
    public static IncidentFetchResult complete(IncidentSnapshot snapshot) {
        return new IncidentFetchResult(snapshot.getSource(), Outcome.COMPLETE,
                Optional.of(snapshot), Optional.empty());
    }
    public static IncidentFetchResult incomplete(IncidentSource source, String reason) {
        return new IncidentFetchResult(source, Outcome.INCOMPLETE,
                Optional.empty(), Optional.of(Checks.text(reason)));
    }
    public static IncidentFetchResult failed(IncidentSource source, String reason) {
        return new IncidentFetchResult(source, Outcome.FAILED,
                Optional.empty(), Optional.of(Checks.text(reason)));
    }
    public IncidentSource getSource() { return source; }
    public Outcome getOutcome() { return outcome; }
    public Optional<IncidentSnapshot> getSnapshot() { return snapshot; }
    public Optional<String> getMessage() { return message; }
}
