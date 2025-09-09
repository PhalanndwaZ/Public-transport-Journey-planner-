package backend;

public class TrainTrip extends Trip {
    public TrainTrip(String tripID, String baseTripID, String dayType, String route) {
        super(tripID, baseTripID, dayType, route);
    }

    @Override
    public String getMode() {
        return "TRAIN";
    }
}
