package backend;

import java.io.IOException;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.util.List;

public class RunTransitSystem {
    public static void main(String[] args) {
        try {
            TransitSystem system = new TransitSystem();
            System.out.println("Transit data loaded.");

            String source = "CAPE TOWN";
            String target = "MAITLAND";
            String departureTime = "04:31";
            String date = "2025-09-23"; // ✅ you can change this for testing

            // ✅ Determine day type from date
            String dayType = getDayType(date);
            System.out.println("[DEBUG] Using dayType = " + dayType);

            List<PathStep> path = system.query(source, target, departureTime, dayType);

            if (path.isEmpty()) {
                System.out.println("No route found.");
            } else {
                System.out.println("Route from " + source + " to " + target + ":");
                for (PathStep step : path) {
                    System.out.println(step);
                }
            }

        } catch (IOException e) {
            System.err.println("Error loading transit data: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("Query failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String getDayType(String date) {
        try {
            LocalDate d = LocalDate.parse(date);
            DayOfWeek dow = d.getDayOfWeek();
            switch (dow) {
                case SATURDAY: return "SATURDAY";
                case SUNDAY: return "SUNDAY";
                default: return "WEEKDAY";
            }
        } catch (Exception e) {
            System.err.println("[WARN] Could not parse date, defaulting to WEEKDAY");
            return "WEEKDAY";
        }
    }
}
