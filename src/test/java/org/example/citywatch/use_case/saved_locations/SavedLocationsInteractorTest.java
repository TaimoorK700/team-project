package org.example.citywatch.use_case.saved_locations;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.example.citywatch.entity.Bookmark;
import org.example.citywatch.entity.IncidentKey;
import org.example.citywatch.entity.IncidentSource;
import org.example.citywatch.entity.SavedLocation;
import org.example.citywatch.entity.TransitSubscription;
import org.example.citywatch.entity.UserProfile;
import org.example.citywatch.test_support.InMemoryProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class SavedLocationsInteractorTest {
    private InMemoryProfileRepository repository;
    private RecordingOutput output;
    private SavedLocationsInteractor interactor;

    @BeforeEach
    void setUp() {
        repository = new InMemoryProfileRepository();
        output = new RecordingOutput();
        interactor = new SavedLocationsInteractor(repository, output);
    }

    @Test
    void loadsAnEmptyList() {
        interactor.load();

        assertNotNull(output.data);
        assertTrue(output.data.locations.isEmpty());
        assertNull(output.error);
    }

    @Test
    void addsAValidLocation() {
        interactor.add(input(" Home ", "43.66", "-79.39", "500"));

        SavedLocation saved = savedLocations().get(0);
        assertEquals("Home", saved.getLabel());
        assertEquals(43.66, saved.getCoordinates().getLatitude(), 0.000001);
        assertEquals(-79.39, saved.getCoordinates().getLongitude(), 0.000001);
        assertEquals(500, saved.getRadiusMetres(), 0.000001);
        assertFalse(saved.getId().isBlank());
        assertEquals(1, output.data.locations.size());
        assertNull(output.error);
    }

    @Test
    void editsWithoutChangingTheIdOrOtherLocations() {
        interactor.add(input("Home", "43", "-79", "500"));
        String id = savedLocations().get(0).getId();

        interactor.add(input("Work", "44", "-78", "200"));
        SavedLocation work = savedLocations().get(1);

        interactor.edit(id, input("University", "45", "-77", "1000"));

        SavedLocation edited = savedLocations().get(0);
        assertEquals(id, edited.getId());
        assertEquals("University", edited.getLabel());
        assertEquals(45, edited.getCoordinates().getLatitude(), 0.000001);
        assertEquals(-77, edited.getCoordinates().getLongitude(), 0.000001);
        assertEquals(1000, edited.getRadiusMetres(), 0.000001);
        assertSame(work, savedLocations().get(1));
        assertEquals(2, output.data.locations.size());
        assertNull(output.error);
    }

    @Test
    void deletesOnlyTheSelectedLocation() {
        interactor.add(input("Home", "43", "-79", "500"));
        String homeId = savedLocations().get(0).getId();

        interactor.add(input("Work", "44", "-78", "200"));
        String workId = savedLocations().get(1).getId();

        interactor.delete(homeId);

        assertEquals(1, savedLocations().size());
        assertEquals(workId, savedLocations().get(0).getId());
        assertEquals(1, output.data.locations.size());
        assertNull(output.error);
    }

    @ParameterizedTest
    @CsvSource({
            "'',43,-79,500",
            "'   ',43,-79,500",
            "Home,abc,-79,500",
            "Home,'',-79,500",
            "Home,91,-79,500",
            "Home,-91,-79,500",
            "Home,NaN,-79,500",
            "Home,Infinity,-79,500",
            "Home,43,abc,500",
            "Home,43,181,500",
            "Home,43,-181,500",
            "Home,43,NaN,500",
            "Home,43,-Infinity,500",
            "Home,43,-79,abc",
            "Home,43,-79,''",
            "Home,43,-79,0",
            "Home,43,-79,-1",
            "Home,43,-79,NaN",
            "Home,43,-79,Infinity"
    })
    void rejectsInvalidAddsAndEdits(String label, String latitude,
                                    String longitude, String radius) {
        interactor.add(input("Existing", "43", "-79", "100"));
        SavedLocation existing = savedLocations().get(0);
        SavedLocationsInputData invalid =
                input(label, latitude, longitude, radius);

        interactor.add(invalid);

        assertNotNull(output.error);
        assertEquals(1, savedLocations().size());
        assertSame(existing, savedLocations().get(0));

        interactor.edit(existing.getId(), invalid);

        assertNotNull(output.error);
        assertSame(existing, savedLocations().get(0));
    }

    @ParameterizedTest
    @CsvSource({"-90,-180", "90,180"})
    void acceptsCoordinateBoundaries(String latitude, String longitude) {
        interactor.add(input("Boundary", latitude, longitude, "0.01"));

        assertEquals(1, savedLocations().size());
        assertNull(output.error);
    }

    @Test
    void reportsMissingLocations() {
        interactor.edit("missing", input("Home", "43", "-79", "500"));
        assertNotNull(output.error);

        interactor.delete("missing");
        assertNotNull(output.error);
        assertTrue(savedLocations().isEmpty());
    }

    @Test
    void failedSavesLeaveStoredLocationsUnchanged() {
        interactor.add(input("Home", "43", "-79", "500"));
        SavedLocation original = savedLocations().get(0);
        repository.setFailOnSave(true);

        interactor.edit(original.getId(), input("Changed", "44", "-78", "100"));
        assertNotNull(output.error);
        assertSame(original, savedLocations().get(0));

        interactor.delete(original.getId());
        assertNotNull(output.error);
        assertSame(original, savedLocations().get(0));

        interactor.add(input("Work", "44", "-78", "200"));
        assertNotNull(output.error);
        assertEquals(1, savedLocations().size());
    }

    @Test
    void locationChangesPreserveOtherProfileData() throws Exception {
        IncidentKey key = new IncidentKey(
                IncidentSource.TORONTO_ROAD, "report-1");

        Bookmark bookmark = new Bookmark(
                key,
                Instant.parse("2026-11-17T12:00:00Z"),
                Optional.of("update-1"));

        TransitSubscription subscription = new TransitSubscription(
                TransitSubscription.Type.ROUTE,
                "501",
                "Queen",
                Set.of());

        UserProfile original = UserProfile.empty("test-profile")
                .withTransitSubscriptions(Set.of(subscription))
                .withBookmarks(Map.of(key, bookmark));

        repository.save(original);

        interactor.add(input("Home", "43.66", "-79.39", "500"));

        UserProfile updated = repository.load().orElseThrow();

        assertNull(output.error);
        assertEquals(original.getId(), updated.getId());
        assertEquals(1, updated.getSavedLocations().size());
        assertSame(original.getPreferences(), updated.getPreferences());
        assertEquals(original.getTransitSubscriptions(),
                updated.getTransitSubscriptions());
        assertEquals(original.getBookmarks(), updated.getBookmarks());
    }

    private List<SavedLocation> savedLocations() {
        return repository.load()
                .map(UserProfile::getSavedLocations)
                .orElseGet(List::of);
    }

    private static SavedLocationsInputData input(
            String label, String latitude, String longitude, String radius) {
        return new SavedLocationsInputData(label, latitude, longitude, radius);
    }

    private static final class RecordingOutput
            implements SavedLocationsOutputBoundary {
        private SavedLocationsOutputData data;
        private String error;

        @Override
        public void presentLocations(SavedLocationsOutputData output) {
            data = output;
            error = null;
        }

        @Override
        public void presentError(String message) {
            error = message;
        }
    }
}