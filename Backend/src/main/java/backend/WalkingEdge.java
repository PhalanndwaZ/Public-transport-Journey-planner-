package backend;

public class WalkingEdge {
    private final int fromStopId;
    private final int toStopId;
    private final int durationMinutes;
    private final double distanceKm;

    public WalkingEdge(int fromStopId, int toStopId, int durationMinutes, double distanceKm) {
        this.fromStopId = fromStopId;
        this.toStopId = toStopId;
        this.durationMinutes = durationMinutes;
        this.distanceKm = distanceKm;
    }

    public int getFromStopId() {
        return fromStopId;
    }

    public int getToStopId() {
        return toStopId;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public double getDistanceKm() {
        return distanceKm;
    }
}

