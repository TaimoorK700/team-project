package org.example.citywatch.use_case.incident_preferences;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.example.citywatch.entity.RankingWeights;
import org.example.citywatch.entity.UserPreferences;
import org.example.citywatch.entity.UserProfile;
import org.example.citywatch.use_case.gateway.DataAccessException;
import org.example.citywatch.use_case.gateway.ProfileRepository;

public final class IncidentPreferencesInteractor
        implements IncidentPreferencesInputBoundary {
    private final ProfileRepository repository;
    private final IncidentPreferencesOutputBoundary output;

    public IncidentPreferencesInteractor(
            ProfileRepository repository,
            IncidentPreferencesOutputBoundary output) {
        this.repository = Objects.requireNonNull(repository);
        this.output = Objects.requireNonNull(output);
    }

    @Override
    public void load() {
        UserProfile profile;

        try {
            profile = loadProfile();
        }
        catch (DataAccessException exception) {
            output.presentError(exception.getMessage());
            return;
        }

        output.presentPreferences(
                new IncidentPreferencesOutputData(
                        profile.getPreferences(), false));
    }

    @Override
    public void save(IncidentPreferencesInputData input) {
        UserProfile updated;

        try {
            UserPreferences preferences = createPreferences(input);

            // Reload before saving so we preserve the latest profile sections.
            UserProfile current = loadProfile();
            updated = current.withPreferences(preferences);
            repository.save(updated);
        }
        catch (IllegalArgumentException | DataAccessException exception) {
            output.presentError(exception.getMessage());
            return;
        }

        // Report success only after persistence succeeds.
        output.presentPreferences(
                new IncidentPreferencesOutputData(
                        updated.getPreferences(), true));
    }

    private UserProfile loadProfile() throws DataAccessException {
        return repository.load()
                .orElseGet(() -> UserProfile.empty("local"));
    }

    private static UserPreferences createPreferences(
            IncidentPreferencesInputData input) {
        if (input == null) {
            throw new IllegalArgumentException(
                    "Enter the incident preferences.");
        }
        if (input.maximumAge == null) {
            throw new IllegalArgumentException(
                    "Choose a recency setting.");
        }

        Set<String> categories = parseCategories(input.categories);

        double proximity = parseWeight(
                input.proximityWeight, "Proximity weight");
        double recency = parseWeight(
                input.recencyWeight, "Recency weight");
        double category = parseWeight(
                input.categoryWeight, "Category weight");
        double severity = parseWeight(
                input.severityWeight, "Severity weight");

        double total = proximity + recency + category + severity;

        if (!Double.isFinite(total) || total <= 0) {
            throw new IllegalArgumentException(
                    "At least one ranking weight must be greater than zero, "
                            + "and the total must be finite.");
        }

        return new UserPreferences(
                categories,
                input.maximumAge,
                input.includeUnknownTimes,
                new RankingWeights(
                        proximity, recency, category, severity));
    }

    private static Set<String> parseCategories(String text) {
        if (text == null) {
            throw new IllegalArgumentException(
                    "Enter category identifiers, or leave the field empty "
                            + "to include all categories.");
        }

        if (text.isBlank()) {
            return Set.of();
        }

        Set<String> categories = new LinkedHashSet<>();

        for (String value : text.split(",", -1)) {
            String category = value.strip();

            if (category.isEmpty()) {
                throw new IllegalArgumentException(
                        "Separate categories with commas without leaving "
                                + "empty entries.");
            }

            // Preserve exact source identifiers, including case.
            categories.add(category);
        }

        return Set.copyOf(categories);
    }

    private static double parseWeight(String text, String field) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }

        double value;

        try {
            value = Double.parseDouble(text.strip());
        }
        catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    field + " must be a number.");
        }

        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(
                    field + " must be finite and zero or greater.");
        }

        return value;
    }
}