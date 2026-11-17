package org.example.citywatch.data_access.profile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.example.citywatch.entity.SavedLocation;
import org.example.citywatch.use_case.gateway.ProfileRepository;
import org.example.citywatch.use_case.gateway.ProfileStorageException;

public final class FileProfileRepository implements ProfileRepository {
    private static final String PREFIX = "savedLocations.";
    private final Path file;

    public FileProfileRepository(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    @Override
    public synchronized List<SavedLocation> loadSavedLocations() {
        return decode(readProperties());
    }

    @Override
    public synchronized void saveSavedLocations(List<SavedLocation> locations) {
        List<SavedLocation> snapshot = List.copyOf(locations);
        Set<String> ids = new HashSet<>();

        for (SavedLocation location : snapshot) {
            if (!ids.add(location.getId())) {
                throw new IllegalArgumentException("Duplicate saved-location ID.");
            }
        }

        Properties properties = readProperties();

        // Refuse to silently overwrite malformed saved-location data.
        decode(properties);

        // Preserve properties owned by other profile features.
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith(PREFIX)) {
                properties.remove(key);
            }
        }

        properties.setProperty(PREFIX + "count", Integer.toString(snapshot.size()));

        for (int i = 0; i < snapshot.size(); i++) {
            SavedLocation location = snapshot.get(i);
            String base = PREFIX + i + ".";

            properties.setProperty(base + "id", location.getId());
            properties.setProperty(base + "label", location.getLabel());
            properties.setProperty(base + "latitude",
                    Double.toString(location.getLatitude()));
            properties.setProperty(base + "longitude",
                    Double.toString(location.getLongitude()));
            properties.setProperty(base + "radiusMetres",
                    Double.toString(location.getRadiusMetres()));
        }

        Path temporary = null;

        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(
                    file.getParent(), "citywatch-profile-", ".tmp");

            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "CityWatch profile");
            }

            Files.move(temporary, file,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException | SecurityException exception) {
            throw new ProfileStorageException(
                    "Could not safely save " + file
                            + ". Check folder permissions, free disk space, and "
                            + "support for atomic file replacement, then retry.",
                    exception);
        }
        finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                }
                catch (IOException | SecurityException ignored) {
                    // Cleanup failure must not hide the original result.
                }
            }
        }
    }

    private Properties readProperties() {
        Properties properties = new Properties();

        try {
            if (Files.notExists(file)) {
                return properties;
            }

            try (InputStream input = Files.newInputStream(file)) {
                properties.load(input);
            }

            return properties;
        }
        catch (IOException | IllegalArgumentException | SecurityException exception) {
            throw new ProfileStorageException(
                    "Could not read " + file
                            + ". Check its permissions and file contents, then reload.",
                    exception);
        }
    }

    private List<SavedLocation> decode(Properties properties) {
        try {
            String countValue = properties.getProperty(PREFIX + "count");

            if (countValue == null) {
                boolean hasLocationData = properties.stringPropertyNames()
                        .stream()
                        .anyMatch(key -> key.startsWith(PREFIX));

                if (hasLocationData) {
                    throw new IllegalArgumentException("Missing location count.");
                }

                return List.of();
            }

            int count = Integer.parseInt(countValue);
            if (count < 0) {
                throw new IllegalArgumentException("Negative location count.");
            }

            List<SavedLocation> locations = new ArrayList<>();
            Set<String> ids = new HashSet<>();

            for (int i = 0; i < count; i++) {
                String base = PREFIX + i + ".";
                SavedLocation location = new SavedLocation(
                        required(properties, base + "id"),
                        required(properties, base + "label"),
                        Double.parseDouble(required(properties, base + "latitude")),
                        Double.parseDouble(required(properties, base + "longitude")),
                        Double.parseDouble(required(properties, base + "radiusMetres"))
                );

                if (!ids.add(location.getId())) {
                    throw new IllegalArgumentException("Duplicate location ID.");
                }

                locations.add(location);
            }

            return List.copyOf(locations);
        }
        catch (IllegalArgumentException exception) {
            throw new ProfileStorageException(
                    "Saved-location data in " + file
                            + " is invalid. Correct the file or restore a backup, then reload.",
                    exception);
        }
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing property: " + key);
        }
        return value;
    }
}