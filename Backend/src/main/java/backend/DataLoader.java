package backend;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class DataLoader {
    public Map<String, Integer> stops = new HashMap<>();
    public Map<String, List<Integer>> routes = new HashMap<>();
    public Map<String, Trip> trips = new HashMap<>();
    public Map<Integer, List<String>> stopToRoutes = new HashMap<>();
    public Map<Integer, StopLocation> stopDetails = new HashMap<>();
    public Map<String, StopLocation> stopNameToDetails = new HashMap<>();
    public Map<Integer, List<WalkingEdge>> walkingEdges = new HashMap<>();
    private final Set<String> invalidRoutes = new HashSet<>();
    /**
     * Records the operating agency for each bus route (e.g., MyCiTi or Golden Arrow).
     * Keys are normalized route identifiers to stay consistent across loaders.
     */
    private final Map<String, String> routeOperators = new HashMap<>();

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double WALKING_SPEED_KMH = 5.0;

    private void storeStationCoordinate(String rawName, double lat, double lon) {
        if (rawName == null) return;
        String normalized = rawName.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) return;
        stationNameMap.put(normalized, new double[]{lat, lon});
        String cleaned = cleanName(normalized);
        if (!cleaned.isEmpty()) {
            stationNameMap.putIfAbsent(cleaned, new double[]{lat, lon});
        }
    }

    private Map<String, double[]> stationNameMap = new HashMap<>();
    private Map<String, double[]> stationIdMap = new HashMap<>();
    private Set<String> standaloneStations = new HashSet<>();
    private int stopCounter = 0;

    // ============= Generic CSV Loader =============
    /** Reads a CSV file from disk while preserving empty columns for downstream loaders. */

    public List<String[]> loadCSV(String filePath) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",", -1);
                rows.add(values);
            }
        }
        return rows;
    }

    // ============= Station Coordinates Loader =============
    /** Parses the station coordinate CSV and seeds lookup maps for trains and standalone stations. */

    public void loadStationCoordinates(String filePath) throws IOException {
        stationNameMap.clear();
        stationIdMap.clear();
        standaloneStations.clear();

        List<String[]> rows = loadCSV(filePath);
        if (rows.isEmpty()) return;

        for (int r = 1; r < rows.size(); r++) {
            String[] row = rows.get(r);
            if (row.length >= 4) {
                String stationName = row[0].trim().toUpperCase();
                String stationId = row[1].trim().toUpperCase();
                String latStr = row[2].trim();
                String lonStr = row[3].trim();

                if (!latStr.isEmpty() && !lonStr.isEmpty()) {
                    try {
                        double lat = Double.parseDouble(latStr);
                        double lon = Double.parseDouble(lonStr);
                        storeStationCoordinate(stationName, lat, lon);
                        stationIdMap.put(stationId, new double[]{lat, lon});
                    } catch (NumberFormatException e) {
                        // Ignore invalid coords
                    }
                }
            } else if (row.length >= 1) {
                String stationName = row[0].trim().toUpperCase();
                if (!stationName.isEmpty()) standaloneStations.add(stationName);
            }
        }
    }

    /** Loads MyCiTi stop coordinates and stores them for later stop matching. */

    public void loadBusStopCoordinates(String filePath) throws IOException {
        List<String[]> rows = loadCSV(filePath);
        if (rows.isEmpty()) return;

        for (int r = 1; r < rows.size(); r++) {
            String[] row = rows.get(r);
            if (row.length < 4) continue;

            String name = row[1].trim().toUpperCase();
            String lonStr = row[2].trim();
            String latStr = row[3].trim();

            if (name.isEmpty() || lonStr.isEmpty() || latStr.isEmpty()) continue;
            try {
                double lon = Double.parseDouble(lonStr);
                double lat = Double.parseDouble(latStr);
                storeStationCoordinate(name, lat, lon);
            } catch (NumberFormatException ignored) {
                // skip malformed entries
            }
        }
    }

    /** Extracts Golden Arrow stop coordinates using header-driven indices. */

    public void loadGABusStopCoordinates(String filePath) throws IOException {
        List<String[]> rows = loadCSV(filePath);
        if (rows.isEmpty()) return;

        String[] headers = rows.get(0);
        int nameIdx = -1;
        int lonIdx = -1;
        int latIdx = -1;

        for (int i = 0; i < headers.length; i++) {
            String header = headers[i] != null ? headers[i].trim().toUpperCase(Locale.ROOT) : "";
            if (header.equals("BUSSTOPDES")) {
                nameIdx = i;
            } else if (header.equals("XCOORD")) {
                lonIdx = i;
            } else if (header.equals("YCOORD")) {
                latIdx = i;
            }
        }

        if (nameIdx == -1 || lonIdx == -1 || latIdx == -1) return;

        int maxIdx = Math.max(nameIdx, Math.max(lonIdx, latIdx));
        for (int r = 1; r < rows.size(); r++) {
            String[] row = rows.get(r);
            if (row.length <= maxIdx) continue;

            String name = row[nameIdx] != null ? row[nameIdx].trim().toUpperCase(Locale.ROOT) : "";
            String lonStr = row[lonIdx] != null ? row[lonIdx].trim() : "";
            String latStr = row[latIdx] != null ? row[latIdx].trim() : "";

            if (name.isEmpty() || lonStr.isEmpty() || latStr.isEmpty()) continue;

            try {
                double lon = Double.parseDouble(lonStr);
                double lat = Double.parseDouble(latStr);
                storeStationCoordinate(name, lat, lon);
            } catch (NumberFormatException ignored) {
                // skip malformed entries
            }
        }
    }

    // ============= Smart Coordinate Lookup =============
    /** Attempts to resolve a stop name to a cached latitude/longitude pair. */

    private double[] findCoordinates(String trainStopName) {
        String normalized = trainStopName.trim().toUpperCase();

        if (stationNameMap.containsKey(normalized)) return stationNameMap.get(normalized);
        String cleaned = cleanName(normalized);
        if (stationNameMap.containsKey(cleaned)) return stationNameMap.get(cleaned);
        if (standaloneStations.contains(normalized)) return null;

        for (String stationName : stationNameMap.keySet()) {
            if (isNameMatch(normalized, stationName)) return stationNameMap.get(stationName);
        }
        for (String stationId : stationIdMap.keySet()) {
            if (isNameMatch(normalized, stationId)) return stationIdMap.get(stationId);
        }
        return null;
    }

    /** Performs loose string matching to catch near-identical stop names and identifiers. */

    private boolean isNameMatch(String a, String b) {
        String cleanA = cleanName(a);
        String cleanB = cleanName(b);
        if (cleanA.equals(cleanB)) return true;
        int minLen = Math.min(cleanA.length(), cleanB.length());
        int lenDiff = Math.abs(cleanA.length() - cleanB.length());
        if (minLen >= 5 && (cleanA.contains(cleanB) || cleanB.contains(cleanA))) return true;
        if (lenDiff <= 2 && (cleanA.startsWith(cleanB) || cleanB.startsWith(cleanA))) return true;
        return false;
    }

    /** Strips whitespace and common suffixes so name comparisons are more forgiving. */

    private String cleanName(String name) {
        return name.replaceAll("\\s+", "")
                .replace("RD", "")
                .replace("ROAD", "")
                .replace("ST", "")
                .replace("STREET", "");
    }

    private String normalizeRouteId(String routeId) {
        if (routeId == null) return null;
        String normalized = routeId.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private void flagRouteIfInvalidCoordinates(String routeId, int stopId) {
        String normalized = normalizeRouteId(routeId);
        if (normalized == null) return;
        StopLocation location = stopDetails.get(stopId);
        if (location == null) return;
        if (!hasValidCoordinates(location.getLat(), location.getLon())) {
            invalidRoutes.add(normalized);
        }
    }

    /**
     * Normalizes an operator label so lookups stay case-insensitive and trimmed.
     */
    private String normalizeOperatorLabel(String operator) {
        if (operator == null) return null;
        String normalized = operator.trim().toUpperCase(Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * Returns the stored operator label (if any) for the given route identifier.
     */
    public String getRouteOperator(String routeId) {
        String normalized = normalizeRouteId(routeId);
        if (normalized == null) return null;
        return routeOperators.get(normalized);
    }

    /** Coerces raw day-type labels into the canonical values used across the system. */

    private String normalizeDayType(String rawDayType) {
        if (rawDayType == null || rawDayType.trim().isEmpty()) return "WEEKDAY";

        String normalized = rawDayType.trim().toUpperCase(Locale.ROOT);
        String collapsed = normalized.replaceAll("[^A-Z]", "");

        switch (collapsed) {
            case "SATURDAY":
            case "SATURDAYS":
                return "SATURDAY";
            case "SUNDAY":
            case "SUNDAYS":
            case "SUNDAYSANDPUBLICHOLIDAYS":
                return "SUNDAY";
            case "PUBLICHOLIDAY":
            case "PUBLICHOLIDAYS":
                return "PUBLIC_HOLIDAY";
            case "FRIDAY":
            case "FRIDAYS":
            case "WEEKDAY":
            case "WEEKDAYS":
            case "MONDAY":
            case "MONDAYS":
            case "TUESDAY":
            case "TUESDAYS":
            case "WEDNESDAY":
            case "WEDNESDAYS":
            case "THURSDAY":
            case "THURSDAYS":
            case "MONDAYTOFRIDAY":
            case "MONDAYSTOFRIDAY":
            case "MONDAYSTOFRIDAYS":
            case "MONDAYTOFRIDAYS":
            case "MONDAYSTOTHURSDAY":
            case "MONDAYSTOTHURSDAYS":
                return "WEEKDAY";
            default:
                if (collapsed.contains("HOLIDAY")) return "PUBLIC_HOLIDAY";
                System.err.println("[WARN] Unknown day type: " + rawDayType + " -> defaulting to WEEKDAY");
                return "WEEKDAY";
        }
    }

    // ============= Unified Stop Creation =============
    /** Allocates a new stop ID, records its coordinates, and caches lookups if unseen. */

    private int createStop(String stopName) {
        String normalized = stopName.trim().toUpperCase();
        if (stops.containsKey(normalized)) return stops.get(normalized);

        double[] coords = findCoordinates(normalized);
        double lat = (coords != null) ? coords[0] : 0.0;
        double lon = (coords != null) ? coords[1] : 0.0;

        int stopIdx = stopCounter++;
        StopLocation location = new StopLocation(stopIdx, normalized, lat, lon);

        stops.put(normalized, stopIdx);
        stopDetails.put(stopIdx, location);
        stopNameToDetails.put(normalized, location);
        return stopIdx;
    }

    // ============= Train Loader =============
    /** Transforms train CSV rows into Trip instances and stop-time sequences. */

    public void buildTrainData(List<String[]> rows) {
        if (rows.isEmpty()) return;
        String[] headers = rows.get(0);

        for (int r = 1; r < rows.size(); r++) {
            String[] row = rows.get(r);
            if (row.length < 4) continue;

            String baseTripID = safeValue(row, 0);
            String rawDayType = safeValue(row, 1);
            String normalizedDayType = normalizeDayType(rawDayType);

            String direction = safeValue(row, 2);
            String routeID = normalizeRouteId(safeValue(row, 3));
            if (routeID == null) continue;
            String tripID = baseTripID + "_" + normalizedDayType;

            Trip trip = new TrainTrip(tripID, baseTripID, normalizedDayType, routeID);
            routes.putIfAbsent(routeID, new ArrayList<>());
            List<StopTime> stopTimes = new ArrayList<>();

            for (int c = 4; c < row.length; c++) {
                String col = headers[c];
                String raw = row[c] != null ? row[c].trim() : "";
                if (!raw.isEmpty()) {
                    String stopName = col.trim().toUpperCase();
                    int stopID = createStop(stopName);
                    flagRouteIfInvalidCoordinates(routeID, stopID);
                    stopTimes.add(new StopTime(stopID, raw));

                    if (!routes.get(routeID).contains(stopID)) routes.get(routeID).add(stopID);
                    stopToRoutes.putIfAbsent(stopID, new ArrayList<>());
                    if (!stopToRoutes.get(stopID).contains(routeID)) stopToRoutes.get(stopID).add(routeID);
                }
            }

            if ("inbound".equalsIgnoreCase(direction)) Collections.reverse(stopTimes);
            trip.times.addAll(stopTimes);
            trips.put(tripID, trip);
        }

        // ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Â ÃƒÂ¢Ã¢â€šÂ¬Ã¢â€žÂ¢ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€šÃ‚Â¦ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã¢â‚¬Â¦ÃƒÂ¢Ã¢â€šÂ¬Ã…â€œÃƒÆ’Ã†â€™Ãƒâ€ Ã¢â‚¬â„¢ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã†â€™Ãƒâ€šÃ‚Â¢ÃƒÆ’Ã‚Â¢ÃƒÂ¢Ã¢â‚¬Å¡Ã‚Â¬Ãƒâ€¦Ã‚Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¬ÃƒÆ’Ã†â€™ÃƒÂ¢Ã¢â€šÂ¬Ã…Â¡ÃƒÆ’Ã¢â‚¬Å¡Ãƒâ€šÃ‚Â¦ Debug print for trip distribution by normalized dayType
        Map<String, Long> dayTypeCounts = trips.values().stream()
                .collect(Collectors.groupingBy(t -> t.dayType, Collectors.counting()));
        System.out.println("[DEBUG] Trips loaded per dayType: " + dayTypeCounts);
    }

    // ============= Bus Loader =============
/**
     * Ingests a bus schedule CSV into the unified transit dataset.
     * @param rows parsed CSV rows where columns contain stop names and times.
     * @param routeFileName source filename used to derive the route identifier.
     * @param operatorLabel label for the agency operating this file (e.g., MyCiTi or Golden Arrow).
     */
    public void buildBusData(List<String[]> rows, String routeFileName, String operatorLabel) {
        if (rows.isEmpty()) return;

        String routeKey = normalizeRouteId(routeFileName.replace(".csv", "").toUpperCase(Locale.ROOT));
        if (routeKey == null) {
            return;
        }
        routes.putIfAbsent(routeKey, new ArrayList<>());
        String normalizedOperator = normalizeOperatorLabel(operatorLabel);
        if (normalizedOperator != null) {
            routeOperators.put(routeKey, normalizedOperator);
        }

        String[] headers = rows.get(0);
        boolean hasRouteNumberColumn = headers.length > 0 && headers[0] != null && headers[0].trim().equalsIgnoreCase("route_number");
        boolean hasDayTypeOnly = headers.length > 0 && headers[0] != null && headers[0].trim().equalsIgnoreCase("day_type");

        boolean includeRouteNumber = hasRouteNumberColumn || (!hasDayTypeOnly && headers.length > 1);
        int dayTypeIndex = includeRouteNumber ? 1 : 0;
        int dataStartIndex = includeRouteNumber ? 2 : 1;

        for (int r = 1; r < rows.size(); r++) {
            String[] row = rows.get(r);
            if (row.length <= dayTypeIndex) continue;

            String routeNumber = includeRouteNumber ? safeValue(row, 0) : routeKey;
            String rawDayType = safeValue(row, dayTypeIndex);
            if (rawDayType.isEmpty()) continue;

            String normalizedDayType = normalizeDayType(rawDayType);
            String tripPrefix = includeRouteNumber ? "BUS" : "GABS";
            String tripId = String.format(Locale.ROOT, "%s_%s_%s_%d", tripPrefix, routeKey, normalizedDayType, r);

            BusTrip trip = new BusTrip(tripId, routeNumber.isEmpty() ? routeKey : routeNumber, normalizedDayType, routeKey, routeFileName);

            List<StopTime> stopTimes = buildStopTimesWithEstimation(headers, row, dataStartIndex, routeKey);
            if (!stopTimes.isEmpty()) {
                trip.times.addAll(stopTimes);
                trips.put(tripId, trip);
            }
        }
    }

    /** Builds stop times for a bus route, interpolating VIA gaps where necessary. */

    private List<StopTime> buildStopTimesWithEstimation(String[] headers, String[] row, int startColumn, String routeKey) {
        List<StopTime> stopTimes = new ArrayList<>();
        List<Integer> routeStops = routes.get(routeKey);

        List<String> stopNames = new ArrayList<>();
        List<String> rawValues = new ArrayList<>();

        for (int c = startColumn; c < headers.length; c++) {
            String stopNameRaw = headers[c];
            if (stopNameRaw == null) continue;
            String stopName = stopNameRaw.trim();
            if (stopName.isEmpty()) continue;

            stopNames.add(stopName);
            String value = c < row.length && row[c] != null ? row[c].trim() : "";
            rawValues.add(value);
        }

        int size = stopNames.size();
        Integer[] minutes = new Integer[size];
        boolean[] viaMarkers = new boolean[size];

        for (int i = 0; i < size; i++) {
            String value = rawValues.get(i);
            if (value.isEmpty()) continue;
            if (isTimeValue(value)) {
                minutes[i] = timeToMinutes(value);
            } else if (isViaValue(value)) {
                viaMarkers[i] = true;
            }
        }

        interpolateViaTimes(minutes, viaMarkers);

        for (int i = 0; i < size; i++) {
            if (minutes[i] == null) continue;

            String stopName = stopNames.get(i).trim().toUpperCase(Locale.ROOT);
            int stopId = createStop(stopName);
            flagRouteIfInvalidCoordinates(routeKey, stopId);
            String timeValue = minutesToTime(minutes[i]);
            stopTimes.add(new StopTime(stopId, timeValue));

            if (!routeStops.contains(stopId)) routeStops.add(stopId);

            stopToRoutes.computeIfAbsent(stopId, k -> new ArrayList<>());
            if (!stopToRoutes.get(stopId).contains(routeKey)) stopToRoutes.get(stopId).add(routeKey);
        }

        return stopTimes;
    }

    /** Fills in VIA-labelled timetable gaps by interpolating between known times. */

    private void interpolateViaTimes(Integer[] minutes, boolean[] viaMarkers) {
        int n = minutes.length;
        for (int i = 0; i < n; i++) {
            if (!viaMarkers[i] || minutes[i] != null) continue;

            int start = i;
            while (i < n && viaMarkers[i] && minutes[i] == null) {
                i++;
            }
            int prevIdx = start - 1;
            Integer prevVal = null;
            while (prevIdx >= 0) {
                if (minutes[prevIdx] != null) {
                    prevVal = minutes[prevIdx];
                    break;
                }
                prevIdx--;
            }

            int nextIdx = i;
            Integer nextVal = null;
            while (nextIdx < n) {
                if (minutes[nextIdx] != null) {
                    nextVal = minutes[nextIdx];
                    break;
                }
                nextIdx++;
            }

            if (prevVal == null || nextVal == null) {
                continue;
            }

            int gap = nextIdx - prevIdx;
            double step = (nextVal - prevVal) / (double) gap;
            int lastValue = prevVal;
            for (int offset = 1; offset < gap; offset++) {
                int currentIdx = prevIdx + offset;
                if (currentIdx >= n) break;
                if (!viaMarkers[currentIdx] || minutes[currentIdx] != null) {
                    if (minutes[currentIdx] != null) {
                        lastValue = minutes[currentIdx];
                    }
                    continue;
                }
                int estimate = (int) Math.round(prevVal + step * offset);
                if (estimate <= lastValue) {
                    estimate = lastValue + 1;
                }
                minutes[currentIdx] = estimate;
                lastValue = estimate;
            }
        }
    }

    /** Detects whether a raw timetable cell represents a VIA marker. */

    private boolean isViaValue(String value) {
        if (value == null) return false;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return normalized.equals("VIA") || normalized.equals("VIA.") || normalized.equals("VIA*");
    }

    /** Determines whether a string looks like an HH:MM time entry. */

    private boolean isTimeValue(String value) {
        if (value == null) return false;
        String trimmed = value.trim();
        int colon = trimmed.indexOf(':');
        if (colon <= 0 || colon == trimmed.length() - 1) return false;
        try {
            int hh = Integer.parseInt(trimmed.substring(0, colon));
            int mm = Integer.parseInt(trimmed.substring(colon + 1));
            return hh >= 0 && hh <= 29 && mm >= 0 && mm < 60;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** Converts an HH:MM string into minutes-since-midnight. */

    private int timeToMinutes(String value) {
        String trimmed = value.trim();
        int colon = trimmed.indexOf(':');
        int hh = Integer.parseInt(trimmed.substring(0, colon));
        int mm = Integer.parseInt(trimmed.substring(colon + 1));
        return hh * 60 + mm;
    }

    /** Formats minutes-since-midnight back into HH:MM. */

    private String minutesToTime(int minutes) {
        int hh = minutes / 60;
        int mm = minutes % 60;
        return String.format(Locale.ROOT, "%02d:%02d", hh, mm);
    }

    /** Safely pulls a trimmed column value or returns an empty string when missing. */

    private String safeValue(String[] row, int idx) {
        if (idx < 0 || idx >= row.length) return "";
        String value = row[idx];
        return value == null ? "" : value.trim();
    }

    // ============= Walking Edge Builder =============
    public void purgeInvalidRoutes() {
        if (invalidRoutes.isEmpty()) {
            return;
        }

        Set<String> toDrop = new LinkedHashSet<>(invalidRoutes);
        int routesBefore = routes.size();
        int tripsBefore = trips.size();

        for (String routeId : toDrop) {
            routes.remove(routeId);
            routeOperators.remove(routeId);
        }

        stopToRoutes.replaceAll((stopId, routeList) -> {
            routeList.removeIf(toDrop::contains);
            return routeList;
        });

        trips.entrySet().removeIf(entry -> toDrop.contains(entry.getValue().getRoute()));

        int routesAfter = routes.size();
        int tripsAfter = trips.size();
        System.out.println("[WARN] Dropped routes with missing coordinates: " + toDrop);
        System.out.println("[WARN] Routes count: " + routesBefore + " -> " + routesAfter
                + ", trips count: " + tripsBefore + " -> " + tripsAfter);
    }

    public boolean isRouteInvalid(String routeId) {
        String normalized = normalizeRouteId(routeId);
        return normalized != null && invalidRoutes.contains(normalized);
    }

    public Set<String> getInvalidRoutes() {
        return Collections.unmodifiableSet(invalidRoutes);
    }

    /** Generates walking edges between stops that are within the configured distance. */

    public void buildWalkingEdges(double maxDistanceKm) {
        walkingEdges.clear();

        List<StopLocation> locations = stopDetails.values().stream()
                .filter(loc -> hasValidCoordinates(loc.getLat(), loc.getLon()))
                .collect(Collectors.toList());

        for (int i = 0; i < locations.size(); i++) {
            StopLocation a = locations.get(i);
            for (int j = i + 1; j < locations.size(); j++) {
                StopLocation b = locations.get(j);

                double distanceKm = haversineDistance(
                        a.getLat(), a.getLon(),
                        b.getLat(), b.getLon());

                if (distanceKm == 0.0 || distanceKm > maxDistanceKm) continue;

                int minutes = (int) Math.ceil((distanceKm / WALKING_SPEED_KMH) * 60.0);
                if (minutes <= 0) minutes = 1;

                addWalkingEdge(a.getID(), b.getID(), distanceKm, minutes);
                addWalkingEdge(b.getID(), a.getID(), distanceKm, minutes);
            }
        }

        int totalEdges = walkingEdges.values().stream().mapToInt(List::size).sum();
        System.out.println("[DEBUG] Walking edges built: " + totalEdges);
    }

    /** Registers a directional walking edge with distance metadata. */

    private void addWalkingEdge(int fromStopId, int toStopId, double distanceKm, int minutes) {
        walkingEdges.computeIfAbsent(fromStopId, k -> new ArrayList<>())
                .add(new WalkingEdge(fromStopId, toStopId, minutes, distanceKm));
    }

    private boolean hasValidCoordinates(double lat, double lon) {
        return !(lat == 0.0 && lon == 0.0);
    }

    /** Computes the great-circle distance between two latitude/longitude points. */

    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double rLat1 = Math.toRadians(lat1);
        double rLat2 = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(rLat1) * Math.cos(rLat2)
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    // ============= Helpers =============
    public Set<String> getAvailableStops() {
        return new TreeSet<>(stops.keySet());
    }

    public Integer findStopByName(String stopName) {
        if (stopName == null) return null;
        String normalized = stopName.trim().toUpperCase().replace(",", "");
        if (normalized.contains("STATION")) {
            normalized = normalized.substring(0, normalized.indexOf("STATION")).trim();
        }
        if (stops.containsKey(normalized)) return stops.get(normalized);

        for (String stop : stops.keySet()) {
            if (normalized.contains(stop) || stop.contains(normalized)) return stops.get(stop);
        }
        for (String stop : stops.keySet()) {
            if (stop.length() >= 3 && normalized.length() >= 3 &&
                    stop.substring(0, 3).equals(normalized.substring(0, 3))) {
                return stops.get(stop);
            }
        }
        return null;
    }

    public String getStopNameById(int stopId) {
        StopLocation stop = stopDetails.get(stopId);
        return stop != null ? stop.getName() : null;
    }

    public Integer findNearestStop(double lat, double lng, double maxDistanceKm) {
        Integer bestId = null;
        double bestDistance = Double.MAX_VALUE;

        for (StopLocation loc : stopDetails.values()) {
            double stopLat = loc.getLat();
            double stopLng = loc.getLon();
            if (stopLat == 0.0 && stopLng == 0.0) continue;

            double distanceKm = haversineDistance(lat, lng, stopLat, stopLng);
            if (distanceKm <= maxDistanceKm && distanceKm < bestDistance) {
                bestDistance = distanceKm;
                bestId = loc.getID();
            }
        }

        return bestId;
    }

}
