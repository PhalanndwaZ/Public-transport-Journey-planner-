package backend;

import java.util.ArrayList;
import java.util.List;

public class Trip {
    // this is the base class for each mode which will have these as a standard 
    protected String tripID;
    protected String baseTripID;
    protected String dayType;
    protected String route;
    protected List<StopTime> times;
    protected TransportMode mode;
    

    // constructor with the base requirements in each class 
    public Trip(String tripID, String baseTripID, String dayType, String route) {
        this.tripID = tripID;
        this.baseTripID = baseTripID;
        this.dayType = dayType;
        this.route = route;
        this.times = new ArrayList<>();
    }

    // helper classes 
    public String getTripID() {
        return tripID;
    }

    public String getRoute() {
        return route;
    }

    public List<StopTime> getTimes() {
        return times;
    }

    // Polymorphic method: each subclass identifies itself
    public String getMode() {
        return null;
    }
}
