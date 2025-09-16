// package backend;

// import org.junit.jupiter.api.BeforeAll;
// import org.junit.jupiter.api.Test;

// import java.io.IOException;
// import java.util.List;

// import static org.junit.jupiter.api.Assertions.*;

// public class TransitSystemTest {

//     private static TransitSystem system;

//     @BeforeAll
//     static void initSystem() throws IOException {
//         // Initialize the whole transit system once before all tests
//         system = new TransitSystem();
//     }

//     @Test
//     void testStopsAreLoaded() {
//         // Ensure stops are loaded from CSVs
//         assertFalse(system.getLoader().stops.isEmpty(), "Stops should not be empty after loading data");
//     }

//     @Test
//     void testTripsAreLoaded() {
//         // Ensure trips are loaded from CSVs
//         assertFalse(system.getLoader().trips.isEmpty(), "Trips should not be empty after loading data");
//     }

//     @Test
//     void testQueryDirectConnection() {
//         // Example query between two known stops on the same route
//         List<PathStep> path = system.query("Cape Town", "Maitland", "4:31");

//         assertNotNull(path, "Path should not be null");
//         assertFalse(path.isEmpty(), "Path should not be empty");

//         // First step must be Cape Town, last must be Maitland
//         assertTrue("Cape Town".equalsIgnoreCase(path.get(0).stopName), "Path should start at source stop");
//         assertTrue("Maitland".equalsIgnoreCase(path.get(path.size() - 1).stopName), "Path should end at target stop");

//     }

//     @Test
//     void testQueryInvalidStops() {
//         // Query with invalid stop names
//         assertThrows(IllegalArgumentException.class, () ->
//                 system.query("InvalidStop", "Maitland", "07:30")
//         );
//     }

//     @Test
//     void testQueryNoPathExists() {
//         // Choose two stops that are not connected at the given time
//         List<PathStep> path = system.query("Cape Town", "GOODWOOD", "23:59");
//         assertTrue(path.isEmpty(), "Path should be empty if no route exists at the given time");
//     }

//     @Test
//     void testQueryMultipleTransfers() {
//         // Ensure system can handle queries that require more than one transfer
//         List<PathStep> path = system.query("Cape Town", "Bellville", "07:00");

//         assertNotNull(path, "Path should not be null");
//         assertFalse(path.isEmpty(), "Path should not be empty");

//         // Verify that at least one transfer occurred
//         long uniqueTrips = path.stream().map(p -> p.tripID).distinct().count();
//         assertTrue(uniqueTrips > 1, "Expected multiple trips (transfers) in path");
//     }
// }
