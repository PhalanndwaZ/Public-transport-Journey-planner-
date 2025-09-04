package src;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;




public class DataLoader {
    public Map<String, Integer> stops = new HashMap<>();               // stopName -> stopID
    public Map<String, List<Integer>> routes = new HashMap<>();        // routeID -> stopIDs
    public Map<String, Trip> trips = new HashMap<>();                  // tripID -> Trip
    public Map<Integer, List<String>> stopToRoutes = new HashMap<>();  // stopID -> routeIDs
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

    // Build data from CSV rows
    public void buildData(List<String[]> rows) {
        if (rows.isEmpty()) return;

        // Header row (stop names, metadata columns)
        String[] headers = rows.get(0);

        for (int r = 1; r < rows.size(); r++) { // skip header row
            String[] row = rows.get(r);

            if (row.length < 4) continue; // skip invalid rows

            String baseTripID = row[0];
            String dayType = row[1].replaceAll("\\s+", "_").toUpperCase();
            String direction = row[2];
            String routeID = row[3];

            String tripID = baseTripID + "_" + dayType;
            Trip trip = new Trip(tripID, baseTripID, row[1], routeID);

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

    public Set<String> getAvailableStops() {
        return new TreeSet<>(stops.keySet());
    }

    public Integer findStopByName(String stopName) {
        return stops.get(stopName);
    }

    public String getStopNameById(int stopId) {
        for (Map.Entry<String, Integer> entry : stops.entrySet()) {
            if (entry.getValue() == stopId) return entry.getKey();
        }
        return null;
    }
}
