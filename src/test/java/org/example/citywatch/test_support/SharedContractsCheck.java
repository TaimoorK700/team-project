package org.example.citywatch.test_support;

import java.util.*;
import java.time.*;
import org.example.citywatch.entity.*;
import org.example.citywatch.use_case.gateway.*;

/** Standalone checks: run main; no JUnit dependency required. */
public final class SharedContractsCheck {
    private static int checks;
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
    private static void rejects(Runnable operation) {
        try { operation.run(); } catch (IllegalArgumentException | NullPointerException expected) {
            checks++; return;
        }
        throw new AssertionError("Invalid input accepted");
    }
    public static void main(String[] args) throws Exception {
        check(!SampleReports.ROAD_KEY.equals(SampleReports.TTC_KEY), "Source keys collide");
        check(SampleReports.ROAD_KEY.equals(new IncidentKey(IncidentSource.TORONTO_ROAD, "123")),
                "Equivalent keys differ");
        rejects(() -> new Coordinates(Double.NaN, 0));
        rejects(() -> new SavedLocation("x", "Home", new Coordinates(0, 0), 0));
        rejects(() -> new EffectivePeriod(Optional.of(SampleReports.SECOND), Optional.of(SampleReports.FIRST)));
        rejects(() -> new IncidentSnapshot(IncidentSource.TTC, SampleReports.FIRST,
                SampleReports.roadBefore().getIncidents()));
        rejects(() -> new IncidentSnapshot(IncidentSource.TORONTO_ROAD, SampleReports.FIRST,
                List.of(SampleReports.road("x"), SampleReports.road("y"))));
        check(!SampleReports.road("x").getSeverity().isPresent(), "Missing severity invented");
        check(!SampleReports.ttc().getIncidents().get(0).getCoordinates().isPresent(), "Missing coordinates invented");
        List<TransitScope> scopes = SampleReports.ttc().getIncidents().get(0).getTransitScopes();
        check(scopes.size() == 2 && scopes.get(0).getRouteId().get().equals("route-A")
                && scopes.get(0).getStopId().get().equals("stop-A"), "Transit grouping lost");
        Incident.Builder builder = Incident.builder(SampleReports.ROAD_KEY, "Original");
        Incident original = builder.build();
        builder.description("Later");
        check(!original.getDescription().isPresent(), "Builder mutated existing incident");
        check(UserPreferences.defaults().acceptsCategory("ANY"), "Empty categories should mean all");
        UserProfile profile = UserProfile.empty("local");
        Bookmark bookmark = new Bookmark(SampleReports.ROAD_KEY, SampleReports.FIRST, Optional.empty());
        profile = profile.withBookmarks(Map.of(bookmark.getIncidentKey(), bookmark));
        profile = profile.withSavedLocations(List.of(new SavedLocation("home", "Home", new Coordinates(0, 0), 500)));
        check(profile.getBookmarks().size() == 1, "Location update lost bookmarks");
        check(bookmark.reviewedThrough("update-1").getLastReviewedUpdateId().get().equals("update-1"), "Review not recorded");
        check(!bookmark.getLastReviewedUpdateId().isPresent(), "Bookmark mutated");
        InMemoryProfileRepository profiles = new InMemoryProfileRepository();
        check(!profiles.load().isPresent(), "Uninitialized profile not empty");
        profiles.save(profile);
        check(profiles.load().get().getSavedLocations().size() == 1, "Profile lost");
        InMemoryIncidentCache cache = new InMemoryIncidentCache();
        check(!cache.getLatestSnapshot(IncidentSource.TORONTO_ROAD).isPresent(), "Unfetched source not empty");
        cache.commit(SampleReports.roadBefore(), List.of());
        cache.commit(SampleReports.ttc(), List.of());
        cache.commit(SampleReports.roadAfter(), List.of(SampleReports.roadChange()));
        check(cache.find(SampleReports.ROAD_KEY).get().getUpdates().size() == 1, "History missing");
        check(cache.find(SampleReports.TTC_KEY).get().getListed(), "Other source affected");
        try {
            cache.commit(new IncidentSnapshot(IncidentSource.TORONTO_ROAD, SampleReports.SECOND, List.of()),
                    List.of(SampleReports.roadChange()));
            throw new AssertionError("Duplicate update accepted");
        } catch (DataAccessException expected) { checks++; }
        check(cache.find(SampleReports.ROAD_KEY).get().getListed(), "Failed commit mutated listing");
        cache.commit(new IncidentSnapshot(IncidentSource.TORONTO_ROAD, SampleReports.SECOND.plusSeconds(1), List.of()), List.of());
        check(cache.getLatestSnapshot(IncidentSource.TORONTO_ROAD).get().getIncidents().isEmpty(), "Empty snapshot missing");
        check(!cache.find(SampleReports.ROAD_KEY).get().getListed()
                && cache.find(SampleReports.ROAD_KEY).get().getUpdates().size() == 1, "Last known history lost");
        FakeIncidentFeed feed = new FakeIncidentFeed(IncidentFetchResult.failed(IncidentSource.TORONTO_ROAD, "Offline"));
        check(!feed.fetch().getSnapshot().isPresent(), "Failure exposed a replacement snapshot");
        feed.setResult(IncidentFetchResult.incomplete(IncidentSource.TORONTO_ROAD, "Missing page"));
        check(feed.fetch().getOutcome() == IncidentFetchResult.Outcome.INCOMPLETE, "Incomplete fetch not distinct");
        feed.setResult(IncidentFetchResult.complete(cache.getLatestSnapshot(IncidentSource.TORONTO_ROAD).get()));
        check(feed.fetch().getSnapshot().isPresent(), "Valid empty snapshot confused with failure");
        System.out.println("Passed " + checks + " shared-contract checks.");
    }
}
