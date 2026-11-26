package org.example.citywatch.data_access.incidents;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

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
import org.example.citywatch.use_case.gateway.IncidentCache;
import org.example.citywatch.use_case.gateway.IncidentSnapshot;

/**
 * Stores incident snapshots, last-known report details and update history in a
 * Properties file so they survive restarts.
 *
 * <p>Every commit writes the whole cache to a temporary file and then atomically
 * moves it over the real file, so the file is always either the complete old
 * version or the complete new version. In-memory state is only replaced after
 * the write succeeds, so a failed commit changes nothing.
 *
 * <p>Optional values are stored by key presence: a missing key means
 * {@code Optional.empty()}, and a present key (even an empty string) means a value.
 */
public final class FileIncidentCache implements IncidentCache {
    private static final String FORMAT_VERSION = "1";

    private final Path file;

    // Loaded from the file on first use, then kept in sync after each commit.
    private boolean loaded;
    private Map<IncidentSource, IncidentSnapshot> snapshots;
    private Map<IncidentKey, CachedIncident> reports;

    public FileIncidentCache(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    //IncidentCache Interface Methods

    @Override
    public synchronized Optional<IncidentSnapshot> getLatestSnapshot(IncidentSource source)
            throws DataAccessException {
        Objects.requireNonNull(source, "Source is required.");
        ensureLoaded();
        return Optional.ofNullable(snapshots.get(source));
    }

    @Override
    public synchronized Optional<CachedIncident> find(IncidentKey key) throws DataAccessException {
        Objects.requireNonNull(key, "Report key is required.");
        ensureLoaded();
        return Optional.ofNullable(reports.get(key));
    }

    @Override
    public synchronized void commit(IncidentSnapshot snapshot, List<IncidentUpdate> updates)
            throws DataAccessException {
        Objects.requireNonNull(snapshot, "Snapshot is required.");
        Objects.requireNonNull(updates, "Updates are required.");
        ensureLoaded();

        // 1. Reject a snapshot older than the one already stored for this source.
        rejectOlderSnapshot(snapshot);

        // 2. Build the next state in copies; the real fields stay untouched.
        Map<IncidentKey, CachedIncident> nextReports = applySnapshot(snapshot);

        // 3. Validate and append the new updates.
        Set<String> updateIds = existingUpdateIds();
        for (IncidentUpdate update : updates) {
            validateUpdate(update, snapshot, nextReports, updateIds);

            CachedIncident report = nextReports.get(update.getIncidentKey());
            List<IncidentUpdate> history = new ArrayList<>(report.getUpdates());
            history.add(update);
            nextReports.put(update.getIncidentKey(),
                    new CachedIncident(report.getIncident(), report.getListed(), history));
        }

        Map<IncidentSource, IncidentSnapshot> nextSnapshots = new EnumMap<>(IncidentSource.class);
        nextSnapshots.putAll(snapshots);
        nextSnapshots.put(snapshot.getSource(), snapshot);

        // 4. Write to disk first. If this throws, nothing in memory has changed.
        writeProperties(encode(nextSnapshots, nextReports));

        // 5. Only now replace the in-memory state.
        snapshots = nextSnapshots;
        reports = nextReports;
    }

    private void rejectOlderSnapshot(IncidentSnapshot snapshot) throws DataAccessException {
        IncidentSnapshot previous = snapshots.get(snapshot.getSource());
        if (previous != null && snapshot.getRetrievedAt().isBefore(previous.getRetrievedAt())) {
            throw new DataAccessException("Cannot replace snapshot with older data.");
        }
    }

    /**
     * Returns a copy of the reports with this snapshot applied: reports from this
     * source that are missing become unlisted (keeping details and history), and
     * reports in the snapshot get their latest details and become listed.
     */
    private Map<IncidentKey, CachedIncident> applySnapshot(IncidentSnapshot snapshot) {
        IncidentSource source = snapshot.getSource();
        Map<IncidentKey, CachedIncident> next = new LinkedHashMap<>(reports);

        next.replaceAll((key, old) -> key.getSource() == source
                ? new CachedIncident(old.getIncident(), false, old.getUpdates())
                : old);

        for (Incident incident : snapshot.getIncidents()) {
            CachedIncident old = next.get(incident.getKey());
            List<IncidentUpdate> history = old == null ? List.of() : old.getUpdates();
            next.put(incident.getKey(), new CachedIncident(incident, true, history));
        }
        return next;
    }

    /**
     * IDs of every update already stored, across all sources.
     */
    private Set<String> existingUpdateIds() {
        Set<String> ids = new HashSet<>();
        for (CachedIncident report : reports.values()) {
            for (IncidentUpdate update : report.getUpdates()) {
                ids.add(update.getId());
            }
        }
        return ids;
    }

    /**
     * Throws if the update breaks a commit rule; otherwise records its ID in usedIds.
     */
    private static void validateUpdate(IncidentUpdate update, IncidentSnapshot snapshot,
                                       Map<IncidentKey, CachedIncident> nextReports,
                                       Set<String> usedIds) throws DataAccessException {
        Objects.requireNonNull(update, "Update must not be null.");
        IncidentKey key = update.getIncidentKey();

        if (key.getSource() != snapshot.getSource()) {
            throw new DataAccessException("Update " + update.getId() + " is from the wrong source.");
        }
        if (!nextReports.containsKey(key)) {
            throw new DataAccessException("Update " + update.getId() + " refers to an unknown report.");
        }
        if (!usedIds.add(update.getId())) {
            throw new DataAccessException("Duplicate update ID: " + update.getId());
        }
        if (!update.getObservedAt().equals(snapshot.getRetrievedAt())) {
            throw new DataAccessException(
                    "Update " + update.getId() + " was not observed at the snapshot's retrieval time.");
        }
    }

    private void ensureLoaded() throws DataAccessException {
        if (loaded) {
            return;
        }

        Properties properties = readProperties();
        if (properties == null) {
            snapshots = new EnumMap<>(IncidentSource.class);
            reports = new LinkedHashMap<>();
        }
        else {
            decode(properties);
        }
        loaded = true;
    }

    /**
     * Returns null if the file doesn't exist yet (nothing has been committed).
     */
    private Properties readProperties() throws DataAccessException {
        try {
            if (Files.notExists(file)) {
                return null;
            }

            Properties properties = new Properties();
            try (InputStream input = Files.newInputStream(file)) {
                properties.load(input);
            }
            return properties;
        }
        catch (IOException | IllegalArgumentException | SecurityException exception) {
            throw new DataAccessException(
                    "Could not read " + file + ". Check its permissions and contents, then reload.",
                    exception);
        }
    }

    private void writeProperties(Properties properties) throws DataAccessException {
        Path temporary = null;

        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "citywatch-incidents-", ".tmp");

            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "CityWatch incident cache");
            }

            Files.move(temporary, file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException | SecurityException exception) {
            throw new DataAccessException(
                    "Could not safely save " + file + ". Check folder permissions, free disk space, and "
                            + "support for atomic file replacement, then retry.",
                    exception);
        }
        finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                }
                catch (IOException | SecurityException ignored) {
                    // Cleanup must not hide the original result.
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Encoding: objects -> Properties
    //
    // File layout:
    //   cache.version=1
    //   reports.count=N
    //   reports.<i>.source / .id / .listed / .title / ...      one per report ever seen
    //   reports.<i>.updates.<j>.id / .kind / .observedAt / ... its history, in order
    //   snapshot.<SOURCE>.retrievedAt=...                        missing = never refreshed
    //   snapshot.<SOURCE>.count=M                                0 = valid empty snapshot
    //   snapshot.<SOURCE>.<k>=<record id>                        listed reports, in order
    // ------------------------------------------------------------------

    /**
     * Converts the whole cache into Properties for saving. Writes the format version, every report ever seen,
     * and each source's latest snapshot as an order list of record IDs.
     */
    private static Properties encode(Map<IncidentSource, IncidentSnapshot> snapshots,
                                     Map<IncidentKey, CachedIncident> reports) {
        Properties properties = new Properties();
        properties.setProperty("cache.version", FORMAT_VERSION);

        properties.setProperty("reports.count", Integer.toString(reports.size()));
        int index = 0;
        for (CachedIncident report : reports.values()) {
            encodeReport(properties, "reports." + index++ + ".", report);
        }

        // A snapshot's reports are exactly the listed reports above, so the
        // snapshot only stores their record IDs, in snapshot order.
        for (IncidentSnapshot snapshot : snapshots.values()) {
            String base = "snapshot." + snapshot.getSource().name() + ".";
            List<Incident> incidents = snapshot.getIncidents();

            properties.setProperty(base + "retrievedAt", snapshot.getRetrievedAt().toString());
            properties.setProperty(base + "count", Integer.toString(incidents.size()));
            for (int i = 0; i < incidents.size(); i++) {
                properties.setProperty(base + i, incidents.get(i).getKey().getSourceRecordId());
            }
        }

        return properties;
    }

    /**
     * Writes one report's details, listed flag and update history under {@code base}.
     * Absent optional fields are left out.
     */
    private static void encodeReport(Properties properties, String base, CachedIncident report) {
        Incident incident = report.getIncident();

        properties.setProperty(base + "source", incident.getKey().getSource().name());
        properties.setProperty(base + "id", incident.getKey().getSourceRecordId());
        properties.setProperty(base + "listed", Boolean.toString(report.getListed()));
        properties.setProperty(base + "title", incident.getTitle());

        putOptional(properties, base + "description", incident.getDescription());
        putOptional(properties, base + "sourceUrl", incident.getSourceUrl());
        putOptional(properties, base + "category", incident.getCategory());
        putOptional(properties, base + "street", incident.getStreet());
        putOptional(properties, base + "severity", incident.getSeverity());
        putOptional(properties, base + "status", incident.getStatus());
        putOptional(properties, base + "reportedAt", incident.getReportedAt().map(Instant::toString));
        putOptional(properties, base + "sourceUpdatedAt", incident.getSourceUpdatedAt().map(Instant::toString));

        incident.getCoordinates().ifPresent(coordinates -> {
            properties.setProperty(base + "latitude", Double.toString(coordinates.getLatitude()));
            properties.setProperty(base + "longitude", Double.toString(coordinates.getLongitude()));
        });

        List<EffectivePeriod> periods = incident.getEffectivePeriods();
        properties.setProperty(base + "periods.count", Integer.toString(periods.size()));
        for (int i = 0; i < periods.size(); i++) {
            String periodBase = base + "periods." + i + ".";
            putOptional(properties, periodBase + "start", periods.get(i).getStart().map(Instant::toString));
            putOptional(properties, periodBase + "end", periods.get(i).getEnd().map(Instant::toString));
        }

        List<TransitScope> scopes = incident.getTransitScopes();
        properties.setProperty(base + "scopes.count", Integer.toString(scopes.size()));
        for (int i = 0; i < scopes.size(); i++) {
            String scopeBase = base + "scopes." + i + ".";
            putOptional(properties, scopeBase + "route", scopes.get(i).getRouteId());
            putOptional(properties, scopeBase + "stop", scopes.get(i).getStopId());
        }

        List<IncidentUpdate> updates = report.getUpdates();
        properties.setProperty(base + "updates.count", Integer.toString(updates.size()));
        for (int i = 0; i < updates.size(); i++) {
            encodeUpdate(properties, base + "updates." + i + ".", updates.get(i));
        }
    }

    /**
     * Writes one update's ID, Kind, observation time and changed fields under {@code base}.
     * Field names are sorted so the same data always produces the same file.
     */
    private static void encodeUpdate(Properties properties, String base, IncidentUpdate update) {
        properties.setProperty(base + "id", update.getId());
        properties.setProperty(base + "kind", update.getKind().name());
        properties.setProperty(base + "observedAt", update.getObservedAt().toString());

        // Sort field names so the file is the same every time for the same data.
        List<String> fields = new ArrayList<>(update.getChangedFields().keySet());
        fields.sort(null);

        properties.setProperty(base + "changes.count", Integer.toString(fields.size()));
        for (int i = 0; i < fields.size(); i++) {
            String changeBase = base + "changes." + i + ".";
            FieldChange change = update.getChangedFields().get(fields.get(i));

            properties.setProperty(changeBase + "field", fields.get(i));
            putOptional(properties, changeBase + "previous", change.getPreviousValue());
            putOptional(properties, changeBase + "new", change.getNewValue());
        }
    }

    // ------------------------------------------------------------------
    // Decoding: Properties -> objects
    // ------------------------------------------------------------------

    /**
     * Rebuilds the cache from the loaded Properties and stores the result in {@code snapshots} and {@code reports}.
     * @throws DataAccessException if the version is unsupported or the data is missing, malformed, or inconsistent.
     */
    private void decode(Properties properties) throws DataAccessException {
        try {
            String version = properties.getProperty("cache.version");
            if (!FORMAT_VERSION.equals(version)) {
                throw new IllegalArgumentException("Unsupported incident cache version: " + version);
            }

            Map<IncidentKey, CachedIncident> loadedReports = new LinkedHashMap<>();
            Set<String> updateIds = new HashSet<>();
            int count = readCount(properties, "reports.");

            for (int i = 0; i < count; i++) {
                CachedIncident report = decodeReport(properties, "reports." + i + ".");

                if (loadedReports.put(report.getIncident().getKey(), report) != null) {
                    throw new IllegalArgumentException("Duplicate report: " + report.getIncident().getKey());
                }
                for (IncidentUpdate update : report.getUpdates()) {
                    if (!updateIds.add(update.getId())) {
                        throw new IllegalArgumentException("Duplicate update ID: " + update.getId());
                    }
                }
            }

            Map<IncidentSource, IncidentSnapshot> loadedSnapshots = new EnumMap<>(IncidentSource.class);
            for (IncidentSource source : IncidentSource.values()) {
                String base = "snapshot." + source.name() + ".";
                String retrievedAt = properties.getProperty(base + "retrievedAt");
                if (retrievedAt == null) {
                    continue; // This source has never been refreshed.
                }

                List<Incident> incidents = new ArrayList<>();
                int size = readCount(properties, base);
                for (int i = 0; i < size; i++) {
                    IncidentKey key = new IncidentKey(source, required(properties, base + i));
                    CachedIncident report = loadedReports.get(key);
                    if (report == null || !report.getListed()) {
                        throw new IllegalArgumentException("Snapshot refers to a missing or unlisted report: " + key);
                    }
                    incidents.add(report.getIncident());
                }

                loadedSnapshots.put(source, new IncidentSnapshot(source, Instant.parse(retrievedAt), incidents));
            }

            snapshots = loadedSnapshots;
            reports = loadedReports;
        }
        catch (IllegalArgumentException | DateTimeException exception) {
            throw new DataAccessException(
                    "Incident cache data in " + file + " is invalid or unsupported. "
                            + "Correct the file or delete it to start fresh, then reload.",
                    exception);
        }
    }

    /**
     * Reads one report stored under {@code base} back into a {@link CachedIncident}, including its details,
     * listed flag and update history.
     * @throws IllegalArgumentException if a required value is missing or invalid.
     */
    private static CachedIncident decodeReport(Properties properties, String base) {
        IncidentKey key = new IncidentKey(
                IncidentSource.valueOf(required(properties, base + "source")),
                required(properties, base + "id"));

        Incident.Builder builder = Incident.builder(key, required(properties, base + "title"));

        optional(properties, base + "description").ifPresent(builder::description);
        optional(properties, base + "sourceUrl").ifPresent(builder::sourceUrl);
        optional(properties, base + "category").ifPresent(builder::category);
        optional(properties, base + "street").ifPresent(builder::street);
        optional(properties, base + "severity").ifPresent(builder::severity);
        optional(properties, base + "status").ifPresent(builder::status);
        optionalInstant(properties, base + "reportedAt").ifPresent(builder::reportedAt);
        optionalInstant(properties, base + "sourceUpdatedAt").ifPresent(builder::sourceUpdatedAt);

        Optional<String> latitude = optional(properties, base + "latitude");
        Optional<String> longitude = optional(properties, base + "longitude");
        if (latitude.isPresent() != longitude.isPresent()) {
            throw new IllegalArgumentException("Incomplete coordinates: " + base);
        }
        if (latitude.isPresent()) {
            builder.coordinates(new Coordinates(
                    Double.parseDouble(latitude.get()),
                    Double.parseDouble(longitude.get())));
        }

        List<EffectivePeriod> periods = new ArrayList<>();
        int periodCount = readCount(properties, base + "periods.");
        for (int i = 0; i < periodCount; i++) {
            String periodBase = base + "periods." + i + ".";
            periods.add(new EffectivePeriod(
                    optionalInstant(properties, periodBase + "start"),
                    optionalInstant(properties, periodBase + "end")));
        }
        builder.effectivePeriods(periods);

        List<TransitScope> scopes = new ArrayList<>();
        int scopeCount = readCount(properties, base + "scopes.");
        for (int i = 0; i < scopeCount; i++) {
            String scopeBase = base + "scopes." + i + ".";
            scopes.add(new TransitScope(
                    optional(properties, scopeBase + "route"),
                    optional(properties, scopeBase + "stop")));
        }
        builder.transitScopes(scopes);

        List<IncidentUpdate> updates = new ArrayList<>();
        int updateCount = readCount(properties, base + "updates.");
        for (int i = 0; i < updateCount; i++) {
            updates.add(decodeUpdate(properties, base + "updates." + i + ".", key));
        }

        // CachedIncident checks that every update belongs to this report and IDs are unique.
        return new CachedIncident(builder.build(), readBoolean(properties, base + "listed"), updates);
    }

    /**
     * Reads one update stored under {@code base} for the report with {@code key}.
     * @throws IllegalArgumentException if a required value is missing or invalid.
     */
    private static IncidentUpdate decodeUpdate(Properties properties, String base, IncidentKey key) {
        Map<String, FieldChange> changes = new LinkedHashMap<>();
        int count = readCount(properties, base + "changes.");

        for (int i = 0; i < count; i++) {
            String changeBase = base + "changes." + i + ".";
            String field = required(properties, changeBase + "field");
            FieldChange change = new FieldChange(
                    optional(properties, changeBase + "previous"),
                    optional(properties, changeBase + "new"));

            if (changes.put(field, change) != null) {
                throw new IllegalArgumentException("Duplicate changed field: " + field);
            }
        }

        return new IncidentUpdate(
                required(properties, base + "id"),
                key,
                Instant.parse(required(properties, base + "observedAt")),
                IncidentUpdate.Kind.valueOf(required(properties, base + "kind")),
                changes);
    }

    //Helper Methods

    /**
     * Writes the key only when the value is present.
     */
    private static void putOptional(Properties properties, String key, Optional<String> value) {
        value.ifPresent(text -> properties.setProperty(key, text));
    }

    /**
     * A missing key means the value was absent.
     */
    private static Optional<String> optional(Properties properties, String key) {
        return Optional.ofNullable(properties.getProperty(key));
    }

    private static Optional<Instant> optionalInstant(Properties properties, String key) {
        return optional(properties, key).map(Instant::parse);
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing property: " + key);
        }
        return value;
    }

    private static int readCount(Properties properties, String base) {
        int count = Integer.parseInt(required(properties, base + "count"));
        if (count < 0) {
            throw new IllegalArgumentException("Negative count: " + base);
        }
        return count;
    }

    private static boolean readBoolean(Properties properties, String key) {
        String value = required(properties, key);
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new IllegalArgumentException("Invalid boolean: " + key);
        }
        return Boolean.parseBoolean(value);
    }
}