package org.example.citywatch.use_case.gateway;

import java.util.*;
import java.time.*;
import org.example.citywatch.entity.*;

/** Implementations translate expected network/parsing failures into fetch results. */
public interface IncidentFeed {
    IncidentSource getSource();
    IncidentFetchResult fetch();
}
