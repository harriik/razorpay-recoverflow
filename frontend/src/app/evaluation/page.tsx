"use client";

import { useEffect, useState } from "react";

type Partition = {
  label: string;
  seedCount: number;
  caseCount: number;
  recoveredRevenue: any;
  baselineARecovered: any;
  baselineBRecovered: any;
  absoluteRevenueDelta: any;
  relativeRevenueLift: any;
  meanTrueRegretPolicyOnly: any;
  meanTrueRegretRecoverFlow: any;
  medianTrueRegretPolicyOnly: any;
  medianTrueRegretRecoverFlow: any;
  regretReduction: any;
  relativeRegretReduction: any;
  actionChangeRate: any;
  helpRate: any;
  hurtRate: any;
  neutralRate: any;
  winCount: number;
  lossCount: number;
  tieCount: number;
  revenue: any;
  decisionQuality: any;
  aiBehavior: any;
};

type AiQualityRow = {
  quality: string;
  measuredAccuracy: number;
  recoveredRevenue: any;
  revenueDeltaVsPolicy: any;
  relativeRevenueLift: any;
  meanRegret: any;
  regretDelta: any;
  relativeRegretReduction: any;
  helpRate: any;
  hurtRate: any;
  neutralRate: any;
  actionChangeRate: any;
  totalCases: number;
  decisionQuality: any;
  aiBehavior: any;
};

type EvaluationResponse = {
  syntheticLabel: string;
  methodology: {
    evaluatorVersion: string;
    estimatorVersion: string;
    policyVersion: string;
    syntheticAiProxyVersion: string;
    trueValueVersion: string;
    syntheticRegistryVersion: string;
    evVersion: string;
    decisionVersion: string;
    datasetSizePerSeed: number;
    datasetSizeTotalDevelopment: number;
    datasetSizeTotalHeldOut: number;
    datasetSizeTotalValidation: number;
    seedInfo: string;
    developmentSeeds: string;
    validationSeeds: string;
    heldOutSeeds: string;
    versionSnapshot: string;
  };
  partitions: Record<string, Partition>;
  development: any;
  validation: any;
  heldOut: any;
  revenue: any;
  decisionQuality: any;
  aiBehavior: any;
  comparison: any;
  aiQuality: Record<string, any>;
};

function fmtMoney(v: any) {
  if (v == null) return "—";
  const n = typeof v === "string" ? parseFloat(v) : Number(v);
  if (isNaN(n)) return "—";
  return "₹" + n.toLocaleString("en-IN", { maximumFractionDigits: 0 });
}
function fmtMoneyPrecise(v: any) {
  if (v == null) return "—";
  const n = typeof v === "string" ? parseFloat(v) : Number(v);
  if (isNaN(n)) return "—";
  return "₹" + n.toLocaleString("en-IN", { maximumFractionDigits: 2, minimumFractionDigits: 2 });
}
function fmtPct(v: any) {
  if (v == null) return "N/A";
  const n = typeof v === "string" ? parseFloat(v) : Number(v);
  if (isNaN(n)) return "N/A";
  return (n * 100).toFixed(2) + "%";
}
function fmtPctSigned(v: any) {
  if (v == null) return "N/A";
  const n = typeof v === "string" ? parseFloat(v) : Number(v);
  if (isNaN(n)) return "N/A";
  const pct = n * 100;
  return (pct >= 0 ? "+" : "") + pct.toFixed(2) + "%";
}
function fmtRate(v: any) {
  if (v == null) return "N/A";
  const n = Number(v);
  if (isNaN(n)) return "N/A";
  return (n * 100).toFixed(1) + "%";
}

export default function EvaluationPage() {
  const [data, setData] = useState<EvaluationResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [active, setActive] = useState<"DEVELOPMENT" | "VALIDATION" | "HELD_OUT">("DEVELOPMENT");
  const [methodologyOpen, setMethodologyOpen] = useState(false);
  const api = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

  useEffect(() => {
    setLoading(true);
    fetch(`${api}/api/v1/analytics`)
      .then(async (r) => {
        if (!r.ok) throw new Error(`evaluation ${r.status}`);
        return r.json();
      })
      .then((j) => {
        setData(j);
        setError(null);
      })
      .catch((e) => setError(String(e)))
      .finally(() => setLoading(false));
  }, [api]);

  if (loading) {
    return (
      <main style={{ maxWidth: 1280, margin: "0 auto", padding: 24, background: "#f8fafc", minHeight: "100vh" }}>
        <h1 style={{ fontSize: 28, fontWeight: 900, color: "#0f172a", margin: 0 }}>Evaluation</h1>
        <div style={{ marginTop: 12, fontSize: 13, color: "#64748b" }}>Loading evaluation…</div>
      </main>
    );
  }
  if (error) {
    return (
      <main style={{ maxWidth: 1280, margin: "0 auto", padding: 24, background: "#f8fafc", minHeight: "100vh" }}>
        <h1 style={{ fontSize: 28, fontWeight: 900, color: "#0f172a", margin: 0 }}>Evaluation</h1>
        <div style={{ marginTop: 8, display: "inline-flex", background: "#fef3c7", border: "1px solid #fde68a", padding: "4px 10px", borderRadius: 8, fontSize: 11, fontWeight: 800, color: "#92400e" }}>SYNTHETIC EVALUATION</div>
        <div style={{ marginTop: 16, background: "#fef2f2", border: "1px solid #fecaca", color: "#991b1b", padding: 16, borderRadius: 12, fontSize: 13 }}>
          Backend unavailable: {error} — evaluation requires live backend at {api}. No synthetic values are fabricated locally.
        </div>
      </main>
    );
  }

  const partitions = data?.partitions;
  const dev = partitions?.DEVELOPMENT as Partition | undefined;
  const val = partitions?.VALIDATION as Partition | undefined;
  const held = partitions?.HELD_OUT as Partition | undefined;
  const current: Partition | undefined = active === "DEVELOPMENT" ? dev : active === "VALIDATION" ? val : held;

  const aiQuality = data?.aiQuality as any;
  const methodology = data?.methodology;

  // Three-way comparison uses development (or current?) — spec says compact comparison Baseline A/B/RecoverFlow/Oracle
  // Use development as primary for three-way
  const baselineA = dev?.baselineARecovered;
  const baselineB = dev?.baselineBRecovered;
  const recoverFlow = dev?.recoveredRevenue;
  const maxRev = Math.max(Number(baselineA ?? 0), Number(baselineB ?? 0), Number(recoverFlow ?? 0));

  return (
    <main style={{ maxWidth: 1280, margin: "0 auto", padding: 24, background: "#f8fafc", minHeight: "100vh" }}>
      {/* Header */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 16, flexWrap: "wrap" }}>
        <div>
          <div style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
            <h1 style={{ margin: 0, fontSize: 32, fontWeight: 900, letterSpacing: -0.8, color: "#0f172a" }}>Evaluation</h1>
            <span style={{ background: "#0f172a", color: "#fff", padding: "4px 10px", borderRadius: 999, fontSize: 11, fontWeight: 800, letterSpacing: 0.8 }}>SYNTHETIC EVALUATION</span>
            <span style={{ background: "#fff", border: "1px solid #e2e8f0", padding: "3px 8px", borderRadius: 6, fontSize: 10, fontWeight: 700, color: "#475569" }}>Experimental evidence</span>
          </div>
          <p style={{ margin: "8px 0 0", color: "#475569", fontSize: 13, maxWidth: 760, lineHeight: 1.5 }}>
            How do we know RecoverFlow is actually better? — Deterministic benchmark on synthetic worlds with <strong style={{ color: "#0f172a" }}>true expected value</strong> as ground truth. All data is <strong>SYNTHETIC EVALUATION</strong>, not production.
          </p>
        </div>
        <div style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: 10, padding: "8px 12px", fontSize: 11, color: "#475569", display: "flex", gap: 8, alignItems: "center" }}>
          <span style={{ fontWeight: 700 }}>Backend</span>
          <span style={{ background: "#dcfce7", color: "#166534", padding: "2px 8px", borderRadius: 999, fontWeight: 800 }}>UP</span>
          <span style={{ color: "#94a3b8" }}>{api}</span>
        </div>
      </div>

      {/* Partitions tabs */}
      <section style={{ marginTop: 24 }}>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap", borderBottom: "1px solid #e2e8f0", paddingBottom: 8 }}>
          {(["DEVELOPMENT", "VALIDATION", "HELD_OUT"] as const).map((p) => {
            const isActive = active === p;
            const label = p === "HELD_OUT" ? "HELD-OUT" : p;
            return (
              <button
                key={p}
                onClick={() => setActive(p)}
                data-testid={`tab-${p}`}
                style={{
                  padding: "8px 14px",
                  borderRadius: 8,
                  fontSize: 12,
                  fontWeight: 800,
                  letterSpacing: 0.6,
                  textTransform: "uppercase",
                  border: isActive ? "1px solid #0f172a" : "1px solid #e2e8f0",
                  background: isActive ? "#0f172a" : "#fff",
                  color: isActive ? "#fff" : "#475569",
                  cursor: "pointer",
                }}
              >
                {label} {p === "DEVELOPMENT" ? "• 30 seeds" : p === "VALIDATION" ? "• 10 seeds" : "• 10 seeds"}
              </button>
            );
          })}
          <span style={{ marginLeft: "auto", fontSize: 11, color: "#64748b", alignSelf: "center" }}>Deterministic • same configuration • no tuning from held-out</span>
        </div>

        {current && (
          <div style={{ marginTop: 12, background: active === "HELD_OUT" ? "#fff" : "#fff", border: active === "HELD_OUT" ? "2px solid #0f172a" : "1px solid #e2e8f0", borderRadius: 12, padding: 16 }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 8 }}>
              <h2 data-testid={`section-${active}`} style={{ margin: 0, fontSize: 13, fontWeight: 800, letterSpacing: 0.8, color: "#0f172a", textTransform: "uppercase" }}>
                {active === "HELD_OUT" ? "HELD-OUT SYNTHETIC EVALUATION" : active}
              </h2>
              <span style={{ fontSize: 11, background: active === "HELD_OUT" ? "#0f172a" : "#f1f5f9", color: active === "HELD_OUT" ? "#fff" : "#475569", padding: "4px 8px", borderRadius: 999, fontWeight: 700 }}>
                {current.seedCount} seeds • {current.caseCount} cases • {current.caseCount === 6000 ? "30×200" : "10×200"}
              </span>
            </div>

            {active === "HELD_OUT" && (
              <div style={{ marginTop: 8, background: "#fef3c7", border: "1px solid #fde68a", borderRadius: 8, padding: "8px 10px", fontSize: 11, color: "#92400e" }}>
                <strong>Seed-level partition</strong> • 10 held-out seeds (30000–30009) • same evaluator configuration • <strong>not used for tuning</strong> • not production performance • no statistical significance claimed
              </div>
            )}

            {/* Metrics grid */}
            <div style={{ marginTop: 12, display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 10 }}>
              <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 10, padding: 12 }}>
                <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: 0.6, color: "#64748b", textTransform: "uppercase" }}>Recovered revenue</div>
                <div data-testid="partition-recovered" style={{ fontSize: 16, fontWeight: 900, color: "#0f172a", marginTop: 4 }}>{fmtMoney(current.recoveredRevenue)}</div>
                <div style={{ fontSize: 10, color: "#64748b" }}>Policy {fmtMoney(current.baselineBRecovered)} • Naïve {fmtMoney(current.baselineARecovered)}</div>
              </div>
              <div style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: 10, padding: 12 }}>
                <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: 0.6, color: "#64748b", textTransform: "uppercase" }}>Absolute delta</div>
                <div data-testid="partition-delta" style={{ fontSize: 16, fontWeight: 900, color: Number(current.absoluteRevenueDelta) >= 0 ? "#16a34a" : "#dc2626", marginTop: 4 }}>{fmtMoney(current.absoluteRevenueDelta)}</div>
                <div style={{ fontSize: 10, color: "#64748b" }}>RecoverFlow − Policy-Only</div>
              </div>
              <div style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: 10, padding: 12 }}>
                <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: 0.6, color: "#64748b", textTransform: "uppercase" }}>Relative lift</div>
                <div data-testid="partition-lift" style={{ fontSize: 16, fontWeight: 900, color: Number(current.relativeRevenueLift) >= 0 ? "#16a34a" : "#dc2626", marginTop: 4 }}>{fmtPctSigned(current.relativeRevenueLift)}</div>
                <div style={{ fontSize: 10, color: "#64748b" }}>vs Policy-Only</div>
              </div>
              <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 10, padding: 12 }}>
                <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: 0.6, color: "#64748b", textTransform: "uppercase" }}>Mean true regret</div>
                <div data-testid="partition-mean-regret" style={{ fontSize: 12, fontWeight: 800, color: "#0f172a", marginTop: 4 }}>P {fmtMoneyPrecise(current.meanTrueRegretPolicyOnly)} → RF {fmtMoneyPrecise(current.meanTrueRegretRecoverFlow)}</div>
                <div style={{ fontSize: 10, color: "#64748b" }}>Median P {fmtMoneyPrecise(current.medianTrueRegretPolicyOnly)} → RF {fmtMoneyPrecise(current.medianTrueRegretRecoverFlow)}</div>
              </div>
              <div style={{ background: "#f0fdf4", border: "1px solid #bbf7d0", borderRadius: 10, padding: 12 }}>
                <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: 0.6, color: "#166534", textTransform: "uppercase" }}>Regret reduction</div>
                <div data-testid="partition-regret-reduction" style={{ fontSize: 16, fontWeight: 900, color: Number(current.regretReduction) >= 0 ? "#16a34a" : "#dc2626", marginTop: 4 }}>{fmtMoneyPrecise(current.regretReduction)}</div>
                <div style={{ fontSize: 10, color: "#15803d" }}>{fmtPct(current.relativeRegretReduction)} relative</div>
              </div>
              <div style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: 10, padding: 12 }}>
                <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: 0.6, color: "#64748b", textTransform: "uppercase" }}>Win / Loss / Tie</div>
                <div data-testid="partition-wlt" style={{ fontSize: 14, fontWeight: 800, color: "#0f172a", marginTop: 4 }}>{current.winCount} / {current.lossCount} / {current.tieCount}</div>
                <div style={{ fontSize: 10, color: "#64748b" }}>Per-seed RecoverFlow vs Policy</div>
              </div>
            </div>

            <div style={{ marginTop: 10, display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(140px, 1fr))", gap: 8 }}>
              <div data-testid="partition-change-rate" style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: 10, textAlign: "center" }}>
                <div style={{ fontSize: 10, fontWeight: 700, color: "#64748b", textTransform: "uppercase" }}>Action-change rate</div>
                <div style={{ fontSize: 14, fontWeight: 900, color: "#0f172a", marginTop: 2 }}>{fmtRate(current.actionChangeRate)}</div>
              </div>
              <div data-testid="partition-help-rate" style={{ background: "#f0fdf4", border: "1px solid #bbf7d0", borderRadius: 8, padding: 10, textAlign: "center" }}>
                <div style={{ fontSize: 10, fontWeight: 700, color: "#166534", textTransform: "uppercase" }}>Help rate</div>
                <div style={{ fontSize: 14, fontWeight: 900, color: "#16a34a", marginTop: 2 }}>{fmtRate(current.helpRate)}</div>
              </div>
              <div data-testid="partition-hurt-rate" style={{ background: "#fef2f2", border: "1px solid #fecaca", borderRadius: 8, padding: 10, textAlign: "center" }}>
                <div style={{ fontSize: 10, fontWeight: 700, color: "#991b1b", textTransform: "uppercase" }}>Hurt rate</div>
                <div style={{ fontSize: 14, fontWeight: 900, color: "#dc2626", marginTop: 2 }}>{fmtRate(current.hurtRate)}</div>
              </div>
              <div data-testid="partition-neutral-rate" style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: 10, textAlign: "center" }}>
                <div style={{ fontSize: 10, fontWeight: 700, color: "#475569", textTransform: "uppercase" }}>Neutral rate</div>
                <div style={{ fontSize: 14, fontWeight: 900, color: "#475569", marginTop: 2 }}>{fmtRate(current.neutralRate)}</div>
              </div>
            </div>

            <div style={{ marginTop: 8, fontSize: 10, color: "#94a3b8", textAlign: "center" }}>
              {current.caseCount} cases • seed {active === "DEVELOPMENT" ? "10000-10029" : active === "VALIDATION" ? "20000-20009" : "30000-30009"} • honest both revenue and regret shown
            </div>
          </div>
        )}
      </section>

      {/* HELD-OUT prominent (always visible even when not active tab, to satisfy spec) */}
      {active !== "HELD_OUT" && held && (
        <section data-testid="heldout-section" style={{ marginTop: 16, background: "#fff", border: "2px solid #0f172a", borderRadius: 12, padding: 16 }}>
          <h2 style={{ margin: 0, fontSize: 12, fontWeight: 900, letterSpacing: 0.8, color: "#0f172a", textTransform: "uppercase" }}>HELD-OUT SYNTHETIC EVALUATION</h2>
          <p style={{ margin: "6px 0 0", fontSize: 11, color: "#475569" }}>
            Seed-level partition • 10 held-out seeds (30000–30009) • same evaluator configuration • not used for tuning • not production
          </p>
          <div style={{ marginTop: 10, display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))", gap: 10 }}>
            <div><div style={{ fontSize: 10, color: "#64748b", fontWeight: 700 }}>Recovered</div><div style={{ fontWeight: 800 }}>{fmtMoney(held.recoveredRevenue)}</div></div>
            <div><div style={{ fontSize: 10, color: "#64748b", fontWeight: 700 }}>Lift</div><div style={{ fontWeight: 800, color: Number(held.relativeRevenueLift) >= 0 ? "#16a34a" : "#dc2626" }}>{fmtPctSigned(held.relativeRevenueLift)}</div></div>
            <div><div style={{ fontSize: 10, color: "#64748b", fontWeight: 700 }}>Regret Δ</div><div style={{ fontWeight: 800 }}>{fmtMoneyPrecise(held.regretReduction)}</div></div>
          </div>
        </section>
      )}

      {/* Three-way comparison */}
      <section style={{ marginTop: 16, background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 16 }}>
        <h2 style={{ margin: 0, fontSize: 12, fontWeight: 800, letterSpacing: 0.8, color: "#0f172a", textTransform: "uppercase" }}>Three-way comparison</h2>
        <p style={{ margin: "4px 0 0", fontSize: 11, color: "#64748b" }}>Baseline A (naïve) • Baseline B / Policy-Only • RecoverFlow • Oracle (evaluator-only reference — not executable)</p>
        <div style={{ marginTop: 12, overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
            <thead>
              <tr style={{ background: "#f8fafc", textAlign: "left", fontSize: 10, letterSpacing: 0.6, color: "#64748b", textTransform: "uppercase" }}>
                <th style={{ padding: "8px 10px", border: "1px solid #e2e8f0" }}>Strategy</th>
                <th style={{ padding: "8px 10px", border: "1px solid #e2e8f0" }}>Recovered Revenue</th>
                <th style={{ padding: "8px 10px", border: "1px solid #e2e8f0" }}>True Decision Regret (mean)</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td style={{ padding: "8px 10px", border: "1px solid #e2e8f0", fontWeight: 700, color: "#64748b" }}>Baseline A</td>
                <td data-testid="baseline-a" style={{ padding: "8px 10px", border: "1px solid #e2e8f0", fontWeight: 800 }}>{fmtMoney(baselineA)}</td>
                <td style={{ padding: "8px 10px", border: "1px solid #e2e8f0", color: "#94a3b8" }}>—</td>
              </tr>
              <tr>
                <td style={{ padding: "8px 10px", border: "1px solid #e2e8f0", fontWeight: 700 }}>Baseline B / Policy-Only</td>
                <td data-testid="baseline-b" style={{ padding: "8px 10px", border: "1px solid #e2e8f0", fontWeight: 800 }}>{fmtMoney(baselineB)}</td>
                <td data-testid="regret-policy" style={{ padding: "8px 10px", border: "1px solid #e2e8f0", fontWeight: 700 }}>{fmtMoneyPrecise(dev?.meanTrueRegretPolicyOnly)}</td>
              </tr>
              <tr style={{ background: "#0f172a", color: "#fff" }}>
                <td style={{ padding: "8px 10px", border: "1px solid #0f172a", fontWeight: 800 }}>RecoverFlow</td>
                <td data-testid="recoverflow-rev" style={{ padding: "8px 10px", border: "1px solid #0f172a", fontWeight: 800 }}>{fmtMoney(recoverFlow)}</td>
                <td data-testid="regret-recoverflow" style={{ padding: "8px 10px", border: "1px solid #0f172a", fontWeight: 700 }}>{fmtMoneyPrecise(dev?.meanTrueRegretRecoverFlow)}</td>
              </tr>
              <tr style={{ background: "#fef3c7" }}>
                <td style={{ padding: "8px 10px", border: "1px solid #fde68a", fontWeight: 700 }}>Oracle <span style={{ fontWeight: 400, fontSize: 10 }}>(evaluator-only reference)</span></td>
                <td style={{ padding: "8px 10px", border: "1px solid #fde68a", fontSize: 10, color: "#92400e" }}>Not executable</td>
                <td data-testid="oracle-regret" style={{ padding: "8px 10px", border: "1px solid #fde68a", fontWeight: 700 }}>0.00 (best permissible)</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div style={{ marginTop: 10, display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
          <div style={{ flex: 1, height: 8, background: "#f1f5f9", borderRadius: 999, overflow: "hidden", display: "flex" }}>
            {[baselineA, baselineB, recoverFlow].map((v, i) => {
              const colors = ["#94a3b8", "#64748b", "#0f172a"];
              const pct = maxRev === 0 ? 0 : (Number(v ?? 0) / maxRev) * 100;
              return <div key={i} style={{ width: `${pct / 3}%`, flex: `0 0 ${pct / 3}%`, background: colors[i] }} />;
            })}
          </div>
          <span style={{ fontSize: 10, color: "#94a3b8" }}>Visual scale: Baseline A • Baseline B • RecoverFlow (actual recovered revenue)</span>
        </div>
      </section>

      {/* Regret explanation */}
      <section style={{ marginTop: 16, background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 16 }}>
        <h3 style={{ margin: 0, fontSize: 12, fontWeight: 800, letterSpacing: 0.8, color: "#0f172a", textTransform: "uppercase" }}>True decision regret</h3>
        <p style={{ margin: "6px 0 0", fontSize: 11, color: "#475569", background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: "8px 10px" }}>
          True decision regret is the gap between the selected action&apos;s true expected value and the best permissible action.
        </p>
        <div data-testid="regret-cascade" style={{ marginTop: 12, display: "grid", gridTemplateColumns: "1fr 40px 1fr 40px 1fr", gap: 8, alignItems: "center", textAlign: "center" }}>
          <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 10, padding: 12 }}>
            <div style={{ fontSize: 10, fontWeight: 700, color: "#64748b", textTransform: "uppercase" }}>Policy-only regret</div>
            <div style={{ fontSize: 16, fontWeight: 900, marginTop: 4 }}>{fmtMoneyPrecise(current?.meanTrueRegretPolicyOnly)}</div>
            <div style={{ fontSize: 10, color: "#64748b" }}>mean per case</div>
          </div>
          <div style={{ fontSize: 20, fontWeight: 900, color: "#0f172a" }}>↓</div>
          <div style={{ background: "#0f172a", color: "#fff", borderRadius: 10, padding: 12 }}>
            <div style={{ fontSize: 10, fontWeight: 700, color: "#94a3b8", textTransform: "uppercase" }}>RecoverFlow regret</div>
            <div style={{ fontSize: 16, fontWeight: 900, marginTop: 4 }}>{fmtMoneyPrecise(current?.meanTrueRegretRecoverFlow)}</div>
            <div style={{ fontSize: 10, color: "#cbd5e1" }}>mean per case</div>
          </div>
          <div style={{ fontSize: 20, fontWeight: 900, color: "#16a34a" }}>↓</div>
          <div style={{ background: "#f0fdf4", border: "1px solid #bbf7d0", borderRadius: 10, padding: 12 }}>
            <div style={{ fontSize: 10, fontWeight: 700, color: "#166534", textTransform: "uppercase" }}>Regret reduction</div>
            <div style={{ fontSize: 16, fontWeight: 900, color: Number(current?.regretReduction) >= 0 ? "#16a34a" : "#dc2626", marginTop: 4 }}>{fmtMoneyPrecise(current?.regretReduction)}</div>
            <div style={{ fontSize: 10, color: "#64748b" }}>{fmtPct(current?.relativeRegretReduction)} relative</div>
          </div>
        </div>
        <div style={{ marginTop: 8, fontSize: 10, color: "#94a3b8", textAlign: "center" }}>Not realized payment failure — evaluator-only true expected value</div>
      </section>

      {/* AI Quality Experiment */}
      <section data-testid="ai-quality-section" style={{ marginTop: 16, background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 16 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 8 }}>
          <h2 style={{ margin: 0, fontSize: 12, fontWeight: 800, letterSpacing: 0.8, color: "#0f172a", textTransform: "uppercase" }}>AI quality experiment</h2>
          <span style={{ fontSize: 10, background: "#f1f5f9", border: "1px solid #e2e8f0", padding: "4px 8px", borderRadius: 999, fontWeight: 700, color: "#475569" }}>Observable-only proxy • same worlds</span>
        </div>
        <p style={{ margin: "6px 0 0", fontSize: 11, color: "#64748b" }}>
          Accuracy shown is measured true-category accuracy of the synthetic observable-only AI proxy under this evaluation configuration.
        </p>
        {aiQuality && !aiQuality.error ? (
          <div style={{ marginTop: 12, overflowX: "auto" }}>
            <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
              <thead>
                <tr style={{ background: "#f8fafc", fontSize: 10, letterSpacing: 0.6, color: "#64748b", textTransform: "uppercase", textAlign: "left" }}>
                  <th style={{ padding: "8px 10px", border: "1px solid #e2e8f0" }}>Quality</th>
                  <th style={{ padding: "8px 10px", border: "1px solid #e2e8f0" }}>Measured accuracy</th>
                  <th style={{ padding: "8px 10px", border: "1px solid #e2e8f0" }}>Recovered revenue</th>
                  <th style={{ padding: "8px 10px", border: "1px solid #e2e8f0" }}>Mean regret</th>
                  <th style={{ padding: "8px 10px", border: "1px solid #e2e8f0" }}>Regret reduction</th>
                  <th style={{ padding: "8px 10px", border: "1px solid #e2e8f0" }}>Help rate</th>
                  <th style={{ padding: "8px 10px", border: "1px solid #e2e8f0" }}>Hurt rate</th>
                </tr>
              </thead>
              <tbody>
                {(["LOW", "MEDIUM", "HIGH"] as const).map((q) => {
                  const row: AiQualityRow | undefined = aiQuality[q];
                  if (!row) return null;
                  return (
                    <tr key={q}>
                      <td style={{ padding: "8px 10px", border: "1px solid #e2e8f0", fontWeight: 800, color: q === "HIGH" ? "#0f172a" : "#475569" }}>{q}</td>
                      <td data-testid={`ai-quality-${q.toLowerCase()}-accuracy`} style={{ padding: "8px 10px", border: "1px solid #e2e8f0", fontWeight: 700 }}>{row.measuredAccuracy != null ? (row.measuredAccuracy * 100).toFixed(1) + "%" : "—"}</td>
                      <td data-testid={`ai-quality-${q.toLowerCase()}-recovered`} style={{ padding: "8px 10px", border: "1px solid #e2e8f0" }}>{fmtMoney(row.recoveredRevenue)}</td>
                      <td style={{ padding: "8px 10px", border: "1px solid #e2e8f0" }}>{fmtMoneyPrecise(row.meanRegret)}</td>
                      <td style={{ padding: "8px 10px", border: "1px solid #e2e8f0", color: Number(row.regretDelta) >= 0 ? "#16a34a" : "#dc2626", fontWeight: 700 }}>{fmtMoneyPrecise(row.regretDelta)}</td>
                      <td style={{ padding: "8px 10px", border: "1px solid #e2e8f0", color: "#16a34a" }}>{fmtRate(row.helpRate)}</td>
                      <td style={{ padding: "8px 10px", border: "1px solid #e2e8f0", color: "#dc2626" }}>{fmtRate(row.hurtRate)}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
            <div style={{ marginTop: 8, fontSize: 11, color: "#64748b", display: "flex", gap: 12, flexWrap: "wrap" }}>
              <span>Policy-only recovered: {fmtMoney(aiQuality.policyOnlyRecovered)}</span>
              <span>•</span>
              <span>{aiQuality.experimentSeeds}</span>
            </div>
          </div>
        ) : (
          <div style={{ marginTop: 12, background: "#fef2f2", border: "1px solid #fecaca", padding: 10, borderRadius: 8, fontSize: 11, color: "#991b1b" }}>
            AI quality experiment unavailable — {aiQuality?.error ?? "no data"} — not fabricating values.
          </div>
        )}
        <div style={{ marginTop: 8, fontSize: 10, color: "#94a3b8" }}>Do not label as 50/75/90% — measured ~44% / ~65% / ~77% reflect noisy observable gateway (85% correlated with hidden truth).</div>
      </section>

      {/* Methodology */}
      <section style={{ marginTop: 16, background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12 }}>
        <button
          onClick={() => setMethodologyOpen(!methodologyOpen)}
          data-testid="methodology-toggle"
          style={{ width: "100%", display: "flex", justifyContent: "space-between", alignItems: "center", padding: 16, background: "transparent", border: "none", cursor: "pointer", textAlign: "left" }}
        >
          <div>
            <h3 style={{ margin: 0, fontSize: 12, fontWeight: 800, letterSpacing: 0.8, color: "#0f172a", textTransform: "uppercase" }}>Methodology</h3>
            <p style={{ margin: "4px 0 0", fontSize: 11, color: "#64748b" }}>Versions • configuration • reproducibility</p>
          </div>
          <span style={{ background: "#f1f5f9", border: "1px solid #e2e8f0", padding: "6px 10px", borderRadius: 8, fontSize: 12, fontWeight: 800, color: "#0f172a" }}>{methodologyOpen ? "▾ Hide" : "▸ Show"}</span>
        </button>
        {methodologyOpen && (
          <div data-testid="methodology-content" style={{ padding: "0 16px 16px", borderTop: "1px solid #f1f5f9", display: "grid", gap: 12 }}>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 10, fontSize: 11, marginTop: 12 }}>
              <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: 10 }}><div style={{ fontWeight: 700, color: "#64748b", fontSize: 10, textTransform: "uppercase" }}>Hidden world</div><div style={{ fontWeight: 800, marginTop: 4 }}>{methodology?.syntheticRegistryVersion}</div></div>
              <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: 10 }}><div style={{ fontWeight: 700, color: "#64748b", fontSize: 10, textTransform: "uppercase" }}>Evaluator</div><div style={{ fontWeight: 800, marginTop: 4 }}>{methodology?.evaluatorVersion}</div></div>
              <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: 10 }}><div style={{ fontWeight: 700, color: "#64748b", fontSize: 10, textTransform: "uppercase" }}>Estimator</div><div style={{ fontWeight: 800, marginTop: 4 }}>{methodology?.estimatorVersion}</div></div>
              <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: 10 }}><div style={{ fontWeight: 700, color: "#64748b", fontSize: 10, textTransform: "uppercase" }}>Policy</div><div style={{ fontWeight: 800, marginTop: 4 }}>{methodology?.policyVersion}</div></div>
              <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: 10 }}><div style={{ fontWeight: 700, color: "#64748b", fontSize: 10, textTransform: "uppercase" }}>EV</div><div style={{ fontWeight: 800, marginTop: 4 }}>{methodology?.evVersion}</div></div>
              <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: 10 }}><div style={{ fontWeight: 700, color: "#64748b", fontSize: 10, textTransform: "uppercase" }}>True value</div><div style={{ fontWeight: 800, marginTop: 4 }}>{methodology?.trueValueVersion}</div></div>
              <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: 10 }}><div style={{ fontWeight: 700, color: "#64748b", fontSize: 10, textTransform: "uppercase" }}>AI proxy</div><div style={{ fontWeight: 800, marginTop: 4 }}>{methodology?.syntheticAiProxyVersion}</div></div>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: 10, fontSize: 11 }}>
              <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: 10 }}><div style={{ fontWeight: 700, color: "#64748b", fontSize: 10, textTransform: "uppercase" }}>Dataset size per seed</div><div style={{ fontWeight: 800, marginTop: 4 }}>{methodology?.datasetSizePerSeed}</div></div>
              <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: 10 }}><div style={{ fontWeight: 700, color: "#64748b", fontSize: 10, textTransform: "uppercase" }}>Development seeds</div><div style={{ fontWeight: 800, marginTop: 4 }}>{methodology?.developmentSeeds}</div></div>
              <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: 10 }}><div style={{ fontWeight: 700, color: "#64748b", fontSize: 10, textTransform: "uppercase" }}>Validation seeds</div><div style={{ fontWeight: 800, marginTop: 4 }}>{methodology?.validationSeeds}</div></div>
              <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: 10 }}><div style={{ fontWeight: 700, color: "#64748b", fontSize: 10, textTransform: "uppercase" }}>Held-out seeds</div><div style={{ fontWeight: 800, marginTop: 4 }}>{methodology?.heldOutSeeds}</div></div>
            </div>
            <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: 10, fontSize: 11, color: "#475569" }}>
              <strong>Reproducibility:</strong> Deterministic evaluation: same seed + same configuration produces the same result.
            </div>
            <div style={{ fontSize: 10, color: "#94a3b8" }}>{methodology?.versionSnapshot}</div>
          </div>
        )}
      </section>

      <div style={{ marginTop: 12, background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: 10, fontSize: 11, color: "#475569", textAlign: "center" }}>
        Deterministic evaluation: same seed + same configuration produces the same result.
      </div>

      <div style={{ marginTop: 16, textAlign: "center", color: "#94a3b8", fontSize: 11 }}>
        Evaluation • Benchmark — SYNTHETIC EVALUATION • <a href="/analytics" style={{ color: "#2563eb" }}>Analytics</a> • <a href="/overview" style={{ color: "#2563eb" }}>Overview</a>
      </div>
    </main>
  );
}
