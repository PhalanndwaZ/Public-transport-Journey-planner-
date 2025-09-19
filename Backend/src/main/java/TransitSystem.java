package backend;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class TransitSystem {
    // This class loads all the data from buses to trains and returns a big dataset
    private final DataLoader loader;

    public TransitSystem() throws IOException {
        loader = new DataLoader();

        // Base directory for transit data (relative path)
        String basePath = "CapeTownTransitData/";

        // 1. Load station coordinates first
        loader.loadStationCoordinates(basePath + "metrorail-stations.csv");

        // Train CSVs (relative paths)
        List<String> trainCSVs = Arrays.asList(
            basePath + "train-schedules-2014/S1.csv",
            basePath + "train-schedules-2014/S2.csv",
            basePath + "train-schedules-2014/S3.csv",
            basePath + "train-schedules-2014/S4.csv",
            basePath + "train-schedules-2014/S5.csv",
            basePath + "train-schedules-2014/S6.csv",
            basePath + "train-schedules-2014/S7.csv",
            basePath + "train-schedules-2014/S8.csv",
            basePath + "train-schedules-2014/S9.csv",
            basePath + "train-schedules-2014/S10.csv",
            basePath + "train-schedules-2014/S11.csv",
            basePath + "train-schedules-2014/S12.csv"
        );

        // Bus CSVs (commented for now, still relative)
        List<String> busCSVs = Arrays.asList(
            basePath + "myciti-bus-schedules/B1.csv",
            basePath + "myciti-bus-schedules/B2.csv"
        );

        // Load train data
        for (String file : trainCSVs) {
            loader.buildTrainData(loader.loadCSV(file));
        }

        // Later: load bus data if needed
        // for (String file : busCSVs) {
        //     loader.buildBusData(loader.loadCSV(file));
        // }
    }

    public DataLoader getLoader() {
        return loader;
    }

    // Wrapper so frontend or tests can run queries
    public List<PathStep> query(String sourceStopName, String targetStopName, String departureTime) {
        Integer sourceId = loader.findStopByName(sourceStopName);
        Integer targetId = loader.findStopByName(targetStopName);

        if (sourceId == null || targetId == null) {
            throw new IllegalArgumentException("Invalid stop name(s).");
        }

        Result result = Raptor.runRaptor(
                sourceId,
                targetId,
                departureTime,
                loader.stops,
                loader.trips,
                loader.stopToRoutes
        );

        // Reconstruct path, including coordinates
        return Raptor.reconstructPath(
                result.predecessor,
                sourceId,
                targetId,
                loader,
                loader.trips
        );
    }
}
