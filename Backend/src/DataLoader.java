package backend;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class DataLoader {
    public Map<String, Integer> stops = new HashMap<>();
    public Map<String, List<Integer>> routes = new HashMap<>();
    public Map<String, Trip> trips = new HashMap<>();
    public Map<Integer, List<String>> stopToRoutes = new HashMap<>();
    public Map<Integer, StopLocation> stopDetails = new HashMap<>();
    public Map<String, StopLocation> stopNameToDetails = new HashMap<>();

    // Coordinate lookup maps from the file
    private Map<String, double[]> stationNameMap = new HashMap<>(); // Station Name -> coords
    private Map<String, double[]> stationIdMap = new HashMap<>(); // StationID -> coords
    private Set<String> standaloneStations = new HashSet<>(); // Stations without coords
    private int stopCounter = 0;

    // ============= Generic CSV Loader =============
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
    public void loadStationCoordinates(String filePath) throws IOException {
        stationNameMap.clear();
        stationIdMap.clear();
        standaloneStations.clear();

        List<String[]> rows = loadCSV(filePath);
        if (rows.isEmpty())
            return;

        for (int r = 1; r < rows.size(); r++) {
            String[] row = rows.get(r);

            // Handle rows with coordinates (4+ columns)
            if (row.length >= 4) {
                String stationName = row[0].trim().toUpperCase();
                String stationId = row[1].trim().toUpperCase();
                String latStr = row[2].trim();
                String lonStr = row[3].trim();

                if (!latStr.isEmpty() && !lonStr.isEmpty()) {
                    try {
                        double lat = Double.parseDouble(latStr);
                        double lon = Double.parseDouble(lonStr);

                        stationNameMap.put(stationName, new double[] { lat, lon });
                        stationIdMap.put(stationId, new double[] { lat, lon });
                    } catch (NumberFormatException e) {
                        // Skip invalid coordinates
                    }
                }
            }
            // Handle standalone station names (without coordinates)
            else if (row.length >= 1) {
                String stationName = row[0].trim().toUpperCase();
                if (!stationName.isEmpty()) {
                    standaloneStations.add(stationName);
                }
            }
        }
    }

    // ============= Smart Coordinate Lookup =============
    private double[] findCoordinates(String trainStopName) {
        String normalized = trainStopName.trim().toUpperCase();

        // 1. Direct match with station names
        if (stationNameMap.containsKey(normalized)) {
            return stationNameMap.get(normalized);
        }

        // 2. Check if it's in standalone stations (names without coords)
        if (standaloneStations.contains(normalized)) {
            // This station exists but has no coordinates in the file
            return null;
        }

        // 3. Try fuzzy matching with station names
        for (String stationName : stationNameMap.keySet()) {
            if (isNameMatch(normalized, stationName)) {
                return stationNameMap.get(stationName);
            }
        }

        // 4. Try fuzzy matching with station IDs
        for (String stationId : stationIdMap.keySet()) {
            if (isNameMatch(normalized, stationId)) {
                return stationIdMap.get(stationId);
            }
        }

        return null; // No match found
    }

    // ============= Smart Name Matching =============
    private boolean isNameMatch(String trainName, String coordName) {
        // Remove common words and spaces for better matching
        String cleanTrainName = cleanName(trainName);
        String cleanCoordName = cleanName(coordName);

        // Exact match after cleaning
        if (cleanTrainName.equals(cleanCoordName)) {
            return true;
        }

        // Partial match - one contains the other
        if (cleanTrainName.contains(cleanCoordName) || cleanCoordName.contains(cleanTrainName)) {
            return true;
        }

        // Check if they start with the same 3+ characters
        if (cleanTrainName.length() >= 3 && cleanCoordName.length() >= 3) {
            if (cleanTrainName.substring(0, 3).equals(cleanCoordName.substring(0, 3))) {
                return true;
            }
        }

        return false;
    }

    private String cleanName(String name) {
        return name.replaceAll("\\s+", "")
                .replace("RD", "")
                .replace("ROAD", "")
                .replace("ST", "")
                .replace("STREET", "");
    }

    // ============= Unified Stop Creation =============
    private int createStop(String stopName) {
        String normalizedName = stopName.trim().toUpperCase();

        if (stops.containsKey(normalizedName)) {
            return stops.get(normalizedName);
        }

        // Find coordinates using smart lookup
        double[] coords = findCoordinates(normalizedName);
        double lat = (coords != null) ? coords[0] : 0.0;
        double lon = (coords != null) ? coords[1] : 0.0;

        // Create stop with coordinates
        int stopIdx = stopCounter++;
        StopLocation location = new StopLocation(stopIdx, normalizedName, lat, lon);

        stops.put(normalizedName, stopIdx);
        stopDetails.put(stopIdx, location);
        stopNameToDetails.put(normalizedName, location);

        return stopIdx;
    }

    // ============= Train Loader =============
    public void buildTrainData(List<String[]> rows) {
        if (rows.isEmpty())
            return;
        String[] headers = rows.get(0);

        for (int r = 1; r < rows.size(); r++) {
            String[] row = rows.get(r);
            if (row.length < 4)
                continue;

            String baseTripID = row[0];
            String dayType = row[1].replaceAll("\\s+", "_").toUpperCase();
            String direction = row[2];
            String routeID = row[3];
            String tripID = baseTripID + "_" + dayType;

            Trip trip = new TrainTrip(tripID, baseTripID, row[1], routeID);
            routes.putIfAbsent(routeID, new ArrayList<>());
            List<StopTime> stopTimes = new ArrayList<>();

            for (int c = 4; c < row.length; c++) {
                String col = headers[c];
                String raw = row[c] != null ? row[c].trim() : "";
                if (!raw.isEmpty()) {
                    String stopName = col.trim().toUpperCase();
                    int stopID = createStop(stopName);
                    stopTimes.add(new StopTime(stopID, raw));

                    if (!routes.get(routeID).contains(stopID)) {
                        routes.get(routeID).add(stopID);
                    }
                    stopToRoutes.putIfAbsent(stopID, new ArrayList<>());
                    if (!stopToRoutes.get(stopID).contains(routeID)) {
                        stopToRoutes.get(stopID).add(routeID);
                    }
                }
            }

            if ("inbound".equalsIgnoreCase(direction)) {
                Collections.reverse(stopTimes);
            }
            trip.times.addAll(stopTimes);
            trips.put(tripID, trip);
        }
    }

    // ============= Bus Loader =============
    public void buildBusData(List<String[]> rows) {
        if (rows.isEmpty())
            return;

        Map<String, BusTrip> busTrips = new HashMap<>();

        for (int r = 1; r < rows.size(); r++) {
            String[] row = rows.get(r);
            if (row.length < 9)
                continue;

            String routeCode = row[1];
            String routeName = row[2];
            String stopId = row[3];
            String stopDesc = row[4].trim();
            String type = row[5];
            int ordinal = Integer.parseInt(row[6]);
            double xcoord = Double.parseDouble(row[7]);
            double ycoord = Double.parseDouble(row[8]);

            busTrips.putIfAbsent(routeCode,
                    new BusTrip("BUS_" + routeCode, "BUS_" + routeCode, "WEEKDAY", routeCode, routeName));
            BusTrip trip = busTrips.get(routeCode);

            String normalizedStopDesc = stopDesc.toUpperCase();
            int stopIdx = createStop(normalizedStopDesc);

            // Update coordinates if they were missing and we have them from bus data
            StopLocation existingLoc = stopDetails.get(stopIdx);
            if (existingLoc != null && existingLoc.getLat() == 0.0 && existingLoc.getLon() == 0.0) {
                stopDetails.put(stopIdx, new StopLocation(stopIdx, normalizedStopDesc, ycoord, xcoord));
            }

            BusStopTime stopTime = new BusStopTime(stopId, stopDesc, type, ordinal, xcoord, ycoord, null);
            trip.addStop(stopTime);

            routes.putIfAbsent(routeCode, new ArrayList<>());
            if (!routes.get(routeCode).contains(stopIdx)) {
                routes.get(routeCode).add(stopIdx);
            }

            stopToRoutes.putIfAbsent(stopIdx, new ArrayList<>());
            if (!stopToRoutes.get(stopIdx).contains(routeCode)) {
                stopToRoutes.get(stopIdx).add(routeCode);
            }
        }

        for (BusTrip trip : busTrips.values()) {
            trip.times.sort(Comparator.comparingInt(st -> {
                if (st instanceof BusStopTime) {
                    return ((BusStopTime) st).ordinal;
                } else {
                    return Integer.MAX_VALUE;
                }
            }));
            trips.put(trip.tripID, trip);
        }
    }

    // ============= Helpers =============
    public Set<String> getAvailableStops() {
        return new TreeSet<>(stops.keySet());
    }

    public Integer findStopByName(String stopName) {
        if (stopName == null)
            return null;
        return stops.get(stopName.toUpperCase());
    }

    public String getStopNameById(int stopId) {
        StopLocation stop = stopDetails.get(stopId);
        return stop != null ? stop.getName() : null;
    }
}