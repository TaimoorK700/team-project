package org.example.citywatch.data_access.profile;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.example.citywatch.entity.SavedLocation;
import org.example.citywatch.use_case.gateway.ProfileStorageException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class FileProfileRepositoryTest {
    @TempDir
    Path directory;

    @Test
    void reloadsLocationsAndPreservesOtherProfileProperties() throws Exception {
        Path file = directory.resolve("profile.properties");
        Properties original = new Properties();
        original.setProperty("preferences.category", "construction");
        original.setProperty("bookmarks.example", "retained");

        try (OutputStream output = Files.newOutputStream(file)) {
            original.store(output, "Test profile");
        }

        FileProfileRepository firstSession = new FileProfileRepository(file);
        firstSession.saveSavedLocations(List.of(
                new SavedLocation("stable-id", "Home", 43.66, -79.39, 500)));

        FileProfileRepository nextSession = new FileProfileRepository(file);
        SavedLocation loaded = nextSession.loadSavedLocations().get(0);

        assertEquals("stable-id", loaded.getId());
        assertEquals("Home", loaded.getLabel());
        assertEquals(43.66, loaded.getLatitude(), 0.000001);
        assertEquals(-79.39, loaded.getLongitude(), 0.000001);
        assertEquals(500, loaded.getRadiusMetres(), 0.000001);

        Properties stored = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            stored.load(input);
        }

        assertEquals("construction", stored.getProperty("preferences.category"));
        assertEquals("retained", stored.getProperty("bookmarks.example"));

        nextSession.saveSavedLocations(List.of());
        assertTrue(new FileProfileRepository(file).loadSavedLocations().isEmpty());
    }

    @Test
    void refusesToOverwriteMalformedProfileData() throws Exception {
        Path file = directory.resolve("profile.properties");
        Files.writeString(file, "savedLocations.count=invalid\n");
        byte[] original = Files.readAllBytes(file);
        FileProfileRepository repository = new FileProfileRepository(file);

        assertThrows(ProfileStorageException.class,
                repository::loadSavedLocations);
        assertThrows(ProfileStorageException.class,
                () -> repository.saveSavedLocations(List.of()));

        assertArrayEquals(original, Files.readAllBytes(file));
    }
}