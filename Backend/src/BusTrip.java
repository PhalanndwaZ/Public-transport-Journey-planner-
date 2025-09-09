package backend;

public class BusTrip extends Trip {
    public BusTrip(String tripID, String baseTripID, String dayType, String route) {
        super(tripID, baseTripID, dayType, route);
    }

    @Override
    public String getMode() {
        return "BUS";
    }
}
