const fileSystem = require("fs");
const parse = require("csv-parse/sync");

class DataLoader {
    constructor() {
        this.stops = {};
        this.routes = {};
        this.trips = {};
    }

    LoadRoutes(filePath) {
        const content = fileSystem.readFileSync(filePath, "utf-8");
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
    BuildData(rows) {
        const stops = {}; // stopname -> stopID
        const routes = {}; // routeID -> [stopIDs]
        const trips = {}; // tripID -> {route, times: [stopID, time]}
        let stopCounter = 0; // transition counter

        rows.forEach((row) => {
            const tripID = row.trip_id;
            const routeID = row.route;
            // ensure route initialization
            if (!routes[routeID]) routes[routeID] = [];

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
                const raw = row[col];
                // value from CSV
                const time = raw ? raw.trim() : "";  // safe trim

                if (time) {
                    // assign stopID if new stop
                    if (!(col in stops)) {
                        stops[col] = stopCounter++;
                    }
                    const stopID = stops[col];
                    // add to trip
                    trip.times.push({ stopID, time });

                    // add trip to route (once in order of appearance)
                    if (!routes[routeID].includes(stopID)) {
                        routes[routeID].push(stopID);
                    }
                }
            });
            trips[tripID] = trip;
        });

        // Store in instance variables
        this.stops = stops;
        this.routes = routes;
        this.trips = trips;

        return { stops, routes, trips };
    }

    // Load data from file and build structures
    loadFromFile(filePath) {
        console.log("Loading transit data from:", filePath);
        const rows = this.LoadRoutes(filePath);
        const data = this.BuildData(rows);
        
        console.log("Data loaded successfully!");
        console.log("Total stops:", Object.keys(data.stops).length);
        console.log("Total routes:", Object.keys(data.routes).length);
        console.log("Total trips:", Object.keys(data.trips).length);
        
        return data;
    }

    // Helper methods
    getAvailableStops() {
        return Object.keys(this.stops).sort();
    }

    findStopByName(stopName) {
        return this.stops[stopName];
    }

    getStopNameById(stopId) {
        for (const [name, id] of Object.entries(this.stops)) {
            if (id === stopId) return name;
        }
        return null;
    }
}

module.exports = DataLoader;