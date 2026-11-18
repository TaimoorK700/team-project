package org.example.citywatch.use_case.gateway;

import java.util.Optional;

import org.example.citywatch.entity.UserProfile;

public interface ProfileRepository {
    /**
     * Returns empty when no profile has been saved.
     * Corrupt or unreadable storage throws DataAccessException.
     */
    Optional<UserProfile> load() throws DataAccessException;

    /**
     * Saves the whole profile atomically.
     * A failed save must preserve the previous profile file.
     */
    void save(UserProfile profile) throws DataAccessException;
}