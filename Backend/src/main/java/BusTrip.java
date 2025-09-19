package backend;

import java.util.List;
import java.util.ArrayList;
import java.util.*;

public class BusTrip extends Trip {
    public String baseTripID;
    public String dayType;
    public String routeCode;
    public String routeName;
    
    public BusTrip(String tripID, String baseTripID, String dayType, String routeCode, String routeName) {
        super(tripID, baseTripID, dayType, routeCode);
        this.baseTripID = baseTripID;
        this.dayType = dayType;
        this.routeCode = routeCode;
        this.routeName = routeName;
    }
    @Override
    public String toString() {
        return "BusTrip{" +
           "tripID='" + tripID + '\'' +
           ", routeCode='" + routeCode + '\'' +
           ", routeName='" + routeName + '\'' +
           ", stops=" + times.size() +
           '}';
    }

}

