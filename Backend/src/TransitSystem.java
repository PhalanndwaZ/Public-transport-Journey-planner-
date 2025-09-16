package backend;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class TransitSystem {
    // This class loads all the data from buses to trains and returns a big dataset
    private final DataLoader loader;

    public TransitSystem() throws IOException {
        loader = new DataLoader();

        // 1. Load station coordinates first (so all stops have lat/lon available)
        loader.loadStationCoordinates(
            "C:\\Users\\Phalanndwa Zwivhuya\\Documents\\School stuff\\computer science\\3rd year\\2025\\CSC3003S\\Data\\CapeTownTransitData\\metrorail-stations.csv"
        );

        // Train CSVs
        List<String> trainCSVs = Arrays.asList(
            "C:\\Users\\Phalanndwa Zwivhuya\\Documents\\School stuff\\computer science\\3rd year\\2025\\CSC3003S\\Data\\CapeTownTransitData\\train-schedules-2014\\S1.csv",
            "C:\\Users\\Phalanndwa Zwivhuya\\Documents\\School stuff\\computer science\\3rd year\\2025\\CSC3003S\\Data\\CapeTownTransitData\\train-schedules-2014\\S2.csv",
            "C:\\Users\\Phalanndwa Zwivhuya\\Documents\\School stuff\\computer science\\3rd year\\2025\\CSC3003S\\Data\\CapeTownTransitData\\train-schedules-2014\\S3.csv",
            "C:\\Users\\Phalanndwa Zwivhuya\\Documents\\School stuff\\computer science\\3rd year\\2025\\CSC3003S\\Data\\CapeTownTransitData\\train-schedules-2014\\S4.csv",
            "C:\\Users\\Phalanndwa Zwivhuya\\Documents\\School stuff\\computer science\\3rd year\\2025\\CSC3003S\\Data\\CapeTownTransitData\\train-schedules-2014\\S5.csv",
            "C:\\Users\\Phalanndwa Zwivhuya\\Documents\\School stuff\\computer science\\3rd year\\2025\\CSC3003S\\Data\\CapeTownTransitData\\train-schedules-2014\\S6.csv",
            "C:\\Users\\Phalanndwa Zwivhuya\\Documents\\School stuff\\computer science\\3rd year\\2025\\CSC3003S\\Data\\CapeTownTransitData\\train-schedules-2014\\S7.csv",
            "C:\\Users\\Phalanndwa Zwivhuya\\Documents\\School stuff\\computer science\\3rd year\\2025\\CSC3003S\\Data\\CapeTownTransitData\\train-schedules-2014\\S8.csv",
            "C:\\Users\\Phalanndwa Zwivhuya\\Documents\\School stuff\\computer science\\3rd year\\2025\\CSC3003S\\Data\\CapeTownTransitData\\train-schedules-2014\\S9.csv",
            "C:\\Users\\Phalanndwa Zwivhuya\\Documents\\School stuff\\computer science\\3rd year\\2025\\CSC3003S\\Data\\CapeTownTransitData\\train-schedules-2014\\S10.csv",
            "C:\\Users\\Phalanndwa Zwivhuya\\Documents\\School stuff\\computer science\\3rd year\\2025\\CSC3003S\\Data\\CapeTownTransitData\\train-schedules-2014\\S11.csv",
            "C:\\Users\\Phalanndwa Zwivhuya\\Documents\\School stuff\\computer science\\3rd year\\2025\\CSC3003S\\Data\\CapeTownTransitData\\train-schedules-2014\\S12.csv"
        );

        // Bus CSVs (commented for now)
        List<String> busCSVs = Arrays.asList(
            "data/bus/B1.csv",
            "data/bus/B2.csv"
        );

        // Load train data
        for (String file : trainCSVs) {
            List<String[]> rows = loader.loadCSV(file);  //  load the CSV into rows
            loader.buildTrainData(rows); // load the rows 
        }

        // Bus data would be loaded here later...
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

        // 2. Reconstruct path, including coordinates
        return Raptor.reconstructPath(
                result.predecessor,
                sourceId,
                targetId,
                loader,
                loader.trips
        );
    }
}