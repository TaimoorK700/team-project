package org.example.citywatch.data_access.incidents;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.example.citywatch.entity.Coordinates;
import org.example.citywatch.entity.EffectivePeriod;
import org.example.citywatch.entity.FieldChange;
import org.example.citywatch.entity.Incident;
import org.example.citywatch.entity.IncidentKey;
import org.example.citywatch.entity.IncidentSource;
import org.example.citywatch.entity.IncidentUpdate;
import org.example.citywatch.entity.TransitScope;
import org.example.citywatch.use_case.gateway.CachedIncident;
import org.example.citywatch.use_case.gateway.DataAccessException;
import org.example.citywatch.use_case.gateway.IncidentSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FileIncidentCacheTest {
    private static final Instant FIRST = Instant.parse("2026-11-17T12:00:00Z");
    private static final Instant SECOND = FIRST.plusSeconds(3600);
    private static final Instant THIRD = SECOND.plusSeconds(3600);

    private static final IncidentKey ROAD_KEY = new IncidentKey(IncidentSource.TORONTO_ROAD, "123");
    private static final IncidentKey OTHER_ROAD_KEY = new IncidentKey(IncidentSource.TORONTO_ROAD, "456");
    // Same record ID as ROAD_KEY on purpose: keys must also be told apart by source.
    private static final IncidentKey TTC_KEY = new IncidentKey(IncidentSource.TTC, "123");

    //Creates temporary directory for each test that gets deleted afterward
    @TempDir
    Path directory;

    /**
     * Tests a new cache with no files on disk;
     * passes if both sources report "never refreshed"(empty) and no report can be found.
     */
    @Test
    void missingFileMeansNoSourceHasBeenRefreshed() throws Exception {
        FileIncidentCache cache = new FileIncidentCache(file());

        assertTrue(cache.getLatestSnapshot(IncidentSource.TORONTO_ROAD).isEmpty());
        assertTrue(cache.getLatestSnapshot(IncidentSource.TTC).isEmpty());
        assertTrue(cache.find(ROAD_KEY).isEmpty());
    }

    /**
     * Tests committing a Road snapshot with 0 reports, then reloading;
     * passes if Road comes back present with an empty list and the right time, while TTC is still "never refreshed".
     */
    @Test
    void emptySnapshotIsStoredAndDistinctFromNeverRefreshed() throws Exception {
        new FileIncidentCache(file()).commit(snapshot(IncidentSource.TORONTO_ROAD, FIRST), List.of());

        FileIncidentCache reloaded = new FileIncidentCache(file());
        IncidentSnapshot road = reloaded.getLatestSnapshot(IncidentSource.TORONTO_ROAD).orElseThrow();

        assertEquals(FIRST, road.getRetrievedAt());
        assertTrue(road.getIncidents().isEmpty());
        assertTrue(reloaded.getLatestSnapshot(IncidentSource.TTC).isEmpty(),
                "Committing Road must not make TTC look refreshed");
    }

    /**
     * Tests that a report with every field filled in and one with only the required fields both survive a restart;
     * passes if a new cache on the same file returns both reports with identical fields,
     * in the original snapshot order, marked as listed.
     */
    @Test
    void reloadRestoresEveryReportFieldAndSnapshotOrder() throws Exception {
        Incident full = fullRoadIncident();
        Incident minimal = Incident.builder(OTHER_ROAD_KEY, "Minimal report").build();
        new FileIncidentCache(file()).commit(snapshot(IncidentSource.TORONTO_ROAD, FIRST, full, minimal), List.of());

        FileIncidentCache reloaded = new FileIncidentCache(file());
        IncidentSnapshot road = reloaded.getLatestSnapshot(IncidentSource.TORONTO_ROAD).orElseThrow();

        assertEquals(FIRST, road.getRetrievedAt());
        assertEquals(2, road.getIncidents().size());
        assertIncidentEquals(full, road.getIncidents().get(0));
        assertIncidentEquals(minimal, road.getIncidents().get(1));
        assertIncidentEquals(full, reloaded.find(ROAD_KEY).orElseThrow().getIncident());
        assertTrue(reloaded.find(ROAD_KEY).orElseThrow().getListed());
    }

    /**
     * Tests a TTC alert with mixed trasit scopes, open-ended periods and no coordinates;
     * passes if every scope and period reloads exactly, missing values stay missing
     * and the empty scope is still a general advisory.
     */
    @Test
    void reloadRestoresTransitScopesPeriodsAndMissingOptionals() throws Exception {
        Incident alert = Incident.builder(TTC_KEY, "Line 1 delay")
                .transitScopes(List.of(
                        new TransitScope(Optional.of("route-A"), Optional.of("stop-A")),
                        new TransitScope(Optional.of("route-B"), Optional.empty()),
                        new TransitScope(Optional.empty(), Optional.empty())))
                .effectivePeriods(List.of(
                        new EffectivePeriod(Optional.of(FIRST), Optional.empty()),
                        new EffectivePeriod(Optional.empty(), Optional.of(SECOND))))
                .build();
        new FileIncidentCache(file()).commit(snapshot(IncidentSource.TTC, FIRST, alert), List.of());

        Incident loaded = new FileIncidentCache(file()).find(TTC_KEY).orElseThrow().getIncident();

        assertIncidentEquals(alert, loaded);
        assertTrue(loaded.getCoordinates().isEmpty(), "Missing coordinates must not be invented");
        assertTrue(loaded.getTransitScopes().get(2).isGeneralAdvisory());
    }

    /**
     * Tests a description that is an empty string, next to a street that is missing;
     * passes if after reloading the description is still "" and the street is still empty.
     */
    @Test
    void emptyTextIsNotConfusedWithMissingText() throws Exception {
        Incident blank = Incident.builder(ROAD_KEY, "Report").description("").build();
        new FileIncidentCache(file()).commit(snapshot(IncidentSource.TORONTO_ROAD, FIRST, blank), List.of());

        Incident loaded = new FileIncidentCache(file()).find(ROAD_KEY).orElseThrow().getIncident();

        assertEquals(Optional.of(""), loaded.getDescription());
        assertEquals(Optional.empty(), loaded.getStreet());
    }

    /**
     * Tests two commits that each add an update, including field changes;
     * passes if after reloading both updates come back in the same order and same field values.
     */
    @Test
    void reloadRestoresUpdateHistoryInObservationOrder() throws Exception {
        Path file = file();
        FileIncidentCache cache = new FileIncidentCache(file);
        IncidentUpdate added = update("u-1", ROAD_KEY, FIRST, IncidentUpdate.Kind.NEWLY_OBSERVED, Map.of());
        IncidentUpdate changed = update("u-2", ROAD_KEY, SECOND, IncidentUpdate.Kind.CHANGED, Map.of(
                "description", new FieldChange(Optional.of("One lane closed"), Optional.of("Two lanes closed")),
                "severity", new FieldChange(Optional.empty(), Optional.of("MAJOR"))));

        cache.commit(snapshot(IncidentSource.TORONTO_ROAD, FIRST, road("One lane closed")), List.of(added));
        cache.commit(snapshot(IncidentSource.TORONTO_ROAD, SECOND, road("Two lanes closed")), List.of(changed));

        List<IncidentUpdate> history = new FileIncidentCache(file).find(ROAD_KEY).orElseThrow().getUpdates();

        assertEquals(2, history.size());
        assertUpdateEquals(added, history.get(0));
        assertUpdateEquals(changed, history.get(1));
    }

    /**
     * Tests a report that disappears from the next Road snapshot;
     * passes if it can still be found with its last details and full history, but is marked as no longer listed.
     */
    @Test
    void reportMissingFromNewSnapshotKeepsDetailsAndHistoryButIsUnlisted() throws Exception {
        FileIncidentCache cache = new FileIncidentCache(file());
        IncidentUpdate added = update("u-1", ROAD_KEY, FIRST, IncidentUpdate.Kind.NEWLY_OBSERVED, Map.of());
        IncidentUpdate gone = update("u-2", ROAD_KEY, SECOND, IncidentUpdate.Kind.NO_LONGER_LISTED, Map.of());

        cache.commit(snapshot(IncidentSource.TORONTO_ROAD, FIRST, road("One lane closed")), List.of(added));
        cache.commit(snapshot(IncidentSource.TORONTO_ROAD, SECOND), List.of(gone));

        CachedIncident report = new FileIncidentCache(file()).find(ROAD_KEY).orElseThrow();

        assertFalse(report.getListed());
        assertEquals(Optional.of("One lane closed"), report.getIncident().getDescription());
        assertEquals(List.of("u-1", "u-2"), ids(report.getUpdates()));
    }

    /**
     * Tests a report that disappears and comes back in a later snapshot;
     * passes if it's listed again, shows newest details, and keeps all updates.
     */
    @Test
    void reportThatReturnsIsListedAgainWithFullHistory() throws Exception {
        FileIncidentCache cache = new FileIncidentCache(file());
        cache.commit(snapshot(IncidentSource.TORONTO_ROAD, FIRST, road("Old")), List.of(
                update("u-1", ROAD_KEY, FIRST, IncidentUpdate.Kind.NEWLY_OBSERVED, Map.of())));
        cache.commit(snapshot(IncidentSource.TORONTO_ROAD, SECOND), List.of(
                update("u-2", ROAD_KEY, SECOND, IncidentUpdate.Kind.NO_LONGER_LISTED, Map.of())));
        cache.commit(snapshot(IncidentSource.TORONTO_ROAD, THIRD, road("New")), List.of(
                update("u-3", ROAD_KEY, THIRD, IncidentUpdate.Kind.RELISTED, Map.of())));

        CachedIncident report = new FileIncidentCache(file()).find(ROAD_KEY).orElseThrow();

        assertTrue(report.getListed());
        assertEquals(Optional.of("New"), report.getIncident().getDescription());
        assertEquals(List.of("u-1", "u-2", "u-3"), ids(report.getUpdates()));
    }

    /**
     * Tests that Road commits leave TTC data alone, even with same record ID;
     * passes if TTC snapshot and report are unchanged and still listed after Road's report is unlisted.
     */
    @Test
    void committingOneSourceNeverChangesTheOther() throws Exception {
        FileIncidentCache cache = new FileIncidentCache(file());
        Incident alert = Incident.builder(TTC_KEY, "Line 1 delay").build();

        cache.commit(snapshot(IncidentSource.TTC, FIRST, alert), List.of());
        cache.commit(snapshot(IncidentSource.TORONTO_ROAD, FIRST, road("One lane closed")), List.of());
        cache.commit(snapshot(IncidentSource.TORONTO_ROAD, SECOND), List.of());

        FileIncidentCache reloaded = new FileIncidentCache(file());
        IncidentSnapshot ttc = reloaded.getLatestSnapshot(IncidentSource.TTC).orElseThrow();

        assertEquals(FIRST, ttc.getRetrievedAt());
        assertEquals(List.of(TTC_KEY), List.of(ttc.getIncidents().get(0).getKey()));
        assertTrue(reloaded.find(TTC_KEY).orElseThrow().getListed());
        assertFalse(reloaded.find(ROAD_KEY).orElseThrow().getListed());
    }

    /**
     * Tests committing a snapshot older than the stored one;
     * passes if the commit throws and neither memory nor the file changes.
     */
    @Test
    void olderSnapshotIsRejected() throws Exception {
        FileIncidentCache cache = cacheWithOneRoadReport();

        assertRejectedWithoutChanges(cache, snapshot(IncidentSource.TORONTO_ROAD, FIRST.minusSeconds(1)), List.of());
    }

    /**
     * Tests a Road commit that includes an update for a TTC report;
     * passes if the commit throws and neither memory nor the file changes.
     */
    @Test
    void updateFromWrongSourceIsRejected() throws Exception {
        FileIncidentCache cache = cacheWithOneRoadReport();
        cache.commit(snapshot(IncidentSource.TTC, FIRST, Incident.builder(TTC_KEY, "Delay").build()), List.of());

        assertRejectedWithoutChanges(cache, snapshot(IncidentSource.TORONTO_ROAD, SECOND),
                List.of(update("u-9", TTC_KEY, SECOND, IncidentUpdate.Kind.NO_LONGER_LISTED, Map.of())));
    }

    /**
     * Tests an update whose ID is already stored;
     * passes if the commit throws and neither memory nor file changes.
     */
    @Test
    void duplicateUpdateIdIsRejected() throws Exception {
        FileIncidentCache cache = cacheWithOneRoadReport(); // already holds update "u-1"

        assertRejectedWithoutChanges(cache, snapshot(IncidentSource.TORONTO_ROAD, SECOND),
                List.of(update("u-1", ROAD_KEY, SECOND, IncidentUpdate.Kind.NO_LONGER_LISTED, Map.of())));
    }

    /**
     * Tests one commit containing 2 updates with same ID;
     * passes if the whole commit throws, including the valid first update, and nothing is added.
     */
    @Test
    void duplicateIdWithinOneCommitIsRejected() throws Exception {
        FileIncidentCache cache = cacheWithOneRoadReport();
        Incident other = Incident.builder(OTHER_ROAD_KEY, "Other").build();

        assertRejectedWithoutChanges(cache, snapshot(IncidentSource.TORONTO_ROAD, SECOND, road("One lane closed"), other),
                List.of(update("u-7", OTHER_ROAD_KEY, SECOND, IncidentUpdate.Kind.NEWLY_OBSERVED, Map.of()),
                        update("u-7", ROAD_KEY, SECOND, IncidentUpdate.Kind.CHANGED, Map.of(
                                "title", new FieldChange(Optional.of("a"), Optional.of("b"))))));
    }

    /**
     * Tests an update whose observation time differs from the snapshot's retrieval time;
     * passes if the commit throws and nothing changes.
     */
    @Test
    void updateObservedAtDifferentTimeIsRejected() throws Exception {
        FileIncidentCache cache = cacheWithOneRoadReport();

        assertRejectedWithoutChanges(cache, snapshot(IncidentSource.TORONTO_ROAD, SECOND),
                List.of(update("u-2", ROAD_KEY, THIRD, IncidentUpdate.Kind.NO_LONGER_LISTED, Map.of())));
    }

    /**
     * Tests an update for a report that is neither stored nor in the new snapshot;
     * passes if the commit throws and nothing changes.
     */
    @Test
    void updateForUnknownReportIsRejected() throws Exception {
        FileIncidentCache cache = cacheWithOneRoadReport();

        assertRejectedWithoutChanges(cache, snapshot(IncidentSource.TORONTO_ROAD, SECOND, road("One lane closed")),
                List.of(update("u-2", OTHER_ROAD_KEY, SECOND, IncidentUpdate.Kind.NEWLY_OBSERVED, Map.of())));
    }

    /**
     * Tests a commit whose disk write fails due to folder being read-only;
     * passes if commit throws, and both in-memory data and the file still holds previous state.
     * Skipped if the folder can't be read-only.
     */
    @Test
    void failedWriteKeepsPreviousDataInMemoryAndOnDisk() throws Exception {
        FileIncidentCache cache = cacheWithOneRoadReport();
        String before = Files.readString(file());

        // A read-only folder makes creating the temporary file fail.
        assumeTrue(directory.toFile().setWritable(false), "Cannot make folder read-only here");
        try {
            assumeTrue(!Files.isWritable(directory), "Folder is still writable (running as root?)");

            assertThrows(DataAccessException.class,
                    () -> cache.commit(snapshot(IncidentSource.TORONTO_ROAD, SECOND), List.of()));
        }
        finally {
            directory.toFile().setWritable(true);
        }

        assertTrue(cache.find(ROAD_KEY).orElseThrow().getListed(), "Failed write changed memory");
        assertEquals(FIRST, cache.getLatestSnapshot(IncidentSource.TORONTO_ROAD).orElseThrow().getRetrievedAt());
        assertEquals(before, Files.readString(file()), "Failed write changed the file");
    }

    /**
     * Tests that the temporary file used for the atomic write is cleaned up;
     * passes if the folder contains only incidents.properties after a commit.
     */
    @Test
    void commitLeavesNoTemporaryFilesBehind() throws Exception {
        cacheWithOneRoadReport();

        try (Stream<Path> files = Files.list(directory)) {
            assertEquals(List.of(file()), files.collect(Collectors.toList()));
        }
    }

    /**
     * Tests loading a file written with an unknown format version;
     * passes if reading it throws DataAccessException instead of guessing.
     */
    @Test
    void unsupportedVersionIsRejected() throws Exception {
        Files.writeString(file(), "cache.version=99\nreports.count=0\n");

        assertThrows(DataAccessException.class,
                () -> new FileIncidentCache(file()).getLatestSnapshot(IncidentSource.TTC));
    }

    /**
     * Tests loading a fiel with invalid data (e.g. unknown source name);
     * passes if reads and commits throw DataAccessException and corrupt file is left exactly as it was.
     */
    @Test
    void corruptFileIsRejectedAndNotOverwritten() throws Exception {
        String corrupt = "cache.version=1\nreports.count=1\nreports.0.source=NOT_A_SOURCE\n";
        Files.writeString(file(), corrupt);
        FileIncidentCache cache = new FileIncidentCache(file());

        assertThrows(DataAccessException.class, () -> cache.find(ROAD_KEY));
        assertThrows(DataAccessException.class,
                () -> cache.commit(snapshot(IncidentSource.TORONTO_ROAD, FIRST), List.of()));
        assertEquals(corrupt, Files.readString(file()), "Corrupt file must be left for the user to fix");
    }

    //HELPER METHODS

    private Path file() {
        return directory.resolve("incidents.properties");
    }

    /**
     * A cache holding one listed Road report (ROAD_KEY) with one update, "u-1", at FIRST.
     */
    private FileIncidentCache cacheWithOneRoadReport() throws DataAccessException {
        FileIncidentCache cache = new FileIncidentCache(file());
        cache.commit(snapshot(IncidentSource.TORONTO_ROAD, FIRST, road("One lane closed")), List.of(
                update("u-1", ROAD_KEY, FIRST, IncidentUpdate.Kind.NEWLY_OBSERVED, Map.of())));
        return cache;
    }

    /**
     * Checks the commit throws, and that neither memory nor the file changed.
     */
    private void assertRejectedWithoutChanges(FileIncidentCache cache, IncidentSnapshot snapshot,
                                              List<IncidentUpdate> updates) throws Exception {
        String fileBefore = Files.readString(file());
        IncidentSnapshot snapshotBefore = cache.getLatestSnapshot(IncidentSource.TORONTO_ROAD).orElseThrow();
        CachedIncident reportBefore = cache.find(ROAD_KEY).orElseThrow();

        assertThrows(DataAccessException.class, () -> cache.commit(snapshot, updates));

        assertEquals(fileBefore, Files.readString(file()), "Rejected commit changed the file");
        assertSame(snapshotBefore, cache.getLatestSnapshot(IncidentSource.TORONTO_ROAD).orElseThrow());
        CachedIncident reportAfter = cache.find(ROAD_KEY).orElseThrow();
        assertEquals(reportBefore.getListed(), reportAfter.getListed());
        assertEquals(ids(reportBefore.getUpdates()), ids(reportAfter.getUpdates()));
        assertTrue(cache.find(OTHER_ROAD_KEY).isEmpty(), "Rejected commit added a report");
    }

    private static IncidentSnapshot snapshot(IncidentSource source, Instant retrievedAt, Incident... incidents) {
        return new IncidentSnapshot(source, retrievedAt, List.of(incidents));
    }

    private static Incident road(String description) {
        return Incident.builder(ROAD_KEY, "Sample road restriction").description(description).build();
    }

    private static Incident fullRoadIncident() {
        return Incident.builder(ROAD_KEY, "Lane closure")
                .description("Two lanes closed")
                .sourceUrl("https://example.com/road/123")
                .category("CONSTRUCTION")
                .street("Sample Street")
                .coordinates(new Coordinates(43.6629, -79.3957))
                .severity("MAJOR")
                .status("ACTIVE")
                .reportedAt(FIRST)
                .sourceUpdatedAt(SECOND)
                .effectivePeriods(List.of(new EffectivePeriod(Optional.of(FIRST), Optional.of(THIRD))))
                .build();
    }

    private static IncidentUpdate update(String id, IncidentKey key, Instant observedAt,
                                         IncidentUpdate.Kind kind, Map<String, FieldChange> changes) {
        return new IncidentUpdate(id, key, observedAt, kind, changes);
    }

    private static List<String> ids(List<IncidentUpdate> updates) {
        return updates.stream().map(IncidentUpdate::getId).collect(Collectors.toList());
    }

    // Incident and IncidentUpdate don't override equals, so compare field by field.

    private static void assertIncidentEquals(Incident expected, Incident actual) {
        assertEquals(expected.getKey(), actual.getKey());
        assertEquals(expected.getTitle(), actual.getTitle());
        assertEquals(expected.getDescription(), actual.getDescription());
        assertEquals(expected.getSourceUrl(), actual.getSourceUrl());
        assertEquals(expected.getCategory(), actual.getCategory());
        assertEquals(expected.getStreet(), actual.getStreet());
        assertEquals(expected.getSeverity(), actual.getSeverity());
        assertEquals(expected.getStatus(), actual.getStatus());
        assertEquals(expected.getReportedAt(), actual.getReportedAt());
        assertEquals(expected.getSourceUpdatedAt(), actual.getSourceUpdatedAt());

        assertEquals(expected.getCoordinates().isPresent(), actual.getCoordinates().isPresent());
        expected.getCoordinates().ifPresent(coordinates -> {
            assertEquals(coordinates.getLatitude(), actual.getCoordinates().get().getLatitude());
            assertEquals(coordinates.getLongitude(), actual.getCoordinates().get().getLongitude());
        });

        assertEquals(expected.getEffectivePeriods().size(), actual.getEffectivePeriods().size());
        for (int i = 0; i < expected.getEffectivePeriods().size(); i++) {
            assertEquals(expected.getEffectivePeriods().get(i).getStart(), actual.getEffectivePeriods().get(i).getStart());
            assertEquals(expected.getEffectivePeriods().get(i).getEnd(), actual.getEffectivePeriods().get(i).getEnd());
        }

        assertEquals(expected.getTransitScopes().size(), actual.getTransitScopes().size());
        for (int i = 0; i < expected.getTransitScopes().size(); i++) {
            assertEquals(expected.getTransitScopes().get(i).getRouteId(), actual.getTransitScopes().get(i).getRouteId());
            assertEquals(expected.getTransitScopes().get(i).getStopId(), actual.getTransitScopes().get(i).getStopId());
        }
    }

    private static void assertUpdateEquals(IncidentUpdate expected, IncidentUpdate actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getIncidentKey(), actual.getIncidentKey());
        assertEquals(expected.getObservedAt(), actual.getObservedAt());
        assertEquals(expected.getKind(), actual.getKind());
        assertEquals(expected.getChangedFields().keySet(), actual.getChangedFields().keySet());
        for (String field : expected.getChangedFields().keySet()) {
            FieldChange want = expected.getChangedFields().get(field);
            FieldChange got = actual.getChangedFields().get(field);
            assertEquals(want.getPreviousValue(), got.getPreviousValue(), field);
            assertEquals(want.getNewValue(), got.getNewValue(), field);
        }
    }
}