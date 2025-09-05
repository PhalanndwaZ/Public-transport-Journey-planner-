package backend;

import java.util.*;

public class Trip {
    String tripID;  // unique id trip 
    String baseTripID;  //identifier linking to base trip item 
    String dayType;     //what type of the day this trip runs 
    String route;       // which route (e.g., bus/train line) this trip belongs to
    List<StopTime> times;   // the sequence of stops and times

    public Trip(String tripID, String baseTripID, String dayType, String route) {
        this.tripID = tripID;
        this.baseTripID = baseTripID;
        this.dayType = dayType;
        this.route = route;
        this.times = new ArrayList<>();
    }
    public String getTripID(){
        return tripID;
    }
}