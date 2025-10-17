Public Transport Journey Planner — Local Setup

Prerequisites


Java 17 (JDK)
Maven 3.8+
A simple static server for the frontend (choose one):

Python 3: python -m http.server 5500

Node.js: npx http-server -p 5500 Frontend --cors




The backend will create and use a local data folder at CapeTownTransitData/. You can manage schedules via the Admin Portal once the backend is running.

Backend (Java + Maven)
From the project root:

cd Backend
mvn clean install
mvn exec:java


The server starts on http://localhost:4567 by default.
Change port (optional):

mvn exec:java -DPORT=8080


Health check:

Open http://localhost:4567/health in a browser. Expected JSON: { "ok": true } (once data is loaded).

Key endpoints:


GET /journey — journey planning

GET /admin/schedules — list schedules

POST /admin/schedules/add — upload CSV schedule

POST /admin/schedules/update — replace CSV schedule

POST /admin/schedules/delete — delete schedule

POST /admin/schedules/restore — restore from bin

POST /admin/schedules/reload — reload transit data


Frontend (static site)
Serve the Frontend directory with any static server (examples below), then open the Home page.
Option A — Python 3:

cd Frontend
python -m http.server 5500


Option B — Node.js http-server:

npx http-server -p 5500 Frontend --cors


Open the app:

http://localhost:5500/HomePage.html

Search flow:

Enter From/To (autocomplete is constrained to Cape Town).
Set Date and Time.
Select transport modes (Bus/Train) and optional walking limit.
Click “Plan journey” → redirects to Search.html which calls the backend and then opens Results.html.

Admin portal (optional):

Open http://localhost:5500/AdminPortal.html to view, add, replace, delete, and restore schedule CSVs. The backend must be running.


API — /journey
GET /journey
Required (either coordinates or place names):

Coordinates: fromLat, fromLng, toLat, toLng

Place names: from, to (the UI uses coordinates)

Always required:


date — e.g., 2025-09-23


time — e.g., 07:30


Optional:


modes — comma-separated, e.g., BUS,TRAIN


maxWalkMeters — integer meters per walking segment

Example:

http://localhost:4567/journey?fromLat=-33.9249&fromLng=18.4241&toLat=-33.9140&toLng=18.5110&date=2025-09-23&time=07:30&modes=BUS,TRAIN&maxWalkMeters=800



CORS and Ports

Backend CORS is configured to allow any local origin (localhost / 127.0.0.1) on any port.
The frontend fetches the backend at http://localhost:4567 by default.
If you change the backend port via -DPORT=..., update the fetch URL in Frontend/Search.html (the string http://localhost:4567/journey).


Troubleshooting

“Cannot reach backend”:

Ensure the backend is running and http://localhost:4567/health returns a response.
Serve the frontend over HTTP (not file://). Use one of the static server options above.
Check Windows firewall prompts; allow Java/Maven to listen on the port.
Verify ports aren’t in use (default backend 4567, example frontend 5500).


Data not loading / empty results:

Use AdminPortal.html to add schedules. After upload/update/delete, the backend reloads data automatically.




Project Layout


Backend/ — Java backend (SparkJava, Gson)

Frontend/ — Static HTML/CSS/JS UI (HomePage.html, Search.html, Results.html, AdminPortal.html)

CapeTownTransitData/ — Local data directory created/used by the backend


Public Transport Journey Planner
A multimodal journey-planning toolkit built for Cape Town public transport. The project couples a Java backend that parses GTFS-like CSV schedules for MyCiTi, Golden Arrow, and Metrorail with a lightweight HTML/CSS/JS frontend that surfaces trip plans, interactive maps, and an admin workflow for maintaining timetable files.

Highlights


Door-to-door journey planning: Accepts named places or coordinates, enforces walking limits, and selects between RAPTOR and CSA algorithms transparently.

Multiple agencies: Distinguishes MyCiTi and Golden Arrow bus routes, alongside Metrorail services, while filtering out schedules that lack valid stop coordinates.

Interactive results: Presents route legs, operators, and timing in a responsive layout with Google Maps overlays for each leg.

Schedule administration: An admin portal lets operators upload, replace, delete, restore, and reload schedule files with built-in trash management.


Repository layout



Path
Description




Backend/
Java 17 Maven project exposing the REST API and data ingestion pipeline.


Backend/CapeTownTransitData/
Raw CSV assets (stations, train & bus timetables, admin trash area).


Frontend/
Static HTML, CSS, and vanilla JS pages for search, results, and administration.




Backend

Prerequisites

Java 17 JDK
Maven 3.8+


Build & run

cd Backend
mvn -q compile          # optional sanity check
mvn exec:java           # launches backend.JourneyAPI on http://localhost:4567


Default port is 4567 (overridable with -DPORT=xxxx). CORS is enabled for the static frontend.

Key components


backend.JourneyAPI � Spark Java service exposing journey and admin endpoints, enriching legs with operator metadata, and orchestrating reloads.

backend.DataLoader � Consolidates CSV datasets, normalises stop names, rejects routes missing coordinates, calculates walking edges, and records bus operators.

backend.Raptor / backend.CSAEngine � Route-finding algorithms and path reconstruction helpers.


API summary



Method
Route
Purpose




GET /health
Liveness check.



GET /journey
Journey planning for coordinates or stop names (requires time, optional date, mode & walking params).



GET /admin/schedules
Lists known schedule types, current uploads, and the trash bin.



POST /admin/schedules/add
Multipart upload of a new schedule file.



POST /admin/schedules/update
Replace an existing schedule with a new CSV.



POST /admin/schedules/delete
Soft-delete a schedule (sends it to .trash).



POST /admin/schedules/restore
Recover a recently deleted file.



POST /admin/schedules/reload
Forces a reload of all datasets from disk.





Data expectations


metrorail-stations.csv provides station coordinates.

train-schedules-2014/*.csv contain train trips.

myciti-bus-schedules/*.csv and ga-bus-schedules/*.csv contain bus timetables for MyCiTi and Golden Arrow respectively (filenames inform operator labelling).
Admin actions move deleted files into CapeTownTransitData/.trash/ with metadata.json for restoration.

Routes with stops that resolve to (0, 0) are automatically flagged and purged to keep search results valid.

Frontend
The frontend is a static bundle of HTML/CSS/JS pages � no build tools required.

Pages


HomePage.html � Landing form with Google Places autocomplete (inputs saved to localStorage).

Search.html � Transitional loader that submits the backend /journey request and stores the response.

Results.html � Displays the itinerary, leg breakdown, operators, and map. Requires the backend to be running and a Google Maps JavaScript API key.

AdminPortal.html � Schedule management dashboard targeting the admin endpoints.


Using the frontend locally

Ensure the backend is running on http://localhost:4567.
Open Frontend/HomePage.html directly in a browser or host the Frontend/ directory via a simple static server.

Google Maps API key: The project currently references a placeholder key in HomePage.html and Results.html. Replace it with your own Maps JavaScript API key (with Places and Directions access) before deploying publicly.


Development workflows


Compile check: cd Backend && mvn -q -DskipTests compile (used throughout this repo).

Refreshing data: Place new CSV files under the appropriate CapeTownTransitData subdirectory and hit Reload Schedules in the admin portal.

Operator labelling: Bus routes inherit operator names from the directory they were loaded from (MyCiTi vs Golden Arrow). The frontend leverages this to show agency names instead of a generic �Bus�.


Known limitations

No automated test suite yet � rely on manual QA and mvn compile for regression checks.
Frontend relies on browser localStorage; clearing storage will remove the most recent journey context.
The Google Maps API key must be supplied by the developer; quota errors surface as map-loading failures.


Contributing

Fork or create a new branch.
Keep backend changes accompanied by mvn compile to catch build errors.
For frontend tweaks, test both desktop and mobile breakpoints (the results layout switches below 1080px).
Submit a pull request with a concise summary of changes and manual verification steps.


License
