package org.example.citywatch.use_case.bookmarks;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.example.citywatch.entity.Bookmark;
import org.example.citywatch.entity.IncidentKey;
import org.example.citywatch.entity.IncidentSource;
import org.example.citywatch.entity.TransitSubscription;
import org.example.citywatch.entity.UserProfile;
import org.example.citywatch.test_support.InMemoryIncidentCache;
import org.example.citywatch.test_support.InMemoryProfileRepository;
import org.example.citywatch.test_support.SampleReports;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class BookmarksInteractorTest {
    private InMemoryProfileRepository profiles;
    private InMemoryIncidentCache cache;
    private MutableClock clock;
    private RecordingOutput output;
    private BookmarksInteractor interactor;

    /*
    SETUP
     */

    @BeforeEach
    void setUp() throws Exception {
        profiles = new InMemoryProfileRepository();
        cache = new InMemoryIncidentCache();
        cache.commit(SampleReports.roadBefore(), List.of());
        cache.commit(SampleReports.ttc(), List.of());
        clock = new MutableClock(SampleReports.FIRST);
        output = new RecordingOutput();
        interactor = new BookmarksInteractor(profiles, cache, clock, output);
    }

    /*
    TESTS
     */

    /**
     * Tests that adding a road restriction and a TTC alert saves both and
     * shows them in one list, with the newest bookmark shown first.
     */
    @Test
    void addsRoadAndTtcBookmarksNewestFirst() {
        interactor.add(input(IncidentSource.TORONTO_ROAD, "123"));
        clock.advance(Duration.ofMinutes(1));
        interactor.add(input(IncidentSource.TTC, "123"));

        assertNull(output.error);
        assertEquals(BookmarksOutputData.Outcome.ADDED, output.data.outcome);
        assertEquals(Set.of(SampleReports.ROAD_KEY, SampleReports.TTC_KEY),
                savedBookmarks().keySet());

        List<BookmarksOutputData.BookmarkRow> rows = output.data.bookmarks;
        assertEquals(2, rows.size());
        assertEquals(IncidentSource.TTC, rows.get(0).source); //check the TTC one is the newest
        assertEquals("Sample transit disruption", rows.get(0).title);
        assertEquals(IncidentSource.TORONTO_ROAD, rows.get(1).source);
        assertEquals("Sample road restriction", rows.get(1).title);
    }

    /**
     * Tests that reports from different sources(road restriction and TTC)
     * with same record ID are bookmarked as separate entries.
     */
    @Test
    void bookmarksSameRecordIdFromDifferentSourcesIndependently() {
        interactor.add(input(IncidentSource.TORONTO_ROAD, "123"));
        clock.advance(Duration.ofMinutes(1));
        interactor.add(input(IncidentSource.TTC, "123"));

        Map<IncidentKey, Bookmark> saved = savedBookmarks();
        assertEquals(2, saved.size());
        assertEquals(SampleReports.FIRST,
                saved.get(SampleReports.ROAD_KEY).getSavedAt());
        assertEquals(SampleReports.FIRST.plus(Duration.ofMinutes(1)),
                saved.get(SampleReports.TTC_KEY).getSavedAt());
    }

    /**
     * Tests that bookmarking the same report twice
     * keeps a single one with original saved time.
     */
    @Test
    void addingTwiceKeepsOneBookmarkAndOriginalSavedAt() {
        interactor.add(input(IncidentSource.TORONTO_ROAD, "123"));
        clock.advance(Duration.ofMinutes(5));
        interactor.add(input(IncidentSource.TORONTO_ROAD, "123"));

        Map<IncidentKey, Bookmark> saved = savedBookmarks();
        assertEquals(1, saved.size());
        assertEquals(SampleReports.FIRST,
                saved.get(SampleReports.ROAD_KEY).getSavedAt());

        assertNull(output.error);
        assertEquals(BookmarksOutputData.Outcome.ALREADY_BOOKMARKED, output.data.outcome);
        assertEquals(1, output.data.bookmarks.size());
    }

    /**
     * Tests that removing a bookmark removes it in
     * both the profile repository and the prsented list.
     */
    @Test
    void removesOnlyTheSelectedBookmark() {
        interactor.add(input(IncidentSource.TORONTO_ROAD, "123"));
        interactor.add(input(IncidentSource.TTC, "123"));

        interactor.remove(input(IncidentSource.TORONTO_ROAD, "123"));

        assertNull(output.error);
        assertEquals(BookmarksOutputData.Outcome.REMOVED, output.data.outcome);
        assertEquals(Set.of(SampleReports.TTC_KEY), savedBookmarks().keySet());
        assertEquals(1, output.data.bookmarks.size());
        assertEquals(IncidentSource.TTC, output.data.bookmarks.get(0).source);
    }

    /*
    HELPER METHODS
     */

    /**
     * Builds the input data for add or remove requests to the interactor.
     * @param source the report's source, or null to test invalid input
     * @param sourceRecordId the report's ID within the source, or null to test invalid input
     * @return input data with exactly the given values
     */
    private static BookmarksInputData input(IncidentSource source, String sourceRecordId) {
        return new BookmarksInputData(source, sourceRecordId);
    }

    /**
     * Returns the bookmarks currently saved in the profile repository.
     */
    private Map<IncidentKey, Bookmark> savedBookmarks() {
        return profiles.load()
                .map(UserProfile::getBookmarks)
                .orElseGet(Map::of);
    }

    /**
     * Test dummy for {@link BookmarksOutputBoundary} that stands in for the presenter.
     */
    private static final class RecordingOutput implements BookmarksOutputBoundary {
        private BookmarksOutputData data;
        private String error;

        @Override
        public void presentBookmarks(BookmarksOutputData data) {
            this.data = data;
            this.error = null;
        }

        @Override
        public void presentError(String message) {
            this.error = message;
        }
    }

    /**
     * Test dummy for {@link Clock} whose current time can be set by the test. Always reports UTC.
     */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}
