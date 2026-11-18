package org.example.citywatch.data_access.profile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import org.example.citywatch.entity.Bookmark;
import org.example.citywatch.entity.Coordinates;
import org.example.citywatch.entity.IncidentKey;
import org.example.citywatch.entity.IncidentSource;
import org.example.citywatch.entity.RankingWeights;
import org.example.citywatch.entity.SavedLocation;
import org.example.citywatch.entity.TransitSubscription;
import org.example.citywatch.entity.UserPreferences;
import org.example.citywatch.entity.UserProfile;
import org.example.citywatch.use_case.gateway.DataAccessException;
import org.example.citywatch.use_case.gateway.ProfileRepository;

public final class FileProfileRepository implements ProfileRepository {
    private static final String FORMAT_VERSION = "2";
    private static final List<String> MANAGED_PREFIXES = List.of(
            "profile.",
            "savedLocations.",
            "preferences.",
            "transitSubscriptions.",
            "bookmarks.");

    private final Path file;

    public FileProfileRepository(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    @Override
    public synchronized Optional<UserProfile> load() throws DataAccessException {
        Properties properties = readProperties();

        if (properties == null) {
            return Optional.empty();
        }

        return Optional.of(decode(properties));
    }

    @Override
    public synchronized void save(UserProfile profile) throws DataAccessException {
        Objects.requireNonNull(profile, "Profile is required.");

        Properties properties = readProperties();
        if (properties == null) {
            properties = new Properties();
        }
        else {
            // Refuse to overwrite corrupt or unsupported profile data.
            decode(properties);
        }

        // Remove old managed entries, including deleted locations.
        // Properties outside these namespaces are preserved.
        for (String key : properties.stringPropertyNames()) {
            if (isManaged(key)) {
                properties.remove(key);
            }
        }

        encode(profile, properties);
        writeProperties(properties);
    }

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
                    "Could not read " + file
                            + ". Check its permissions and contents, then reload.",
                    exception);
        }
    }

    private void writeProperties(Properties properties) throws DataAccessException {
        Path temporary = null;

        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(
                    file.getParent(), "citywatch-profile-", ".tmp");

            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "CityWatch profile");
            }

            Files.move(temporary, file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException | SecurityException exception) {
            throw new DataAccessException(
                    "Could not safely save " + file
                            + ". Check folder permissions, free disk space, and "
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

    private UserProfile decode(Properties properties) throws DataAccessException {
        try {
            String version = properties.getProperty("profile.version");

            if (version == null) {
                // The original application stored only savedLocations.*.
                for (String key : properties.stringPropertyNames()) {
                    if (isManaged(key) && !key.startsWith("savedLocations.")) {
                        throw new IllegalArgumentException(
                                "Profile format version is missing.");
                    }
                }

                return UserProfile.empty("local")
                        .withSavedLocations(readLocations(properties));
            }

            if (!FORMAT_VERSION.equals(version)) {
                throw new IllegalArgumentException(
                        "Unsupported profile version: " + version);
            }

            return new UserProfile(
                    required(properties, "profile.id"),
                    readLocations(properties),
                    readPreferences(properties),
                    readSubscriptions(properties),
                    readBookmarks(properties));
        }
        catch (IllegalArgumentException | DateTimeException exception) {
            throw new DataAccessException(
                    "Profile data in " + file
                            + " is invalid or unsupported. Correct the file "
                            + "or restore a backup, then reload.",
                    exception);
        }
    }

    private static List<SavedLocation> readLocations(Properties properties) {
        List<SavedLocation> locations = new ArrayList<>();
        int count = readCount(properties, "savedLocations.");

        for (int i = 0; i < count; i++) {
            String base = "savedLocations." + i + ".";

            locations.add(new SavedLocation(
                    required(properties, base + "id"),
                    required(properties, base + "label"),
                    new Coordinates(
                            readNumber(properties, base + "latitude"),
                            readNumber(properties, base + "longitude")),
                    readNumber(properties, base + "radiusMetres")));
        }

        return locations;
    }

    private static UserPreferences readPreferences(Properties properties) {
        String maximumAge = required(properties, "preferences.maximumAge");

        Optional<Duration> age = maximumAge.isEmpty()
                ? Optional.empty()
                : Optional.of(Duration.parse(maximumAge));

        RankingWeights weights = new RankingWeights(
                readNumber(properties, "preferences.weights.proximity"),
                readNumber(properties, "preferences.weights.recency"),
                readNumber(properties, "preferences.weights.category"),
                readNumber(properties, "preferences.weights.severity"));

        return new UserPreferences(
                readStrings(properties, "preferences.categories."),
                age,
                readBoolean(properties, "preferences.includeUnknownTimes"),
                weights);
    }

    private static Set<TransitSubscription> readSubscriptions(Properties properties) {
        Set<TransitSubscription> subscriptions = new LinkedHashSet<>();
        int count = readCount(properties, "transitSubscriptions.");

        for (int i = 0; i < count; i++) {
            String base = "transitSubscriptions." + i + ".";

            TransitSubscription subscription = new TransitSubscription(
                    TransitSubscription.Type.valueOf(
                            required(properties, base + "type")),
                    required(properties, base + "serviceId"),
                    required(properties, base + "displayName"),
                    readStrings(properties, base + "stopIds."));

            if (!subscriptions.add(subscription)) {
                throw new IllegalArgumentException(
                        "Duplicate transit subscription.");
            }
        }

        return subscriptions;
    }

    private static Map<IncidentKey, Bookmark> readBookmarks(Properties properties) {
        Map<IncidentKey, Bookmark> bookmarks = new LinkedHashMap<>();
        int count = readCount(properties, "bookmarks.");

        for (int i = 0; i < count; i++) {
            String base = "bookmarks." + i + ".";

            IncidentKey key = new IncidentKey(
                    IncidentSource.valueOf(required(properties, base + "source")),
                    required(properties, base + "sourceRecordId"));

            String reviewed = required(properties, base + "lastReviewedUpdateId");

            Bookmark bookmark = new Bookmark(
                    key,
                    Instant.parse(required(properties, base + "savedAt")),
                    reviewed.isEmpty() ? Optional.empty() : Optional.of(reviewed));

            if (bookmarks.put(key, bookmark) != null) {
                throw new IllegalArgumentException("Duplicate bookmark.");
            }
        }

        return bookmarks;
    }

    private static void encode(UserProfile profile, Properties properties) {
        properties.setProperty("profile.version", FORMAT_VERSION);
        properties.setProperty("profile.id", profile.getId());

        List<SavedLocation> locations = profile.getSavedLocations();
        properties.setProperty(
                "savedLocations.count", Integer.toString(locations.size()));

        for (int i = 0; i < locations.size(); i++) {
            SavedLocation location = locations.get(i);
            String base = "savedLocations." + i + ".";

            properties.setProperty(base + "id", location.getId());
            properties.setProperty(base + "label", location.getLabel());
            putNumber(properties, base + "latitude",
                    location.getCoordinates().getLatitude());
            putNumber(properties, base + "longitude",
                    location.getCoordinates().getLongitude());
            putNumber(properties, base + "radiusMetres",
                    location.getRadiusMetres());
        }

        UserPreferences preferences = profile.getPreferences();
        writeStrings(properties, "preferences.categories.",
                preferences.getCategories());

        properties.setProperty("preferences.maximumAge",
                preferences.getMaximumAge().map(Duration::toString).orElse(""));
        properties.setProperty("preferences.includeUnknownTimes",
                Boolean.toString(preferences.getIncludeUnknownTimes()));

        RankingWeights weights = preferences.getRankingWeights();
        putNumber(properties, "preferences.weights.proximity", weights.getProximity());
        putNumber(properties, "preferences.weights.recency", weights.getRecency());
        putNumber(properties, "preferences.weights.category", weights.getCategory());
        putNumber(properties, "preferences.weights.severity", weights.getSeverity());

        properties.setProperty("transitSubscriptions.count",
                Integer.toString(profile.getTransitSubscriptions().size()));

        int index = 0;
        for (TransitSubscription subscription : profile.getTransitSubscriptions()) {
            String base = "transitSubscriptions." + index++ + ".";

            properties.setProperty(base + "type", subscription.getType().name());
            properties.setProperty(base + "serviceId", subscription.getServiceId());
            properties.setProperty(base + "displayName", subscription.getDisplayName());
            writeStrings(properties, base + "stopIds.",
                    subscription.getAssociatedStopIds());
        }

        properties.setProperty("bookmarks.count",
                Integer.toString(profile.getBookmarks().size()));

        index = 0;
        for (Bookmark bookmark : profile.getBookmarks().values()) {
            String base = "bookmarks." + index++ + ".";
            IncidentKey key = bookmark.getIncidentKey();

            properties.setProperty(base + "source", key.getSource().name());
            properties.setProperty(base + "sourceRecordId", key.getSourceRecordId());
            properties.setProperty(base + "savedAt", bookmark.getSavedAt().toString());
            properties.setProperty(base + "lastReviewedUpdateId",
                    bookmark.getLastReviewedUpdateId().orElse(""));
        }
    }

    private static Set<String> readStrings(Properties properties, String base) {
        Set<String> values = new LinkedHashSet<>();
        int count = readCount(properties, base);

        for (int i = 0; i < count; i++) {
            if (!values.add(required(properties, base + i))) {
                throw new IllegalArgumentException(
                        "Duplicate value in " + base);
            }
        }

        return values;
    }

    private static void writeStrings(
            Properties properties, String base, Set<String> values) {
        properties.setProperty(base + "count", Integer.toString(values.size()));

        int index = 0;
        for (String value : values) {
            properties.setProperty(base + index++, value);
        }
    }

    private static int readCount(Properties properties, String base) {
        int count = Integer.parseInt(required(properties, base + "count"));
        if (count < 0) {
            throw new IllegalArgumentException("Negative count: " + base);
        }
        return count;
    }

    private static double readNumber(Properties properties, String key) {
        return Double.parseDouble(required(properties, key));
    }

    private static void putNumber(
            Properties properties, String key, double value) {
        properties.setProperty(key, Double.toString(value));
    }

    private static boolean readBoolean(Properties properties, String key) {
        String value = required(properties, key);
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new IllegalArgumentException("Invalid boolean: " + key);
        }
        return Boolean.parseBoolean(value);
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing property: " + key);
        }
        return value;
    }

    private static boolean isManaged(String key) {
        for (String prefix : MANAGED_PREFIXES) {
            if (key.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}