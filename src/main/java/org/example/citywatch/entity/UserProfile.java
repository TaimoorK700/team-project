package org.example.citywatch.entity;

import java.util.*;
import java.time.*;

/** Immutable shared value used by CityWatch. */
public final class UserProfile {
    private final String id;
    private final List<SavedLocation> savedLocations;
    private final UserPreferences preferences;
    private final Set<TransitSubscription> transitSubscriptions;
    private final Map<IncidentKey, Bookmark> bookmarks;

    public UserProfile(String id, List<SavedLocation> savedLocations, UserPreferences preferences, Set<TransitSubscription> transitSubscriptions, Map<IncidentKey, Bookmark> bookmarks) {
        Set<String> ids = new HashSet<>();
        for (SavedLocation location : savedLocations) {
            if (!ids.add(location.getId())) throw new IllegalArgumentException("Duplicate location ID");
        }
        bookmarks.forEach((key, bookmark) -> {
            if (!key.equals(bookmark.getIncidentKey())) throw new IllegalArgumentException("Bookmark key mismatch");
        });
        this.id = Checks.text(id);
        this.savedLocations = List.copyOf(savedLocations);
        this.preferences = Objects.requireNonNull(preferences);
        this.transitSubscriptions = Set.copyOf(transitSubscriptions);
        this.bookmarks = Map.copyOf(bookmarks);
    }

    public String getId() { return id; }

    public List<SavedLocation> getSavedLocations() { return savedLocations; }

    public UserPreferences getPreferences() { return preferences; }

    public Set<TransitSubscription> getTransitSubscriptions() { return transitSubscriptions; }

    public Map<IncidentKey, Bookmark> getBookmarks() { return bookmarks; }

    public static UserProfile empty(String id) {
        return new UserProfile(id, List.of(), UserPreferences.defaults(), Set.of(), Map.of());
    }
    public UserProfile withSavedLocations(List<SavedLocation> values) {
        return new UserProfile(id, values, preferences, transitSubscriptions, bookmarks);
    }
    public UserProfile withPreferences(UserPreferences value) {
        return new UserProfile(id, savedLocations, value, transitSubscriptions, bookmarks);
    }
    public UserProfile withTransitSubscriptions(Set<TransitSubscription> values) {
        return new UserProfile(id, savedLocations, preferences, values, bookmarks);
    }
    public UserProfile withBookmarks(Map<IncidentKey, Bookmark> values) {
        return new UserProfile(id, savedLocations, preferences, transitSubscriptions, values);
    }

}
