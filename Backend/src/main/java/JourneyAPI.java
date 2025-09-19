// backend/JourneyAPI.java
package backend;

import static spark.Spark.*;
import com.google.gson.Gson;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class JourneyAPI {
    private static final Gson gson = new Gson();

    public static void main(String[] args) throws Exception {
        // PORT (default 4567). Allow override: java -DPORT=8080 -jar app.jar
        int port = Integer.getInteger("PORT", 4567);
        port(port);

        // Basic CORS for local static HTML
        enableCORS("*", "*", "*");

        // Load transit system ONCE (singleton-ish)
        AtomicReference<TransitSystem> systemRef = new AtomicReference<>();
        try {
            systemRef.set(new TransitSystem()); // uses your fixed relative paths
            System.out.println("TransitSystem loaded.");
        } catch (Exception e) {
            System.err.println("Failed to load TransitSystem: " + e.getMessage());
            e.printStackTrace();
        }

        // Health check
        get("/health", (req, res) -> {
            res.type("application/json");
            boolean ok = systemRef.get() != null;
            return gson.toJson(Map.of("ok", ok));
        });

        // Journey endpoint: /journey?from=CAPE%20TOWN&to=MAITLAND&time=04:31
        get("/journey", (req, res) -> {
            res.type("application/json");

            TransitSystem system = systemRef.get();
            if (system == null) {
                res.status(500);
                return gson.toJson(Map.of("error", "Backend not initialized"));
            }

            String from = trim(req.queryParams("from"));
            String to   = trim(req.queryParams("to"));
            String time = trim(req.queryParams("time"));

            if (from == null || to == null || time == null) {
                res.status(400);
                return gson.toJson(Map.of("error", "Missing required query params: from, to, time"));
            }

            try {
                var path = system.query(from, to, time); // returns List<PathStep>
                if (path == null || path.isEmpty()) {
                    res.status(200);
                    return gson.toJson(Map.of(
                        "routes", List.of(),
                        "message", "No route found"
                    ));
                }
                // Optionally derive duration & transfers
                Map<String, Object> summary = computeSummary(path);
                return gson.toJson(Map.of(
                    "routes", List.of(Map.of(
                        "summary", summary,
                        "steps", path
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
    }

    private static String trim(String s) {
        return (s == null) ? null : s.trim();
    }

    private static Map<String, Object> computeSummary(List<PathStep> steps) {
        // duration (first time -> last time), transfers = count tripID changes
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
        return Map.of(
            "start", firstT,
            "end", lastT,
            "durationMinutes", duration,
            "transfers", transfers
        );
    }

    // simple "HH:mm" -> minutes
    private static int timeToMinutes(String hhmm) {
        try {
            String[] p = hhmm.split(":");
            return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
        } catch (Exception e) {
            return 0;
        }
    }

    // Minimal CORS helper
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
