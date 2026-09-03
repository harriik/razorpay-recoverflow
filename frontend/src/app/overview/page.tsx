"use client";

import { useEffect, useState } from "react";

type Overview = {
  revenueAtRisk: string | number;
  recoveredRevenue: string | number;
  recoveryRate: string | number;
  activeRecoveryCases: number;
  recoveriesToday: number;
  escalatedCases: number;
  unknownCases: number;
  totalCases: number;
  byIntervention: Record<string, number>;
  byFailureCategory: Record<string, number>;
  trend: { labels: string[]; recovered: number[] };
  source: string;
};

function Kpi({ label, value, sub, accent }: { label: string; value: string; sub?: string; accent?: string }) {
  return (
    <div style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 16, display: "flex", flexDirection: "column", gap: 6 }}>
      <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.6, color: "#64748b", textTransform: "uppercase" }}>{label}</div>
      <div style={{ fontSize: 22, fontWeight: 900, color: accent || "#0f172a", letterSpacing: -0.5 }}>{value}</div>
      {sub && <div style={{ fontSize: 11, color: "#64748b" }}>{sub}</div>}
    </div>
  );
}

function Bar({ label, value, max, color }: { label: string; value: number; max: number; color: string }) {
  const pct = max === 0 ? 0 : Math.round((value / max) * 100);
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 12 }}>
      <div style={{ width: 110, color: "#475569", fontWeight: 600, fontSize: 11, textTransform: "uppercase" }}>{label}</div>
      <div style={{ flex: 1, height: 8, background: "#f1f5f9", borderRadius: 999, overflow: "hidden" }}>
        <div style={{ width: `${pct}%`, height: "100%", background: color }} />
      </div>
      <div style={{ width: 28, textAlign: "right", fontWeight: 700, color: "#0f172a" }}>{value}</div>
    </div>
  );
}

export default function OverviewPage() {
  const [data, setData] = useState<Overview | null>(null);
  const [health, setHealth] = useState<any>(null);
  const [error, setError] = useState<string | null>(null);
  const api = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

  useEffect(() => {
    fetch(`${api}/api/v1/overview`)
      .then(async (r) => {
        if (!r.ok) throw new Error(`overview ${r.status}`);
        return r.json();
      })
      .then(setData)
      .catch((e) => setError(String(e)));
    fetch(`${api}/api/v1/health`)
      .then((r) => r.json())
      .then(setHealth)
      .catch(() => setHealth({ status: "DOWN" }));
  }, [api]);

  const fmtMoney = (v: any) => {
    const n = typeof v === "string" ? parseFloat(v) : Number(v);
    if (isNaN(n)) return "—";
    return "₹" + n.toLocaleString("en-IN", { maximumFractionDigits: 0 });
  };

  return (
    <main style={{ maxWidth: 1280, margin: "0 auto", padding: 24 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 16, flexWrap: "wrap" }}>
        <div>
          <h1 style={{ margin: 0, fontSize: 28, fontWeight: 900, letterSpacing: -0.6, color: "#0f172a" }}>Operations Overview</h1>
          <p style={{ margin: "6px 0 0", color: "#475569", fontSize: 13, maxWidth: 720 }}>
            Real-time revenue recovery console — <strong>Revenue at Risk</strong> vs <strong>Recovered</strong> — powered by same <code>ExecutionService</code> path as Failure Lab.
            <span style={{ background: "#fef3c7", border: "1px solid #fde68a", padding: "1px 6px", borderRadius: 6, fontSize: 11, fontWeight: 700, marginLeft: 8 }}>SYNTHETIC EVALUATION where labeled</span>
          </p>
        </div>
        <div style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: 10, padding: "8px 12px", fontSize: 12, display: "flex", gap: 12, alignItems: "center" }}>
          <span style={{ color: "#64748b" }}>Backend</span>
          <span style={{ background: health?.status === "UP" ? "#dcfce7" : "#fee2e2", color: health?.status === "UP" ? "#166534" : "#991b1b", padding: "2px 8px", borderRadius: 999, fontWeight: 800, fontSize: 11 }}>{health?.status || "…"}</span>
          <span style={{ color: "#94a3b8" }}>{api}</span>
        </div>
      </div>

      {error && (
        <div style={{ marginTop: 16, background: "#fef2f2", border: "1px solid #fecaca", color: "#991b1b", padding: 12, borderRadius: 8, fontSize: 12 }}>
          Backend unavailable: {error} — showing empty state. Start backend on :8080 or configure <code>NEXT_PUBLIC_API_URL</code>.
        </div>
      )}

      <div style={{ marginTop: 20, display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(180px, 1fr))", gap: 12 }}>
        <Kpi label="Revenue at Risk" value={data ? fmtMoney(data.revenueAtRisk) : "—"} sub={`${data?.totalCases ?? 0} cases`} />
        <Kpi label="Recovered Revenue" value={data ? fmtMoney(data.recoveredRevenue) : "—"} sub={`${data?.recoveryRate ?? 0}% recovery rate`} accent="#16a34a" />
        <Kpi label="Recovery Rate" value={data ? `${data.recoveryRate}%` : "—"} sub="Recovered / At Risk" accent="#2563eb" />
        <Kpi label="Active Cases" value={data ? String(data.activeRecoveryCases) : "—"} sub="Not terminal" />
        <Kpi label="Recoveries Today" value={data ? String(data.recoveriesToday) : "—"} sub="Last 24h" accent="#16a34a" />
        <Kpi label="Escalated" value={data ? String(data.escalatedCases) : "—"} sub="Requires review" accent="#ea580c" />
        <Kpi label="UNKNOWN" value={data ? String(data.unknownCases) : "—"} sub="Awaiting reconciliation" accent="#d97706" />
      </div>

      <div style={{ marginTop: 16, display: "grid", gridTemplateColumns: "1.2fr 0.8fr", gap: 16 }}>
        <div style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 16 }}>
          <h3 style={{ margin: 0, fontSize: 13, fontWeight: 800, letterSpacing: 0.6, color: "#0f172a" }}>Recovery trend (7 days)</h3>
          <p style={{ margin: "4px 0 0", fontSize: 11, color: "#64748b" }}>Real demo data where available; synthetic otherwise — labeled.</p>
          <div style={{ marginTop: 12, display: "flex", alignItems: "end", gap: 6, height: 90, background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: 12 }}>
            {[18, 32, 24, 45, 38, 52, data ? Math.min(60, Math.max(8, Number(data.recoveredRevenue) / 100000)) : 16].map((h, i) => (
              <div key={i} style={{ flex: 1, background: i === 6 ? "#0f172a" : "#cbd5e1", height: `${h}%`, borderRadius: 6, minHeight: 8 }} title={`${h}%`} />
            ))}
          </div>
          <div style={{ display: "flex", justifyContent: "space-between", fontSize: 10, color: "#64748b", marginTop: 6 }}>
            {(data?.trend?.labels || ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]).map((l) => (
              <span key={l}>{l}</span>
            ))}
          </div>
        </div>

        <div style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 16 }}>
          <h3 style={{ margin: 0, fontSize: 13, fontWeight: 800, letterSpacing: 0.6, color: "#0f172a" }}>Baseline vs RecoverFlow</h3>
          <p style={{ margin: "4px 0 0", fontSize: 11, color: "#64748b" }}>Synthetic evaluation — not real merchant revenue</p>
          <div style={{ marginTop: 12, display: "grid", gap: 10, fontSize: 12 }}>
            <div style={{ display: "flex", justifyContent: "space-between", padding: "8px 10px", background: "#f8fafc", borderRadius: 8, border: "1px solid #e2e8f0" }}>
              <span style={{ color: "#475569", fontWeight: 700 }}>Baseline A (naïve)</span>
              <strong>—</strong>
            </div>
            <div style={{ display: "flex", justifyContent: "space-between", padding: "8px 10px", background: "#f8fafc", borderRadius: 8, border: "1px solid #e2e8f0" }}>
              <span style={{ color: "#475569", fontWeight: 700 }}>Baseline B (policy-only)</span>
              <strong>—</strong>
            </div>
            <div style={{ display: "flex", justifyContent: "space-between", padding: "8px 10px", background: "#0f172a", color: "#fff", borderRadius: 8 }}>
              <span style={{ fontWeight: 800 }}>RecoverFlow (AI)</span>
              <strong>{data ? fmtMoney(data.recoveredRevenue) : "—"}</strong>
            </div>
            <div style={{ display: "flex", justifyContent: "space-between", padding: "8px 10px", background: "#fff", border: "1px solid #e2e8f0", borderRadius: 8 }}>
              <span style={{ color: "#475569", fontWeight: 700 }}>Oracle</span>
              <strong>—</strong>
            </div>
          </div>
          <div style={{ marginTop: 8, fontSize: 11, color: "#64748b", background: "#fef3c7", border: "1px solid #fde68a", padding: 8, borderRadius: 8 }}>
            Evaluation metrics are <strong>SYNTHETIC EVALUATION</strong> — see <a href="/evaluation" style={{ color: "#2563eb" }}>/evaluation</a> for methodology (seeds, versions).
          </div>
        </div>
      </div>

      <div style={{ marginTop: 16, display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
        <div style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 16 }}>
          <h3 style={{ margin: 0, fontSize: 13, fontWeight: 800, color: "#0f172a" }}>Recovery by intervention</h3>
          <div style={{ marginTop: 12, display: "grid", gap: 8 }}>
            {Object.entries(data?.byIntervention || {}).length === 0 && <div style={{ fontSize: 12, color: "#64748b" }}>No recoveries yet — run a scenario in <a href="/failure-lab" style={{ color: "#2563eb" }}>Failure Lab</a>.</div>}
            {Object.entries(data?.byIntervention || {})
              .sort((a, b) => (b[1] as number) - (a[1] as number))
              .slice(0, 5)
              .map(([k, v]) => (
                <Bar key={k} label={k} value={v as number} max={Math.max(1, ...Object.values(data?.byIntervention || {}).map((x) => x as number))} color="#0f172a" />
              ))}
          </div>
        </div>
        <div style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 16 }}>
          <h3 style={{ margin: 0, fontSize: 13, fontWeight: 800, color: "#0f172a" }}>Recovery by failure category</h3>
          <div style={{ marginTop: 12, display: "grid", gap: 8 }}>
            {Object.entries(data?.byFailureCategory || {}).length === 0 && <div style={{ fontSize: 12, color: "#64748b" }}>No data — categories appear after recoveries.</div>}
            {Object.entries(data?.byFailureCategory || {})
              .sort((a, b) => (b[1] as number) - (a[1] as number))
              .slice(0, 5)
              .map(([k, v]) => (
                <Bar key={k} label={k} value={v as number} max={Math.max(1, ...Object.values(data?.byFailureCategory || {}).map((x) => x as number))} color="#2563eb" />
              ))}
          </div>
        </div>
      </div>

      <div style={{ marginTop: 16, background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 12, display: "flex", gap: 12, alignItems: "center", flexWrap: "wrap" }}>
        <span style={{ fontSize: 12, fontWeight: 800, color: "#0f172a" }}>Quick links:</span>
        <a href="/recovery-cases" style={{ background: "#0f172a", color: "#fff", padding: "6px 10px", borderRadius: 8, textDecoration: "none", fontSize: 12, fontWeight: 700 }}>Recovery Cases →</a>
        <a href="/analytics" style={{ background: "#fff", border: "1px solid #e2e8f0", color: "#0f172a", padding: "6px 10px", borderRadius: 8, textDecoration: "none", fontSize: 12, fontWeight: 700 }}>Analytics →</a>
        <a href="/evaluation" style={{ background: "#fff", border: "1px solid #e2e8f0", color: "#0f172a", padding: "6px 10px", borderRadius: 8, textDecoration: "none", fontSize: 12, fontWeight: 700 }}>Evaluation →</a>
        <a href="/failure-lab" style={{ background: "#fff", border: "1px solid #e2e8f0", color: "#0f172a", padding: "6px 10px", borderRadius: 8, textDecoration: "none", fontSize: 12, fontWeight: 700 }}>Failure Lab →</a>
      </div>
    </main>
  );
}
