package backend;

import java.io.IOException;
import java.util.List;

public class RunTransitSystem {
    public static void main(String[] args) {
        try {
            // Create and load the transit system
            TransitSystem system = new TransitSystem();
            System.out.println("Transit data loaded.");

            // Example query (replace with real stop names and time you have in the data)
            String source = "CAPE TOWN";
            String target = "MAITLAND";
            String departureTime = "04:31";  // HH:mm

            List<PathStep> path = system.query(source, target, departureTime);

            System.out.println("Route from " + source + " to " + target + ":");
            for (PathStep step : path) {
                System.out.println(step);
            }

        } catch (IOException e) {
            System.err.println("Error loading transit data: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Query failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
