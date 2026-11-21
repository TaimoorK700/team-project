package org.example.citywatch.data_access.ttc;

import com.google.protobuf.InvalidProtocolBufferException;

import com.google.transit.realtime.GtfsRealtime.TimeRange;
import com.google.transit.realtime.GtfsRealtime.EntitySelector;
import com.google.transit.realtime.GtfsRealtime.TranslatedString;
import com.google.transit.realtime.GtfsRealtime.Alert;
import com.google.transit.realtime.GtfsRealtime.FeedEntity;
import com.google.transit.realtime.GtfsRealtime.FeedMessage;
import org.example.citywatch.entity.*;
import org.example.citywatch.use_case.gateway.IncidentFeed;
import org.example.citywatch.use_case.gateway.IncidentFetchResult;
import org.example.citywatch.use_case.gateway.IncidentSnapshot;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TtcAlertsFeed implements IncidentFeed {
    static final String ALERT_URL = "https://gtfsrt.ttc.ca/alerts/all?format=binary";
    private final HttpClient httpClient = HttpClient.newHttpClient();

    byte[] downloadFeed() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(ALERT_URL))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new IOException("TTC alerts request failed: HTTP " + response.statusCode());
        }

        return response.body();
    }

    static FeedMessage parseFeed(byte[] bytes) throws InvalidProtocolBufferException {
        return FeedMessage.parseFrom(bytes);
    }

    static Optional<Incident> toIncident(FeedEntity entity) {
        if (!entity.hasAlert() || entity.getId().isBlank()) {
            return Optional.empty();
        }
        Alert alert = entity.getAlert();
        Optional<String> title = firstText(alert.getHeaderText());
        if (title.isEmpty()) {
            return Optional.empty();
        }

        IncidentKey key = new IncidentKey(IncidentSource.TTC, entity.getId());
        Incident.Builder builder = Incident.builder(key, title.get());

        firstText(alert.getDescriptionText()).ifPresent(builder::description);
        firstText(alert.getUrl()).ifPresent(builder::sourceUrl);

        if (alert.hasEffect()) {
            builder.category(alert.getEffect().name());
        }

        builder.effectivePeriods(toPeriods(alert.getActivePeriodList()));
        builder.transitScopes(toScopes(alert.getInformedEntityList()));

        if (alert.hasSeverityLevel()) {
            builder.severity(alert.getSeverityLevel().name());
        }

        return Optional.of(builder.build());
    }

    private static List<TransitScope> toScopes(List<EntitySelector> selectors) {

        List<TransitScope> scopes = new ArrayList<>();

        for (EntitySelector selector : selectors) {
            Optional<String> routeId = Optional.empty();
            if (selector.hasRouteId() && !selector.getRouteId().isBlank()) {
                routeId = Optional.of(selector.getRouteId());
            }

            Optional<String> stopId = Optional.empty();
            if (selector.hasStopId() && !selector.getStopId().isBlank()) {
                stopId = Optional.of(selector.getStopId());
            }
            scopes.add(new TransitScope(routeId, stopId));
        }
        if (scopes.isEmpty()) {
            scopes.add(new TransitScope(Optional.empty(), Optional.empty()));
        }

        return scopes;
    }

    private static List<EffectivePeriod> toPeriods(List<TimeRange> ranges) {
        List<EffectivePeriod> periods = new ArrayList<>();
        for (TimeRange range : ranges) {
            Optional<Instant> start = Optional.empty();
            if (range.hasStart() && range.getStart() > 0) {
                start = Optional.of(Instant.ofEpochSecond(range.getStart()));
            }

            Optional<Instant> end = Optional.empty();
            if (range.hasEnd() && range.getEnd() > 0) {
                end = Optional.of(Instant.ofEpochSecond(range.getEnd()));
            }
            periods.add(new EffectivePeriod(start, end));
        }
        return periods;
    }

    static Optional<String> firstText(TranslatedString text) {
        Optional<String> fallBack = Optional.empty();
        for (TranslatedString.Translation translation : text.getTranslationList()) {
            String value = translation.getText();
            if (value.isBlank()) {
                continue;
            }
            if (translation.getLanguage().isEmpty() || translation.getLanguage().startsWith("en")) {
                return Optional.of(value);
            }
            if (fallBack.isEmpty()) { // Text is neither blank nor in English.
                // Save the first text as the fall-back.
                fallBack = Optional.of(value);
            }
        }
        return fallBack;
    }

    @Override
    public IncidentSource getSource() {
        return IncidentSource.TTC;
    }

    /** Downloads, parses and converts TTC alerts. */
    @Override
    public IncidentFetchResult fetch() {
        FeedMessage feed;
        try {
            feed = parseFeed(downloadFeed());
        } catch (IOException e) {
            return IncidentFetchResult.failed(IncidentSource.TTC, "could not load TTC alerts: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return IncidentFetchResult.failed(IncidentSource.TTC, "TTC alerts request was interrupted");
        }
        return toResult(feed, Instant.now());
    }

    static IncidentFetchResult toResult(FeedMessage feed, Instant retrievedAt) {
        List<Incident> incidents = new ArrayList<>();
        int skipped = 0;
        for (FeedEntity entity : feed.getEntityList()) {
            if (!entity.hasAlert()) {
                continue;
            }
            try {
                Optional<Incident> incident = toIncident(entity);
                if (incident.isPresent()) {
                    incidents.add(incident.get());
                } else {
                    skipped++;
                }
            } catch (IllegalArgumentException e) {
                skipped++;
            }
        }

        if (skipped > 0 ) {
            return IncidentFetchResult.incomplete(IncidentSource.TTC, skipped + " TTC alerts could not be converted");
        }
        try {
            return IncidentFetchResult.complete(new IncidentSnapshot(IncidentSource.TTC, retrievedAt, incidents));
        } catch (IllegalArgumentException e) {
            return IncidentFetchResult.incomplete(IncidentSource.TTC, "TTC feed has duplicate alert ids");
        }
    }
}
