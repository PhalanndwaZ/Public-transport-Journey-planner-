package backend;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CSAEngine {
    private static final double EPS = 1e-6;

    private CSAEngine() {
    }

    public static Result run(int sourceStop,
                             int targetStop,
                             String departureTime,
                             Map<String, Trip> trips,
                             Map<Integer, StopLocation> stopDetails,
                             Map<Integer, List<WalkingEdge>> walkingEdges,
                             QueryPreferences preferences) {
        if (preferences == null) {
            throw new IllegalArgumentException("Query preferences are required for CSA");
        }

        int stopCount = computeStopCount(stopDetails, sourceStop, targetStop);
        int departureMinutes = Raptor.timeToMinutes(departureTime);

        int[] earliestArrival = new int[stopCount];
        Arrays.fill(earliestArrival, Integer.MAX_VALUE);

        double[] totalWalk = new double[stopCount];
        Arrays.fill(totalWalk, Double.POSITIVE_INFINITY);

        double[] consecutiveWalk = new double[stopCount];
        Arrays.fill(consecutiveWalk, Double.POSITIVE_INFINITY);

        Map<Integer, Predecessor> predecessor = new HashMap<>();

        earliestArrival[sourceStop] = departureMinutes;
        totalWalk[sourceStop] = 0.0;
        consecutiveWalk[sourceStop] = 0.0;

        if (preferences.allowsWalking()) {
            propagateFootpaths(sourceStop,
                    departureMinutes,
                    0.0,
                    0.0,
                    walkingEdges,
                    preferences,
                    earliestArrival,
                    totalWalk,
                    consecutiveWalk,
                    predecessor,
                    targetStop);
        }

        List<Connection> connections = buildConnections(trips, preferences);
        if (connections.isEmpty()) {
            return new Result(earliestArrival, totalWalk, consecutiveWalk, predecessor);
        }

        connections.sort(Comparator
                .comparingInt((Connection c) -> c.departureTime)
                .thenComparingInt(c -> c.arrivalTime));

        int bestTargetArrival = earliestArrival[targetStop];

        for (Connection connection : connections) {
            if (bestTargetArrival != Integer.MAX_VALUE && connection.departureTime > bestTargetArrival) {
                break;
            }

            int depStop = connection.departureStop;
            if (depStop < 0 || depStop >= earliestArrival.length) continue;

            int availableAt = earliestArrival[depStop];
            if (availableAt == Integer.MAX_VALUE) continue;
            if (availableAt > connection.departureTime) continue;

            double totalAt = totalWalk[depStop];
            double consecutiveAt = consecutiveWalk[depStop];
            if (Double.isInfinite(totalAt) || Double.isInfinite(consecutiveAt)) continue;

            double newTotalWalk = totalAt;
            double newConsecutiveWalk = 0.0;

            int arrStop = connection.arrivalStop;
            if (arrStop < 0 || arrStop >= earliestArrival.length) continue;

            int arrTime = connection.arrivalTime;
            if (arrTime <= connection.departureTime) continue;

            if (isBetterState(arrTime,
                    newTotalWalk,
                    newConsecutiveWalk,
                    earliestArrival[arrStop],
                    totalWalk[arrStop],
                    consecutiveWalk[arrStop])) {

                earliestArrival[arrStop] = arrTime;
                totalWalk[arrStop] = newTotalWalk;
                consecutiveWalk[arrStop] = newConsecutiveWalk;

                predecessor.put(arrStop, new Predecessor(connection.tripId,
                        depStop,
                        arrTime,
                        connection.departureTime,
                        false,
                        0.0,
                        connection.mode));

                if (preferences.allowsWalking()) {
                    propagateFootpaths(arrStop,
                            arrTime,
                            newTotalWalk,
                            newConsecutiveWalk,
                            walkingEdges,
                            preferences,
                            earliestArrival,
                            totalWalk,
                            consecutiveWalk,
                            predecessor,
                            targetStop);
                    bestTargetArrival = Math.min(bestTargetArrival, earliestArrival[targetStop]);
                }

                if (arrStop == targetStop && arrTime < bestTargetArrival) {
                    bestTargetArrival = arrTime;
                }
            }
        }

        return new Result(earliestArrival, totalWalk, consecutiveWalk, predecessor);
    }

    private static List<Connection> buildConnections(Map<String, Trip> trips, QueryPreferences preferences) {
        List<Connection> connections = new ArrayList<>();
        for (Trip trip : trips.values()) {
            if (trip == null) continue;
            String mode = trip.getMode();
            if (!preferences.allowsMode(mode)) continue;

            List<StopTime> times = trip.getTimes();
            if (times == null || times.size() < 2) continue;

            for (int i = 0; i < times.size() - 1; i++) {
                StopTime depart = times.get(i);
                StopTime arrive = times.get(i + 1);
                if (depart == null || arrive == null) continue;

                int departMinutes = Raptor.timeToMinutes(depart.time);
                int arriveMinutes = Raptor.timeToMinutes(arrive.time);
                if (arriveMinutes <= departMinutes) continue;

                connections.add(new Connection(trip.getTripID(),
                        mode,
                        depart.stopID,
                        arrive.stopID,
                        departMinutes,
                        arriveMinutes));
            }
        }
        return connections;
    }

    private static void propagateFootpaths(int originStop,
                                           int originArrival,
                                           double originTotalWalk,
                                           double originConsecutiveWalk,
                                           Map<Integer, List<WalkingEdge>> walkingEdges,
                                           QueryPreferences preferences,
                                           int[] earliestArrival,
                                           double[] totalWalk,
                                           double[] consecutiveWalk,
                                           Map<Integer, Predecessor> predecessor,
                                           int targetStop) {
        if (walkingEdges == null || walkingEdges.isEmpty()) return;

        Double maxSingleWalk = preferences.getMaxSingleWalkKm();
        double maxTotal = preferences.getMaxCumulativeWalkKm();
        double maxConsecutive = preferences.getMaxConsecutiveWalkKm();

        ArrayDeque<WalkState> queue = new ArrayDeque<>();
        queue.add(new WalkState(originStop, originArrival, originTotalWalk, originConsecutiveWalk));

        while (!queue.isEmpty()) {
            WalkState state = queue.removeFirst();
            List<WalkingEdge> neighbors = walkingEdges.getOrDefault(state.stopId, Collections.emptyList());
            if (neighbors.isEmpty()) continue;

            for (WalkingEdge edge : neighbors) {
                double segmentDistance = edge.getDistanceKm();
                if (maxSingleWalk != null && segmentDistance > maxSingleWalk + EPS) continue;

                double newTotalWalk = state.totalWalkKm + segmentDistance;
                if (newTotalWalk > maxTotal + EPS) continue;

                double newConsecutiveWalk = state.consecutiveWalkKm + segmentDistance;
                if (newConsecutiveWalk > maxConsecutive + EPS) continue;

                int departAt = state.arrivalTime;
                int arrivalTime = departAt + edge.getDurationMinutes();
                int toStop = edge.getToStopId();
                if (toStop < 0 || toStop >= earliestArrival.length) continue;

                if (isBetterState(arrivalTime,
                        newTotalWalk,
                        newConsecutiveWalk,
                        earliestArrival[toStop],
                        totalWalk[toStop],
                        consecutiveWalk[toStop])) {

                    earliestArrival[toStop] = arrivalTime;
                    totalWalk[toStop] = newTotalWalk;
                    consecutiveWalk[toStop] = newConsecutiveWalk;

                    predecessor.put(toStop, new Predecessor("WALK",
                            state.stopId,
                            arrivalTime,
                            departAt,
                            true,
                            segmentDistance,
                            "WALK"));

                    if (toStop != targetStop) {
                        queue.addLast(new WalkState(toStop, arrivalTime, newTotalWalk, newConsecutiveWalk));
                    }
                }
            }
        }
    }

    private static boolean isBetterState(int newArrival,
                                         double newTotalWalk,
                                         double newConsecutiveWalk,
                                         int currentArrival,
                                         double currentTotalWalk,
                                         double currentConsecutiveWalk) {
        if (currentArrival == Integer.MAX_VALUE) return true;
        if (newArrival < currentArrival) return true;
        if (newArrival > currentArrival) return false;

        if (Double.isInfinite(currentTotalWalk)) return true;
        if (newTotalWalk + EPS < currentTotalWalk) return true;
        if (newTotalWalk > currentTotalWalk + EPS) return false;

        if (Double.isInfinite(currentConsecutiveWalk)) return true;
        return newConsecutiveWalk + EPS < currentConsecutiveWalk;
    }

    private static int computeStopCount(Map<Integer, StopLocation> stopDetails,
                                        int sourceStop,
                                        int targetStop) {
        int maxId = Math.max(sourceStop, targetStop);
        if (stopDetails != null && !stopDetails.isEmpty()) {
            for (Integer id : stopDetails.keySet()) {
                if (id != null && id > maxId) {
                    maxId = id;
                }
            }
        }
        return maxId + 1;
    }

    private record Connection(String tripId,
                               String mode,
                               int departureStop,
                               int arrivalStop,
                               int departureTime,
                               int arrivalTime) {
    }

    private static final class WalkState {
        final int stopId;
        final int arrivalTime;
        final double totalWalkKm;
        final double consecutiveWalkKm;

        WalkState(int stopId, int arrivalTime, double totalWalkKm, double consecutiveWalkKm) {
            this.stopId = stopId;
            this.arrivalTime = arrivalTime;
            this.totalWalkKm = totalWalkKm;
            this.consecutiveWalkKm = consecutiveWalkKm;
        }
    }
}
