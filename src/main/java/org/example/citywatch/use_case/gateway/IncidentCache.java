package org.example.citywatch.use_case.gateway;

import java.util.*;
import java.time.*;
import org.example.citywatch.entity.*;

public interface IncidentCache {
    /** Empty means no successful fetch yet; a present snapshot may contain zero reports. */
    Optional<IncidentSnapshot> getLatestSnapshot(IncidentSource source) throws DataAccessException;

    /** Includes historical reports no longer listed; updates are in observation order. */
    Optional<CachedIncident> find(IncidentKey key) throws DataAccessException;

    /**
     * Atomically store a complete snapshot and its new updates, preserving all prior history.
     * Retain absent reports' last details but mark them unlisted for this source only.
     * Reject wrong-source updates, duplicate update IDs and older snapshots.
     * Update observation times must equal the committed snapshot retrieval time.
     * On failure, preserve the previous snapshot, retrieval time, listing states and history.
     * Implementations must survive restart. Serialize refresh calls at the application level.
     */
    void commit(IncidentSnapshot snapshot, List<IncidentUpdate> updates) throws DataAccessException;
}
