import { useState } from "react";
import { useNavigate } from "react-router-dom";
import axios from "axios";

const apiKey = "eyJvcmciOiI1YjNjZTM1OTc4NTExMTAwMDFjZjYyNDgiLCJpZCI6IjM5ZWFmYjYwMjI4ZjQ5NGM4MDlhZmRkYmNkYTkxZWMzIiwiaCI6Im11cm11cjY0In0=";

function Home() {
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [date, setDate] = useState("");
  const [time, setTime] = useState("");
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
          "boundary.country": "ZA" // ✅ restrict to South Africa
        }
      }
    );
    setFunc(res.data.features);
  };

  const handleSubmit = () => {
    if (!from || !to || !date || !time) {
      alert("Please fill in all fields");
      return;
    }
    navigate("/search", { state: { from, to, date, time } });
  };

  return (
    <div style={{ padding: "20px", maxWidth: "420px", margin: "auto" }}>
      <h2>Journey Planner</h2>

      {/* FROM input */}
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

      {/* TO input */}
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

      {/* DATE input */}
      <label style={{ display: "block", marginTop: "12px" }}>Date</label>
      <input
        type="date"
        value={date}
        onChange={(e) => setDate(e.target.value)}
      />

      {/* TIME input */}
      <label style={{ display: "block", marginTop: "12px" }}>Time</label>
      <input
        type="time"
        value={time}
        onChange={(e) => setTime(e.target.value)}
      />

      <button onClick={handleSubmit} style={{ marginTop: "16px" }}>
        Search
      </button>
    </div>
  );
}

export default Home;
