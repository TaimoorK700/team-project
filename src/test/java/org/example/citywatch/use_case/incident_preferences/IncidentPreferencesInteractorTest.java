package org.example.citywatch.use_case.incident_preferences;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.example.citywatch.data_access.profile.FileProfileRepository;
import org.example.citywatch.entity.Bookmark;
import org.example.citywatch.entity.Coordinates;
import org.example.citywatch.entity.IncidentKey;
import org.example.citywatch.entity.IncidentSource;
import org.example.citywatch.entity.SavedLocation;
import org.example.citywatch.entity.TransitSubscription;
import org.example.citywatch.entity.UserPreferences;
import org.example.citywatch.entity.UserProfile;
import org.example.citywatch.test_support.InMemoryProfileRepository;
import org.example.citywatch.use_case.gateway.DataAccessException;
import org.example.citywatch.use_case.gateway.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

class IncidentPreferencesInteractorTest {
    private InMemoryProfileRepository repository;
    private RecordingOutput output;
    private IncidentPreferencesInteractor interactor;

    @BeforeEach
    void setUp() {
        repository = new InMemoryProfileRepository();
        output = new RecordingOutput();
        interactor = new IncidentPreferencesInteractor(repository, output);
    }

    @Test
    void loadsSharedDefaultsWithoutSavingAProfile() {
        interactor.load();

        assertNull(output.error);
        assertNotNull(output.data);
        assertFalse(output.data.saved);
        assertTrue(repository.load().isEmpty());

        assertTrue(output.data.categories.isEmpty());
        assertTrue(output.data.maximumAge.isEmpty());
        assertTrue(output.data.includeUnknownTimes);
        assertEquals(0.5, output.data.proximityWeight, 0.000001);
        assertEquals(0.3, output.data.recencyWeight, 0.000001);
        assertEquals(0.15, output.data.categoryWeight, 0.000001);
        assertEquals(0.05, output.data.severityWeight, 0.000001);

        assertMatches(UserPreferences.defaults(), output.data);
    }

    @Test
    void savesChangedPreferencesAndLoadsThemThroughANewInteractor() {
        interactor.save(validInput());

        assertNull(output.error);
        assertTrue(output.data.saved);

        UserPreferences saved = repository.load().orElseThrow()
                .getPreferences();

        assertEquals(Set.of("CONSTRUCTION", "EVENT"), saved.getCategories());
        assertEquals(Optional.of(Duration.ofHours(24)),
                saved.getMaximumAge());
        assertFalse(saved.getIncludeUnknownTimes());
        assertEquals(0.6,
                saved.getRankingWeights().getProximity(), 0.000001);
        assertEquals(0.2,
                saved.getRankingWeights().getRecency(), 0.000001);
        assertEquals(0.15,
                saved.getRankingWeights().getCategory(), 0.000001);
        assertEquals(0.05,
                saved.getRankingWeights().getSeverity(), 0.000001);

        RecordingOutput reopened = new RecordingOutput();
        new IncidentPreferencesInteractor(repository, reopened).load();

        assertNull(reopened.error);
        assertFalse(reopened.data.saved);
        assertMatches(saved, reopened.data);
    }

    @Test
    void emptyCategoriesMeanAllCategoriesAndNoAgeLimitIsSupported() {
        interactor.save(new IncidentPreferencesInputData(
                "   ",
                Optional.empty(),
                true,
                "1", "0", "0", "0"));

        assertNull(output.error);

        UserPreferences saved = repository.load().orElseThrow()
                .getPreferences();

        assertTrue(saved.getCategories().isEmpty());
        assertTrue(saved.acceptsCategory("ANY_CATEGORY"));
        assertTrue(saved.acceptsCategory(null));
        assertTrue(saved.getMaximumAge().isEmpty());
        assertTrue(saved.getIncludeUnknownTimes());
    }

    @Test
    void categoryIdentifiersKeepTheirExactCase() {
        interactor.save(new IncidentPreferencesInputData(
                "construction, CONSTRUCTION",
                Optional.empty(),
                true,
                "1", "0", "0", "0"));

        assertNull(output.error);
        assertEquals(
                Set.of("construction", "CONSTRUCTION"),
                repository.load().orElseThrow()
                        .getPreferences().getCategories());
    }

    @Test
    void savingPreferencesPreservesTheOtherProfileSections()
            throws Exception {
        SavedLocation location = new SavedLocation(
                "home",
                "Home",
                new Coordinates(43.66, -79.39),
                500);

        TransitSubscription subscription = new TransitSubscription(
                TransitSubscription.Type.ROUTE,
                "501",
                "Queen",
                Set.of());

        IncidentKey key = new IncidentKey(
                IncidentSource.TORONTO_ROAD, "report-1");

        Bookmark bookmark = new Bookmark(
                key,
                Instant.parse("2026-11-17T12:00:00Z"),
                Optional.of("reviewed-update-1"));

        UserProfile original = UserProfile.empty("existing-profile")
                .withSavedLocations(List.of(location))
                .withTransitSubscriptions(Set.of(subscription))
                .withBookmarks(Map.of(key, bookmark));

        repository.save(original);
        interactor.save(validInput());

        assertNull(output.error);

        UserProfile updated = repository.load().orElseThrow();
        assertEquals(original.getId(), updated.getId());
        assertEquals(original.getSavedLocations(),
                updated.getSavedLocations());
        assertEquals(original.getTransitSubscriptions(),
                updated.getTransitSubscriptions());
        assertEquals(original.getBookmarks(), updated.getBookmarks());
        assertEquals(Optional.of("reviewed-update-1"),
                updated.getBookmarks().get(key).getLastReviewedUpdateId());
    }

    @ParameterizedTest(name = "rejects invalid input {index}")
    @MethodSource("invalidInputs")
    void invalidInputDoesNotChangeTheStoredProfile(
            IncidentPreferencesInputData input) throws Exception {
        UserProfile original = UserProfile.empty("existing-profile");
        repository.save(original);

        interactor.save(input);

        assertNotNull(output.error);
        assertNull(output.data);
        assertSame(original, repository.load().orElseThrow());
    }

    static Stream<IncidentPreferencesInputData> invalidInputs() {
        Stream.Builder<IncidentPreferencesInputData> inputs =
                Stream.builder();

        for (String invalid : List.of(
                "", "abc", "-1", "NaN", "Infinity", "-Infinity")) {
            for (int index = 0; index < 4; index++) {
                String[] weights = {"1", "1", "1", "1"};
                weights[index] = invalid;

                inputs.add(new IncidentPreferencesInputData(
                        "CONSTRUCTION",
                        Optional.empty(),
                        true,
                        weights[0], weights[1], weights[2], weights[3]));
            }
        }

        inputs.add(new IncidentPreferencesInputData(
                "", Optional.empty(), true,
                "0", "0", "0", "0"));

        inputs.add(new IncidentPreferencesInputData(
                "", Optional.empty(), true,
                "1e308", "1e308", "1e308", "1e308"));

        inputs.add(new IncidentPreferencesInputData(
                "CONSTRUCTION,,EVENT", Optional.empty(), true,
                "1", "1", "1", "1"));

        inputs.add(new IncidentPreferencesInputData(
                "CONSTRUCTION,", Optional.empty(), true,
                "1", "1", "1", "1"));

        inputs.add(new IncidentPreferencesInputData(
                "", Optional.of(Duration.ZERO), true,
                "1", "1", "1", "1"));

        inputs.add(new IncidentPreferencesInputData(
                "", Optional.of(Duration.ofHours(-1)), true,
                "1", "1", "1", "1"));

        inputs.add(new IncidentPreferencesInputData(
                "", null, true,
                "1", "1", "1", "1"));

        inputs.add(new IncidentPreferencesInputData(
                null, Optional.empty(), true,
                "1", "1", "1", "1"));

        inputs.add(new IncidentPreferencesInputData(
                "", Optional.empty(), true,
                null, "1", "1", "1"));

        inputs.add(null);

        return inputs.build();
    }

    @Test
    void failedSaveDoesNotReportSuccessOrChangeStoredPreferences()
            throws Exception {
        UserProfile original = UserProfile.empty("existing-profile");
        repository.save(original);
        repository.setFailOnSave(true);

        interactor.save(validInput());

        assertNotNull(output.error);
        assertNull(output.data);
        assertSame(original, repository.load().orElseThrow());
    }

    @Test
    void failedLoadDoesNotFallBackToDefaultsOrOverwriteStorage() {
        ProfileRepository unreadable = new ProfileRepository() {
            @Override
            public Optional<UserProfile> load() throws DataAccessException {
                throw new DataAccessException("Profile cannot be read.");
            }

            @Override
            public void save(UserProfile profile) {
                fail("Unreadable profile storage must not be overwritten.");
            }
        };

        IncidentPreferencesInteractor failing =
                new IncidentPreferencesInteractor(unreadable, output);

        failing.load();

        assertEquals("Profile cannot be read.", output.error);
        assertNull(output.data);

        failing.save(validInput());

        assertEquals("Profile cannot be read.", output.error);
        assertNull(output.data);
    }

    @Test
    void preferencesSurviveReopeningFileStorage(@TempDir Path directory) {
        Path file = directory.resolve("profile.properties");

        RecordingOutput first = new RecordingOutput();
        new IncidentPreferencesInteractor(
                new FileProfileRepository(file), first)
                .save(validInput());

        assertNull(first.error);
        assertNotNull(first.data);
        assertTrue(first.data.saved);

        RecordingOutput reopened = new RecordingOutput();
        new IncidentPreferencesInteractor(
                new FileProfileRepository(file), reopened)
                .load();

        assertNull(reopened.error);
        assertNotNull(reopened.data);
        assertFalse(reopened.data.saved);
        assertEquals(first.data.categories, reopened.data.categories);
        assertEquals(first.data.maximumAge, reopened.data.maximumAge);
        assertEquals(first.data.includeUnknownTimes,
                reopened.data.includeUnknownTimes);
        assertEquals(first.data.proximityWeight,
                reopened.data.proximityWeight, 0.000001);
        assertEquals(first.data.recencyWeight,
                reopened.data.recencyWeight, 0.000001);
        assertEquals(first.data.categoryWeight,
                reopened.data.categoryWeight, 0.000001);
        assertEquals(first.data.severityWeight,
                reopened.data.severityWeight, 0.000001);
    }

    private static IncidentPreferencesInputData validInput() {
        return new IncidentPreferencesInputData(
                " CONSTRUCTION, EVENT, CONSTRUCTION ",
                Optional.of(Duration.ofHours(24)),
                false,
                "0.6", "0.2", "0.15", "0.05");
    }

    private static void assertMatches(
            UserPreferences expected,
            IncidentPreferencesOutputData actual) {
        assertNotNull(actual);
        assertEquals(expected.getCategories(), actual.categories);
        assertEquals(expected.getMaximumAge(), actual.maximumAge);
        assertEquals(expected.getIncludeUnknownTimes(),
                actual.includeUnknownTimes);
        assertEquals(expected.getRankingWeights().getProximity(),
                actual.proximityWeight, 0.000001);
        assertEquals(expected.getRankingWeights().getRecency(),
                actual.recencyWeight, 0.000001);
        assertEquals(expected.getRankingWeights().getCategory(),
                actual.categoryWeight, 0.000001);
        assertEquals(expected.getRankingWeights().getSeverity(),
                actual.severityWeight, 0.000001);
    }

    private static final class RecordingOutput
            implements IncidentPreferencesOutputBoundary {
        private IncidentPreferencesOutputData data;
        private String error;

        @Override
        public void presentPreferences(IncidentPreferencesOutputData output) {
            data = output;
            error = null;
        }

        @Override
        public void presentError(String message) {
            error = message;
        }
    }
}