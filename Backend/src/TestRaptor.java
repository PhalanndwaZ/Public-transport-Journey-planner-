package backend;

import java.util.*;

public class TestRaptor {
// Debug function
// Remove-Item bin\* -Recurse -Force to clean the bin after compiling 
// javac -d bin src/*.java - to compile again 
// java -cp bin src.TestRaptor MUTUAL "CAPE TOWN" 07:21 to test the times on each occaaion 






    public static void debugJourneyPlanning(
            int sourceStop,
            int targetStop,
            String departureTime,
            Map<String, Integer> stops,
            Map<String, Trip> trips,
            Map<Integer, List<String>> stopToRoutes,
            DataLoader loader
    ) {
        System.out.println("\n🔍 COMPREHENSIVE DEBUG");
        System.out.println("Source: " + sourceStop + " (" + loader.getStopNameById(sourceStop) + ")");
        System.out.println("Target: " + targetStop + " (" + loader.getStopNameById(targetStop) + ")");
        System.out.println("Departure: " + departureTime);

        // 1. Check if stops exist
        System.out.println("\n1️⃣ STOP VALIDATION");
        System.out.println("Source stop exists: " + stopToRoutes.containsKey(sourceStop));
        System.out.println("Target stop exists: " + stopToRoutes.containsKey(targetStop));

        // 2. Routes from source
        System.out.println("\n2️⃣ ROUTES FROM SOURCE");
        List<String> sourceRoutes = stopToRoutes.getOrDefault(sourceStop, new ArrayList<>());
        System.out.println("Routes from source: " + String.join(", ", sourceRoutes));

        // 3. All valid trips from source
        System.out.println("\n3️⃣ ALL VALID TRIPS FROM SOURCE");
        int departureMinutes = Raptor.timeToMinutes(departureTime);

        for (String routeID : sourceRoutes) {
            System.out.println("\n--- Route " + routeID + " ---");
            List<Trip> routeTrips = new ArrayList<>();
            for (Trip t : trips.values()) {
                if (t.route.equals(routeID)) {
                    routeTrips.add(t);
                }
            }

            List<Trip> validTrips = new ArrayList<>();
            for (Trip trip : routeTrips) {
                StopTime sourceStopInTrip = trip.times.stream()
                        .filter(t -> t.stopID == sourceStop)
                        .findFirst().orElse(null);

                if (sourceStopInTrip != null) {
                    int tripDepartureTime = Raptor.timeToMinutes(sourceStopInTrip.time);
                    boolean isValid = tripDepartureTime >= departureMinutes;

                    System.out.println("Trip " + trip.tripID + ": departs " +
                            sourceStopInTrip.time + " (" + (isValid ? "✅ VALID" : "❌ TOO EARLY") + ")");

                    if (isValid) {
                        validTrips.add(trip);
                    }
                }
            }

            System.out.println("Valid trips: " + validTrips.size());
            for (Trip trip : validTrips) {
                StringBuilder sb = new StringBuilder();
                for (StopTime st : trip.times) {
                    sb.append(loader.getStopNameById(st.stopID)).append("@").append(st.time).append(" → ");
                }
                String routeStops = sb.substring(0, sb.length() - 3);
                System.out.println("  🚆 " + trip.tripID + " @ " + trip.times.get(0).time);
                System.out.println("     Route: " + routeStops);

                // Direct connection check
                StopTime targetInTrip = trip.times.stream()
                        .filter(t -> t.stopID == targetStop)
                        .findFirst().orElse(null);
                if (targetInTrip != null) {
                    System.out.println("     🎯 DIRECT CONNECTION TO TARGET @ " + targetInTrip.time);
                }
            }
        }

        // 4. Routes to target
        System.out.println("\n4️⃣ ROUTES TO TARGET");
        List<String> targetRoutes = stopToRoutes.getOrDefault(targetStop, new ArrayList<>());
        System.out.println("Routes serving target: " + String.join(", ", targetRoutes));

        // 5. Direct connections
        System.out.println("\n5️⃣ DIRECT CONNECTIONS");
        List<String> commonRoutes = new ArrayList<>(sourceRoutes);
        commonRoutes.retainAll(targetRoutes);
        System.out.println("Common routes: " + String.join(", ", commonRoutes));

        for (String routeID : commonRoutes) {
            System.out.println("\nDirect trips on route " + routeID + ":");
            for (Trip trip : trips.values()) {
                if (!trip.route.equals(routeID)) continue;

                StopTime sourceStopInTrip = trip.times.stream()
                        .filter(t -> t.stopID == sourceStop)
                        .findFirst().orElse(null);
                StopTime targetStopInTrip = trip.times.stream()
                        .filter(t -> t.stopID == targetStop)
                        .findFirst().orElse(null);

                if (sourceStopInTrip != null && targetStopInTrip != null) {
                    int sourceDep = Raptor.timeToMinutes(sourceStopInTrip.time);
                    int targetArr = Raptor.timeToMinutes(targetStopInTrip.time);
                    boolean isValidTime = sourceDep >= departureMinutes;
                    boolean isForward = targetArr > sourceDep;

                    System.out.println("Trip " + trip.tripID + ": " +
                            sourceStopInTrip.time + " → " + targetStopInTrip.time +
                            (isValidTime && isForward ? " ✅" : " ❌"));
                }
            }
        }

        // 6. Data integrity sample
        System.out.println("\n6️⃣ DATA INTEGRITY CHECK");
        int count = 0;
        for (Trip trip : trips.values()) {
            System.out.print("Trip " + trip.tripID + " sample: ");
            for (int i = 0; i < Math.min(5, trip.times.size()); i++) {
                StopTime st = trip.times.get(i);
                System.out.print(st.stopID + "@" + st.time + " → ");
            }
            System.out.println();
            if (++count >= 3) break;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: java TestRaptor <sourceStop> <targetStop> <HH:MM>");
            return;
        }

        String sourceStopName = args[0];
        String targetStopName = args[1];
        String departureTime = args[2];

        DataLoader loader = new DataLoader();
        String filePath = "C:\\Users\\Phalanndwa Zwivhuya\\Documents\\School stuff\\computer science\\3rd year\\2025\\CSC3003S\\Data\\CapeTownTransitData\\train-schedules-2014\\S1.csv";
        List<String[]> rows = loader.loadCSV(filePath);
        loader.buildData(rows);

        // Build stopToRoutes (already built in DataLoader, but let's copy it)
        Map<Integer, List<String>> stopToRoutes = loader.stopToRoutes;

        Integer sourceStop = loader.findStopByName(sourceStopName.toUpperCase());
        Integer targetStop = loader.findStopByName(targetStopName.toUpperCase());

        if (sourceStop == null || targetStop == null) {
            System.out.println("❌ Could not find source or target stop");
            System.out.println("Available stops (sample): " + loader.getAvailableStops());
            return;
        }

        System.out.println("\n=== Planning Journey from " +
                sourceStopName + " to " + targetStopName + " at " + departureTime + " ===");

        // Debugging
        debugJourneyPlanning(sourceStop, targetStop, departureTime,
                loader.stops, loader.trips, stopToRoutes, loader);

        // Run RAPTOR
        Result result = Raptor.runRaptor(
                sourceStop, targetStop, departureTime,
                loader.stops, loader.trips, stopToRoutes
        );

        if (result.earliestArrival[targetStop] == Integer.MAX_VALUE) {
            System.out.println("❌ No path found");
        } else {
            System.out.println("✅ Earliest arrival: " +
                    Raptor.minutesToTime(result.earliestArrival[targetStop]));
            List<PathStep> path = Raptor.reconstructPath(
                    result.predecessor, sourceStop, targetStop, loader, loader.trips
            );
            for (int i = 0; i < path.size(); i++) {
                System.out.println((i + 1) + ". " + path.get(i));
            }
        }
    }
}
