const DataLoader = require('./fileLoader');

// convert time to minutes for comparison
function timeToMinutes(time) {
    const [hh, mm] = time.split(":").map(Number);
    return hh * 60 + mm;
}

// convert minutes back to time
function minutesToTime(minutes) {
    const hh = Math.floor(minutes / 60).toString().padStart(2, "0");
    const mm = (minutes % 60).toString().padStart(2, "0");
    return `${hh}:${mm}`;
}

// Main RAPTOR Algorithm
function RAPTOR(sourceStop, targetStop, departureTime, stops, routes, trips) {
    // Initialize the parameters
    const INF = Infinity;
    const numStops = Object.keys(stops).length;
    const earliestArrival = Array(numStops).fill(INF);
    
    // Initialize starting location - use minutes for consistent comparison
    earliestArrival[sourceStop] = timeToMinutes(departureTime);
    
    // keep predecessor info for path reconstruction
    const predecessor = Array(numStops).fill(null);
    
    const MAX_TRANSFERS = 5; // we are keeping it as default for now
    let markedStops = new Set([sourceStop]);
    
    for (let round = 0; round < MAX_TRANSFERS; round++) {
        const newMarked = new Set();
        
        for (const stop of markedStops) {
            // check every trip in dataset
            for (const [tripID, trip] of Object.entries(trips)) {
                const routeID = trip.route;
                const stopSequence = routes[routeID];
                
                // locating stop
                const pos = stopSequence.indexOf(stop);
                if (pos === -1) continue;
                
                // find boarding time at this stop
                const times = trip.times;
                const boarding = times.find(t => t.stopID === stop);
                if (!boarding) continue;
                
                const boardTime = timeToMinutes(boarding.time);
                
                // ensuring user is at stop
                if (boardTime < earliestArrival[stop]) continue;
                
                // ride this trip forward
                for (let i = pos + 1; i < times.length; i++) {
                    const { stopID, time } = times[i];
                    const arrTime = timeToMinutes(time);
                    
                    if (arrTime < earliestArrival[stopID]) {
                        earliestArrival[stopID] = arrTime;
                        predecessor[stopID] = { tripID, from: stop, time: arrTime };
                        newMarked.add(stopID);
                    }
                }
            }
        }
        
        markedStops = newMarked; // Update markedStops for next round
        if (newMarked.size === 0) break; // no improvement found
        
        // Optional: Early termination if target is reached and no better path possible
        if (earliestArrival[targetStop] !== INF && newMarked.size === 0) break;
    }
    
    return { earliestArrival, predecessor };
}

function reconstructFullPath(predecessor, source, target, dataLoader, trips) {
    const path = [];
    let current = target;
    
    while (current !== source && predecessor[current]) {
        const step = predecessor[current];
        const trip = trips[step.tripID];
        
        // find indices of from and current in trip
        const fromIdx = trip.times.findIndex(t => t.stopID === step.from);
        const toIdx = trip.times.findIndex(t => t.stopID === current);
        
        // expand all stops between from → to
        for (let i = fromIdx; i <= toIdx; i++) {
            const stopID = trip.times[i].stopID;
            const time = trip.times[i].time;
            path.push({
                tripID: step.tripID,
                stopID: stopID,
                stopName: dataLoader.getStopNameById(stopID),
                time: time
            });
        }
        
        current = step.from;
    }
    
    return path.reverse();
}

// Test the RAPTOR algorithm
function testRAPTOR() {
    // Initialize data loader
    const loader = new DataLoader();
    const filePath = "C:\\Users\\Phalanndwa Zwivhuya\\Documents\\School stuff\\computer science\\3rd year\\2025\\CSC3003S\\Data\\CapeTownTransitData\\train-schedules-2014\\S1.csv";
    
    // Load the data
    const data = loader.loadFromFile(filePath);
    const { stops, routes, trips } = data;
    
    console.log("\n=== Available Stops ===");
    const availableStops = loader.getAvailableStops();
    console.log("Total stops:", availableStops.length);
    console.log("First 10 stops:", availableStops.slice(0, 10));
    
    // Test with actual stops from your data
    const sourceStopName = availableStops[0]; // Use first available stop
    const targetStopName = availableStops[Math.min(5, availableStops.length - 1)]; // Use 6th stop or last available
    
    const sourceStop = loader.findStopByName(sourceStopName);
    const targetStop = loader.findStopByName(targetStopName);
    const departureTime = "06:00";
    
    console.log("\n=== Testing RAPTOR ===");
    console.log("Source:", sourceStopName, "(ID:", sourceStop, ")");
    console.log("Target:", targetStopName, "(ID:", targetStop, ")");
    console.log("Departure time:", departureTime);
    
    if (sourceStop === undefined || targetStop === undefined) {
        console.log("Error: Could not find source or target stop");
        return;
    }
    
    const result = RAPTOR(sourceStop, targetStop, departureTime, stops, routes, trips);
    
    if (result.earliestArrival[targetStop] === Infinity) {
        console.log("No path found between", sourceStopName, "and", targetStopName);
    } else {
        console.log("Earliest arrival at target:", minutesToTime(result.earliestArrival[targetStop]));
        
        const path = reconstructFullPath(result.predecessor, sourceStop, targetStop, loader, trips);
        console.log("Path length:", path.length, "stops");
        
        if (path.length > 0) {
            console.log("Path details:");
            path.forEach((step, index) => {
                console.log(`  ${index + 1}. ${step.stopName} at ${step.time} (Trip: ${step.tripID})`);
            });
        }
    }
}

// Export functions for use in other files
module.exports = {
    RAPTOR,
    timeToMinutes,
    minutesToTime,
    reconstructFullPath,
    testRAPTOR
};

// Run test if this file is executed directly
if (require.main === module) {
    testRAPTOR();
}