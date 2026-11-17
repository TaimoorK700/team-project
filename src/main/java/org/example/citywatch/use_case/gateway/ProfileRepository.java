package org.example.citywatch.use_case.gateway;

import java.util.*;
import java.time.*;
import org.example.citywatch.entity.*;

public interface ProfileRepository {
    /** No profile yet is empty; corrupt/unreadable storage throws an exception. */
    Optional<UserProfile> load() throws DataAccessException;

    /** Save the whole profile atomically; preserve the previous file on failure. */
    void save(UserProfile profile) throws DataAccessException;
}
