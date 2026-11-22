package org.example.citywatch.use_case.bookmarks;

public interface BookmarksOutputBoundary {
    void presentBookmarks(BookmarksOutputData data);

    void presentError(String message);
}
