# Project Name

## Team Information

### Team Members
- Taimoor Khawaja
- Muhammad Mohid Khan
- Lian Lee
- Michael Chen

---

## Project Description

### Domain

CityWatch is a proposed urban safety and awareness application that would help Toronto residents, university students, and commuters understand incidents and disruptions near the places they care about.

Information about road closures, traffic incidents, fires, police activity, and transit disruptions is often spread across different sources. CityWatch aims to bring relevant reports together and personalize them using a user's saved locations, monitoring distances, transit subscriptions, and preferences.

Users would be able to save places such as home, university, or work and choose a monitoring radius for each. The application would identify nearby incidents and prioritize them using transparent rules based on proximity, recency, incident type, available source-reported severity, and user preferences. The resulting priority would represent relevance to the user, rather than a prediction of personal danger.

A personalized dashboard would summarize relevant incidents and TTC disruptions. Users would also be able to view report details, bookmark incidents, and see changes detected during later refreshes.

The proposed MVP would focus on road restrictions and incidents available through the City of Toronto's road restrictions feed, alongside TTC service alerts. Fire reports, police activity, and other hazards would be additional integrations, subject to suitable data access and project time. The application would only display incident categories supported by its connected sources.

---

## User Stories

### User Story 1 -- Manage Locations and Preferences

As a user, I want to save important locations, set a monitoring radius for each, and choose incident categories so that CityWatch focuses on information relevant to me.

### User Story 2 -- View a Prioritized Incident Dashboard

As a user, I want to view nearby incidents ranked by relevance, with their sources and reporting times, so that I can quickly understand what might affect me.

### User Story 3 -- Monitor TTC Disruptions

As a commuter, I want to save frequently used TTC routes and stations and view matching service alerts so that I can anticipate disruptions to my commute.

### User Story 4 --  Track Incidents and Updates

As a user, I want to bookmark incidents and review changes when reports are refreshed so that I can follow situations I care about without repeatedly searching for them.

---

## Use Cases

### Use Case 1 -- Manage Locations and Preferences

**Related user story:** User Story 1

**Actor:** User

**Precondition:** The user has opened their local profile.

**Main flow:**
1. The user chooses to add a location.
2. The user enters a label, location coordinates, and monitoring radius.
3. The application validates the input and saves the location.
4. The user selects preferred incident categories, a recency filter, and ranking preferences.
5. The application saves those preferences for filtering and prioritization.
6. The user can subsequently edit or delete locations and update their preferences.

**Outcome:** The user's saved locations and preferences are available to personalize the dashboard.

**Alternative flow:** Invalid coordinates or a non-positive radius produce a validation message. The location is saved only after correction. Default preferences would apply until the user customizes them.

### Use Case 2 -- View a Prioritized Incident Dashboard

**Related user story:** User Story 2

**Actor:** User

**Precondition:** The user has at least one saved location and incident data has been loaded through the shared retrieval process described in Use Case 4.

**Main flow:**
1. The user opens the dashboard.
2. The application compares available incident coordinates with each saved location's monitoring radius.
3. The application applies the user's category preferences and recency filter.
4. The application calculates a relevance score using documented rules for proximity, recency, category preferences, and source-reported severity where available.
5. The application displays matching incidents in relevance order, with their sources and available reporting times.
6. The user can filter the results by saved location or incident type and select an incident.
7. The application displays its description, source, available timestamps, matching location, and a short explanation of its relevance, such as "Within 500 m of university."
   Outcome: The user can quickly review and understand the reports most relevant to their saved places.

**Alternative flows:**
- If there are no matching reports, the dashboard displays an empty result message.
- If a report has no usable coordinates, the application does not invent a distance or claim that it is within a monitoring radius.
- Missing severity is treated as unknown. Reports are not assigned a high severity solely because severity information is absent.
- If the most recent refresh failed, the dashboard labels retained data with its last successful retrieval time.

### Use Case 3 -- Manage Transit Subscriptions and View Disruptions

**Related user story:** User Story 3

**Actor:** User

**Precondition:** TTC alert data and the route or station identifiers needed for matching are available.

**Main flow:**
1. The user saves a TTC route or station they regularly use.
2. The application stores the subscription.
3. The application compares subscriptions with affected route or stop identifiers supplied by TTC alerts.
4. Matching disruptions appear in a transit section of the dashboard.
5. The user opens an alert to review its description, source, and available effective period.
6. The user can remove subscriptions they no longer need.
   Outcome: The user can identify reported disruptions affecting subscribed transit services.

**Alternative flow:** Alerts that cannot be matched to a specific route or station remain clearly labelled as general advisories, where applicable.

### Use Case 4 -- Track Incidents and Review Updates

**Related user story:** User Story 4

**Actor:** User

**Precondition:** At least one external data source is configured. Incident details must be available to add a bookmark, and a previous successful snapshot is needed to identify changes.

**Main flow:**
1. The user opens an incident and chooses to bookmark it.
2. The application stores a reference to the incident in the user's profile.
3. The user requests a refresh from the dashboard or bookmarks view.
4. The application retrieves available reports from its configured sources and converts supported fields into its internal incident format.
5. The application matches records using their source and source-provided identifier.
6. Reports first observed after the initial load are labelled as new. Meaningful changes to existing reports are recorded as updates.
7. The application refreshes dashboard results and identifies bookmarked incidents with updates.
8. The user opens their bookmarks and selects an incident to review its latest locally stored details and recorded update history.
9. The application records which update the user has reviewed.
10. The user can remove a bookmark when they no longer want to follow the incident.
    Outcome: Selected incidents remain easy to revisit, and the user can distinguish newly observed reports from updates to existing reports.

**Alternative flows:**
- The user can refresh reports without bookmarking an incident. Retrieval and change detection would be shared operations used by both dashboard and tracking views.
- If a source is unavailable, the application retains previously retrieved data and displays its last successful retrieval time. It does not present a failed refresh as confirmation that no incidents exist.
- If an incident no longer appears in its source feed, its last observed details remain available with an appropriate label. Its disappearance alone does not confirm that the incident has been resolved.
- The initial load establishes a baseline; later successful refreshes provide the comparisons needed to identify new and changed records.

---

## Minimal Viable Product (MVP)

The proposed MVP would be a Java desktop application with one local user profile and local persistence.


| Team Member | MVP Use Case |
|-------------|--------------|
| Taimoor | Use Case 1 -- Manage Locations and Preferences |
| Mohid | Use Case 2 -- View a Prioritized Incident Dashboard |
| Lian | Use Case 3 -- Manage Transit Subscriptions and View Disruptions |
| Michael | Use Case 4 -- Track Incidents and Review Updates |

The shared incident model, source adapters, retrieval process, and persistence interfaces would be agreed upon early so that these use cases can be developed together. Use Case 1 would supply the configuration used by Use Case 2; the retrieval and change-detection operations in Use Case 4 would supply updated records for the dashboard and transit views.

The minimum working demonstration would allow a user to save a location and preferences, load supported reports, view a prioritized dashboard, inspect subscribed TTC alerts, bookmark an incident, and review a detected change.

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

Represents a normalized report retrieved from a supported source, including a road incident or transit disruption.

**Proposed attributes:** internal ID, source name, source record ID, source URL, category, title, description, coordinates where available, affected transit identifiers where available, source-reported severity where available, reported time, source update time, effective period where available, last retrieved time, and source-provided status where available.

Unknown fields would remain explicitly unknown. Source identifiers would be combined with source names to avoid collisions between providers.

### Entity 4 -- UserPreferences

Represents how the user wants reports filtered and prioritized.

**Proposed attributes:** preferred categories, recency filter, and relevance-ranking settings.

### Entity 5 -- TransitSubscription
Represents a TTC service the user wants to monitor.

**Proposed attributes:** subscription ID, subscription type, route or station identifier, display name, and associated stop identifiers where needed.

### Entity 6 -- Bookmark
Represents an incident saved by the user.

**Proposed attributes:** bookmark ID, incident ID, saved time, and last reviewed update ID.

### Entity 7 -- IncidentUpdate
Represents a meaningful change observed between successful source refreshes.

**Proposed attributes:** update ID, incident ID, observation time, changed fields, and previous and new values.

A relevance score would be calculated for the current user and matching location, rather than stored as an inherent severity of the incident.

---

## API Usage

The following integrations are proposed. Endpoint access, response fields, licensing requirements, and usage limits would be checked during an initial feasibility task before the implementation scope is finalized.

**API Name:** City of Toronto Road Restrictions Feed

**Documentation:** [Road Restrictions Dataset](https://open.toronto.ca/dataset/road-restrictions/).

**How it is used in the program:**

The application would retrieve supported road restrictions and incident reports, convert them into Incident entities, and use available coordinates and reporting details for location filtering, prioritization, and update tracking. The feed would supply the initial geographically located reports for the MVP.

**API Name:** TTC GTFS-Realtime (GTFS-RT)

**Documentation**: [TTC GTFS-Realtime dataset](https://open.toronto.ca/dataset/ttc-gtfs-realtime-gtfs-rt/)

**Format Reference**: [GTFS Realtime Service Alerts](https://gtfs.org/documentation/realtime/reference/#message-alert)

**How it is used in the program:**

The application would retrieve service alerts and compare their affected transit identifiers with the user's subscriptions. Available descriptions and effective periods would be shown on the dashboard.

The team would confirm route and station coverage before implementation. GTFS-Realtime feeds use Protocol Buffers, so this integration would require an appropriate Java parser. Supporting route or stop reference data may also be needed.

**API Name:** Additional official incident feeds

**Documentation**: 

- [Toronto Fire Active Indicents](https://www.toronto.ca/community-people/public-safety-alerts/alerts-notifications/toronto-fire-active-incidents/)
- [Toronto Police Service Public Safety Data Portal](https://data.tps.ca/)

**How it is used in the program:**

These would be investigated as potential sources for fire reports, police activity, or other supported incident categories. They are candidate sources, rather than confirmed MVP integrations. Any historical datasets would be clearly distinguished from current incident feeds.

All integrations would retain source attribution and available reporting timestamps. Data retrieval and parsing would be separated from filtering and prioritization logic so that each source could be adapted independently.

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

[Any additional project information.]

TBD
