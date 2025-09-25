// backend/JourneyAPI.java
package backend;

import static spark.Spark.*;
import com.google.gson.Gson;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class JourneyAPI {
    private static final Gson gson = new Gson();

    public static void main(String[] args) throws Exception {        System.out.println("JourneyAPI starting...");
        int port = Integer.getInteger("PORT", 4567);
        port(port);

        enableCORS("*", "*", "*");

        AtomicReference<TransitSystem> systemRef = new AtomicReference<>();
        try {
            systemRef.set(new TransitSystem());
            System.out.println("TransitSystem loaded.");
        } catch (Throwable e) {
            System.err.println("Failed to load TransitSystem: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("TransitSystem present: " + (systemRef.get() != null));

        get("/health", (req, res) -> {
            res.type("application/json");
            boolean ok = systemRef.get() != null;
            return gson.toJson(Map.of("ok", ok));
        });

        get("/journey", (req, res) -> {
            res.type("application/json");

            TransitSystem system = systemRef.get();
            if (system == null) {
                res.status(500);
                return gson.toJson(Map.of("error", "Backend not initialized"));
            }

            String fromLatParam = trim(req.queryParams("fromLat"));
            String fromLngParam = trim(req.queryParams("fromLng"));
            String toLatParam = trim(req.queryParams("toLat"));
            String toLngParam = trim(req.queryParams("toLng"));
            String from = trim(req.queryParams("from"));
            String to   = trim(req.queryParams("to"));
            String time = trim(req.queryParams("time"));
            String date = trim(req.queryParams("date"));

            if (time == null || time.isEmpty()) {
                res.status(400);
                return gson.toJson(Map.of("error", "Missing required query params: time"));
            }

            boolean hasCoordinates = fromLatParam != null && !fromLatParam.isEmpty()
                    && fromLngParam != null && !fromLngParam.isEmpty()
                    && toLatParam != null && !toLatParam.isEmpty()
                    && toLngParam != null && !toLngParam.isEmpty();

            try {
                String dayType = system.resolveDayType(date);
                List<PathStep> path;
                if (hasCoordinates) {
                    double fromLat = Double.parseDouble(fromLatParam);
                    double fromLng = Double.parseDouble(fromLngParam);
                    double toLat = Double.parseDouble(toLatParam);
                    double toLng = Double.parseDouble(toLngParam);

                    path = system.query(fromLat, fromLng, toLat, toLng, time, date);
                } else {
                    if (from == null || to == null) {
                        res.status(400);
                        return gson.toJson(Map.of("error", "Missing required query params: coordinates or place names"));
                    }
                    path = system.query(from, to, time, date);
                }
                if (path == null || path.isEmpty()) {
                    res.status(200);
                    return gson.toJson(Map.of(
                        "routes", List.of(),
                        "message", "No route found"
                    ));
                }

                Map<String, Object> summary = computeSummary(path, dayType);
                List<Map<String, Object>> legs = buildLegs(path, system.getLoader());
                return gson.toJson(Map.of(
                    "routes", List.of(Map.of(
                        "summary", summary,
                        "steps", path,
                        "legs", legs
                    ))
                ));
            } catch (IllegalArgumentException iae) {
                res.status(400);
                return gson.toJson(Map.of("error", iae.getMessage()));
            } catch (Exception e) {
                e.printStackTrace();
                res.status(500);
                return gson.toJson(Map.of("error", "Internal error: " + e.getMessage()));
            }
        });

        try {
            init();
            awaitInitialization();
            System.out.println("JourneyAPI listening on port " + port);
            awaitStop();
        } catch (Exception e) {
            System.err.println("[ERROR] Spark startup failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String trim(String s) {
        return (s == null) ? null : s.trim();
    }

    private static List<Map<String, Object>> buildLegs(List<PathStep> steps, DataLoader loader) {
        List<Map<String, Object>> legs = new ArrayList<>();
        if (steps == null || steps.isEmpty()) {
            return legs;
        }

        Map<String, Object> currentLeg = null;
        List<Map<String, Object>> currentStops = null;
        String currentTrip = null;

        for (PathStep step : steps) {
            String tripId = step.getTripID();
            if (currentLeg == null || !Objects.equals(tripId, currentTrip)) {
                if (currentLeg != null) {
                    finalizeLeg(currentLeg);
                    currentLeg.put("stops", currentStops);
                    legs.add(currentLeg);
                }

                currentLeg = new LinkedHashMap<>();
                currentStops = new ArrayList<>();
                currentTrip = tripId;

                String mode = resolveMode(step);
                currentLeg.put("tripId", tripId);
                currentLeg.put("mode", mode);
                currentLeg.put("line", deriveLine(tripId));
                currentLeg.put("startTime", step.getTime());
                currentLeg.put("startStop", step.getStopName());
                currentLeg.put("startCoords", coordsFor(step, loader));
                currentLeg.put("distanceKm", step.isWalking() ? step.getDistanceKm() : 0.0);
            } else if (step.isWalking()) {
                double existing = ((Number) currentLeg.getOrDefault("distanceKm", 0.0)).doubleValue();
                if (step.getDistanceKm() > existing) {
                    currentLeg.put("distanceKm", step.getDistanceKm());
                }
            }

            Map<String, Object> stopEntry = new LinkedHashMap<>();
            stopEntry.put("time", step.getTime());
            stopEntry.put("stop", step.getStopName());
            stopEntry.put("coords", coordsFor(step, loader));
            currentStops.add(stopEntry);

            currentLeg.put("endTime", step.getTime());
            currentLeg.put("endStop", step.getStopName());
            currentLeg.put("endCoords", coordsFor(step, loader));
        }

        if (currentLeg != null) {
            finalizeLeg(currentLeg);
            currentLeg.put("stops", currentStops);
            legs.add(currentLeg);
        }

        return legs;
    }

    private static void finalizeLeg(Map<String, Object> leg) {
        String start = (String) leg.get("startTime");
        String end = (String) leg.get("endTime");
        Integer duration = minutesBetween(start, end);
        if (duration != null) {
            leg.put("durationMinutes", duration);
        }
    }

    private static Integer minutesBetween(String start, String end) {
        Integer startMin = timeToMinutes(start);
        Integer endMin = timeToMinutes(end);
        if (startMin == null || endMin == null || endMin < startMin) return null;
        return endMin - startMin;
    }

    private static String resolveMode(PathStep step) {
        if (step == null) return "UNKNOWN";
        if (step.isWalking()) return "WALK";
        String mode = step.getMode();
        if (mode == null) return "UNKNOWN";
        mode = mode.toUpperCase(Locale.ROOT);
        if (mode.equals("BUS") || mode.equals("TRAIN")) return mode;
        return "UNKNOWN";
    }

    private static String deriveLine(String tripId) {
        if (tripId == null) return "";
        if (tripId.startsWith("BUS_")) {
            String[] parts = tripId.split("_");
            if (parts.length >= 2) return parts[1];
        }
        if (tripId.startsWith("TRAIN")) {
            return "TRAIN";
        }
        return tripId;
    }

    private static Map<String, Double> coordsFor(PathStep step, DataLoader loader) {
        if (step == null) return Map.of();
        StopLocation loc = loader.stopDetails.get(step.getStopID());
        if (loc == null) return Map.of();
        return Map.of("lat", loc.getLat(), "lon", loc.getLon());
    }

    private static Map<String, Object> computeSummary(List<PathStep> steps, String dayType) {
        if (steps.isEmpty()) return Map.of();
        String firstT = steps.get(0).getTime();
        String lastT  = steps.get(steps.size() - 1).getTime();
        int duration = timeToMinutes(lastT) - timeToMinutes(firstT);

        int transfers = 0;
        String prevTrip = steps.get(0).getTripID();
        for (int i = 1; i < steps.size(); i++) {
            if (!Objects.equals(prevTrip, steps.get(i).getTripID())) transfers++;
            prevTrip = steps.get(i).getTripID();
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("start", firstT);
        summary.put("end", lastT);
        summary.put("durationMinutes", duration);
        summary.put("transfers", transfers);
        if (dayType != null && !dayType.isBlank()) {
            summary.put("dayType", dayType);
        }
        return summary;
    }

    private static int timeToMinutes(String hhmm) {
        try {
            String[] p = hhmm.split(":");
            return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
        } catch (Exception e) {
            return 0;
        }
    }

    private static void enableCORS(final String origin, final String methods, final String headers) {
        options("/*", (request, response) -> {
            String reqHeaders = request.headers("Access-Control-Request-Headers");
            if (reqHeaders != null) response.header("Access-Control-Allow-Headers", reqHeaders);
            String reqMethod = request.headers("Access-Control-Request-Method");
            if (reqMethod != null) response.header("Access-Control-Allow-Methods", reqMethod);
            return "OK";
        });
        before((request, response) -> {
            response.header("Access-Control-Allow-Origin", origin);
            response.header("Access-Control-Request-Method", methods);
            response.header("Access-Control-Allow-Headers", headers);
        });
    }
}


