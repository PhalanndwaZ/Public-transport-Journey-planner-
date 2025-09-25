package backend;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

public class TransitSystem {
    // This class loads all the data from buses to trains and returns a big dataset
    private final DataLoader loader;
    private static final double MAX_CONSECUTIVE_WALK_KM = 0.8;
    private static final double MAX_CUMULATIVE_WALK_KM = 2.0;
    private static final double MAX_STOP_LOOKUP_DISTANCE_KM = MAX_CONSECUTIVE_WALK_KM;

    public TransitSystem() throws IOException {
        loader = new DataLoader();

        // Base directory for transit data (relative path)
        String basePath = "CapeTownTransitData/";

        // 1. Load station coordinates first
        loader.loadStationCoordinates(basePath + "metrorail-stations.csv");

        // Train CSVs (relative paths)
        List<String> trainCSVs = Arrays.asList(
            basePath + "train-schedules-2014/S1.csv",
            basePath + "train-schedules-2014/S2.csv",
            basePath + "train-schedules-2014/S3.csv",
            basePath + "train-schedules-2014/S4.csv",
            basePath + "train-schedules-2014/S5.csv",
            basePath + "train-schedules-2014/S6.csv",
            basePath + "train-schedules-2014/S7.csv",
            basePath + "train-schedules-2014/S8.csv",
            basePath + "train-schedules-2014/S9.csv",
            basePath + "train-schedules-2014/S10.csv",
            basePath + "train-schedules-2014/S11.csv",
            basePath + "train-schedules-2014/S12.csv"
        );

        loader.loadBusStopCoordinates(basePath + "myciti-bus-stops.csv");
        loader.loadGABusStopCoordinates(basePath + "ga-bus-stops.csv");

        // Load train data
        for (String file : trainCSVs) {
            loader.buildTrainData(loader.loadCSV(file));
        }

        // Load all MyCiTi schedules dynamically
        Path busDir = Paths.get(basePath + "myciti-bus-schedules");
        if (Files.exists(busDir)) {
            try (var stream = Files.list(busDir)) {
                List<Path> busFiles = stream
                        .filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".csv"))
                        .toList();
                for (Path csv : busFiles) {
                    loader.buildBusData(loader.loadCSV(csv.toString()), csv.getFileName().toString());
                }
            }
        }

        // Load all Golden Arrow schedules
        Path gaDir = Paths.get(basePath + "ga-bus-schedules");
        if (Files.exists(gaDir)) {
            try (var stream = Files.list(gaDir)) {
                List<Path> gaFiles = stream
                        .filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".csv"))
                        .toList();
                for (Path csv : gaFiles) {
                    loader.buildBusData(loader.loadCSV(csv.toString()), csv.getFileName().toString());
                }
            }
        }

        // Build walking edges to allow transfers between nearby stops
        loader.buildWalkingEdges(MAX_CONSECUTIVE_WALK_KM);
    }

    public DataLoader getLoader() {
        return loader;
    }

    // Updated query method with simplified day type handling
    public List<PathStep> query(String sourceStopName, String targetStopName, String departureTime, String dateStr) {
        Integer sourceId = loader.findStopByName(sourceStopName);
        Integer targetId = loader.findStopByName(targetStopName);

        if (sourceId == null || targetId == null) {
            throw new IllegalArgumentException("Invalid stop name(s).");
        }

        String dayType = resolveDayType(dateStr);
        return executeQuery(sourceId, targetId, departureTime, dayType);
    }

    public List<PathStep> query(double sourceLat, double sourceLng, double targetLat, double targetLng,
                                String departureTime, String dateStr) {
        Integer sourceId = loader.findNearestStop(sourceLat, sourceLng, MAX_STOP_LOOKUP_DISTANCE_KM);
        Integer targetId = loader.findNearestStop(targetLat, targetLng, MAX_STOP_LOOKUP_DISTANCE_KM);

        if (sourceId == null || targetId == null) {
            throw new IllegalArgumentException("No nearby stop within walking distance.");
        }

        String dayType = resolveDayType(dateStr);
        return executeQuery(sourceId, targetId, departureTime, dayType);
    }

    private List<PathStep> executeQuery(int sourceId, int targetId, String departureTime, String dayType) {
        System.out.println("[DEBUG] Using dayType=" + dayType);

        Map<String, Trip> filteredTrips = new HashMap<>();
        for (var entry : loader.trips.entrySet()) {
            Trip trip = entry.getValue();
            if (trip.dayType.equalsIgnoreCase(dayType)) {
                filteredTrips.put(entry.getKey(), trip);
            }
        }

        System.out.println("[DEBUG] Raptor running with dayType=" + dayType +
                " | Matching trips: " + filteredTrips.size() + "/" + loader.trips.size());

        Result result = Raptor.runRaptor(
                sourceId,
                targetId,
                departureTime,
                loader.stops,
                filteredTrips,
                loader.stopToRoutes,
                dayType,
                loader.walkingEdges,
                MAX_CUMULATIVE_WALK_KM,
                MAX_CONSECUTIVE_WALK_KM
        );

        WalkingStats walkingStats = computeWalkingStats(sourceId, targetId, result.predecessor);
        final double WALK_EPS = 1e-6;
        if (walkingStats.totalKm > MAX_CUMULATIVE_WALK_KM + WALK_EPS
                || walkingStats.maxConsecutiveKm > MAX_CONSECUTIVE_WALK_KM + WALK_EPS) {
            System.out.println(String.format(Locale.ROOT,
                    "[DEBUG] Discarding route due to walking limits: total=%.3f km, consecutive=%.3f km",
                    walkingStats.totalKm, walkingStats.maxConsecutiveKm));
            return Collections.emptyList();
        }

        return Raptor.reconstructPath(
                result.predecessor,
                sourceId,
                targetId,
                loader,
                filteredTrips
        );
    }

    private WalkingStats computeWalkingStats(int sourceId, int targetId, Map<Integer, Predecessor> predecessors) {
        if (predecessors == null || predecessors.isEmpty()) {
            return new WalkingStats(0.0, 0.0);
        }

        double total = 0.0;
        double consecutive = 0.0;
        double maxConsecutive = 0.0;
        Set<Integer> visited = new HashSet<>();

        int cursor = targetId;
        while (cursor != sourceId && predecessors.containsKey(cursor) && visited.add(cursor)) {
            Predecessor step = predecessors.get(cursor);
            if (step.walking) {
                total += step.walkingDistanceKm;
                consecutive += step.walkingDistanceKm;
                if (consecutive > maxConsecutive) {
                    maxConsecutive = consecutive;
                }
            } else {
                consecutive = 0.0;
            }
            cursor = step.from;
        }

        return new WalkingStats(total, maxConsecutive);
    }

    private static final class WalkingStats {
        final double totalKm;
        final double maxConsecutiveKm;

        WalkingStats(double totalKm, double maxConsecutiveKm) {
            this.totalKm = totalKm;
            this.maxConsecutiveKm = maxConsecutiveKm;
        }
    }

    public String resolveDayType(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return "WEEKDAY";

        try {
            LocalDate parsed = LocalDate.parse(dateStr.trim());
            if (SouthAfricanHolidays.isPublicHoliday(parsed)) {
                return "PUBLIC_HOLIDAY";
            }
            DayOfWeek dow = parsed.getDayOfWeek();
            return switch (dow) {
                case SATURDAY -> "SATURDAY";
                case SUNDAY -> "SUNDAY";
                default -> "WEEKDAY";
            };
        } catch (Exception e) {
            String normalized = dateStr.trim().toUpperCase(Locale.ROOT);
            if (normalized.contains("HOLIDAY")) return "PUBLIC_HOLIDAY";
            if (normalized.contains("SAT")) return "SATURDAY";
            if (normalized.contains("SUN")) return "SUNDAY";
            return "WEEKDAY";
        }
    }
}




