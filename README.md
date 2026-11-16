# CityWatch

## Team Information

### Team Members
- Taimoor Khawaja
- Muhammad Mohid Khan
- Lian Lee
- Michael Chen

---

## Project Description

### Domain

CityWatch is a proposed urban safety and awareness application that helps Toronto residents, university students, and commuters understand transportation disruptions near the places they care about and on the transit services they use.

The MVP brings together two sources. The City of Toronto Road Restrictions feed provides reports about road and lane restrictions, such as closures for construction, events, or reported incidents. TTC service alerts provide information about disruptions affecting transit routes and stations.

Users can save places such as home, university, or work and choose a monitoring radius for each. The nearby dashboard displays road restrictions that fall within those radii and prioritizes them using proximity, recency, restriction type, available source-reported severity, and user preferences. The ranking represents how relevant a report is to the user’s saved places and settings.

Users can also subscribe to TTC routes and stations and see matching service alerts. They can bookmark a road restriction or TTC alert, refresh the reports, and review changes that CityWatch detects between successful refreshes.

---

## User Stories

### User Story 1 -- Manage Locations and Preferences

As a user, I want to save important locations, set a monitoring radius for each, and choose incident preferences so that CityWatch focuses on road restrictions relevant to me.

### User Story 2 -- View a Prioritized Incident Dashboard

As a user, I want to see nearby road restrictions ranked by relevance, with their sources and available times, so that I can quickly understand which disruptions might affect me.

### User Story 3 -- Monitor TTC Disruptions

As a commuter, I want to save frequently used TTC routes and stations and view matching service alerts so that I can anticipate disruptions to my commute.

### User Story 4 --  Track Incidents and Updates

As a user, I want to bookmark road restrictions and TTC alerts and review changes after a refresh so that I can follow disruptions I care about over time.

---

## Use Cases

### Use Case 1 -- Manage Saved Locations

**Related user story:** User Story 1

**Actor:** User

**Precondition:** The user has opened their local profile.

**Main flow:**
1. The user opens the saved locations view and chooses to add, edit, or delete a location.
2. When adding or editing a location, the user enters a label, latitude, longitude, and a monitoring radius in metres.
3. The application validates the input and saves the change to the local profile.
4. The application displays the updated list of locations.

**Outcome:** The user's saved locations and monitoring radii are available to the nearby dashboard.

**Alternative flow:** Invalid coordinates or a non-positive radius produce a validation message. The application saves the location after the user corrects the input.

### Use Case 2 -- Update Incident Preferences

**Related user story:** User Story 1

**Actor:** User

**Precondition:** The user has opened their local profile.

**Main flow:**
1. The user opens the preferences view.
2. The user selects preferred road restriction categories, a recency filter, and ranking preferences.
3. The application saves the preferences to the local profile.
4. The nearby dashboard applies the saved preferences when loading or recalculating its results.

**Outcome:** The user can control which road restrictions appear and how the dashboard ranks them.

**Alternative flow:** The application uses default preferences for a new profile until the user saves their own selections.

### Use Case 3 -- Browse Ranked Road Restrictions

**Related user story:** User Story 2

**Actor:** User

**Precondition:** The user has at least one saved location, and road restriction reports have been loaded through Use Case 9.

**Main flow:**
1. The user opens the nearby dashboard.
2. The application compares available report coordinates with the monitoring radius of each relevant saved location.
3. The application applies the user's category preferences and recency filter.
4. The application calculates a relevance score using documented rules for proximity, recency, category preferences, and source-reported severity where available.
5. The application displays matching reports in relevance order with their sources and available times.
6. The user can narrow the results by saved location or restriction type.

**Outcome:** The user can see which road restrictions are most relevant to their saved places.

**Alternative flows:**
- If the user has no saved locations, the dashboard prompts them to add one.
- If no reports match, the dashboard displays an empty result message.
- A report needs usable coordinates to qualify as nearby. Missing severity is treated as unknown.
- If a refresh fails, the dashboard labels retained road reports with their last successful retrieval time.

### Use Case 4 -- Inspect a Road Restriction

**Related user story:** User Story 2

**Actor:** User

**Precondition:** The nearby dashboard displays at least one matching report.

**Main flow:**
1. The user selects a road restriction.
2. The application displays its description, affected street or segment, source, and available timestamps.
3. The application identifies the matching saved location and explains why the report appeared, such as “Within 500 m of University.”
4. The user can bookmark the report through Use Case 7.

**Outcome:** The user can understand what the source reported and why the restriction is relevant to them.

### Use Case 5 -- Manage Transit Subscriptions

**Related user story:** User Story 3

**Actor:** User

**Precondition:** The user has opened their local profile, and TTC route or station reference data is available.

**Main flow:**
1. The user opens the transit subscriptions view.
2. The user selects a TTC route or station to follow, or removes an existing subscription.
3. The application saves the change to the local profile and displays the updated subscription list.

**Outcome:** The user's subscriptions are available for matching TTC service alerts.

**Alternative flow:** If the user selects a service they already follow, the application displays the existing subscription.

### Use Case 6 -- View Matching TTC Disruptions

**Related user story:** User Story 3

**Actor:** User

**Precondition:** The user has at least one transit subscription, and TTC alert data has been loaded through Use Case 9.

**Main flow:**
1. The user opens the TTC disruptions view.
2. The application compares the user's subscriptions with the route or stop identifiers supplied by TTC alerts. For station subscriptions, the application uses associated stop identifiers where needed.
3. The application displays matching alerts.
4. The user opens an alert to review its description, source, affected service, and available effective period.
5. The user can bookmark the alert through Use Case 7.

**Outcome:** The user can identify reported disruptions affecting the TTC services they follow.

**Alternative flows:**
- If the user has no subscriptions, the application prompts them to add a route or station.
- If no alerts match, the application displays an empty result message.
- Broad service advisories can appear with a general advisory label where applicable.

### Use Case 7 -- Manage Bookmarks

**Related user story:** User Story 4

**Actor:** User

**Precondition:** The user has opened a report to bookmark or opened the bookmarks view to manage saved reports.

**Main flow:**
1. The user selects the bookmark action on a road restriction or TTC alert.
2. The application saves a reference to the report in the local profile using its source and source record ID.
3. The user can open the bookmarks view to revisit a saved report or remove its bookmark.

**Outcome:** The user can revisit reports from either source from one bookmarks view.

**Alternative flow:** If the report is already bookmarked, the application displays its saved state and keeps a single bookmark for that report.

### Use Case 8 -- Review Bookmarked Incident Updates

**Related user story:** User Story 4

**Actor:** User

**Precondition:** The user has bookmarked a report. Detecting a change requires a successful refresh and an earlier successful observation of that report.

**Main flow:**
1. The user selects a report in the bookmarks view.
2. The application displays its latest stored details and recorded changes, such as a revised description, status, or effective period.
3. The application highlights changes that the user has yet to review.
4. The application records the latest update the user reviewed.

**Outcome:** The user can see what changed in a report since their last review.

**Alternative flows:**
- If no changes have been detected, the application displays the latest stored details and indicates that there are no recorded updates.
- If a report disappears from a later complete and successful response, the application displays its last known details and labels it **No longer listed by source**.

### Use Case 9 -- Refresh Reports

**Related user story:** User Stories 2, 3, and 4

**Actor:** User

**Precondition:** At least one of the MVP data sources is configured.

**Main flow:**
1. The user requests a refresh from the dashboard or bookmarks view.
2. The application calls the configured source adapters. Each adapter retrieves reports and converts supported fields into the shared incident format.
3. The application compares each successful response with that source's previous successful snapshot, matching reports by source and source record ID.
4. The application identifies newly observed reports and records meaningful changes to existing reports.
5. The application updates the report cache used by the nearby dashboard, TTC view, and bookmarks.

**Outcome:** The app’s views use refreshed reports, and the bookmarks view can show detected changes.

**Alternative flows:**
- The first successful response from each source establishes its baseline for future comparisons.
- If a source is unavailable, the application retains its last successful data and displays the time it was retrieved.
- If one source fails and the other succeeds, the successful source can still update its results.

---

## Minimal Viable Product (MVP)

The proposed MVP would be a Java desktop application with a Swing GUI, one local user profile, and local persistence. Each team member leads one user story and the use cases that deliver it.

| Team Member | MVP Use Case |
|-------------|--------------|
| Taimoor | Use Case 1 -- Manage Saved Locations, Use Case 2 -- Update Incident Preferences |
| Mohid | Use Case 3 -- Browse Ranked Road Restrictions, Use Case 4 -- Inspect a Road Restriction |
| Lian | Use Case 5 -- Manage Transit Subscriptions, Use Case 6 -- View Matching TTC Disruptions |
| Michael | Use Case 7 -- Manage Bookmarks, Use Case 8 -- Review Bookmarked Incident Updates, Use Case 9 -- Refresh Reports |

Taimoor also leads the shared Swing application shell, navigation, and local profile storage. Mohid implements the Toronto Road Restrictions adapter, and Lian implements the TTC service alerts adapter and verifies the identifiers needed for matching. Michael leads the shared refresh process, report cache, and change detection. The team agrees on the incident model and data access interfaces early so that each use case can be developed and tested with sample reports.

The minimum working demonstration would allow a user to save a location and preferences, refresh reports, view ranked road restrictions, inspect alerts for a subscribed TTC service, bookmark a report, and review a detected change. The user can restart the application and recover their saved profile data.

---

## Entities
The following entities are proposed. Their fields and relationships may change during design.

### Entity 1 -- UserProfile
Represents the local user's saved configuration.

**Proposed attributes:** profile ID, saved locations, preferences, transit subscriptions, and bookmarks.

### Entity 2 -- SavedLocation
Represents a place the user wants to monitor.

**Proposed attributes:** location ID, label, latitude, longitude, and monitoring radius in metres.

### Entity 3 -- Incident

Represents a normalized report from either the Toronto Road Restrictions feed or the TTC service alerts source.

**Proposed attributes:** internal ID, source name, source record ID, source URL, category, title, description, affected street or segment where available, coordinates where available, affected transit identifiers where available, source-reported severity where available, reported time, source update time, effective period where available, last retrieved time, and source-provided status where available.

Unknown fields remain explicitly unknown. The application combines the source name and source record ID when matching reports across refreshes.

### Entity 4 -- UserPreferences

Represents how the user wants nearby road restrictions filtered and prioritized.

**Proposed attributes:** preferred categories, recency filter, and relevance-ranking settings.

### Entity 5 -- TransitSubscription
Represents a TTC service the user wants to monitor.

**Proposed attributes:** subscription ID, subscription type, route or station identifier, display name, and associated stop identifiers where needed.

### Entity 6 -- Bookmark
Represents a road restriction or TTC alert saved by the user.

**Proposed attributes:** bookmark ID, incident ID, saved time, and last reviewed update ID.

### Entity 7 -- IncidentUpdate
Represents a meaningful change observed between successful refreshes of the same source.

**Proposed attributes:** update ID, incident ID, observation time, changed fields, and previous and new values.

The application calculates a report's relevance for the current user and matching saved location. The report's source-reported severity is a separate field.

---

## API Usage

The MVP plans to use the following two integrations. The team will verify endpoint access, available response fields, and any usage requirements with runnable Java requests before completing the adapters.

**API Name:** City of Toronto Road Restrictions Feed

**Documentation:** [Road Restrictions Dataset](https://open.toronto.ca/dataset/road-restrictions/).

**How it is used in the program:**

The road restrictions adapter would retrieve supported reports and convert them into `Incident` entities. The nearby dashboard would use available street details, coordinates, restriction types, and timing information to filter and rank reports. The shared refresh use case would use source record IDs to detect changes and keep bookmarked road reports up to date.

**API Name:** TTC GTFS-Realtime (GTFS-RT)

**Documentation**: [TTC GTFS-Realtime dataset](https://open.toronto.ca/dataset/ttc-gtfs-realtime-gtfs-rt/)

**Format Reference**: [GTFS Realtime Service Alerts](https://gtfs.org/documentation/realtime/reference/#message-alert)

**How it is used in the program:**

The TTC adapter would retrieve service alerts and convert them into `Incident` entities. The TTC view would compare affected route or stop identifiers with the user's subscriptions and show alert descriptions, sources, and effective periods where available. The shared refresh use case would also track changes to bookmarked TTC alerts.

The team will confirm that an accessible TTC resource supplies service alerts and the identifiers needed for matching. Station subscriptions may require reference data that maps stations to their associated stops. A GTFS-Realtime integration would use a Java parser for Protocol Buffers.

Both adapters would retain source attribution and available timestamps. They would handle retrieval and parsing so that filtering, ranking, subscription matching, and change detection can operate on the shared incident format.

---

## Design Diagrams

### UML Class Diagram

TBD

### Sequence Diagrams

TBD

---

## How to Run

[Instructions for running the program.]

TBD

---

## Notes

The application will follow Clean Architecture. A Swing view sends user actions to its controller, which passes input to the interactor through an input boundary. The interactor applies the use case logic and accesses reports or profile data through interfaces defined in the use case layer. It sends output through an output boundary to a presenter, which updates the view model used by the view.

The application entry point will connect the Swing views, controllers, interactors, presenters, and data access implementations. The road and TTC adapters will implement the same report source interface so the shared refresh use case can call both. The dashboard, TTC view, and bookmarks will read from the same report cache through their data access interfaces.

Each use case lead will write interactor tests for their work. Sample reports and in-memory data access implementations will support testing location validation, radius matching, ranking, TTC subscriptions, bookmarks, and change detection.
