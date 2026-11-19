package org.example.citywatch.data_access.ttc;

import com.google.protobuf.InvalidProtocolBufferException;

import com.google.transit.realtime.GtfsRealtime.FeedMessage;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class TtcAlertsFeed {
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
}
