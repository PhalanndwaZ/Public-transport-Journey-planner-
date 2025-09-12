package backend;

import java.util.*;
import java.io.IOException;

public class Stoptimes {

    private Map<String, BusStop> stops;
    private GoogleDistanceService distanceService; 


    public Stoptimes(){
        this.stops = loadStops(stopsFile);
        this.distanceService = new GoogleDistanceService("key");

    }

    // load stops with coordinates 
    public Map<String,BusStop> loadStops(String filepath){
        Map<String,BusStop> result = new HashMap<>();
        
        return result;

    }


     // 1. Convert startTime + endTime → minutes
    // 2. Query Google Distance Matrix API between consecutive stops
     // 3. Normalize to fit within (endTime - startTime)
    // 4. Assign times to via stops
    private List<Stoptime> addtimes(String startStop, String endStop,String StartTime,Lis<String> viaStops ){

        return new ArrayList<>();

    }
}