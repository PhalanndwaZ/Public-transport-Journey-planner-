import { useEffect } from "react";
import { useLocation, useNavigate } from "react-router-dom";

export default function Search() {
  const { state } = useLocation(); // { from, to, ... }
  const navigate = useNavigate();

  useEffect(() => {
    // Do your real fetching here, then navigate to results.
    // For demo: simulate waiting
    const t = setTimeout(() => {
      navigate("/results", { state });
    }, 2000);
    return () => clearTimeout(t);
  }, [navigate, state]);

  return (
    <div
      style={{
        minHeight: "100vh",
        display: "grid",
        placeItems: "center",
        padding: "32px",
        background:
          "linear-gradient(180deg, #e6f7f5 0%, #d6ecfa 100%)" /* pastel green → blue */,
      }}
    >
      <div
        style={{
          width: "min(520px, 92vw)",
          textAlign: "center",
          background: "white",
          borderRadius: "16px",
          padding: "28px 24px",
          boxShadow:
            "0 8px 20px rgba(0,0,0,0.06), 0 2px 8px rgba(0,0,0,0.05)",
        }}
      >
        {/* Rotating SVG spinner */}
        <svg
          className="spinner"
          width="96"
          height="96"
          viewBox="0 0 50 50"
          role="img"
          aria-label="Searching for routes"
        >
          <defs>
            {/* pastel gradient stroke */}
            <linearGradient id="pastelSpin" x1="0" y1="0" x2="1" y2="1">
              <stop offset="0%" stopColor="#a3d9c9" />
              <stop offset="100%" stopColor="#9fc9f3" />
            </linearGradient>
          </defs>

          {/* Track (faint ring) */}
          <circle
            cx="25"
            cy="25"
            r="20"
            fill="none"
            stroke="#eaf6f3"
            strokeWidth="6"
          />

          {/* Arc that visually spins */}
          <circle
            cx="25"
            cy="25"
            r="20"
            fill="none"
            stroke="url(#pastelSpin)"
            strokeWidth="6"
            strokeLinecap="round"
            strokeDasharray="110"
            strokeDashoffset="80"
          />
        </svg>

        <h2 style={{ margin: "12px 0 4px" }}>Finding the best routes…</h2>
        <p style={{ margin: 0, opacity: 0.8 }}>
          Please wait while we search public transport options.
        </p>

        {/* Optional: show what the user searched for */}
        {state?.from && state?.to && (
          <p style={{ marginTop: 14, fontSize: 14, opacity: 0.7 }}>
            {state.from} → {state.to}
          </p>
        )}
      </div>
    </div>
  );
}
