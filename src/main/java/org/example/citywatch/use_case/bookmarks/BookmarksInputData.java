package org.example.citywatch.use_case.bookmarks;

import org.example.citywatch.entity.IncidentSource;

public final class BookmarksInputData {
    public final IncidentSource source;
    public final String sourceRecordId;

    public BookmarksInputData(IncidentSource source, String sourceRecordId) {
        this.source = source;
        this.sourceRecordId = sourceRecordId;
    }
}
