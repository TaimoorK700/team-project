package org.example.citywatch.use_case.bookmarks;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.example.citywatch.entity.Bookmark;
import org.example.citywatch.entity.IncidentKey;
import org.example.citywatch.entity.IncidentSource;
import org.example.citywatch.use_case.gateway.CachedIncident;

public final class BookmarksOutputData {
    public enum Outcome{
        LOADED,
        ADDED,
        ALREADY_BOOKMARKED,
        REMOVED
    }

    public final Outcome outcome;
    public final List<BookmarkRow> bookmarks;

    /**
     * BookmarksOutputData constructor
     * @param outcome outcome of use case, can be one of: LOADED, ADDED, ALREADY_BOOKMARKED, REMOVED
     * @param bookmarks list of bookmark, already sorted into display order by the interactor
     * @param details cached report details by key, missing key means details unavailable
     */
    public BookmarksOutputData(Outcome outcome,
                               List<Bookmark> bookmarks,
                               Map<IncidentKey, CachedIncident> details) {
        List<BookmarkRow> rows = new ArrayList<>();
        for (Bookmark bookmark : bookmarks) {
            rows.add(new BookmarkRow(bookmark, details.get(bookmark.getIncidentKey())));
        }
        this.outcome = Objects.requireNonNull(outcome);
        this.bookmarks = List.copyOf(rows);
    }

    public static final class BookmarkRow {
        public final IncidentSource source;
        public final String sourceRecordId;
        public final Instant savedAt;
        public final boolean detailsAvailable;
        public final String title;  //title == "" when details are unavailable
        public final boolean listed;    //only used when detailsAvailable

        private BookmarkRow(Bookmark bookmark, CachedIncident cached) {
            IncidentKey key = bookmark.getIncidentKey();
            source = key.getSource();
            sourceRecordId = key.getSourceRecordId();
            savedAt = bookmark.getSavedAt();
            detailsAvailable = (cached != null);

            if (detailsAvailable) {
                title = cached.getIncident().getTitle();
            } else {
                title = "";
            }
            listed = detailsAvailable && cached.getListed();
        }
    }
}
