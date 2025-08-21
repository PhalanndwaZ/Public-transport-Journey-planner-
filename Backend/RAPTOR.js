// RAPTOR.js
function timeToMinutes(time) {
    const [hh, mm] = time.split(":").map(Number);
    return hh * 60 + mm;
}

function minutesToTime(minutes) {
    const hh = Math.floor(minutes / 60).toString().padStart(2, "0");
    const mm = (minutes % 60).toString().padStart(2, "0");
    return `${hh}:${mm}`;
}

/**
 * RAPTOR Algorithm
 * @param {number} sourceStop - stopID of source
 * @param {number} targetStop - stopID of target
 * @param {string} departureTime - "HH:MM"
 * @param {object} stops - stopName -> stopID
 * @param {object} trips - tripID -> { route, times: [{stopID, time}] }
 * @param {object} stopToRoutes - stopID -> [routeIDs]
 */
function RAPTOR(sourceStop, targetStop, departureTime, stops, trips, stopToRoutes) {
    const INF = Infinity;
    const numStops = Object.keys(stops).length;
    const departureMinutes = timeToMinutes(departureTime);

    const earliestArrival = Array(numStops).fill(INF);
    const predecessor = Array(numStops).fill(null);

    earliestArrival[sourceStop] = departureMinutes;

    const MAX_TRANSFERS = 5;
    let marked = new Set([sourceStop]);

    for (let round = 0; round < MAX_TRANSFERS; round++) {
        const nextMarked = new Set();

        for (const stop of marked) {
            const routes = stopToRoutes[stop] || [];
            
            for (const routeID of routes) {
                // Get all trips for this route and sort by departure time from current stop
                const allRouteTrips = Object.values(trips).filter(t => t.route === routeID);
                
                // Sort trips by departure time from current stop to find earliest valid option
                const routeTrips = allRouteTrips
                    .map(trip => {
                        const stopTime = trip.times.find(t => t.stopID === stop);
                        return stopTime ? { ...trip, departureTime: timeToMinutes(stopTime.time) } : null;
                    })
                    .filter(trip => trip !== null)
                    .sort((a, b) => a.departureTime - b.departureTime);

             

                for (const trip of routeTrips) {
                    const times = trip.times;

                    const stopIdx = times.findIndex(t => t.stopID === stop);
                    if (stopIdx === -1) continue;

                    const boardTime = timeToMinutes(times[stopIdx].time);
                    
                    // Handle source vs transfer stops differently
                    if (stop === sourceStop) {
                        // For source stop: find best trip at or after departure time
                        if (boardTime < departureMinutes) {
                            continue;
                        }
                    } else {
                        // For transfer stops: must board after you arrive there
                        if (boardTime < earliestArrival[stop]) {
                            continue;
                        }
                    }

                    // Additional check: Ensure trip goes forward in time from this stop
                    let validTrip = true;
                    if (stopIdx < times.length - 1) {
                        const nextStopTime = timeToMinutes(times[stopIdx + 1].time);
                        if (nextStopTime <= boardTime) {
                            validTrip = false;
                        }
                    }
                    
                    if (!validTrip) continue;

                    // Ride forward on this trip
                    for (let i = stopIdx + 1; i < times.length; i++) {
                        const { stopID, time } = times[i];
                        const arrTime = timeToMinutes(time);

                        // Ensure arrival time is after board time (trip goes forward)
                        if (arrTime <= boardTime) {
                            continue;
                        }

                        if (arrTime < earliestArrival[stopID]) {
                            earliestArrival[stopID] = arrTime;
                            predecessor[stopID] = {
                                tripID: trip.tripID,
                                from: stop,
                                time: arrTime,
                                boardTime: boardTime
                            };
                            nextMarked.add(stopID);
                        }
                    }
                }
            }
        }

        if (nextMarked.size === 0) break;
        marked = nextMarked;
    }

    return { earliestArrival, predecessor };
}
/**
 * Reconstruct path from predecessor info
 */
function reconstructPath(predecessor, source, target, loader, trips) {
    const path = [];
    let current = target;

    while (current !== source && predecessor[current]) {
        const step = predecessor[current];
        const trip = trips[step.tripID];

        const fromIdx = trip.times.findIndex(t => t.stopID === step.from);
        const toIdx = trip.times.findIndex(t => t.stopID === current);

        for (let i = fromIdx; i <= toIdx; i++) {
            const stopID = trip.times[i].stopID;
            const time = trip.times[i].time;
            path.push({
                tripID: step.tripID,
                stopID,
                stopName: loader.getStopNameById(stopID),
                time
            });
        }
        current = step.from;
    }

    return path.reverse();
}

module.exports = {
    RAPTOR,
    timeToMinutes,
    minutesToTime,
    reconstructPath
};
