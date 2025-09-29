package backend;

public abstract class TransportMode {
    private final String name;

    public TransportMode(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
