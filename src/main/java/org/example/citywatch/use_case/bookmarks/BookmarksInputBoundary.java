package org.example.citywatch.use_case.bookmarks;

public interface BookmarksInputBoundary {
    void load();

    void add(BookmarksInputData input);

    void remove(BookmarksInputData input);
}
