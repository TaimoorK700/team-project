package org.example.citywatch.interface_adapter.bookmarks;

import org.example.citywatch.entity.IncidentSource;
import org.example.citywatch.use_case.bookmarks.BookmarksInputBoundary;
import org.example.citywatch.use_case.bookmarks.BookmarksInputData;

public final class BookmarksController {
    private final BookmarksInputBoundary interactor;

    public BookmarksController(BookmarksInputBoundary interactor) {
        this.interactor = interactor;
    }

    public void load() {
        interactor.load();
    }

    public void add(IncidentSource source, String sourceRecordId) {
        interactor.add(new BookmarksInputData(source, sourceRecordId));
    }

    public void remove(IncidentSource source, String sourceRecordId) {
        interactor.remove(new BookmarksInputData(source, sourceRecordId));
    }
}
