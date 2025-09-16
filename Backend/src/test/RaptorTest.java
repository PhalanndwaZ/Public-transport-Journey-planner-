package backend;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class RaptorTest {

    /**
     * Case 1: Direct fastest route (A → B on same route)
     */
    @Test
    public void testDirectFastestRoute() {
        DataLoader loader = new DataLoader();

        // Stops
        loader.stops.put("A", 0);
        loader.stops.put("B", 1);

        loader.stopDetails.put(0, new StopLocation(0, "A", 0.0, 0.0));
        loader.stopDetails.put(1, new StopLocation(1, "B", 0.0, 0.0));

        loader.routes.put("R1", Arrays.asList(0, 1));
        loader.stopToRoutes.put(0, Arrays.asList("R1"));
        loader.stopToRoutes.put(1, Arrays.asList("R1"));

        // Trip A → B
        Trip t1 = new Trip("T1", "T1", "WEEKDAY", "R1");
        t1.times.add(new StopTime(0, "08:00"));
        t1.times.add(new StopTime(1, "08:30"));
        loader.trips.put("T1", t1);

        // Run RAPTOR
        Result result = Raptor.runRaptor(0, 1, "07:55", loader.stops, loader.trips, loader.stopToRoutes);
        List<PathStep> path = Raptor.reconstructPath(result.predecessor, 0, 1, loader, loader.trips);

        assertFalse(path.isEmpty(), "Path should not be empty");
        assertEquals("B", path.get(path.size() - 1).stopName);
        assertEquals("08:30", path.get(path.size() - 1).time);
    }

    /**
     * Case 2: Transfer required (A → C via B)
     */
    @Test
    public void testTransferRequired() {
        DataLoader loader = new DataLoader();

        // Stops
        loader.stops.put("A", 0);
        loader.stops.put("B", 1);
        loader.stops.put("C", 2);

        loader.stopDetails.put(0, new StopLocation(0, "A", 0.0, 0.0));
        loader.stopDetails.put(1, new StopLocation(1, "B", 0.0, 0.0));
        loader.stopDetails.put(2, new StopLocation(2, "C", 0.0, 0.0));

        loader.routes.put("R1", Arrays.asList(0, 1));
        loader.routes.put("R2", Arrays.asList(1, 2));

        loader.stopToRoutes.put(0, Arrays.asList("R1"));
        loader.stopToRoutes.put(1, Arrays.asList("R1", "R2"));
        loader.stopToRoutes.put(2, Arrays.asList("R2"));

        // Trip A → B
        Trip t1 = new Trip("T1", "T1", "WEEKDAY", "R1");
        t1.times.add(new StopTime(0, "08:00"));
        t1.times.add(new StopTime(1, "08:20"));
        loader.trips.put("T1", t1);

        // Trip B → C
        Trip t2 = new Trip("T2", "T2", "WEEKDAY", "R2");
        t2.times.add(new StopTime(1, "08:30"));
        t2.times.add(new StopTime(2, "08:50"));
        loader.trips.put("T2", t2);

        // Run RAPTOR
        Result result = Raptor.runRaptor(0, 2, "07:55", loader.stops, loader.trips, loader.stopToRoutes);
        List<PathStep> path = Raptor.reconstructPath(result.predecessor, 0, 2, loader, loader.trips);

        assertFalse(path.isEmpty(), "Path should not be empty");
        assertEquals("C", path.get(path.size() - 1).stopName);
        assertEquals("08:50", path.get(path.size() - 1).time);
    }

    /**
     * Case 3: No path exists
     */
    @Test
    public void testNoPathExists() {
        DataLoader loader = new DataLoader();

        loader.stops.put("A", 0);
        loader.stops.put("B", 1);

        loader.stopDetails.put(0, new StopLocation(0, "A", 0.0, 0.0));
        loader.stopDetails.put(1, new StopLocation(1, "B", 0.0, 0.0));

        loader.routes.put("R1", Arrays.asList(0));
        loader.stopToRoutes.put(0, Arrays.asList("R1"));

        Trip t1 = new Trip("T1", "T1", "WEEKDAY", "R1");
        t1.times.add(new StopTime(0, "08:00"));
        loader.trips.put("T1", t1);

        Result result = Raptor.runRaptor(0, 1, "08:00", loader.stops, loader.trips, loader.stopToRoutes);
        List<PathStep> path = Raptor.reconstructPath(result.predecessor, 0, 1, loader, loader.trips);

        assertTrue(path.isEmpty(), "Path should be empty when no route exists");
    }

    /**
     * Case 4: Multiple trips, choose earliest arrival
     */
    @Test
    public void testChooseEarliestArrivalWhenMultipleTrips() {
        DataLoader loader = new DataLoader();

        loader.stops.put("A", 0);
        loader.stops.put("C", 1);

        loader.stopDetails.put(0, new StopLocation(0, "A", 0.0, 0.0));
        loader.stopDetails.put(1, new StopLocation(1, "C", 0.0, 0.0));

        loader.routes.put("R1", Arrays.asList(0, 1));
        loader.stopToRoutes.put(0, Arrays.asList("R1"));
        loader.stopToRoutes.put(1, Arrays.asList("R1"));

        Trip t1 = new Trip("T1", "T1", "WEEKDAY", "R1");
        t1.times.add(new StopTime(0, "08:00"));
        t1.times.add(new StopTime(1, "09:00"));
        loader.trips.put("T1", t1);

        Trip t2 = new Trip("T2", "T2", "WEEKDAY", "R1");
        t2.times.add(new StopTime(0, "08:10"));
        t2.times.add(new StopTime(1, "08:30"));
        loader.trips.put("T2", t2);

        Result result = Raptor.runRaptor(0, 1, "07:50", loader.stops, loader.trips, loader.stopToRoutes);
        List<PathStep> path = Raptor.reconstructPath(result.predecessor, 0, 1, loader, loader.trips);

        assertFalse(path.isEmpty(), "Path should not be empty");
        assertEquals("T2", path.get(0).tripID, "Algorithm should pick the faster trip (T2)");
    }

    /**
     * Case 5: Reverse order for inbound trips
     */
    @Test
    public void testInboundTripReverseOrder() {
        DataLoader loader = new DataLoader();

        loader.stops.put("X", 0);
        loader.stops.put("Y", 1);
        loader.stops.put("Z", 2);

        loader.stopDetails.put(0, new StopLocation(0, "X", 0.0, 0.0));
        loader.stopDetails.put(1, new StopLocation(1, "Y", 0.0, 0.0));
        loader.stopDetails.put(2, new StopLocation(2, "Z", 0.0, 0.0));

        loader.routes.put("R1", Arrays.asList(0, 1, 2));
        loader.stopToRoutes.put(0, Arrays.asList("R1"));
        loader.stopToRoutes.put(1, Arrays.asList("R1"));
        loader.stopToRoutes.put(2, Arrays.asList("R1"));

        Trip inbound = new Trip("T1", "T1", "WEEKDAY", "R1");
        inbound.times.add(new StopTime(2, "08:00"));
        inbound.times.add(new StopTime(1, "08:10"));
        inbound.times.add(new StopTime(0, "08:20"));
        loader.trips.put("T1", inbound);

        Result result = Raptor.runRaptor(2, 0, "07:55", loader.stops, loader.trips, loader.stopToRoutes);
        List<PathStep> path = Raptor.reconstructPath(result.predecessor, 2, 0, loader, loader.trips);

        assertFalse(path.isEmpty(), "Path should not be empty for inbound trip");
        assertEquals("Z", path.get(0).stopName);
        assertEquals("X", path.get(path.size() - 1).stopName);
    }
}
