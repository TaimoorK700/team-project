package org.example.citywatch.interface_adapter.bookmarks;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.swing.SwingUtilities;

import org.example.citywatch.entity.IncidentSource;
import org.example.citywatch.interface_adapter.bookmarks.BookmarksViewModel.BookmarkItem;
import org.example.citywatch.use_case.bookmarks.BookmarksOutputBoundary;
import org.example.citywatch.use_case.bookmarks.BookmarksOutputData;
import org.example.citywatch.use_case.bookmarks.BookmarksOutputData.BookmarkRow;

public final class BookmarksPresenter implements BookmarksOutputBoundary {
    static final String DETAILS_UNAVAILABLE = "Details unavailable";
    static final String NO_LONGER_LISTED = "No longer listed by source";

    private final BookmarksViewModel viewModel;
    private final DateTimeFormatter savedAtFormat;

    /**
     * @param viewModel the view model to update
     * @param zone the time zone used to show saved timestamps
     */
    public BookmarksPresenter(BookmarksViewModel viewModel, ZoneId zone) {
        this.viewModel = Objects.requireNonNull(viewModel);
        this.savedAtFormat = DateTimeFormatter.ofPattern(
                "d MMM yyyy, h:mm a", Locale.ENGLISH)
                .withZone(zone);
    }

    @Override
    public void presentBookmarks(BookmarksOutputData data) {
        List<BookmarkItem> items = new ArrayList<>();
        for (BookmarkRow row : data.bookmarks) {
            items.add(toItem(row));
        }
        String message = messageFor(data.outcome);
        onEventThread(() -> viewModel.showBookmarks(items, message));
    }

    @Override
    public void presentError(String message){
        onEventThread(() -> viewModel.showError(message));
    }

    /*
    HELPERS
     */

    /**
     * Formats one bookmark row to display.
     */
    private BookmarkItem toItem(BookmarkRow row) {
        String title;
        String status;

        if (!row.detailsAvailable) {
            title = sourceName(row.source) + " report " + row.sourceRecordId;
            status = DETAILS_UNAVAILABLE;
        } else {
            title = row.title;
            status = row.listed ? "" : NO_LONGER_LISTED;
        }

        String savedAt = "Saved " + savedAtFormat.format(row.savedAt);
        return new BookmarkItem(row.source, row.sourceRecordId, title, savedAt, status);
    }

    /**
     * Chooses the status message shown after an action.
     */
    private static String messageFor(BookmarksOutputData.Outcome outcome) {
        switch (outcome) {
            case ADDED:
                return "Bookmark added.";
            case ALREADY_BOOKMARKED:
                return "This report is already bookmarked.";
            case REMOVED:
                return "Bookmark removed.";
            case LOADED:
            default:
                return "";
        }
    }

    /**
     * Names a report source.
     */
    private static String sourceName(IncidentSource source) {
        switch (source) {
            case TTC:
                return "TTC";
            case TORONTO_ROAD:
            default:
                return "Road restriction";
        }
    }

    /**
     * Runs the update on the Swing thread. If not on EDT, queues the action.
     */
    private static void onEventThread(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) {
            action.run();
        } else {
            SwingUtilities.invokeLater(action);
        }
    }
}
