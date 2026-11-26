package org.example.citywatch.data_access.road;

import com.fasterxml.jackson.databind.JsonNode;
import org.example.citywatch.entity.Coordinates;
import org.example.citywatch.entity.EffectivePeriod;
import org.example.citywatch.entity.Incident;
import org.example.citywatch.entity.IncidentKey;
import org.example.citywatch.entity.IncidentSource;
import org.example.citywatch.use_case.gateway.IncidentFeed;
import org.example.citywatch.use_case.gateway.IncidentFetchResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.citywatch.use_case.gateway.IncidentSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;

public class TorontoRoadRestrictionsFeed implements IncidentFeed {
    private static final URI FEED_URI = URI.create(
            "https://secure.toronto.ca/opendata/cart/road_restrictions/v3?format=json");
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public IncidentSource getSource() {
        return IncidentSource.TORONTO_ROAD;
    }

    @Override
    public IncidentFetchResult fetch() {
        try {
            String body = downloadFeed();
            return parseResponse(body, Instant.now());
        } catch (IOException e) {
            return IncidentFetchResult.failed(getSource(), "Road feed request failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return IncidentFetchResult.failed(getSource(), "Road feed request was interrupted");
        }
    }

    String downloadFeed() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(FEED_URI)
                .timeout(Duration.ofSeconds(35))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    private IncidentFetchResult parseResponse(String body, Instant retrievedAt) {
        IncidentSource source = getSource();
        JsonNode root;
        try {
            root = MAPPER.readTree(body);
        } catch (JsonProcessingException e) {
            return IncidentFetchResult.failed(source, "Road feed returned invalid JSON");
        }

        if (root == null || !root.path("Closure").isArray()) {
            return IncidentFetchResult.failed(source, "Road feed response is missing the Closure array");
        }

        List<Incident> incidents = new ArrayList<>();
        for (JsonNode closure : root.path("Closure")) {
            try {
                incidents.add(toIncident(closure));
            } catch (IllegalArgumentException e) {
                int recordNumber = incidents.size() + 1;
                return IncidentFetchResult.incomplete(
                        source, "Could not map road closure " + recordNumber + ": " + e.getMessage());
            }
        }

        try {
            return IncidentFetchResult.complete(new IncidentSnapshot(source, retrievedAt, incidents));
        } catch (IllegalArgumentException e) {
            return IncidentFetchResult.incomplete(source, "Road feed contains duplicate closure IDs");
        }
    }
    static Incident toIncident(JsonNode closure) {
        if (!closure.isObject()) {
            throw new IllegalArgumentException("Expected a road closure object");
        }

        String id = optionalText(closure, "id")
                .orElseThrow(() -> new IllegalArgumentException("Road closure is missing its ID"));
        Optional<String> road = optionalText(closure, "road");
        String title = optionalText(closure, "name").or(() -> road)
                .orElseThrow(() -> new IllegalArgumentException("Road closure is missing its name and road"));

        IncidentKey key = new IncidentKey(IncidentSource.TORONTO_ROAD, id);
        Incident.Builder builder = Incident.builder(key, title);

        road.ifPresent(builder::street);
        optionalText(closure, "type").ifPresent(builder::category);
        optionalText(closure, "description").ifPresent(builder::description);
        optionalText(closure, "URL").ifPresent(builder::sourceUrl);
        optionalText(closure, "currImpact").ifPresent(builder::severity);
        optionalTime(closure, "createdTime").ifPresent(builder::reportedAt);
        optionalTime(closure, "lastUpdated").ifPresent(builder::sourceUpdatedAt);

        Optional<Double> latitude = optionalText(closure, "latitude").map(Double::parseDouble);
        Optional<Double> longitude = optionalText(closure, "longitude").map(Double::parseDouble);
        if (latitude.isPresent() && longitude.isPresent()) {
            builder.coordinates(new Coordinates(latitude.get(), longitude.get()));
        }

        Optional<Instant> start = optionalTime(closure, "startTime");
        Optional<Instant> end = optionalTime(closure, "endTime");
        if (start.isPresent() || end.isPresent()) {
            builder.effectivePeriods(List.of(new EffectivePeriod(start, end)));
        }

        return builder.build();
    }

    private static Optional<String> optionalText(JsonNode closure, String field) {
        JsonNode value = closure.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return Optional.empty();
        }
        if (!value.isTextual()) {
            throw new IllegalArgumentException("Expected text for " + field);
        }
        return Optional.of(value.textValue().strip()).filter(text -> !text.isEmpty());
    }

    private static Optional<Instant> optionalTime(JsonNode closure, String field) {
        return optionalText(closure, field).map(Long::parseLong).map(Instant::ofEpochMilli);
    }
}