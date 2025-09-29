package backend;

import java.util.ArrayList;
import java.util.List;

/**
 * Base trip abstraction capturing a sequence of stop times for a route/day.
 */
public class Trip {
    protected String tripID;
    protected String baseTripID;
    protected String dayType;
    protected String route;
    protected List<StopTime> times;
    protected String mode;

    public Trip(String tripID, String baseTripID, String dayType, String route) {
        this.tripID = tripID;
        this.baseTripID = baseTripID;
        this.dayType = dayType;
        this.route = route;
        this.times = new ArrayList<>();
        this.mode = "UNKNOWN";
    }

    /** Returns unique trip identifier. */
    public String getTripID() {
        return tripID;
    }

    /** Returns route identifier for this trip. */
    public String getRoute() {
        return route;
    }

    /** Returns ordered stop times for this trip. */
    public List<StopTime> getTimes() {
        return times;
    }

    /** Returns transport mode label (BUS/TRAIN/WALK/UNKNOWN). */
    public String getMode() {
        return mode;
    }

    /** Returns day-type bucket for which the trip is valid. */
    public String getDayType() {
        return dayType;
    }

    /** Sets the transport mode label. */
    protected void setMode(String mode) {
        this.mode = mode;
    }

    /** Appends a stop-time to this trip. */
    public void addStop(StopTime stopTime) {
        this.times.add(stopTime);
    }
}
