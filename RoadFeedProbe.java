import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;

class RoadFeedProbe {
    public static void main(String[] args) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(
                        "https://secure.toronto.ca/opendata/cart/road_restrictions/v3?format=json"))
                .timeout(Duration.ofSeconds(35))
                .GET()
                .build();

        var response = HttpClient.newHttpClient().send(request,
                HttpResponse.BodyHandlers.ofFile(
                        Path.of("road-restrictions.json"),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING));

        System.out.println("HTTP " + response.statusCode());
    }
}