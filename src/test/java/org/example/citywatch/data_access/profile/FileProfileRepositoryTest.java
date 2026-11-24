package org.example.citywatch.data_access.profile;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class FileProfileRepositoryTest {
    @TempDir
    Path directory;

    @Test
    void missingFileMeansNoSavedProfile() throws Exception {
        FileProfileRepository repository =
                new FileProfileRepository(directory.resolve("profile.properties"));

        assertTrue(repository.load().isEmpty());
    }

    @Test
    void reloadsWholeProfileAndPreservesOtherDataDuringLocationChanges()
            throws Exception {
        Path file = directory.resolve("profile.properties");
        FileProfileRepository repository = new FileProfileRepository(file);
        UserProfile original = sampleProfile();

        repository.save(original);

        UserProfile loaded =
                new FileProfileRepository(file).load().orElseThrow();
        assertProfileEquals(original, loaded);

        UserProfile edited = loaded.withSavedLocations(List.of(
                new SavedLocation(
                        "home",
                        "University",
                        new Coordinates(43.67, -79.40),
                        750)));

        repository.save(edited);
        assertProfileEquals(
                edited, new FileProfileRepository(file).load().orElseThrow());

        UserProfile deleted = edited.withSavedLocations(List.of());
        repository.save(deleted);

        assertProfileEquals(
                deleted, new FileProfileRepository(file).load().orElseThrow());
    }

    @Test
    void readsAndUpgradesTheOldLocationOnlyFile() throws Exception {
        Path file = directory.resolve("profile.properties");
        Properties legacy = new Properties();

        legacy.setProperty("savedLocations.count", "1");
        legacy.setProperty("savedLocations.0.id", "home");
        legacy.setProperty("savedLocations.0.label", "Home");
        legacy.setProperty("savedLocations.0.latitude", "43.66");
        legacy.setProperty("savedLocations.0.longitude", "-79.39");
        legacy.setProperty("savedLocations.0.radiusMetres", "500");
        legacy.setProperty("custom.note", "keep-me");

        try (OutputStream output = Files.newOutputStream(file)) {
            legacy.store(output, "Old CityWatch profile");
        }

        FileProfileRepository repository = new FileProfileRepository(file);
        UserProfile expected = UserProfile.empty("local")
                .withSavedLocations(List.of(
                        new SavedLocation(
                                "home", "Home",
                                new Coordinates(43.66, -79.39), 500)));

        UserProfile loaded = repository.load().orElseThrow();
        assertProfileEquals(expected, loaded);

        repository.save(loaded);

        assertProfileEquals(
                expected, new FileProfileRepository(file).load().orElseThrow());

        Properties upgraded = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            upgraded.load(input);
        }

        assertEquals("2", upgraded.getProperty("profile.version"));
        assertEquals("keep-me", upgraded.getProperty("custom.note"));
    }

    @Test
    void refusesToOverwriteMalformedProfileData() throws Exception {
        Path file = directory.resolve("profile.properties");
        Files.writeString(file, "savedLocations.count=invalid\n");
        byte[] original = Files.readAllBytes(file);
        FileProfileRepository repository = new FileProfileRepository(file);

        assertThrows(DataAccessException.class, repository::load);
        assertThrows(DataAccessException.class,
                () -> repository.save(UserProfile.empty("local")));

        assertArrayEquals(original, Files.readAllBytes(file));
    }

    @Test
    void reportsStorageFailureWithoutChangingAnExistingBlockingFile()
            throws Exception {
        Path blockingFile = directory.resolve("not-a-directory");
        Files.writeString(blockingFile, "existing contents");

        FileProfileRepository repository = new FileProfileRepository(
                blockingFile.resolve("profile.properties"));

        assertThrows(DataAccessException.class,
                () -> repository.save(UserProfile.empty("local")));

        assertEquals("existing contents", Files.readString(blockingFile));
    }

    @Test
    void roundTripsDefaultProfileWithEmptySections() throws Exception {
        Path file = directory.resolve("profile.properties");
        UserProfile expected = UserProfile.empty("local");

        new FileProfileRepository(file).save(expected);

        UserProfile reopened =
                new FileProfileRepository(file).load().orElseThrow();

        assertProfileEquals(expected, reopened);
    }

    @Test
    void preservesSourceKeysAndReviewMarkersAcrossReload() throws Exception {
        Path file = directory.resolve("profile.properties");

        IncidentKey roadKey = new IncidentKey(
                IncidentSource.TORONTO_ROAD, "same-provider-id");
        IncidentKey ttcKey = new IncidentKey(
                IncidentSource.TTC, "same-provider-id");

        Instant savedAt = Instant.parse("2026-11-17T12:00:00Z");

        Bookmark roadBookmark = new Bookmark(
                roadKey,
                savedAt,
                Optional.of("road-update-1"));

        Bookmark ttcBookmark = new Bookmark(
                ttcKey,
                savedAt,
                Optional.empty());

        UserProfile expected = sampleProfile().withBookmarks(Map.of(
                roadKey, roadBookmark,
                ttcKey, ttcBookmark));

        new FileProfileRepository(file).save(expected);

        UserProfile reopened =
                new FileProfileRepository(file).load().orElseThrow();

        assertProfileEquals(expected, reopened);
    }

    private static UserProfile sampleProfile() {
        UserPreferences preferences = new UserPreferences(
                Set.of("construction", "event"),
                Optional.of(Duration.ofHours(12)),
                false,
                new RankingWeights(0.4, 0.3, 0.2, 0.1));

        TransitSubscription subscription = new TransitSubscription(
                TransitSubscription.Type.STATION,
                "station-1",
                "Union",
                Set.of("stop-a", "stop-b"));

        IncidentKey key = new IncidentKey(
                IncidentSource.TORONTO_ROAD, "report-1");

        Bookmark bookmark = new Bookmark(
                key,
                Instant.parse("2026-11-17T12:00:00Z"),
                Optional.of("update-1"));

        return new UserProfile(
                "profile-1",
                List.of(new SavedLocation(
                        "home", "Home", new Coordinates(43.66, -79.39), 500)),
                preferences,
                Set.of(subscription),
                Map.of(key, bookmark));
    }

    private static void assertProfileEquals(
            UserProfile expected, UserProfile actual) {
        assertEquals(expected.getId(), actual.getId());

        assertEquals(expected.getSavedLocations().size(),
                actual.getSavedLocations().size());

        for (int i = 0; i < expected.getSavedLocations().size(); i++) {
            SavedLocation first = expected.getSavedLocations().get(i);
            SavedLocation second = actual.getSavedLocations().get(i);

            assertEquals(first.getId(), second.getId());
            assertEquals(first.getLabel(), second.getLabel());
            assertEquals(first.getCoordinates().getLatitude(),
                    second.getCoordinates().getLatitude(), 0.000001);
            assertEquals(first.getCoordinates().getLongitude(),
                    second.getCoordinates().getLongitude(), 0.000001);
            assertEquals(first.getRadiusMetres(),
                    second.getRadiusMetres(), 0.000001);
        }

        UserPreferences first = expected.getPreferences();
        UserPreferences second = actual.getPreferences();

        assertEquals(first.getCategories(), second.getCategories());
        assertEquals(first.getMaximumAge(), second.getMaximumAge());
        assertEquals(first.getIncludeUnknownTimes(), second.getIncludeUnknownTimes());

        RankingWeights firstWeights = first.getRankingWeights();
        RankingWeights secondWeights = second.getRankingWeights();

        assertEquals(firstWeights.getProximity(),
                secondWeights.getProximity(), 0.000001);
        assertEquals(firstWeights.getRecency(),
                secondWeights.getRecency(), 0.000001);
        assertEquals(firstWeights.getCategory(),
                secondWeights.getCategory(), 0.000001);
        assertEquals(firstWeights.getSeverity(),
                secondWeights.getSeverity(), 0.000001);

        assertEquals(expected.getTransitSubscriptions(),
                actual.getTransitSubscriptions());

        for (TransitSubscription subscription : expected.getTransitSubscriptions()) {
            TransitSubscription loaded = actual.getTransitSubscriptions().stream()
                    .filter(subscription::equals)
                    .findFirst()
                    .orElseThrow();

            assertEquals(subscription.getDisplayName(), loaded.getDisplayName());
            assertEquals(subscription.getAssociatedStopIds(),
                    loaded.getAssociatedStopIds());
        }

        assertEquals(expected.getBookmarks().keySet(),
                actual.getBookmarks().keySet());

        for (IncidentKey key : expected.getBookmarks().keySet()) {
            Bookmark expectedBookmark = expected.getBookmarks().get(key);
            Bookmark actualBookmark = actual.getBookmarks().get(key);

            assertEquals(expectedBookmark.getIncidentKey(),
                    actualBookmark.getIncidentKey());
            assertEquals(expectedBookmark.getSavedAt(),
                    actualBookmark.getSavedAt());
            assertEquals(expectedBookmark.getLastReviewedUpdateId(),
                    actualBookmark.getLastReviewedUpdateId());
        }
    }
}