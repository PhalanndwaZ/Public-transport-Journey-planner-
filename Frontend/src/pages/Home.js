import { useState } from "react";
import { useNavigate } from "react-router-dom";
import axios from "axios";

const apiKey = "eyJvcmciOiI1YjNjZTM1OTc4NTExMTAwMDFjZjYyNDgiLCJpZCI6IjM5ZWFmYjYwMjI4ZjQ5NGM4MDlhZmRkYmNkYTkxZWMzIiwiaCI6Im11cm11cjY0In0=";

function Home() {
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [fromSuggestions, setFromSuggestions] = useState([]);
  const [toSuggestions, setToSuggestions] = useState([]);
  const navigate = useNavigate();

  const fetchSuggestions = async (query, setFunc) => {
    if (query.length < 3) return;
    const res = await axios.get(
        `https://api.openrouteservice.org/geocode/search`, {
            params: {
            api_key: apiKey,
            text: query,
            "boundary.country": "ZA"
          }
        }
    );
    setFunc(res.data.features);
  };

  const handleSubmit = () => {
    navigate("/search", { state: { from, to } });
  };

  return (
    <div style={{ padding: "20px", maxWidth: "400px", margin: "auto" }}>
      <h2>Journey Planner</h2>
      <input
        value={from}
        onChange={(e) => {
          setFrom(e.target.value);
          fetchSuggestions(e.target.value, setFromSuggestions);
        }}
        placeholder="From..."
      />
      <ul>
        {fromSuggestions.map((s, i) => (
          <li key={i} onClick={() => setFrom(s.properties.label)}>
            {s.properties.label}
          </li>
        ))}
      </ul>

      <input
        value={to}
        onChange={(e) => {
          setTo(e.target.value);
          fetchSuggestions(e.target.value, setToSuggestions);
        }}
        placeholder="To..."
      />
      <ul>
        {toSuggestions.map((s, i) => (
          <li key={i} onClick={() => setTo(s.properties.label)}>
            {s.properties.label}
          </li>
        ))}
      </ul>

      <button onClick={handleSubmit}>Search</button>
    </div>
  );
}
export default Home;
