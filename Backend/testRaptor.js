// testRaptor.js
const DataLoader = require("./fileLoader");
const { RAPTOR, minutesToTime, reconstructPath, timeToMinutes } = require("./RAPTOR");

// ADD THE DEBUG FUNCTION HERE
function debugJourneyPlanning(sourceStop, targetStop, departureTime, stops, trips, stopToRoutes, loader) {
    console.log(`\n🔍 COMPREHENSIVE DEBUG`);
    console.log(`Source: ${sourceStop} (${loader.getStopNameById(sourceStop)})`);
    console.log(`Target: ${targetStop} (${loader.getStopNameById(targetStop)})`);
    console.log(`Departure: ${departureTime}`);
    
    // 1. Check if stops exist
    console.log(`\n1️⃣ STOP VALIDATION`);
    console.log(`Source stop ${sourceStop} exists: ${sourceStop in stopToRoutes}`);
    console.log(`Target stop ${targetStop} exists: ${targetStop in stopToRoutes}`);
    
    // 2. Check routes from source
    console.log(`\n2️⃣ ROUTES FROM SOURCE`);
    const sourceRoutes = stopToRoutes[sourceStop] || [];
    console.log(`Routes from source: ${sourceRoutes.join(', ')}`);
    
    // 3. Find ALL trips that serve the source stop at or after departure time
    console.log(`\n3️⃣ ALL VALID TRIPS FROM SOURCE`);
    const departureMinutes = timeToMinutes(departureTime);
    
    sourceRoutes.forEach(routeID => {
        console.log(`\n--- Route ${routeID} ---`);
        const routeTrips = Object.values(trips).filter(t => t.route === routeID);
        
        const validTrips = [];
        routeTrips.forEach(trip => {
            const sourceStopInTrip = trip.times.find(t => t.stopID === sourceStop);
            if (sourceStopInTrip) {
                const tripDepartureTime = timeToMinutes(sourceStopInTrip.time);
                const isValid = tripDepartureTime >= departureMinutes;
                
                console.log(`Trip ${trip.tripID}: departs ${sourceStopInTrip.time} (${isValid ? '✅ VALID' : '❌ TOO EARLY'})`);
                
                if (isValid) {
                    validTrips.push({
                        tripID: trip.tripID,
                        departureTime: sourceStopInTrip.time,
                        stops: trip.times.map(t => `${loader.getStopNameById(t.stopID)}@${t.time}`).join(' → ')
                    });
                }
            }
        });
        
        console.log(`Valid trips: ${validTrips.length}`);
        validTrips.forEach(trip => {
            console.log(`  🚆 ${trip.tripID} @ ${trip.departureTime}`);
            console.log(`     Route: ${trip.stops}`);
            
            // Check if this trip reaches target directly
            const tripData = trips[trip.tripID];
            const targetInTrip = tripData.times.find(t => t.stopID === targetStop);
            if (targetInTrip) {
                console.log(`     🎯 DIRECT CONNECTION TO TARGET @ ${targetInTrip.time}`);
            }
        });
    });
    
    // 4. Check what routes serve the target
    console.log(`\n4️⃣ ROUTES TO TARGET`);
    const targetRoutes = stopToRoutes[targetStop] || [];
    console.log(`Routes serving target: ${targetRoutes.join(', ')}`);
    
    // 5. Find direct connections
    console.log(`\n5️⃣ DIRECT CONNECTIONS`);
    const commonRoutes = sourceRoutes.filter(r => targetRoutes.includes(r));
    console.log(`Common routes (direct connection possible): ${commonRoutes.join(', ')}`);
    
    commonRoutes.forEach(routeID => {
        console.log(`\nDirect trips on route ${routeID}:`);
        const routeTrips = Object.values(trips).filter(t => t.route === routeID);
        
        routeTrips.forEach(trip => {
            const sourceStopInTrip = trip.times.find(t => t.stopID === sourceStop);
            const targetStopInTrip = trip.times.find(t => t.stopID === targetStop);
            
            if (sourceStopInTrip && targetStopInTrip) {
                const sourceDep = timeToMinutes(sourceStopInTrip.time);
                const targetArr = timeToMinutes(targetStopInTrip.time);
                const isValidTime = sourceDep >= departureMinutes;
                const isForward = targetArr > sourceDep;
                
                console.log(`Trip ${trip.tripID}: ${sourceStopInTrip.time} → ${targetStopInTrip.time} ${isValidTime && isForward ? '✅' : '❌'}`);
                if (isValidTime && isForward) {
                    console.log(`  🎯 PERFECT DIRECT TRIP!`);
                }
            }
        });
    });
    
    // 6. Sample a few trips to check data integrity
    console.log(`\n6️⃣ DATA INTEGRITY CHECK`);
    const sampleTrips = Object.values(trips).slice(0, 3);
    sampleTrips.forEach(trip => {
        console.log(`Trip ${trip.tripID} sample:`, 
            trip.times.slice(0, 5).map(t => `${t.stopID}@${t.time}`).join(' → ')
        );
    });
}

// arguments: node testRaptor.js "CAPE TOWN" "WOODSTOCK" 04:30
const [,, sourceStopName, targetStopName, departureTime] = process.argv;

if (!sourceStopName || !targetStopName || !departureTime) {
    console.log("Usage: node testRaptor.js <sourceStop> <targetStop> <HH:MM>");
    process.exit(1);
}

const loader = new DataLoader();
const filePath = "C:\\Users\\Phalanndwa Zwivhuya\\Documents\\School stuff\\computer science\\3rd year\\2025\\CSC3003S\\Data\\CapeTownTransitData\\train-schedules-2014\\S1.csv";
const data = loader.loadFromFile(filePath);


// BUILD stopToRoutes mapping (you might be missing this!)
const stopToRoutes = {};
Object.values(data.trips).forEach(trip => {
    trip.times.forEach(stopTime => {
        const stopID = stopTime.stopID;
        const routeID = trip.route;
        
        if (!stopToRoutes[stopID]) {
            stopToRoutes[stopID] = [];
        }
        if (!stopToRoutes[stopID].includes(routeID)) {
            stopToRoutes[stopID].push(routeID);
        }
    });
});

const sourceStop = loader.findStopByName(sourceStopName.toUpperCase());
const targetStop = loader.findStopByName(targetStopName.toUpperCase());

// Add this right after loading the data in testRaptor.js
console.log("\n🔍 DEBUGGING TRIP 3535");
const trip3535 = data.trips['3535'];
if (trip3535) {
    console.log("Trip 3535 data:", trip3535);
    console.log("All stop times:");
    trip3535.times.forEach((stopTime, index) => {
        console.log(`  ${index}: Stop ${stopTime.stopID} (${loader.getStopNameById(stopTime.stopID)}) at ${stopTime.time}`);
    });
    
    // Find Cape Town specifically
    const capeStopTime = trip3535.times.find(t => t.stopID === sourceStop);
    if (capeStopTime) {
        console.log(`Cape Town departure: ${capeStopTime.time}`);
    } else {
        console.log("Cape Town not found in this trip!");
    }
} else {
    console.log("Trip 3535 not found in data!");
}

if (sourceStop === undefined || targetStop === undefined) {
    console.error("❌ Could not find source or target stop");
    console.log("Available stops:", loader.getAvailableStops().slice(0, 10).join(", "), "...");
    process.exit(1);
}

console.log(`\n=== Planning Journey from ${sourceStopName} to ${targetStopName} at ${departureTime} ===`);

// ADD DEBUG CALL HERE
debugJourneyPlanning(sourceStop, targetStop, departureTime, data.stops, data.trips, stopToRoutes, loader);

// THEN RUN RAPTOR
const result = RAPTOR(sourceStop, targetStop, departureTime, data.stops, data.trips, stopToRoutes);

if (result.earliestArrival[targetStop] === Infinity) {
    console.log("❌ No path found");
} else {
    console.log("✅ Earliest arrival:", minutesToTime(result.earliestArrival[targetStop]));
    const path = reconstructPath(result.predecessor, sourceStop, targetStop, loader, data.trips);
    path.forEach((step, i) => {
        console.log(`${i + 1}. ${step.stopName} at ${step.time} (Trip: ${step.tripID})`);
    });
}
