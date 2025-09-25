package backend;

import java.util.*;

public class Raptor {

    public static int timeToMinutes(String time) {
        String[] parts = time.split(":");
        int hh = Integer.parseInt(parts[0]);
        int mm = Integer.parseInt(parts[1]);
        return hh * 60 + mm;
    }

    public static String minutesToTime(int minutes) {
        int hh = minutes / 60;
        int mm = minutes % 60;
        return String.format("%02d:%02d", hh, mm);
    }

    public static Result runRaptor(
            int sourceStop,
            int targetStop,
            String departureTime,
            Map<String, Integer> stops,
            Map<String, Trip> trips,
            Map<Integer, List<String>> stopToRoutes,
            String dayTypeFilter,
            Map<Integer, List<WalkingEdge>> walkingEdges,
            double maxCumulativeWalkKm,
            double maxConsecutiveWalkKm
    ) {
        final int INF = Integer.MAX_VALUE;
        int numStops = stops.size();
        int departureMinutes = timeToMinutes(departureTime);

        int[] earliestArrival = new int[numStops];
        Arrays.fill(earliestArrival, INF);

        double[] bestWalkDistance = new double[numStops];
        double[] bestConsecutiveWalk = new double[numStops];
        Arrays.fill(bestWalkDistance, Double.POSITIVE_INFINITY);
        Arrays.fill(bestConsecutiveWalk, Double.POSITIVE_INFINITY);

        Map<Integer, Predecessor> predecessor = new HashMap<>();

        earliestArrival[sourceStop] = departureMinutes;
        bestWalkDistance[sourceStop] = 0.0;
        bestConsecutiveWalk[sourceStop] = 0.0;

        int MAX_TRANSFERS = 5;
        Set<Integer> marked = new HashSet<>();
        marked.add(sourceStop);

        long totalTrips = trips.size();
        long matchingTrips = trips.values().stream()
                .filter(t -> t.dayType.equalsIgnoreCase(dayTypeFilter))
                .count();
        System.out.println("[DEBUG] Raptor running with dayType=" + dayTypeFilter +
                " | Matching trips: " + matchingTrips + "/" + totalTrips);

        for (int round = 0; round < MAX_TRANSFERS; round++) {
            Set<Integer> nextMarked = new HashSet<>();

            for (int stop : marked) {
                List<String> routes = stopToRoutes.getOrDefault(stop, Collections.emptyList());

                for (String routeID : routes) {
                    List<Trip> allRouteTrips = new ArrayList<>();
                    for (Trip t : trips.values()) {
                        if (t.route.equals(routeID)) {
                            if (dayTypeFilter != null && !dayTypeFilter.isEmpty()
                                    && !t.dayType.equalsIgnoreCase(dayTypeFilter)) {
                                continue;
                            }
                            allRouteTrips.add(t);
                        }
                    }

                    if (allRouteTrips.isEmpty()) {
                        System.out.println("[DEBUG] No trips found for route " + routeID + " on " + dayTypeFilter);
                    }

                    allRouteTrips.sort(Comparator.comparingInt(t ->
                            t.times.stream()
                                    .filter(st -> st.stopID == stop)
                                    .map(st -> timeToMinutes(st.time))
                                    .findFirst().orElse(INF)));

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

                        if (Double.isInfinite(bestWalkDistance[stop]) || Double.isInfinite(bestConsecutiveWalk[stop])) continue;

                        int boardTime = timeToMinutes(times.get(stopIdx).time);

                        if (stop == sourceStop) {
                            if (boardTime < departureMinutes) continue;
                        } else {
                            if (boardTime < earliestArrival[stop]) continue;
                        }

                        if (stopIdx < times.size() - 1) {
                            int nextStopTime = timeToMinutes(times.get(stopIdx + 1).time);
                            if (nextStopTime <= boardTime) continue;
                        }

                        double walkAtBoard = bestWalkDistance[stop];

                        for (int i = stopIdx + 1; i < times.size(); i++) {
                            int stopID = times.get(i).stopID;
                            int arrTime = timeToMinutes(times.get(i).time);

                            if (arrTime <= boardTime) continue;

                            double newTotalWalk = walkAtBoard;
                            double newConsecutiveWalk = 0.0;

                            if (isBetterState(arrTime, newTotalWalk, newConsecutiveWalk,
                                    earliestArrival[stopID], bestWalkDistance[stopID], bestConsecutiveWalk[stopID])) {
                                earliestArrival[stopID] = arrTime;
                                bestWalkDistance[stopID] = newTotalWalk;
                                bestConsecutiveWalk[stopID] = newConsecutiveWalk;
                                predecessor.put(stopID,
                                        new Predecessor(trip.tripID, stop, arrTime, boardTime, false, 0.0, trip.getMode()));
                                nextMarked.add(stopID);
                            }
                        }
                    }
                }

                if (walkingEdges != null && !walkingEdges.isEmpty()) {
                    List<WalkingEdge> neighbors = walkingEdges.getOrDefault(stop, Collections.emptyList());
                    int departAt = earliestArrival[stop];
                    if (departAt != INF && !Double.isInfinite(bestWalkDistance[stop])
                            && !Double.isInfinite(bestConsecutiveWalk[stop])) {
                        for (WalkingEdge edge : neighbors) {
                            double cumulativeWalk = bestWalkDistance[stop] + edge.getDistanceKm();
                            if (cumulativeWalk > maxCumulativeWalkKm) continue;

                            double consecutiveWalk = bestConsecutiveWalk[stop] + edge.getDistanceKm();
                            if (consecutiveWalk > maxConsecutiveWalkKm) continue;

                            int arrival = departAt + edge.getDurationMinutes();
                            int toStopId = edge.getToStopId();
                            if (isBetterState(arrival, cumulativeWalk, consecutiveWalk,
                                    earliestArrival[toStopId], bestWalkDistance[toStopId], bestConsecutiveWalk[toStopId])) {
                                earliestArrival[toStopId] = arrival;
                                bestWalkDistance[toStopId] = cumulativeWalk;
                                bestConsecutiveWalk[toStopId] = consecutiveWalk;
                                predecessor.put(toStopId,
                                        new Predecessor("WALK", stop, arrival, departAt, true, edge.getDistanceKm(), "WALK"));
                                nextMarked.add(toStopId);
                            }
                        }
                    }
                }
            }

            if (nextMarked.isEmpty()) break;
            marked = nextMarked;
        }

        return new Result(earliestArrival, bestWalkDistance, bestConsecutiveWalk, predecessor);
    }

    private static boolean isBetterState(int newArrival, double newTotalWalk, double newConsecutiveWalk,
                                         int currentArrival, double currentTotalWalk, double currentConsecutiveWalk) {
        final double EPS = 1e-6;
        if (currentArrival == Integer.MAX_VALUE) return true;
        if (newArrival < currentArrival) return true;
        if (newArrival > currentArrival) return false;
        if (Double.isInfinite(currentTotalWalk)) return true;
        if (newTotalWalk + EPS < currentTotalWalk) return true;
        if (newTotalWalk > currentTotalWalk + EPS) return false;
        if (Double.isInfinite(currentConsecutiveWalk)) return true;
        return newConsecutiveWalk + EPS < currentConsecutiveWalk;
    }

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

            if (step.walking || trip == null) {
                StopLocation fromLoc = loader.stopDetails.get(step.from);
                StopLocation toLoc = loader.stopDetails.get(current);

                String departTime = minutesToTime(step.boardTime);
                String arrivalTime = minutesToTime(step.time);

                PathStep startStep = new PathStep(
                        "WALK",
                        step.from,
                        fromLoc != null ? fromLoc.getName() : "Unknown Stop",
                        departTime,
                        fromLoc != null ? fromLoc.getLat() : 0.0,
                        fromLoc != null ? fromLoc.getLon() : 0.0,
                        true,
                        step.walkingDistanceKm,
                        step.mode
                );

                PathStep endStep = new PathStep(
                        "WALK",
                        current,
                        toLoc != null ? toLoc.getName() : "Unknown Stop",
                        arrivalTime,
                        toLoc != null ? toLoc.getLat() : 0.0,
                        toLoc != null ? toLoc.getLon() : 0.0,
                        true,
                        step.walkingDistanceKm,
                        step.mode
                );

                List<PathStep> segment = new ArrayList<>();
                segment.add(startStep);
                segment.add(endStep);
                path.addAll(0, segment);
                current = step.from;
                continue;
            }

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
                String stopName = loc != null ? loc.getName() : "Unknown Stop";
                double lat = loc != null ? loc.getLat() : 0.0;
                double lon = loc != null ? loc.getLon() : 0.0;

                segment.add(new PathStep(
                        step.tripID,
                        st.stopID,
                        stopName,
                        st.time,
                        lat,
                        lon,
                        false,
                        0.0,
                        trip.getMode()
                ));
            }

            path.addAll(0, segment);
            current = step.from;
        }

        if (path.isEmpty() || path.get(0).stopID != source) {
            StopLocation sourceLoc = loader.stopDetails.get(source);
            if (sourceLoc != null) {
                String departureTime = path.isEmpty() ? minutesToTime(loader.stopDetails.get(source).getID()) : path.get(0).getTime();
                path.add(0, new PathStep(
                        "SOURCE",
                        source,
                        sourceLoc.getName(),
                        departureTime,
                        sourceLoc.getLat(),
                        sourceLoc.getLon(),
                        false,
                        0.0,
                        "SOURCE"
                ));
            }
        }

        return path;
    }
}

class Predecessor {
    String tripID;
    int from;
    int time;
    int boardTime;
    boolean walking;
    double walkingDistanceKm;
    String mode;

    public Predecessor(String tripID, int from, int time, int boardTime) {
        this(tripID, from, time, boardTime, false, 0.0, "UNKNOWN");
    }

    public Predecessor(String tripID, int from, int time, int boardTime, boolean walking, double walkingDistanceKm, String mode) {
        this.tripID = tripID;
        this.from = from;
        this.time = time;
        this.boardTime = boardTime;
        this.walking = walking;
        this.walkingDistanceKm = walkingDistanceKm;
        this.mode = mode;
    }
}

class PathStep {
    public String tripID;
    public int stopID;
    public String stopName;
    public String time;
    public double lat;
    public double lon;
    public boolean walking;
    public double distanceKm;
    public String mode;

    public PathStep(String tripID, int stopID, String stopName, String time, double lat, double lon) {
        this(tripID, stopID, stopName, time, lat, lon, false, 0.0, null);
    }

    public PathStep(String tripID, int stopID, String stopName, String time, double lat, double lon, boolean walking, double distanceKm, String mode) {
        this.tripID = tripID;
        this.stopID = stopID;
        this.stopName = stopName;
        this.time = time;
        this.lat = lat;
        this.lon = lon;
        this.walking = walking;
        this.distanceKm = distanceKm;
        this.mode = mode != null ? mode : (walking ? "WALK" : "UNKNOWN");
    }

    public String getTripID() { return tripID; }
    public int getStopID() { return stopID; }
    public String getStopName() { return stopName; }
    public String getTime() { return time; }
    public double getLat() { return lat; }
    public double getLon() { return lon; }
    public boolean isWalking() { return walking; }
    public double getDistanceKm() { return distanceKm; }
    public String getMode() { return mode; }

    @Override
    public String toString() {
        String label = walking ? "Walk" : mode + " (" + tripID + ")";
        return stopName + " at " + time + " (" + label + ")" + ", Coordinates: " + lat + "," + lon + ")";
    }
}

class Result {
    int[] earliestArrival;
    double[] totalWalkingKm;
    double[] consecutiveWalkingKm;
    Map<Integer, Predecessor> predecessor;

    public Result(int[] earliestArrival, double[] totalWalkingKm, double[] consecutiveWalkingKm, Map<Integer, Predecessor> predecessor) {
        this.earliestArrival = earliestArrival;
        this.totalWalkingKm = totalWalkingKm;
        this.consecutiveWalkingKm = consecutiveWalkingKm;
        this.predecessor = predecessor;
    }
}
