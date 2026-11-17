package org.example.citywatch.use_case.gateway;

import java.util.*;
import java.time.*;
import org.example.citywatch.entity.*;

/** Catalog entries use the same service identifiers and station mappings as subscriptions. */
public interface TransitCatalog {
    List<TransitSubscription> listServices() throws DataAccessException;
    Optional<TransitSubscription> find(TransitSubscription.Type type, String serviceId)
            throws DataAccessException;
}
