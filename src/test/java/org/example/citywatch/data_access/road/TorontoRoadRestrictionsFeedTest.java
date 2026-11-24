package org.example.citywatch.data_access.road;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.citywatch.entity.Incident;
import org.example.citywatch.entity.IncidentKey;
import org.example.citywatch.entity.IncidentSource;
import org.junit.jupiter.api.Test;
import org.example.citywatch.use_case.gateway.IncidentFetchResult;
import java.io.IOException;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TorontoRoadRestrictionsFeedTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mapsOneClosure() throws Exception {
        String json = "{\"id\":\"Tor-RD123-2\",\"name\":\"Queen St W from Bay St to Yonge St\","
                + "\"road\":\"Queen St W\",\"type\":\"CONSTRUCTION\","
                + "\"latitude\":\"43.651894\",\"longitude\":\"-79.381718\","
                + "\"startTime\":\"1735689600000\",\"endTime\":null}";

        Incident incident = TorontoRoadRestrictionsFeed.toIncident(mapper.readTree(json));

        assertEquals(new IncidentKey(IncidentSource.TORONTO_ROAD, "Tor-RD123-2"), incident.getKey());
        assertEquals("Queen St W from Bay St to Yonge St", incident.getTitle());
        assertEquals("Queen St W", incident.getStreet().orElseThrow());
        assertEquals("CONSTRUCTION", incident.getCategory().orElseThrow());
        assertEquals(-79.381718, incident.getCoordinates().orElseThrow().getLongitude());
        assertEquals(1, incident.getEffectivePeriods().size());
        assertEquals(Instant.parse("2025-01-01T00:00:00Z"),
                incident.getEffectivePeriods().get(0).getStart().orElseThrow());
        assertTrue(incident.getEffectivePeriods().get(0).getEnd().isEmpty());
    }

    @Test
    void usesRoadAsTitleWhenNameIsMissing() throws Exception {
        Incident incident = TorontoRoadRestrictionsFeed.toIncident(
                mapper.readTree("{\"id\":\"123\",\"road\":\"Bay St\"}"));

        assertEquals("Bay St", incident.getTitle());
        assertTrue(incident.getCoordinates().isEmpty());
        assertTrue(incident.getEffectivePeriods().isEmpty());
    }
    @Test
    void returnsCompleteSnapshotForValidResponse() {
        IncidentFetchResult result = fetchResponse(
                "{\"Closure\":[{\"id\":\"123\",\"road\":\"Bay St\"}]}");

        assertEquals(IncidentFetchResult.Outcome.COMPLETE, result.getOutcome());
        var snapshot = result.getSnapshot().orElseThrow();
        assertEquals(IncidentSource.TORONTO_ROAD, snapshot.getSource());
        assertEquals(1, snapshot.getIncidents().size());
        assertEquals(new IncidentKey(IncidentSource.TORONTO_ROAD, "123"),
                snapshot.getIncidents().get(0).getKey());
    }

    @Test
    void returnsCompleteSnapshotForEmptyArray() {
        IncidentFetchResult result = fetchResponse("{\"Closure\":[]}");

        assertEquals(IncidentFetchResult.Outcome.COMPLETE, result.getOutcome());
        assertTrue(result.getSnapshot().orElseThrow().getIncidents().isEmpty());
    }

    @Test
    void failsForMalformedJsonOrMissingArray() {
        IncidentFetchResult malformed = fetchResponse("{");
        IncidentFetchResult missingArray = fetchResponse("{}");

        assertEquals(IncidentFetchResult.Outcome.FAILED, malformed.getOutcome());
        assertTrue(malformed.getSnapshot().isEmpty());
        assertEquals(IncidentFetchResult.Outcome.FAILED, missingArray.getOutcome());
        assertTrue(missingArray.getSnapshot().isEmpty());
    }

    @Test
    void returnsIncompleteIfARecordCannotBeMapped() {
        IncidentFetchResult result = fetchResponse(
                "{\"Closure\":[{\"id\":\"123\",\"road\":\"Bay St\"},{\"road\":\"King St\"}]}");

        assertEquals(IncidentFetchResult.Outcome.INCOMPLETE, result.getOutcome());
        assertTrue(result.getSnapshot().isEmpty());
    }

    @Test
    void returnsIncompleteWhenIdsAreDuplicated() {
        IncidentFetchResult result = fetchResponse(
                "{\"Closure\":[{\"id\":\"123\",\"road\":\"Bay St\"},"
                        + "{\"id\":\"123\",\"road\":\"King St\"}]}");

        assertEquals(IncidentFetchResult.Outcome.INCOMPLETE, result.getOutcome());
        assertTrue(result.getSnapshot().isEmpty());
    }

    @Test
    void returnsFailedWhenDownloadThrows() {
        TorontoRoadRestrictionsFeed feed = new TorontoRoadRestrictionsFeed() {
            @Override
            String downloadFeed() throws IOException {
                throw new IOException("HTTP 503");
            }
        };

        IncidentFetchResult result = feed.fetch();

        assertEquals(IncidentFetchResult.Outcome.FAILED, result.getOutcome());
        assertTrue(result.getSnapshot().isEmpty());
    }

    private static IncidentFetchResult fetchResponse(String body) {
        TorontoRoadRestrictionsFeed feed = new TorontoRoadRestrictionsFeed() {
            @Override
            String downloadFeed() {
                return body;
            }
        };
        return feed.fetch();
    }
}