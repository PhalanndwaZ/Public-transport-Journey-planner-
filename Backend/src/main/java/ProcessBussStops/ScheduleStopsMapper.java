package backend;
import java.io.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ScheduleStopsMapper {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    // Map: stop name → coordinates pair (longitude, latitude)
    private Map<String, double[]> stopCoordinates = new HashMap<>();

    // Map aliases or simplified schedule stops -> official stop names
    private Map<String, String> scheduleStopAlias = new HashMap<>();

    public static class StopTime {
        String stopName;
        String timeOrVia;
        double[] coordinates; // [lon, lat]

        StopTime(String stopName, String timeOrVia) {
            this.stopName = stopName;
            this.timeOrVia = timeOrVia;
        }
    }

    // Load stops (from your detailed stops file) into stopCoordinates
    public void loadStopCoordinates(String filePath) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            br.readLine(); // skip header

            while ((line = br.readLine()) != null) {
                String[] parts = line.split(","); // Adjust split if CSV with commas inside fields
                String stopName = parts[4].trim().toUpperCase(); // BUSSTOPDES or BUSSTOPNO
                double lon = Double.parseDouble(parts[6].trim()); // XCOORD
                double lat = Double.parseDouble(parts[7].trim()); // YCOORD
                stopCoordinates.put(stopName, new double[]{lon, lat});
            }
        }
    }

    // Populate manual alias map from schedule summary and known mappings
    public void initAliasMap() {
        scheduleStopAlias.put("BELLVILLE", "BELLVILLE");
        scheduleStopAlias.put("BELLVILLE (SANLAM)", "BELLVILLE (SANLAM)");
        scheduleStopAlias.put("CAPE TOWN", "GOLDEN ACRE"); // Example mapping, adjust accordingly
        scheduleStopAlias.put("ELISIES RIVER", "ELSIES RIVER"); // Correct with your stops
        // Add more aliases for other schedule stops mapping to official names...
    }

    // Resolve stop name using alias map or fallback to input name
    public String resolveStopName(String scheduleStop) {
        return scheduleStopAlias.getOrDefault(scheduleStop.toUpperCase(), scheduleStop.toUpperCase());
    }

    // Map schedule data stops to coordinates using alias map and stopCoordinates map
    public List<StopTime> mapScheduleRowStops(List<String> scheduleStops, List<String> timesOrVia) {
        List<StopTime> mappedStops = new ArrayList<>();
        for (int i = 0; i < scheduleStops.size(); i++) {
            String schedStop = scheduleStops.get(i).toUpperCase();
            String timeVal = timesOrVia.get(i);
            String officialStopName = resolveStopName(schedStop);
            double[] coords = stopCoordinates.get(officialStopName);

            StopTime stopTime = new StopTime(schedStop, timeVal);
            stopTime.coordinates = coords; // could be null if not found
            mappedStops.add(stopTime);

            if (coords == null) {
                System.err.println("Warning: Coordinates not found for stop " + schedStop + " resolved to " + officialStopName);
            }
        }
        return mappedStops;
    }

    // Example Usage
    public static void main(String[] args) throws IOException {
        ScheduleStopsMapper mapper = new ScheduleStopsMapper();
        mapper.loadStopCoordinates("path/to/stops.csv");
        mapper.initAliasMap();

        // Example schedule row data, replace with your file loading
        List<String> scheduleStops = Arrays.asList("BELLVILLE", "BELLVILLE (SANLAM)", "PAROW", "ELSIES RIVER", "GOODWOOD");
        List<String> timesOrVia = Arrays.asList("14:45", "via", "via", "via", "16:00");

        List<StopTime> mappedStops = mapper.mapScheduleRowStops(scheduleStops, timesOrVia);

        for (StopTime st : mappedStops) {
            System.out.println("Stop: " + st.stopName + ", Time: " + st.timeOrVia +
                (st.coordinates == null ? ", Coordinates missing" :
                 ", Coordinates: [" + st.coordinates[0] + ", " + st.coordinates[1] + "]"));
        }
    }
}
