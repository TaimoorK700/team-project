package org.example.citywatch.test_support;

import java.util.*;
import java.time.*;
import org.example.citywatch.entity.*;
import org.example.citywatch.use_case.gateway.*;

/** Test double only. Production FileIncidentCache must additionally persist atomically. */
public final class InMemoryIncidentCache implements IncidentCache {
    private final Map<IncidentSource, IncidentSnapshot> snapshots = new EnumMap<>(IncidentSource.class);
    private final Map<IncidentKey, CachedIncident> reports = new HashMap<>();
    @Override public synchronized Optional<IncidentSnapshot> getLatestSnapshot(IncidentSource source) {
        return Optional.ofNullable(snapshots.get(source));
    }
    @Override public synchronized Optional<CachedIncident> find(IncidentKey key) {
        return Optional.ofNullable(reports.get(key));
    }
    @Override public synchronized void commit(IncidentSnapshot snapshot, List<IncidentUpdate> updates)
            throws DataAccessException {
        IncidentSnapshot previous = snapshots.get(snapshot.getSource());
        if (previous != null && snapshot.getRetrievedAt().isBefore(previous.getRetrievedAt())) {
            throw new DataAccessException("Cannot replace snapshot with older data");
        }
        Map<IncidentKey, CachedIncident> next = new HashMap<>(reports);
        // Preserve last-known details/history of reports missing from the new snapshot.
        next.replaceAll((key, old) -> key.getSource() == snapshot.getSource()
                ? new CachedIncident(old.getIncident(), false, old.getUpdates()) : old);
        for (Incident incident : snapshot.getIncidents()) {
            CachedIncident old = next.get(incident.getKey());
            next.put(incident.getKey(), new CachedIncident(incident, true,
                    old == null ? List.of() : old.getUpdates()));
        }
        Set<String> updateIds = new HashSet<>();
        for (CachedIncident report : reports.values()) {
            for (IncidentUpdate update : report.getUpdates()) updateIds.add(update.getId());
        }
        for (IncidentUpdate update : updates) {
            IncidentKey key = update.getIncidentKey();
            CachedIncident report = next.get(key);
            if (key.getSource() != snapshot.getSource() || report == null
                    || !updateIds.add(update.getId())
                    || !update.getObservedAt().equals(snapshot.getRetrievedAt())) {
                throw new DataAccessException("Invalid update source, ID, report or observation time");
            }
            List<IncidentUpdate> history = new ArrayList<>(report.getUpdates());
            history.add(update);
            next.put(key, new CachedIncident(report.getIncident(), report.getListed(), history));
        }
        // No shared state was changed before all validation completed.
        reports.clear();
        reports.putAll(next);
        snapshots.put(snapshot.getSource(), snapshot);
    }
}
