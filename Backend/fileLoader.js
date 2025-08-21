const fileSystem = require("fs");
const parse = require("csv-parse/sync");

class DataLoader {
    constructor() {
        this.stops = {};
        this.routes = {};
        this.trips = {};
        this.stopToRoutes = {}; // NEW: stopID -> [routeIDs]
    }

    LoadRoutes(filePath) {
        const content = fileSystem.readFileSync(filePath, "utf-8");
        return parse.parse(content, {
            columns: true,
            bom: true,
            skip_empty_lines: true,
            relax_column_count: true,
            relax_quotes: true,
            trim: false
        });
    }

    BuildData(rows) {
        const stops = {};
        const routes = {};
        const trips = {};
        const stopToRoutes = {};
        let stopCounter = 0;

        rows.forEach((row) => {
            // CREATE UNIQUE TRIP IDs: Include day type to avoid overwriting
            const baseTripID = row.trip_id;
            const dayType = row.day_type.replace(/\s+/g, '_').toUpperCase();
            const tripID = `${baseTripID}_${dayType}`;
            
            const routeID = row.route;
            if (!routes[routeID]) routes[routeID] = [];

            const trip = { 
                tripID, 
                baseTripID, // Keep original for reference
                dayType: row.day_type,
                route: routeID, 
                times: [] 
            };
                      
            // Collect all stop times first
            const stopTimes = [];
            Object.keys(row).forEach(col => {
                if (["trip_id", "day_type", "direction", "route"].includes(col)) return;

                const raw = row[col];
                const time = raw ? raw.trim() : "";
                if (!time) return;

                if (!(col in stops)) {
                    stops[col] = stopCounter++;
                }
                const stopID = stops[col];
                stopTimes.push({ stopID, time, stopName: col });

                // add stop to route if not already
                if (!routes[routeID].includes(stopID)) {
                    routes[routeID].push(stopID);
                }

                //  link stop → route
                if (!stopToRoutes[stopID]) stopToRoutes[stopID] = [];
                if (!stopToRoutes[stopID].includes(routeID)) {
                    stopToRoutes[stopID].push(routeID);
                }
            });

            // FIX: Reverse stop order for inbound trips
            if (row.direction && row.direction.toLowerCase() === 'inbound') {
                stopTimes.reverse();
            }

            trip.times = stopTimes.map(({ stopID, time }) => ({ stopID, time }));
            trips[tripID] = trip;
        });

        this.stops = stops;
        this.routes = routes;
        this.trips = trips;
        this.stopToRoutes = stopToRoutes;

        return { stops, routes, trips, stopToRoutes };
    }

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