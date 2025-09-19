package backend;

import java.util.*;

public class Raptor {

    // Convert "HH:MM" → minutes since midnight
    public static int timeToMinutes(String time) {
        String[] parts = time.split(":");
        int hh = Integer.parseInt(parts[0]);
        int mm = Integer.parseInt(parts[1]);
        return hh * 60 + mm;
    }

    // Convert minutes since midnight → "HH:MM"
    public static String minutesToTime(int minutes) {
        int hh = minutes / 60;
        int mm = minutes % 60;
        return String.format("%02d:%02d", hh, mm);
    }

    /**
     * RAPTOR Algorithm
     * @param sourceStop stopID of source
     * @param targetStop stopID of target
     * @param departureTime "HH:MM"
     * @param stops stopName -> stopID
     * @param trips tripID -> Trip
     * @param stopToRoutes stopID -> [routeIDs]
     */
    public static Result runRaptor(
            int sourceStop,
            int targetStop,
            String departureTime,
            Map<String, Integer> stops,
            Map<String, Trip> trips,
            Map<Integer, List<String>> stopToRoutes
    ) {
        final int INF = Integer.MAX_VALUE;
        int numStops = stops.size();
        int departureMinutes = timeToMinutes(departureTime);

        int[] earliestArrival = new int[numStops];
        Arrays.fill(earliestArrival, INF);

        Map<Integer, Predecessor> predecessor = new HashMap<>();
        earliestArrival[sourceStop] = departureMinutes;

        int MAX_TRANSFERS = 5;
        Set<Integer> marked = new HashSet<>();
        marked.add(sourceStop);

        for (int round = 0; round < MAX_TRANSFERS; round++) {
            Set<Integer> nextMarked = new HashSet<>();

            for (int stop : marked) {
                List<String> routes = stopToRoutes.getOrDefault(stop, new ArrayList<>());

                for (String routeID : routes) {
                    // Collect all trips on this route
                    List<Trip> allRouteTrips = new ArrayList<>();
                    for (Trip t : trips.values()) {
                        if (t.route.equals(routeID)) {
                            allRouteTrips.add(t);
                        }
                    }

                    // Sort by departure time at current stop
                    allRouteTrips.sort(Comparator.comparingInt(t -> {
                        return t.times.stream()
                                .filter(st -> st.stopID == stop)
                                .map(st -> timeToMinutes(st.time))
                                .findFirst().orElse(INF);
                    }));

                    for (Trip trip : allRouteTrips) {
                        List<StopTime> times = trip.times;
                        int stopIdx = -1;

                        for (int i = 0; i < times.size(); i++) {
                            if (times.get(i).stopID == stop) {
                                stopIdx = i;
                                break;
                            }
                        }
                        if (stopIdx == -1) continue;

                        int boardTime = timeToMinutes(times.get(stopIdx).time);

                        // Boarding condition
                        if (stop == sourceStop) {
                            if (boardTime < departureMinutes) continue;
                        } else {
                            if (boardTime < earliestArrival[stop]) continue;
                        }

                        // Ensure trip goes forward in time
                        if (stopIdx < times.size() - 1) {
                            int nextStopTime = timeToMinutes(times.get(stopIdx + 1).time);
                            if (nextStopTime <= boardTime) continue;
                        }

                        // Ride forward
                        for (int i = stopIdx + 1; i < times.size(); i++) {
                            int stopID = times.get(i).stopID;
                            int arrTime = timeToMinutes(times.get(i).time);

                            if (arrTime <= boardTime) continue;

                            if (arrTime < earliestArrival[stopID]) {
                                earliestArrival[stopID] = arrTime;
                                predecessor.put(stopID,
                                        new Predecessor(trip.tripID, stop, arrTime, boardTime));
                                nextMarked.add(stopID);
                            }
                        }
                    }
                }
            }

            if (nextMarked.isEmpty()) break;
            marked = nextMarked;
        }

        return new Result(earliestArrival, predecessor);
    }

    /**
 * Reconstruct path from predecessor info
 */
     public static List<PathStep> reconstructPath(
            Map<Integer, Predecessor> predecessor,
            int source,
            int target,
            DataLoader loader,
            Map<String, Trip> trips
    ) {
        List<PathStep> path = new ArrayList<>();
        int current = target;

        while (current != source && predecessor.containsKey(current)) {
            Predecessor step = predecessor.get(current);
            Trip trip = trips.get(step.tripID);

            int fromIdx = -1, toIdx = -1;
            for (int i = 0; i < trip.times.size(); i++) {
                if (trip.times.get(i).stopID == step.from) fromIdx = i;
                if (trip.times.get(i).stopID == current) toIdx = i;
            }

            if (fromIdx == -1 || toIdx == -1) {
                current = step.from;
                continue;
            }

            List<PathStep> segment = new ArrayList<>();
            for (int i = fromIdx; i <= toIdx; i++) {
                StopTime st = trip.times.get(i);

                StopLocation loc = loader.stopDetails.get(st.stopID);
                String stopName = "Unknown Stop";
                double lat = 0.0;
                double lon = 0.0;

                if (loc != null) {
                    stopName = loc.getName();
                    lat = loc.getLat();
                    lon = loc.getLon();
                }

                segment.add(new PathStep(
                        step.tripID,
                        st.stopID,
                        stopName,
                        st.time,
                        lat,
                        lon
                ));
            }

            path.addAll(0, segment);
            current = step.from;
        }

        if (path.isEmpty() || path.get(0).stopID != source) {
            StopLocation sourceLoc = loader.stopDetails.get(source);
            if (sourceLoc != null) {
                String departureTime = "N/A";
                if (!path.isEmpty()) {
                    departureTime = path.get(0).time;
                }

                path.add(0, new PathStep(
                        "SOURCE",
                        source,
                        sourceLoc.getName(),
                        departureTime,
                        sourceLoc.getLat(),
                        sourceLoc.getLon()
                ));
            }
        }

        return path;
    }
}

// Helper Classes
class Predecessor {
    String tripID;
    int from;
    int time;
    int boardTime;

    public Predecessor(String tripID, int from, int time, int boardTime) {
        this.tripID = tripID;
        this.from = from;
        this.time = time;
        this.boardTime = boardTime;
    }
}

class PathStep {
    public String tripID;
    public int stopID;
    public String stopName;
    public String time;
    public double lat;
    public double lon;

    public PathStep(String tripID, int stopID, String stopName, String time, double lat, double lon) {
        this.tripID = tripID;
        this.stopID = stopID;
        this.stopName = stopName;
        this.time = time;
        this.lat = lat;
        this.lon = lon;
    }

    public String getTripID() { return tripID; }
    public int getStopID() { return stopID; }
    public String getStopName() { return stopName; }
    public String getTime() { return time; }
    public double getLat() { return lat; }
    public double getLon() { return lon; }

    @Override
    public String toString() {
        return stopName + " at " + time + " (Trip: " + tripID + "), Coordinates: " + lat + "," + lon + ")";
    }
}

class Result {
    int[] earliestArrival;
    Map<Integer, Predecessor> predecessor;

    public Result(int[] earliestArrival, Map<Integer, Predecessor> predecessor) {
        this.earliestArrival = earliestArrival;
        this.predecessor = predecessor;
    }
}
