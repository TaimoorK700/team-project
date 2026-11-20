package org.example.citywatch.use_case.bookmarks;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import org.example.citywatch.entity.Bookmark;
import org.example.citywatch.entity.IncidentKey;
import org.example.citywatch.entity.UserProfile;
import org.example.citywatch.use_case.gateway.CachedIncident;
import org.example.citywatch.use_case.gateway.DataAccessException;
import org.example.citywatch.use_case.gateway.IncidentCache;
import org.example.citywatch.use_case.gateway.ProfileRepository;

public final class BookmarksInteractor implements BookmarksInputBoundary {
    private static final Logger LOGGER = Logger.getLogger(BookmarksInteractor.class.getName());

    private final ProfileRepository profiles;
    private final IncidentCache cache;
    private final Clock clock;
    private final BookmarksOutputBoundary output;

    public BookmarksInteractor(ProfileRepository profiles, IncidentCache cache,
                               Clock clock, BookmarksOutputBoundary output) {
        this.profiles = profiles;
        this.cache = cache;
        this.clock = clock;
        this.output = output;
    }

    @Override
    public void load() {
        UserProfile profile;

        try {
            profile = loadProfile();
        } catch (DataAccessException e) {
            output.presentError(e.getMessage());
            return;
        }

        output.presentBookmarks(buildOutput(BookmarksOutputData.Outcome.LOADED, profile));
    }

    @Override
    public void add(BookmarksInputData input) {
        UserProfile updated;
        try {
            IncidentKey key = toKey(input);
            UserProfile profile = loadProfile();

            //already bookmarked, keep original savedAt time
            // and ID of the last update the user has seen
            if (profile.getBookmarks().containsKey(key)) {
                output.presentBookmarks(buildOutput(BookmarksOutputData.Outcome.ALREADY_BOOKMARKED, profile));
                return;
            }

            Map<IncidentKey, Bookmark> bookmarks = new HashMap<>(profile.getBookmarks());
            bookmarks.put(key, new Bookmark(key, clock.instant(), latestUpdateId(key)));

            updated = profile.withBookmarks(bookmarks);
            profiles.save(updated);
        }
        catch (IllegalArgumentException | DataAccessException e) {
            output.presentError(e.getMessage());
            return;
        }

        //update the screen only after saving succeeds
        output.presentBookmarks(buildOutput(BookmarksOutputData.Outcome.ADDED, updated));

    }

    @Override
    public void remove(BookmarksInputData input) {
        UserProfile updated;
        try {
            IncidentKey key = toKey(input);
            UserProfile profile = loadProfile();

            Map<IncidentKey, Bookmark> bookmarks = new HashMap<>(profile.getBookmarks());

            //covers cases such as double input
            // so the call still goes through with no error message
            if (bookmarks.remove(key) == null) {
                output.presentBookmarks(buildOutput(BookmarksOutputData.Outcome.REMOVED, profile));
                return;
            }

            updated = profile.withBookmarks(bookmarks);
            profiles.save(updated);
        } catch (IllegalArgumentException | DataAccessException e) {
            output.presentError(e.getMessage());
            return;
        }

        output.presentBookmarks(buildOutput(BookmarksOutputData.Outcome.REMOVED, updated));
    }

    /*
    HELPERS
     */

    /**
     * Loads saved profile, or create empty profile if none has been saved yet.
     */
    private UserProfile loadProfile() throws DataAccessException {
        return profiles.load().orElseGet(() -> UserProfile.empty("local"));
    }

    /**
     * Converts input data to report key.
     * @throws IllegalArgumentException if the source or record ID is null or blank.
     */
    private static IncidentKey toKey(BookmarksInputData input) {
        if (input == null || input.source == null) {
            throw new IllegalArgumentException("Select a report to bookmark.");
        }
        return new IncidentKey(input.source, input.sourceRecordId);
    }

    /**
     * Returns the ID of the report's most recently recorded update.
     */
    private Optional<String> latestUpdateId(IncidentKey key) {
        try {
            return cache.find(key)
                    .map(CachedIncident::getUpdates)    //same as calling getUpdates() on the CachedIncident object
                    .filter(updates -> !updates.isEmpty())
                    .map(updates -> updates.get(updates.size() - 1).getId());
        }
        catch (DataAccessException e){
            return Optional.empty();
        }
    }

    /**
     * Builds the presented list
     * (newest first, each bookmark containing whatever details the cache has).
     */
    private BookmarksOutputData buildOutput(BookmarksOutputData.Outcome outcome,
                                            UserProfile profile) {
        List<Bookmark> sorted = new ArrayList<>(profile.getBookmarks().values());
        sorted.sort(Comparator.comparing(Bookmark::getSavedAt).reversed()
                .thenComparing(bookmark -> bookmark.getIncidentKey().toString()));
        Map<IncidentKey, CachedIncident> details = new HashMap<>();
        for (Bookmark bookmark : sorted) {
            IncidentKey key = bookmark.getIncidentKey();
            try {
                cache.find(key).ifPresent(cached -> details.put(key, cached));

            } catch (DataAccessException e) {
                LOGGER.warning("Could not read cached details for " + key + ": " + e.getMessage());
            }
        }

        return new BookmarksOutputData(outcome, sorted, details);
    }

}
