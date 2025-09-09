package backend;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class TransitSystem {
    // This class loads all the data from buses to trins and returns a big dataset that can be used to run multiple queries 
    private final DataLoader loader;

    public TransitSystem() throws IOException {
        loader = new DataLoader();

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
            // ... all 16 train CSVs
        );

        // Bus CSVs
        List<String> busCSVs = Arrays.asList(
            "data/bus/B1.csv",
            "data/bus/B2.csv"
            // ... all bus CSVs
        );

        // Load train data
        for (String file : trainCSVs) {
            loader.buildData(loader.loadCSV(file), "TRAIN");
        }

        // Load bus data commented out for now to deal with the train list first
        // for (String file : busCSVs) {
        //     loader.buildData(loader.loadCSV(file), "BUS");
        // }
    }

    public DataLoader getLoader() {
        return loader;
    }
    

    // wrapper so that frontend or tests can be run to test the query 
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

        return Raptor.reconstructPath(result.predecessor, sourceId, targetId, loader, loader.trips);
    }
}
