package org.example.citywatch.data_access.ttc;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.transit.realtime.GtfsRealtime.Alert;
import com.google.transit.realtime.GtfsRealtime.EntitySelector;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedHeader;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import com.google.transit.realtime.GtfsRealtime.TimeRange;
import com.google.transit.realtime.GtfsRealtime.TranslatedString;

import org.example.citywatch.entity.EffectivePeriod;
import org.example.citywatch.entity.Incident;
import org.example.citywatch.entity.IncidentKey;
import org.example.citywatch.entity.IncidentSource;
import org.example.citywatch.entity.TransitScope;

import org.example.citywatch.use_case.gateway.IncidentFetchResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class TtcAlertsFeedTest {

    // Fixed time so results are the same every run
    private static final Instant RETRIEVED_AT = Instant.parse("2026-02-07T00:00:00Z");

    // ------------- helpers for building sample responses -------------

    // Wraps text in a TranslatedString
    private static TranslatedString text(String value) {
        return TranslatedString.newBuilder()
                .addTranslation(TranslatedString.Translation.newBuilder().setText(value))
                .build();
    }

    // Builds one informed_entity item with both a route id and a stop id
    private static EntitySelector routeAndStop(String routeId, String stopId) {
        return EntitySelector.newBuilder().setRouteId(routeId).setStopId(stopId).build();
    }

    // A feed holding given entities
    private static FeedMessage feedOf(FeedEntity... entities) {
        FeedMessage.Builder feed = FeedMessage.newBuilder()
                .setHeader(FeedHeader.newBuilder().setGtfsRealtimeVersion("2.0"));
        for (FeedEntity entity : entities) {
            feed.addEntity(entity);
        }
        return feed.build();
    }

    // Sample alert based on alert 80511: Line 1, two stops, all fields filled in
    private static FeedEntity routeAndStopAlert() {
        Alert alert = Alert.newBuilder()
                .setHeaderText(text("Line 1: No service between Finch West and Wilson"))
                .setDescriptionText(text("Shuttle buses are running."))
                .setUrl(text("https://www.ttc.ca/service-advisories"))
                .setEffect(Alert.Effect.NO_SERVICE)

                .addActivePeriod(TimeRange.newBuilder().setStart(1795237140L).setEnd(1795249800L))

                .addInformedEntity(routeAndStop("1", "15659"))
                .addInformedEntity(routeAndStop("1", "15658"))
                .build();
        return FeedEntity.newBuilder().setId("80511").setAlert(alert).build();
    }

    // ------------- toIncident tests -------------

    // Success case: an alert with every field maps to all Incident fields
    @Test
    void testToIncidentRouteAndStopAlert() {
        Incident incident = TtcAlertsFeed.toIncident(routeAndStopAlert()).orElseThrow();

        assertEquals(new IncidentKey(IncidentSource.TTC, "80511"), incident.getKey());
        assertEquals("Line 1: No service between Finch West and Wilson", incident.getTitle());

        assertEquals(Optional.of("Shuttle buses are running."), incident.getDescription());
        assertEquals(Optional.of("https://www.ttc.ca/service-advisories"),
                incident.getSourceUrl());
        assertEquals(Optional.of("NO_SERVICE"), incident.getCategory());

        EffectivePeriod period = incident.getEffectivePeriods().get(0);
        assertEquals(Optional.of(Instant.ofEpochSecond(1795237140L)), period.getStart());
        assertEquals(Optional.of(Instant.ofEpochSecond(1795249800L)), period.getEnd());
    }

    // Data structure consistency: each route and stop pair becomes one scope in order
    @Test
    void testToIncidentKeepsRouteStopPairs() {
        Incident incident = TtcAlertsFeed.toIncident(routeAndStopAlert()).orElseThrow();
        List<TransitScope> scopes = incident.getTransitScopes();

        assertEquals(2, scopes.size());
        assertEquals(Optional.of("1"), scopes.get(0).getRouteId());
        assertEquals(Optional.of("15659"), scopes.get(0).getStopId());
        assertEquals(Optional.of("1"), scopes.get(1).getRouteId());
        assertEquals(Optional.of("15658"), scopes.get(1).getStopId());
    }

    // Boundary case: stop-only alert keeps the stop and has no route
    @Test
    void testToIncidentStopOnlyAlert() {
        Alert alert = Alert.newBuilder()
                .setHeaderText(text("Elevator out of service"))
                .addInformedEntity(EntitySelector.newBuilder().setStopId("14945"))
                .build();
        FeedEntity entity = FeedEntity.newBuilder().setId("2").setAlert(alert).build();

        TransitScope scope = TtcAlertsFeed.toIncident(entity).orElseThrow().getTransitScopes().get(0);

        assertEquals(Optional.empty(), scope.getRouteId());
        assertEquals(Optional.of("14945"), scope.getStopId());
        assertFalse(scope.isGeneralAdvisory());
    }

    // Boundary case: alert with no route or stop becomes a general advisory
    @Test
    void testToIncidentBroadAdvisory() {
        Alert alert = Alert.newBuilder()
                .setHeaderText(text("Holiday service on all routes"))
                .addInformedEntity(EntitySelector.newBuilder().setAgencyId("1"))
                .build();
        FeedEntity entity = FeedEntity.newBuilder().setId("3").setAlert(alert).build();

        List<TransitScope> scopes = TtcAlertsFeed.toIncident(entity).orElseThrow().getTransitScopes();

        assertEquals(1, scopes.size());
        assertTrue(scopes.get(0).isGeneralAdvisory());
    }

    // Boundary case: missing optional fields become Optional.empty()
    @Test
    void testToIncidentMissingOptionalFields() {
        Alert alert = Alert.newBuilder()
                .setHeaderText(text("Minor delay"))
                .addActivePeriod(TimeRange.newBuilder().setStart(1795237140L))
                .addInformedEntity(routeAndStop("2", "14110"))
                .build();
        FeedEntity entity = FeedEntity.newBuilder().setId("4").setAlert(alert).build();

        Incident incident = TtcAlertsFeed.toIncident(entity).orElseThrow();

        assertEquals(Optional.empty(), incident.getDescription());
        assertEquals(Optional.empty(), incident.getSourceUrl());
        assertEquals(Optional.empty(), incident.getCategory());
        assertEquals(Optional.empty(), incident.getSeverity());
        assertEquals(Optional.empty(), incident.getEffectivePeriods().get(0).getEnd());
    }

    // Atypical behaviour: toIncident returns an empty Optional instead of throwing
    @Test
    void testToIncidentWithoutHeader() {
        FeedEntity entity = FeedEntity.newBuilder().setId("5").setAlert(Alert.newBuilder().build()).build();

        assertTrue(TtcAlertsFeed.toIncident(entity).isEmpty());
    }

    // ------------- toResult tests -------------

    // Success case: a feed of good alerts is COMPLETE and keeps every alert
    @Test
    void testToResultValidFeed() {
        IncidentFetchResult result = TtcAlertsFeed.toResult(feedOf(routeAndStopAlert()), RETRIEVED_AT);
        assertEquals(IncidentFetchResult.Outcome.COMPLETE, result.getOutcome());
        assertEquals(1, result.getSnapshot().orElseThrow().getIncidents().size());
        assertEquals(RETRIEVED_AT, result.getSnapshot().orElseThrow().getRetrievedAt());
    }

    // Boundary case: an empty feed is COMPLETE, with no incidents
    @Test
    void testToResultZeroAlerts() {
        IncidentFetchResult result = TtcAlertsFeed.toResult(feedOf(), RETRIEVED_AT);

        assertEquals(IncidentFetchResult.Outcome.COMPLETE, result.getOutcome());

        assertTrue(result.getSnapshot().orElseThrow().getIncidents().isEmpty());
    }

    // Atypical behaviour: one alert that cannot be converted makes the result INCOMPLETE
    @Test
    void testToResultUnconvertibleAlert() {
        FeedEntity noHeader = FeedEntity.newBuilder().setId("6").setAlert(Alert.newBuilder().build()).build();

        IncidentFetchResult result = TtcAlertsFeed.toResult(feedOf(routeAndStopAlert(), noHeader), RETRIEVED_AT);

        assertEquals(IncidentFetchResult.Outcome.INCOMPLETE, result.getOutcome());
        assertTrue(result.getSnapshot().isEmpty());
    }

    // Atypical behaviour: two alerts with the same id make the result INCOMPLETE
    @Test
    void testToResultDuplicateIds() {
        IncidentFetchResult result = TtcAlertsFeed.toResult(feedOf(routeAndStopAlert(), routeAndStopAlert()), RETRIEVED_AT);

        assertEquals(IncidentFetchResult.Outcome.INCOMPLETE, result.getOutcome());
    }

    // ---------- parseFeed and getSource tests ----------

    // Atypical behaviour: bytes that are not protobuf throw
    @Test
    void testParseFeedInvalidBytes() {
        byte[] notProtobuf = "<html>Service unavailable</html>".getBytes();

        assertThrows(InvalidProtocolBufferException.class, () -> TtcAlertsFeed.parseFeed(notProtobuf));
    }

    // Success case: the feed reports TTC as its source
    @Test
    void testGetSource() {
        assertEquals(IncidentSource.TTC, new TtcAlertsFeed().getSource());
    }
}
