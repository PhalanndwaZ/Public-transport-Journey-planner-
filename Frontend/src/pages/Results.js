import { useEffect, useState } from "react";
import { useLocation } from "react-router-dom";
import { MapContainer, TileLayer, Polyline } from "react-leaflet";
import "leaflet/dist/leaflet.css";
import axios from "axios";

const apiKey = "eyJvcmciOiI1YjNjZTM1OTc4NTExMTAwMDFjZjYyNDgiLCJpZCI6IjM5ZWFmYjYwMjI4ZjQ5NGM4MDlhZmRkYmNkYTkxZWMzIiwiaCI6Im11cm11cjY0In0=";

function Results() {
  const { state } = useLocation();
  const [route, setRoute] = useState(null);

  useEffect(() => {
    const fetchRoute = async () => {
      // Convert place names to coordinates
      const geocode = async (place) => {
        const res = await axios.get(
          `https://api.openrouteservice.org/geocode/search?api_key=${apiKey}&text=${place}`
        );
        return res.data.features[0].geometry.coordinates;
      };

      const fromCoords = await geocode(state.from);
      const toCoords = await geocode(state.to);

      const res = await axios.post(
        "https://api.openrouteservice.org/v2/directions/driving-car",
        { coordinates: [fromCoords, toCoords] },
        { headers: { Authorization: apiKey, "Content-Type": "application/json" } }
      );

      setRoute(res.data.features[0]);
    };

    fetchRoute();
  }, [state]);

  return (
    <div>
      <h2>Results</h2>
      {route ? (
        <>
          <MapContainer
            style={{ height: "400px" }}
            center={[51.505, -0.09]}
            zoom={13}
          >
            <TileLayer url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />
            <Polyline
              positions={route.geometry.coordinates.map(([lng, lat]) => [lat, lng])}
              color="blue"
              weight={5}
            />
          </MapContainer>
          <p>
            Distance: {(route.properties.summary.distance / 1000).toFixed(2)} km
            <br />
            Duration: {(route.properties.summary.duration / 60).toFixed(1)} min
          </p>
        </>
      ) : (
        <p>Loading route...</p>
      )}
    </div>
  );
}
export default Results;
