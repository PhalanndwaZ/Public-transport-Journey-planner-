package backend;


public class BusStop {
    public String stopId;      // BUSSTOPNO
    public String description; // BUSSTOPDES
    public String routeCode;   // ROUTECODE
    public String routeName;   // ROUTENAME
    public String type;        // CLASSIFICA (Station / Stop)
    public int ordinal;        // ORDINAL
    public double longitude;   // xcoord
    public double latitude;    // ycoord

    public BusStop(String stopId, String description, String routeCode, String routeName,
                   String type, int ordinal, double longitude, double latitude) {
        this.stopId = stopId;
        this.description = description;
        this.routeCode = routeCode;
        this.routeName = routeName;
        this.type = type;
        this.ordinal = ordinal;
        this.longitude = longitude;
        this.latitude = latitude;
    }

    @Override
    public String toString() {
        return stopId + " - " + description + " (" + latitude + "," + longitude + ")";
    }
}


