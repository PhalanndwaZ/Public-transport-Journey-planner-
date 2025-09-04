package src;
import java.util.*;

public class Trip {
    String tripID;
    String baseTripID;
    String dayType;
    String route;
    List<StopTime> times;

    public Trip(String tripID, String baseTripID, String dayType, String route) {
        this.tripID = tripID;
        this.baseTripID = baseTripID;
        this.dayType = dayType;
        this.route = route;
        this.times = new ArrayList<>();
    }
}