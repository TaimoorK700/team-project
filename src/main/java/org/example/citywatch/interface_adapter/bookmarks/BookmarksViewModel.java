package org.example.citywatch.interface_adapter.bookmarks;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.List;
import java.util.Objects;

import org.example.citywatch.entity.IncidentSource;

public final class BookmarksViewModel {
    public static final String BOOKMARKS_PROPERTY = "bookmarks";
    public static final String ERROR_PROPERTY = "error";
    private final PropertyChangeSupport changes = new PropertyChangeSupport(this);
    private List<BookmarkItem> bookmarks = List.of();
    private String message = "";
    private String error = "";

    public List<BookmarkItem> getBookmarks() {
        return bookmarks;
    }

    /**
     * Returns a status message for the last action, or "" if there is none.
     */
    public String getMessage() {
        return message;
    }

    public String getError() {
        return error;
    }

    /**
     * Replaces the shown bookmarks and status message, clears any error.
     */
    public void showBookmarks(List<BookmarkItem> bookmarks, String message) {
        this.bookmarks = List.copyOf(bookmarks);
        this.message = Objects.requireNonNull(message);
        this.error = "";
        changes.firePropertyChange(BOOKMARKS_PROPERTY, null, this.bookmarks);
    }

    /**
     * Show an error and leaves the current bookmarks unchanged.
     */
    public void showError(String message) {
        this.error = Objects.requireNonNull(message);
        changes.firePropertyChange(ERROR_PROPERTY, null, message);
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        changes.addPropertyChangeListener(listener);
    }

    /**
     * Object for one bookmark row, formatted for display.
     */
    public static final class BookmarkItem {
        public final IncidentSource source;
        public final String sourceRecordId;
        public final String title;
        public final String savedAtText;
        public final String statusText;

        public BookmarkItem(IncidentSource source, String sourceRecordId,
                            String title, String savedAtText, String statusText) {
            this.source = Objects.requireNonNull(source);
            this.sourceRecordId = Objects.requireNonNull(sourceRecordId);
            this.title = Objects.requireNonNull(title);
            this.savedAtText = Objects.requireNonNull(savedAtText);
            this.statusText = Objects.requireNonNull(statusText);
        }
    }
}
