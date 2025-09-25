package backend;

//Stop locations with id, name, latitude, longitude
public class StopLocation {

    private final int id;
    private final String name;
    private final double lat;
    private final double lon;

    public StopLocation(int id, String name, double lat, double lon) {
        this.id = id;
        this.name = name;
        this.lat = lat;
        this.lon = lon;

    }

    public int getID() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

}
