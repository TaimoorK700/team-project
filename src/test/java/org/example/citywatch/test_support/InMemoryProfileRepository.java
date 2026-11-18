package org.example.citywatch.test_support;

import java.util.Objects;
import java.util.Optional;

import org.example.citywatch.entity.UserProfile;
import org.example.citywatch.use_case.gateway.DataAccessException;
import org.example.citywatch.use_case.gateway.ProfileRepository;

/**
 * Test repository that stores a profile in memory.
 * Data does not survive an application restart.
 */
public final class InMemoryProfileRepository implements ProfileRepository {
    private UserProfile profile;
    private boolean failOnSave;

    @Override
    public Optional<UserProfile> load() {
        return Optional.ofNullable(profile);
    }

    @Override
    public void save(UserProfile value) throws DataAccessException {
        if (failOnSave) {
            throw new DataAccessException("Could not save. Please retry.");
        }

        profile = Objects.requireNonNull(value, "Profile cannot be null.");
    }

    public void setFailOnSave(boolean failOnSave) {
        this.failOnSave = failOnSave;
    }
}