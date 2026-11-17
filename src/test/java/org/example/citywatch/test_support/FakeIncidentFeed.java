package org.example.citywatch.test_support;

import java.util.*;
import java.time.*;
import org.example.citywatch.entity.*;
import org.example.citywatch.use_case.gateway.*;

/** Deterministic feed for interactor tests; performs no network calls. */
public final class FakeIncidentFeed implements IncidentFeed {
    private IncidentFetchResult result;
    public FakeIncidentFeed(IncidentFetchResult result) { this.result = Objects.requireNonNull(result); }
    public void setResult(IncidentFetchResult next) {
        if (next.getSource() != getSource()) throw new IllegalArgumentException("Source changed");
        result = next;
    }
    @Override public IncidentSource getSource() { return result.getSource(); }
    @Override public IncidentFetchResult fetch() { return result; }
}
