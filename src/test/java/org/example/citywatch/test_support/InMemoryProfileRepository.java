package org.example.citywatch.test_support;

import java.util.*;
import java.time.*;
import org.example.citywatch.entity.*;
import org.example.citywatch.use_case.gateway.*;

/** Test double only: deliberately does not persist across process restarts. */
public final class InMemoryProfileRepository implements ProfileRepository {
    private UserProfile profile;
    @Override public Optional<UserProfile> load() { return Optional.ofNullable(profile); }
    @Override public void save(UserProfile value) { profile = Objects.requireNonNull(value); }
}
