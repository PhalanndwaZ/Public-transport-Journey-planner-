package backend;

import java.util.*;

public class BusStopTime extends StopTime {
    public String stopId;
    public String description;
    public String type;
    public int ordinal;
    public double longitude;
    public double latitude;

    public BusStopTime(String stopId, String description, String type, int ordinal,
                       double longitude, double latitude, String time) {
        super(-1, time); // -1 since BusStopTime already has stopId
        this.stopId = stopId;
        this.description = description;
        this.type = type;
        this.ordinal = ordinal;
        this.longitude = longitude;
        this.latitude = latitude;
    }
}


