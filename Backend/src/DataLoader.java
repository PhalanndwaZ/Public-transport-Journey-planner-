package backend;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class DataLoader {
    public Map<String, Integer> stops = new HashMap<>(); // stopName -> stopID
    public Map<String, List<Integer>> routes = new HashMap<>(); // routeID -> stopIDs
    public Map<String, Trip> trips = new HashMap<>(); // tripID -> Trip
    public Map<Integer, List<String>> stopToRoutes = new HashMap<>(); // stopID -> routeIDs
    public Map<Integer, StopLocation> stopDetails = new HashMap<>(); // stopID -> Stop (with name + coords)
    private int stopCounter = 0;

    // Simple CSV loader
    public List<String[]> loadCSV(String filePath) throws IOException {
        List<String[]> rows = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                // Basic split on commas (no advanced quoting)
                String[] values = line.split(",", -1); // keep empty fields
                rows.add(values);
            }
        }
        return rows;
    }

    // Load station coordinates from a CSV like: stopName,lat,lon
    public void loadStations(String stationFile) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(stationFile))) {
            String line = br.readLine(); // skip header
            while ((line = br.readLine()) != null) {
                String[] row = line.split(",", -1);
                if (row.length < 3)
                    continue;

                String stopName = row[0].trim();
                double lat = Double.parseDouble(row[1]);
                double lon = Double.parseDouble(row[2]);

                // If this stop is already in the system, reuse ID
                Integer stopId = stops.get(stopName);
                if (stopId == null) {
                    stopId = stopCounter++;
                    stops.put(stopName, stopId);
                }

                stopDetails.put(stopId, new StopLocation(stopId, stopName, lat, lon));
            }
        }
    }

    // Build data from CSV rows
    public void buildData(List<String[]> rows, String mode) {
        if (rows.isEmpty())
            return;

        // Header row (stop names, metadata columns)
        String[] headers = rows.get(0);

        for (int r = 1; r < rows.size(); r++) { // skip header row
            String[] row = rows.get(r);

            if (row.length < 4)
                continue; // skip invalid rows

            String baseTripID = row[0];
            String dayType = row[1].replaceAll("\\s+", "_").toUpperCase();
            String direction = row[2];
            String routeID = row[3];

            String tripID = baseTripID + "_" + dayType;
            // get transport mode ( bus, train etc)
            Trip trip;
            if ("TRAIN".equalsIgnoreCase(mode)) {
                trip = new TrainTrip(tripID, baseTripID, row[1], routeID);
            } else if ("BUS".equalsIgnoreCase(mode)) {
                trip = new BusTrip(tripID, baseTripID, row[1], routeID);
            } else {
                throw new IllegalArgumentException("Unsupported mode: " + mode);
            }

            routes.putIfAbsent(routeID, new ArrayList<>());

            List<StopTime> stopTimes = new ArrayList<>();

            for (int c = 4; c < row.length; c++) { // stops start after metadata columns
                String col = headers[c];
                String raw = row[c] != null ? row[c].trim() : "";

                if (!raw.isEmpty()) {
                    if (!stops.containsKey(col)) {
                        stops.put(col, stopCounter++);
                    }
                    int stopID = stops.get(col);
                    stopTimes.add(new StopTime(stopID, raw));

                    // Add stop to route if not already
                    if (!routes.get(routeID).contains(stopID)) {
                        routes.get(routeID).add(stopID);
                    }

                    // Link stop → route
                    stopToRoutes.putIfAbsent(stopID, new ArrayList<>());
                    if (!stopToRoutes.get(stopID).contains(routeID)) {
                        stopToRoutes.get(stopID).add(routeID);
                    }
                }
            }

            // Reverse order for inbound trips
            if (direction != null && direction.equalsIgnoreCase("inbound")) {
                Collections.reverse(stopTimes);
            }

            trip.times.addAll(stopTimes);
            trips.put(tripID, trip);
        }
    }

    // get all stops back
    public Set<String> getAvailableStops() {
        return new TreeSet<>(stops.keySet());
    }

    // get stop name make it ignore case sensitivity
    public Integer findStopByName(String stopName) {
        for (Map.Entry<String, Integer> entry : stops.entrySet()) {
            if (stopName.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null; // not found
    }

    // convert id to string and returns it
    public String getStopNameById(int stopId) {
        StopLocation stop = stopDetails.get(stopId);
        return stop != null ? stop.getName() : null;
    }

}
