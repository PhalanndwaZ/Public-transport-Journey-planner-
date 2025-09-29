package backend;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

public class TransitSystem {
    private final DataLoader loader;
    private static final double MAX_CONSECUTIVE_WALK_KM = 0.8;
    private static final double MAX_CUMULATIVE_WALK_KM = 6.0;
    private static final double MAX_STOP_LOOKUP_DISTANCE_KM = MAX_CONSECUTIVE_WALK_KM;
    private static final double WALKING_SPEED_KMH = 5.0;
    private static final double ENDPOINT_WALK_THRESHOLD_KM = 0.02;
    private static final double EARTH_RADIUS_KM = 6371.0;

    public TransitSystem() throws IOException {
        loader = new DataLoader();

        String basePath = "CapeTownTransitData/";

        loader.loadStationCoordinates(basePath + "metrorail-stations.csv");

        Path trainDir = Paths.get(basePath + "train-schedules-2014");
        if (Files.exists(trainDir)) {
            try (var stream = Files.list(trainDir)) {
                List<Path> trainFiles = stream
                        .filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".csv"))
                        .sorted()
                        .toList();
                for (Path csv : trainFiles) {
                    loader.buildTrainData(loader.loadCSV(csv.toString()));
                }
            }
        }

        loader.loadBusStopCoordinates(basePath + "myciti-bus-stops.csv");
        loader.loadGABusStopCoordinates(basePath + "ga-bus-stops.csv");

        Path busDir = Paths.get(basePath + "myciti-bus-schedules");
        if (Files.exists(busDir)) {
            try (var stream = Files.list(busDir)) {
                List<Path> busFiles = stream
                        .filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".csv"))
                        .toList();
                for (Path csv : busFiles) {
                    loader.buildBusData(loader.loadCSV(csv.toString()), csv.getFileName().toString(), "MYCITI");
                }
            }
        }

        Path gaDir = Paths.get(basePath + "ga-bus-schedules");
        if (Files.exists(gaDir)) {
            try (var stream = Files.list(gaDir)) {
                List<Path> gaFiles = stream
                        .filter(p -> p.toString().toLowerCase(Locale.ROOT).endsWith(".csv"))
                        .toList();
                for (Path csv : gaFiles) {
                    loader.buildBusData(loader.loadCSV(csv.toString()), csv.getFileName().toString(), "GOLDENARROW");
                }
            }
        }

        loader.purgeInvalidRoutes();
        loader.buildWalkingEdges(MAX_CONSECUTIVE_WALK_KM);
    }

    /** Returns the underlying DataLoader for direct data inspection. */
    public DataLoader getLoader() {
        return loader;
    }

    /** Exposes the default single-segment walking limit used by planners. */
    public static double getDefaultMaxConsecutiveWalkKm() {
        return MAX_CONSECUTIVE_WALK_KM;
    }

    /** Exposes the default total walking limit used by planners. */
    public static double getDefaultMaxCumulativeWalkKm() {
        return MAX_CUMULATIVE_WALK_KM;
    }

    /** Finds a journey between two named stops using baseline walking preferences. */
    public List<PathStep> query(String sourceStopName,
                                String targetStopName,
                                String departureTime,
                                String dateStr) {
        QueryPreferences baseline = QueryPreferences.baseline(MAX_CONSECUTIVE_WALK_KM, MAX_CUMULATIVE_WALK_KM);
        return query(sourceStopName, targetStopName, departureTime, dateStr, baseline);
    }

    /** Finds a journey between named stops using the supplied preferences. */
    public List<PathStep> query(String sourceStopName,
                                String targetStopName,
                                String departureTime,
                                String dateStr,
                                QueryPreferences preferences) {
        Integer sourceId = loader.findStopByName(sourceStopName);
        Integer targetId = loader.findStopByName(targetStopName);

        if (sourceId == null || targetId == null) {
            throw new IllegalArgumentException("Invalid stop name(s).");
        }

        return executeQuery(sourceId, targetId, departureTime, dateStr, preferences);
    }

    /** Finds a journey between geographic coordinates with default walking limits. */
    public List<PathStep> query(double sourceLat,
                                double sourceLng,
                                double targetLat,
                                double targetLng,
                                String departureTime,
                                String dateStr) {
        QueryPreferences baseline = QueryPreferences.baseline(MAX_CONSECUTIVE_WALK_KM, MAX_CUMULATIVE_WALK_KM);
        return query(sourceLat, sourceLng, targetLat, targetLng, departureTime, dateStr, baseline);
    }

    /** Finds a journey between coordinates honoring the provided preferences. */
    public List<PathStep> query(double sourceLat,
                                double sourceLng,
                                double targetLat,
                                double targetLng,
                                String departureTime,
                                String dateStr,
                                QueryPreferences preferences) {
        QueryPreferences effective = preferences != null
                ? preferences
                : QueryPreferences.baseline(MAX_CONSECUTIVE_WALK_KM, MAX_CUMULATIVE_WALK_KM);

        double lookupRadius = effective.allowsWalking()
                ? Math.min(MAX_STOP_LOOKUP_DISTANCE_KM, Math.max(effective.getMaxConsecutiveWalkKm(), 0.0))
                : 0.0;

        Integer sourceId = loader.findNearestStop(sourceLat, sourceLng, lookupRadius);
        Integer targetId = loader.findNearestStop(targetLat, targetLng, lookupRadius);

        if (sourceId == null || targetId == null) {
            throw new IllegalArgumentException("No nearby stop within walking distance.");
        }

        List<PathStep> basePath = executeQuery(sourceId, targetId, departureTime, dateStr, effective);
        if (basePath.isEmpty()) {
            return basePath;
        }

        return addEndpointWalks(basePath, sourceId, targetId, sourceLat, sourceLng, targetLat, targetLng, effective);
    }

    /** Runs the chosen algorithm (RAPTOR or CSA) after resolving day type and filters. */
    private List<PathStep> executeQuery(int sourceId,
                                        int targetId,
                                        String departureTime,
                                        String dateStr,
                                        QueryPreferences preferences) {
        QueryPreferences effective = preferences != null
                ? preferences
                : QueryPreferences.baseline(MAX_CONSECUTIVE_WALK_KM, MAX_CUMULATIVE_WALK_KM);

        if (!effective.isValid()) {
            throw new IllegalArgumentException("Invalid transport preference configuration");
        }

        String dayType = resolveDayType(dateStr);
        Set<String> modeFilter = effective.requiresCSA() ? effective.getAllowedModes() : null;
        Map<String, Trip> filteredTrips = filterTrips(dayType, modeFilter);

        if (filteredTrips.isEmpty()) {
            return Collections.emptyList();
        }

        Result result;
        boolean useCSA = effective.requiresCSA();

        if (useCSA) {
            result = CSAEngine.run(
                    sourceId,
                    targetId,
                    departureTime,
                    filteredTrips,
                    loader.stopDetails,
                    loader.walkingEdges,
                    effective
            );
        } else {
            result = Raptor.runRaptor(
                    sourceId,
                    targetId,
                    departureTime,
                    loader.stops,
                    filteredTrips,
                    loader.stopToRoutes,
                    dayType,
                    loader.walkingEdges,
                    effective.getMaxCumulativeWalkKm(),
                    effective.getMaxConsecutiveWalkKm()
            );
        }

        if (result == null) {
            return Collections.emptyList();
        }

        List<PathStep> path = Raptor.reconstructPath(
                result.predecessor,
                sourceId,
                targetId,
                loader,
                filteredTrips
        );

        if (path.isEmpty()) {
            return path;
        }

        WalkingStats walkingStats = computeWalkingStats(sourceId, targetId, result.predecessor);
        final double WALK_EPS = 1e-6;
        if (walkingStats.totalKm > effective.getMaxCumulativeWalkKm() + WALK_EPS
                || walkingStats.maxConsecutiveKm > effective.getMaxConsecutiveWalkKm() + WALK_EPS) {
            return Collections.emptyList();
        }

        return path;
    }

    /** Augments a path with walking legs between exact coordinates and nearest stops. */
    private List<PathStep> addEndpointWalks(List<PathStep> basePath,
                                            int sourceId,
                                            int targetId,
                                            double sourceLat,
                                            double sourceLng,
                                            double targetLat,
                                            double targetLng,
                                            QueryPreferences preferences) {
        if (basePath == null || basePath.isEmpty()) {
            return basePath;
        }
        if (preferences == null || !preferences.allowsWalking()) {
            return basePath;
        }

        List<PathStep> augmented = new ArrayList<>();
        augmented.addAll(buildOriginWalk(basePath, sourceId, sourceLat, sourceLng, preferences));
        augmented.addAll(basePath);
        augmented.addAll(buildDestinationWalk(basePath, targetId, targetLat, targetLng, preferences));
        return augmented;
    }

    /** Builds the synthetic walking steps from the origin point to the first transit stop. */
    private List<PathStep> buildOriginWalk(List<PathStep> basePath,
                                           int sourceId,
                                           double sourceLat,
                                           double sourceLng,
                                           QueryPreferences preferences) {
        List<PathStep> steps = new ArrayList<>();
        if (basePath.isEmpty()) {
            return steps;
        }
        PathStep first = basePath.get(0);
        if (first.getStopID() < 0) {
            return steps;
        }
        StopLocation sourceStop = loader.stopDetails.get(sourceId);
        if (sourceStop == null) {
            return steps;
        }
        double distanceKm = haversineDistance(sourceLat, sourceLng, sourceStop.getLat(), sourceStop.getLon());
        if (distanceKm < ENDPOINT_WALK_THRESHOLD_KM) {
            return steps;
        }
        if (distanceKm > preferences.getMaxConsecutiveWalkKm() + 1e-6
                || distanceKm > preferences.getMaxCumulativeWalkKm() + 1e-6) {
            return steps;
        }
        int boardMinutes = Raptor.timeToMinutes(first.getTime());
        int walkMinutes = computeWalkMinutes(distanceKm);
        int departMinutes = Math.max(0, boardMinutes - walkMinutes);
        String departTime = Raptor.minutesToTime(departMinutes);
        PathStep origin = new PathStep(
                "WALK",
                -1,
                "Start location",
                departTime,
                sourceLat,
                sourceLng,
                true,
                distanceKm,
                "WALK"
        );
        PathStep reachStop = new PathStep(
                "WALK",
                sourceStop.getID(),
                sourceStop.getName(),
                first.getTime(),
                sourceStop.getLat(),
                sourceStop.getLon(),
                true,
                distanceKm,
                "WALK"
        );
        steps.add(origin);
        steps.add(reachStop);
        return steps;
    }

    /** Builds the walking steps from the final stop to the requested destination point. */
    private List<PathStep> buildDestinationWalk(List<PathStep> basePath,
                                                int targetId,
                                                double targetLat,
                                                double targetLng,
                                                QueryPreferences preferences) {
        List<PathStep> steps = new ArrayList<>();
        if (basePath.isEmpty()) {
            return steps;
        }
        PathStep last = basePath.get(basePath.size() - 1);
        if (last.getStopID() < 0) {
            return steps;
        }
        StopLocation targetStop = loader.stopDetails.get(targetId);
        if (targetStop == null) {
            return steps;
        }
        double distanceKm = haversineDistance(targetLat, targetLng, targetStop.getLat(), targetStop.getLon());
        if (distanceKm < ENDPOINT_WALK_THRESHOLD_KM) {
            return steps;
        }
        if (distanceKm > preferences.getMaxConsecutiveWalkKm() + 1e-6
                || distanceKm > preferences.getMaxCumulativeWalkKm() + 1e-6) {
            return steps;
        }
        int arriveMinutes = Raptor.timeToMinutes(last.getTime());
        int walkMinutes = computeWalkMinutes(distanceKm);
        String finishTime = Raptor.minutesToTime(arriveMinutes + walkMinutes);
        PathStep leaveStop = new PathStep(
                "WALK",
                targetStop.getID(),
                targetStop.getName(),
                last.getTime(),
                targetStop.getLat(),
                targetStop.getLon(),
                true,
                distanceKm,
                "WALK"
        );
        PathStep destination = new PathStep(
                "WALK",
                -2,
                "Destination",
                finishTime,
                targetLat,
                targetLng,
                true,
                distanceKm,
                "WALK"
        );
        steps.add(leaveStop);
        steps.add(destination);
        return steps;
    }

    /** Converts a walking distance in kilometers to whole minutes at the configured speed. */
    private static int computeWalkMinutes(double distanceKm) {
        double minutes = (distanceKm / WALKING_SPEED_KMH) * 60.0;
        return Math.max(1, (int) Math.ceil(minutes));
    }

    /** Computes the great-circle distance between two latitude/longitude points. */
    private static double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
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

    /** Filters trips by day type and allowed transport modes before planning. */
    private Map<String, Trip> filterTrips(String dayType, Set<String> allowedModes) {
        boolean filterByDayType = dayType != null && !dayType.isBlank();
        boolean filterByMode = allowedModes != null && !allowedModes.isEmpty();
        Map<String, Trip> filtered = new HashMap<>();

        for (Map.Entry<String, Trip> entry : loader.trips.entrySet()) {
            Trip trip = entry.getValue();
            if (trip == null) continue;
            if (loader.isRouteInvalid(trip.getRoute())) {
                continue;
            }

            if (filterByDayType && (trip.getDayType() == null
                    || !trip.getDayType().equalsIgnoreCase(dayType))) {
                continue;
            }

            if (filterByMode) {
                String mode = trip.getMode();
                String normalized = mode == null ? "" : mode.toUpperCase(Locale.ROOT);
                if (!allowedModes.contains(normalized)) {
                    continue;
                }
            }

            filtered.put(entry.getKey(), trip);
        }

        return filtered;
    }

    /** Calculates cumulative and maximum consecutive walking distances for a result. */
    private WalkingStats computeWalkingStats(int sourceId,
                                             int targetId,
                                             Map<Integer, Predecessor> predecessors) {
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

    /** Infers a service day category from the supplied date string. */
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
