const fileSystem = require("fs");
const parse = require("csv-parse/sync");
const { time, log } = require("console");

const filePath = "C:\\Users\\Phalanndwa Zwivhuya\\Documents\\School stuff\\computer science\\3rd year\\2025\\CSC3003S\\Data\\CapeTownTransitData\\train-schedules-2014\\S1.csv";


function LoadRoutes(filePath){
    const content = fileSystem.readFileSync(filePath,"utf-8");
    //parse into array with header row 
    const rows = parse.parse(content, {
        columns: true,
        bom: true,
        skip_empty_lines: true,
        relax_column_count: true,
        relax_quotes: true,
        trim: false
    });
    return rows;
}

// Build data for the data structure 
function BuildData(rows){
    const stops = {}; // stopname -> stopID
    const routes = {}; // routeID -> [stopIDs]
    const trips = {}; // tripID -> {route, times: [stopID, time]}
    let stopCounter = 0; // transtition counter
    
    
    rows.forEach((row) =>{
    const tripID = row.trip_id;
    const routeID = row.route;
    // ensure route initialization 
    if(!routes[routeID]) routes[routeID] = [];


    // build trip 
    const trip = {
        tripID,
        route: routeID,
        times: [],
    };

    // loop to remove fields that are not data 
    Object.keys(row).forEach(col => {
        if (
            col === "trip_id" ||
            col === "day_type" ||
            col === "direction" ||
            col === "route"
        ) {
            return; // skip meta info
        }
        const raw = row[col];        // value from CSV
        const time = raw ? raw.trim() : "";  // safe trim
        //console.log(`Col: ${col}, Value: "${row[col]}"`);

        if(time){
            // assign stopIF if new stop 
            if(!(col in stops)){
                stops[col] = stopCounter++;
            }
            const stopID = stops[col];
            // add to trip 
            trip.times.push({stopID,time});


            // add trip to route( once in order of appearance)
            if(!routes[routeID].includes(stopID)){
                routes[routeID].push(stopID);

            }
        }

    })
    trips[tripID] = trip;
    });
    return { stops, routes, trips};


}

const rows = LoadRoutes(filePath);
const { stops, routes, trips } = BuildData(rows);

console.log("Stops: ",stops)
console.log("Route E1: ",routes["E1"]);
console.log("SampleTrip: ", trips["3558"]);

//Object.values(trips).forEach(trip => console.log(trip));




// const rows = LoadRoutes(filePath);
// //console.log(rows);
// // running the first row 
// console.log(rows[0])
