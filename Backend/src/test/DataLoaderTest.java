package backend;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.*;

public class DataLoaderTest {

    private static final String CSV_FILE =
        "C:\\Users\\Phalanndwa Zwivhuya\\Documents\\School stuff\\computer science\\3rd year\\2025\\CSC3003S\\Data\\CapeTownTransitData\\train-schedules-2014\\S1.csv";

    @Test
    public void testCSVNotEmpty() throws IOException {
        // test if it can read data 
        DataLoader loader = new DataLoader();
        List<String[]> rows = loader.loadCSV(CSV_FILE);

        assertNotNull(rows, "CSV rows should not be null");
        assertFalse(rows.isEmpty(), "CSV should not be empty");
    }

    @Test
    public void testBuildDataPopulatesStopsRoutesTrips() throws IOException {
        // check if each columns are added tripIDet etc 
        DataLoader loader = new DataLoader();
        List<String[]> rows = loader.loadCSV(CSV_FILE);
        loader.buildData(rows);

        assertFalse(loader.stops.isEmpty(), "Stops should be loaded");
        assertFalse(loader.routes.isEmpty(), "Routes should be loaded");
        assertFalse(loader.trips.isEmpty(), "Trips should be loaded");
        assertFalse(loader.stopToRoutes.isEmpty(), "Stop-to-routes should be linked");
    }

    @Test
    public void testFindStopByName() throws IOException {

        // test if indiviudal stop trully exists 
        DataLoader loader = new DataLoader();
        loader.buildData(loader.loadCSV(CSV_FILE));
        // note the stops are all in upper case 
        Integer stopId = loader.findStopByName("CAPE TOWN");
        assertNotNull(stopId, "Cape Town stop should exist");
        assertEquals("CAPE TOWN", loader.getStopNameById(stopId), "Stop ID should map back to Cape Town");
    }

    @Test
    public void testGetAvailableStopsSorted() throws IOException {
        DataLoader loader = new DataLoader();
        loader.buildData(loader.loadCSV(CSV_FILE));


        Set<String> stops = loader.getAvailableStops();
        assertFalse(stops.isEmpty(), "Available stops should not be empty");

        List<String> sortedStops = new ArrayList<>(stops);
        List<String> manuallySorted = new ArrayList<>(stops);
        Collections.sort(manuallySorted);

        assertEquals(manuallySorted, sortedStops, "Available stops should be sorted alphabetically");
    }

    @Test
    public void testTripsContainStopTimes() throws IOException {
        //testing if trips have id 
        DataLoader loader = new DataLoader();
        loader.buildData(loader.loadCSV(CSV_FILE));

        for (Trip trip : loader.trips.values()) {
            assertNotNull(trip.getTripID(), "Trip ID should not be null");
            assertFalse(trip.times.isEmpty(), "Trip " + trip.getTripID() + " should have stop times");
        }
    }
}
