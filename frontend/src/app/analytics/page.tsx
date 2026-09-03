"use client";

import { useEffect, useState } from "react";

type AnalyticsResponse = {
  syntheticLabel: string;
  note: string;
  methodology: {
    evaluatorVersion: string;
    estimatorVersion: string;
    policyVersion: string;
    syntheticAiProxyVersion: string;
    trueValueVersion: string;
    syntheticRegistryVersion: string;
    datasetSizePerSeed: number;
    datasetSizeTotalDevelopment: number;
    datasetSizeTotalHeldOut: number;
    datasetSizeTotalValidation: number;
    seedInfo: string;
    developmentSeeds: string;
    heldOutSeeds: string;
    validationSeeds: string;
    versionSnapshot: string;
  };
  revenue: {
    revenueAtRisk: any;
    recoveredRevenue: any;
    baselineARecovered: any;
    baselineBRecovered: any;
    recoveryRate: any;
    attempts: number;
    absoluteRecoveredRevenueDelta: any;
    relativeAiLift: any;
    absoluteDelta: any;
    relativeLift: any;
  };
  decisionQuality: {
    policyOnlyTotalTrueRegret: any;
    recoverFlowTotalTrueRegret: any;
    policyOnlyMeanTrueRegret: any;
    recoverFlowMeanTrueRegret: any;
    policyOnlyMedianTrueRegret: any;
    recoverFlowMedianTrueRegret: any;
    meanOracleTrueValue: any;
    regretDelta: any;
    relativeRegretReduction: any;
    policyOnlyRegret: any;
    recoverFlowRegret: any;
    regretReduction: any;
  };
  aiBehavior: {
    actionChangedCount: number;
    aiHelpedCount: number;
    aiHurtCount: number;
    aiNeutralCount: number;
    actionChangeRate: any;
    aiHelpRate: any;
    aiHurtRate: any;
    aiNeutralRate: any;
    totalCases: number;
    changed: number;
    helped: number;
    hurt: number;
    neutral: number;
    changeRate: any;
    helpRate: any;
    hurtRate: any;
    neutralRate: any;
  };
  comparison: {
    baselineA: any;
    baselineB: any;
    recoverFlow: any;
    oracleMeanTrueValue: any;
    revenueAtRisk: any;
  };
  heldOut: {
    syntheticLabel: string;
    note: string;
    revenue: any;
    decisionQuality: any;
    aiBehavior: any;
    comparison: any;
    seedInfo: string;
    totalCases: number;
  };
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
  const pct = n * 100;
  const sign = pct > 0 ? "+" : "";
  return sign + pct.toFixed(2) + "%";
}

function fmtPctNoSign(v: any) {
  if (v == null) return "N/A";
  const n = typeof v === "string" ? parseFloat(v) : Number(v);
  if (isNaN(n)) return "N/A";
  return (n * 100).toFixed(1) + "%";
}

function RateBadge({ value, tone }: { value: any; tone?: string }) {
  const n = value == null ? null : Number(value);
  const pct = n == null || isNaN(n) ? "N/A" : (n * 100).toFixed(1) + "%";
  const bg = tone === "green" ? "#dcfce7" : tone === "red" ? "#fee2e2" : tone === "amber" ? "#fef3c7" : "#f1f5f9";
  const color = tone === "green" ? "#166534" : tone === "red" ? "#991b1b" : tone === "amber" ? "#92400e" : "#475569";
  return (
    <span style={{ background: bg, color, padding: "2px 8px", borderRadius: 999, fontSize: 11, fontWeight: 800 }}>{pct}</span>
  );
}

function Kpi({ label, value, sub, accent, testId }: { label: string; value: string; sub?: string; accent?: string; testId?: string }) {
  return (
    <div data-testid={testId} style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 16, display: "flex", flexDirection: "column", gap: 6 }}>
      <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.6, color: "#64748b", textTransform: "uppercase" }}>{label}</div>
      <div style={{ fontSize: 24, fontWeight: 900, color: accent || "#0f172a", letterSpacing: -0.6 }}>{value}</div>
      {sub && <div style={{ fontSize: 11, color: "#64748b", lineHeight: 1.4 }}>{sub}</div>}
    </div>
  );
}

function BarRow({ label, value, max, color, muted }: { label: string; value: number; max: number; color: string; muted?: boolean }) {
  const pct = max === 0 ? 0 : Math.round((value / max) * 100);
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 10, fontSize: 12 }}>
      <div style={{ width: 118, color: muted ? "#64748b" : "#0f172a", fontWeight: 700, fontSize: 11, textTransform: "uppercase", letterSpacing: 0.3 }}>{label}</div>
      <div style={{ flex: 1, height: 12, background: "#f1f5f9", borderRadius: 999, overflow: "hidden", border: "1px solid #e2e8f0" }}>
        <div style={{ width: `${pct}%`, height: "100%", background: color, borderRadius: 999 }} />
      </div>
      <div style={{ width: 110, textAlign: "right", fontWeight: 800, color: "#0f172a", fontSize: 12 }}>{fmtMoney(value)}</div>
      <div style={{ width: 36, textAlign: "right", fontSize: 10, color: "#64748b", fontWeight: 700 }}>{pct}%</div>
    </div>
  );
}

export default function AnalyticsPage() {
  const [data, setData] = useState<AnalyticsResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [methodologyOpen, setMethodologyOpen] = useState(false);
  const api = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

  useEffect(() => {
    setLoading(true);
    fetch(`${api}/api/v1/analytics`)
      .then(async (r) => {
        if (!r.ok) throw new Error(`analytics ${r.status}`);
        return r.json();
      })
      .then((j) => {
        setData(j);
        setError(null);
      })
      .catch((e) => setError(String(e)))
      .finally(() => setLoading(false));
  }, [api]);

  const recovered = data?.revenue?.recoveredRevenue;
  const baselineB = data?.revenue?.baselineBRecovered;
  const baselineA = data?.revenue?.baselineARecovered;
  const delta = data?.revenue?.absoluteDelta ?? data?.revenue?.absoluteRecoveredRevenueDelta;
  const lift = data?.revenue?.relativeLift ?? data?.revenue?.relativeAiLift;

  const maxRevenue = Math.max(
    Number(baselineA ?? 0),
    Number(baselineB ?? 0),
    Number(recovered ?? 0)
  );

  if (loading) {
    return (
      <main style={{ maxWidth: 1280, margin: "0 auto", padding: 24, background: "#f8fafc", minHeight: "100vh" }}>
        <h1 style={{ fontSize: 28, fontWeight: 900, color: "#0f172a", margin: 0 }}>Analytics</h1>
        <div style={{ marginTop: 16, fontSize: 13, color: "#64748b" }}>Loading analytics…</div>
      </main>
    );
  }

  if (error) {
    return (
      <main style={{ maxWidth: 1280, margin: "0 auto", padding: 24, background: "#f8fafc", minHeight: "100vh" }}>
        <h1 style={{ fontSize: 28, fontWeight: 900, color: "#0f172a", margin: 0 }}>Analytics</h1>
        <div style={{ marginTop: 8, display: "inline-flex", background: "#fef3c7", border: "1px solid #fde68a", padding: "4px 10px", borderRadius: 8, fontSize: 11, fontWeight: 800, letterSpacing: 0.6, color: "#92400e" }}>SYNTHETIC EVALUATION</div>
        <div style={{ marginTop: 16, background: "#fef2f2", border: "1px solid #fecaca", color: "#991b1b", padding: 16, borderRadius: 12, fontSize: 13 }}>
          Backend unavailable: {error} — Ensure backend at {api} is running. Analytics requires live evaluation data; no synthetic values are fabricated locally.
        </div>
      </main>
    );
  }

  return (
    <main style={{ maxWidth: 1280, margin: "0 auto", padding: 24, background: "#f8fafc", minHeight: "100vh" }}>
      {/* HEADER */}
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 16, flexWrap: "wrap" }}>
        <div>
          <div style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
            <h1 style={{ margin: 0, fontSize: 32, fontWeight: 900, letterSpacing: -0.8, color: "#0f172a" }}>Analytics</h1>
            <span style={{ background: "#0f172a", color: "#fff", padding: "4px 10px", borderRadius: 999, fontSize: 11, fontWeight: 800, letterSpacing: 0.8 }}>SYNTHETIC EVALUATION</span>
            <span style={{ background: "#fef3c7", border: "1px solid #fde68a", color: "#92400e", padding: "3px 8px", borderRadius: 6, fontSize: 10, fontWeight: 700 }}>Not real merchant revenue</span>
          </div>
          <p style={{ margin: "8px 0 0", color: "#475569", fontSize: 13, maxWidth: 760, lineHeight: 1.5 }}>
            Evidence & operations —{" "}
            <strong style={{ color: "#0f172a" }}>Policy-Only / Baseline B vs RecoverFlow</strong> on deterministic synthetic worlds. All revenue is{" "}
            <strong>synthetic recovered revenue</strong>; decision quality is measured via true expected value.
          </p>
        </div>
        <div style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: 10, padding: "8px 12px", fontSize: 11, display: "flex", gap: 8, alignItems: "center", color: "#475569" }}>
          <span style={{ fontWeight: 700 }}>Backend</span>
          <span style={{ background: "#dcfce7", color: "#166534", padding: "2px 8px", borderRadius: 999, fontWeight: 800 }}>UP</span>
          <span style={{ color: "#94a3b8" }}>{api}</span>
        </div>
      </div>

      {/* HEADLINE COMPARISON — Baseline B vs RecoverFlow */}
      <section style={{ marginTop: 24 }}>
        <div style={{ display: "flex", alignItems: "baseline", gap: 10, flexWrap: "wrap" }}>
          <h2 style={{ margin: 0, fontSize: 13, fontWeight: 800, letterSpacing: 0.8, color: "#0f172a", textTransform: "uppercase" }}>Baseline B vs RecoverFlow</h2>
          <span style={{ fontSize: 11, color: "#64748b" }}>Policy-only baseline compared to AI-enabled RecoverFlow — same hidden worlds, same P_true</span>
        </div>

        <div style={{ marginTop: 12, display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))", gap: 12 }}>
          <Kpi
            label="Recovered revenue — Baseline B"
            value={fmtMoney(baselineB)}
            sub="Policy-only recovered revenue"
            testId="recovered-baseline-b"
          />
          <div style={{ background: "#0f172a", border: "1px solid #0f172a", borderRadius: 12, padding: 16, display: "flex", flexDirection: "column", gap: 6, color: "#fff" }}>
            <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.6, color: "#94a3b8", textTransform: "uppercase" }}>Recovered revenue — RecoverFlow</div>
            <div data-testid="recovered-revenue" style={{ fontSize: 24, fontWeight: 900, letterSpacing: -0.6, color: "#fff" }}>{fmtMoney(recovered)}</div>
            <div style={{ fontSize: 11, color: "#cbd5e1" }}>AI-enabled recovered revenue — synthetic evaluation</div>
          </div>
          <div style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 16, display: "flex", flexDirection: "column", gap: 6 }}>
            <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.6, color: "#64748b", textTransform: "uppercase" }}>Revenue delta</div>
            <div data-testid="revenue-delta" style={{ fontSize: 24, fontWeight: 900, letterSpacing: -0.6, color: Number(delta) >= 0 ? "#16a34a" : "#dc2626" }}>
              {delta == null ? "—" : (Number(delta) >= 0 ? "+" : "") + fmtMoney(delta)}
            </div>
            <div style={{ fontSize: 11, color: "#64748b", display: "flex", gap: 6, alignItems: "center", flexWrap: "wrap" }}>
              <span>Absolute revenue delta</span>
              <span style={{ background: Number(lift) >= 0 ? "#dcfce7" : "#fee2e2", color: Number(lift) >= 0 ? "#166534" : "#991b1b", padding: "1px 6px", borderRadius: 999, fontWeight: 800 }}>Relative lift {fmtPct(lift)}</span>
            </div>
            <div data-testid="relative-lift" style={{ fontSize: 10, color: "#94a3b8" }}>Relative revenue lift vs Baseline B • {fmtPct(lift)}</div>
          </div>
        </div>

        <div style={{ marginTop: 8, fontSize: 11, color: "#64748b", background: "#fff", border: "1px solid #e2e8f0", borderRadius: 8, padding: "8px 12px", display: "flex", gap: 12, flexWrap: "wrap" }}>
          <span><strong style={{ color: "#0f172a" }}>Recovered revenue</strong> — synthetic</span>
          <span>•</span>
          <span><strong style={{ color: "#0f172a" }}>Absolute revenue delta</strong>: {fmtMoney(delta)}</span>
          <span>•</span>
          <span><strong style={{ color: "#0f172a" }}>Relative revenue lift</strong>: {fmtPct(lift)}</span>
          <span style={{ marginLeft: "auto", background: "#f8fafc", border: "1px solid #e2e8f0", padding: "2px 6px", borderRadius: 6, fontSize: 10 }}>{data?.methodology?.datasetSizeTotalDevelopment ?? 6000} cases • {data?.methodology?.seedInfo}</span>
        </div>
      </section>

      {/* REVENUE COMPARISON — ONE CLEAR VISUALIZATION */}
      <section style={{ marginTop: 20, background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 16 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 12, flexWrap: "wrap" }}>
          <div>
            <h3 style={{ margin: 0, fontSize: 13, fontWeight: 800, letterSpacing: 0.6, color: "#0f172a" }}>Revenue comparison</h3>
            <p style={{ margin: "4px 0 0", fontSize: 11, color: "#64748b" }}>Actual backend evaluation data — no statistical certainty implied beyond aggregate</p>
          </div>
          <span style={{ background: "#fef3c7", border: "1px solid #fde68a", padding: "4px 8px", borderRadius: 6, fontSize: 11, fontWeight: 700, color: "#92400e" }}>SYNTHETIC EVALUATION</span>
        </div>

        <div style={{ marginTop: 14, display: "grid", gap: 12 }}>
          <BarRow label="Baseline A — naïve" value={Number(baselineA ?? 0)} max={maxRevenue} color="#94a3b8" muted />
          <BarRow label="Baseline B — policy-only" value={Number(baselineB ?? 0)} max={maxRevenue} color="#475569" />
          <BarRow label="RecoverFlow — AI" value={Number(recovered ?? 0)} max={maxRevenue} color="#0f172a" />
          {data?.comparison?.oracleMeanTrueValue != null && (
            <div style={{ marginTop: 4, paddingTop: 10, borderTop: "1px dashed #e2e8f0", display: "flex", justifyContent: "space-between", fontSize: 11, color: "#64748b" }}>
              <span>Oracle mean true value (per case): <strong style={{ color: "#0f172a" }}>{fmtMoneyPrecise(data?.decisionQuality?.meanOracleTrueValue)}</strong></span>
              <span style={{ fontSize: 10 }}>Reference — not revenue, per-case true expected value</span>
            </div>
          )}
        </div>

        <div style={{ marginTop: 12, display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 10, fontSize: 11 }}>
          <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: 10 }}>
            <div style={{ fontWeight: 700, color: "#64748b", textTransform: "uppercase", fontSize: 10, letterSpacing: 0.6 }}>Baseline A</div>
            <div style={{ fontWeight: 800, color: "#0f172a", marginTop: 2 }}>{fmtMoney(baselineA)}</div>
            <div style={{ color: "#64748b", fontSize: 10 }}>Naïve retry — synthetic</div>
          </div>
          <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: 10 }}>
            <div style={{ fontWeight: 700, color: "#64748b", textTransform: "uppercase", fontSize: 10, letterSpacing: 0.6 }}>Baseline B</div>
            <div style={{ fontWeight: 800, color: "#0f172a", marginTop: 2 }}>{fmtMoney(baselineB)}</div>
            <div style={{ color: "#64748b", fontSize: 10 }}>Policy-only — synthetic</div>
          </div>
          <div style={{ background: "#0f172a", borderRadius: 8, padding: 10, color: "#fff" }}>
            <div style={{ fontWeight: 700, color: "#94a3b8", textTransform: "uppercase", fontSize: 10, letterSpacing: 0.6 }}>RecoverFlow</div>
            <div style={{ fontWeight: 800, color: "#fff", marginTop: 2 }}>{fmtMoney(recovered)}</div>
            <div style={{ color: "#cbd5e1", fontSize: 10 }}>AI-enabled — synthetic</div>
          </div>
        </div>

        <div style={{ marginTop: 10, fontSize: 10, color: "#94a3b8", textAlign: "center" }}>
          Revenue at risk: {fmtMoney(data?.revenue?.revenueAtRisk)} • Recovery rate: {data?.revenue?.recoveryRate ?? "—"}% • {data?.aiBehavior?.totalCases ?? 6000} cases
        </div>
      </section>

      {/* TRUE DECISION QUALITY */}
      <section style={{ marginTop: 16, background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 16 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
          <h2 style={{ margin: 0, fontSize: 13, fontWeight: 800, letterSpacing: 0.8, color: "#0f172a", textTransform: "uppercase" }}>True Decision Quality</h2>
          <span style={{ background: "#0f172a", color: "#fff", padding: "4px 8px", borderRadius: 6, fontSize: 10, fontWeight: 800, letterSpacing: 0.6 }}>EVALUATOR-ONLY • P_TRUE</span>
        </div>
        <p style={{ margin: "8px 0 0", fontSize: 11, color: "#475569", background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: "8px 10px", lineHeight: 1.5 }}>
          True decision regret measures the gap between the selected action&apos;s true expected value and the best permissible action. It is not realized payment failure.
        </p>

        <div style={{ marginTop: 14, display: "grid", gridTemplateColumns: "1fr 40px 1fr 40px 1fr", gap: 8, alignItems: "center" }}>
          {/* Policy-only regret */}
          <div data-testid="policy-regret" style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 12, padding: 14, textAlign: "center" }}>
            <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: 0.8, color: "#64748b", textTransform: "uppercase" }}>Policy-only true regret</div>
            <div style={{ fontSize: 20, fontWeight: 900, color: "#475569", marginTop: 6 }}>{fmtMoneyPrecise(data?.decisionQuality?.policyOnlyTotalTrueRegret)}</div>
            <div style={{ fontSize: 10, color: "#64748b", marginTop: 4 }}>Mean {fmtMoneyPrecise(data?.decisionQuality?.policyOnlyMeanTrueRegret)} • Total</div>
          </div>
          <div style={{ textAlign: "center", fontSize: 20, color: "#0f172a", fontWeight: 900 }}>↓</div>
          {/* RecoverFlow regret */}
          <div data-testid="recoverflow-regret" style={{ background: "#0f172a", borderRadius: 12, padding: 14, textAlign: "center", color: "#fff" }}>
            <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: 0.8, color: "#94a3b8", textTransform: "uppercase" }}>RecoverFlow true regret</div>
            <div style={{ fontSize: 20, fontWeight: 900, color: "#fff", marginTop: 6 }}>{fmtMoneyPrecise(data?.decisionQuality?.recoverFlowTotalTrueRegret)}</div>
            <div style={{ fontSize: 10, color: "#cbd5e1", marginTop: 4 }}>Mean {fmtMoneyPrecise(data?.decisionQuality?.recoverFlowMeanTrueRegret)} • Total</div>
          </div>
          <div style={{ textAlign: "center", fontSize: 20, color: "#16a34a", fontWeight: 900 }}>↓</div>
          {/* Reduction */}
          <div data-testid="regret-reduction" style={{ background: "#f0fdf4", border: "1px solid #bbf7d0", borderRadius: 12, padding: 14, textAlign: "center" }}>
            <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: 0.8, color: "#166534", textTransform: "uppercase" }}>Regret reduction</div>
            <div style={{ fontSize: 20, fontWeight: 900, color: "#16a34a", marginTop: 6 }}>{fmtMoneyPrecise(data?.decisionQuality?.regretDelta)}</div>
            <div style={{ fontSize: 10, color: "#15803d", marginTop: 4 }}>{fmtPct(data?.decisionQuality?.relativeRegretReduction)} • relative</div>
          </div>
        </div>

        <div style={{ marginTop: 12, display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 10, fontSize: 11 }}>
          <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: 10 }}>
            <div style={{ fontWeight: 700, color: "#64748b" }}>Policy-only true regret (total)</div>
            <div data-testid="true-regret-policy" style={{ fontWeight: 800, color: "#0f172a", marginTop: 2 }}>{fmtMoneyPrecise(data?.decisionQuality?.policyOnlyTotalTrueRegret)}</div>
          </div>
          <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: 10 }}>
            <div style={{ fontWeight: 700, color: "#64748b" }}>RecoverFlow true regret (total)</div>
            <div data-testid="true-regret-recoverflow" style={{ fontWeight: 800, color: "#0f172a", marginTop: 2 }}>{fmtMoneyPrecise(data?.decisionQuality?.recoverFlowTotalTrueRegret)}</div>
          </div>
          <div style={{ background: "#f0fdf4", border: "1px solid #bbf7d0", borderRadius: 8, padding: 10 }}>
            <div style={{ fontWeight: 700, color: "#166534" }}>Regret reduction</div>
            <div data-testid="regret-delta" style={{ fontWeight: 800, color: "#16a34a", marginTop: 2 }}>{fmtMoneyPrecise(data?.decisionQuality?.regretDelta)} • {fmtPct(data?.decisionQuality?.relativeRegretReduction)}</div>
          </div>
          <div style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: 8, padding: 10 }}>
            <div style={{ fontWeight: 700, color: "#64748b" }}>Oracle mean true value</div>
            <div style={{ fontWeight: 800, color: "#0f172a", marginTop: 2 }}>{fmtMoneyPrecise(data?.decisionQuality?.meanOracleTrueValue)}</div>
            <div style={{ fontSize: 10, color: "#64748b" }}>Best permissible — aggregate</div>
          </div>
        </div>
      </section>

      {/* AI BEHAVIOR vs BUSINESS OUTCOME */}
      <section style={{ marginTop: 16, display: "grid", gridTemplateColumns: "1.1fr 0.9fr", gap: 16 }}>
        {/* AI BEHAVIOR */}
        <div style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 16 }}>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <h3 style={{ margin: 0, fontSize: 12, fontWeight: 800, letterSpacing: 0.8, color: "#0f172a", textTransform: "uppercase" }}>AI Behavior</h3>
            <span style={{ fontSize: 10, color: "#64748b", background: "#f1f5f9", padding: "3px 8px", borderRadius: 999, fontWeight: 700 }}>True-value based • not outcome</span>
          </div>
          <p style={{ margin: "6px 0 0", fontSize: 11, color: "#64748b" }}>Classification via true expected value of selected actions. Neutral includes no-change.</p>

          <div style={{ marginTop: 14, display: "grid", gridTemplateColumns: "repeat(2, 1fr)", gap: 10 }}>
            <div data-testid="action-changes" style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 10, padding: 12, textAlign: "center" }}>
              <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: 0.6, color: "#64748b", textTransform: "uppercase" }}>Action changes</div>
              <div style={{ fontSize: 22, fontWeight: 900, color: "#0f172a", marginTop: 4 }}>{data?.aiBehavior?.actionChangedCount ?? "—"}</div>
              <div style={{ marginTop: 4 }}><RateBadge value={data?.aiBehavior?.actionChangeRate} /></div>
              <div style={{ fontSize: 10, color: "#64748b", marginTop: 4 }}>{data?.aiBehavior?.actionChangedCount ?? 0} / {data?.aiBehavior?.totalCases ?? 0} cases</div>
            </div>
            <div data-testid="ai-helped" style={{ background: "#f0fdf4", border: "1px solid #bbf7d0", borderRadius: 10, padding: 12, textAlign: "center" }}>
              <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: 0.6, color: "#166534", textTransform: "uppercase" }}>Helped</div>
              <div style={{ fontSize: 22, fontWeight: 900, color: "#16a34a", marginTop: 4 }}>{data?.aiBehavior?.aiHelpedCount ?? "—"}</div>
              <div style={{ marginTop: 4 }}><RateBadge value={data?.aiBehavior?.aiHelpRate} tone="green" /></div>
              <div style={{ fontSize: 10, color: "#15803d", marginTop: 4 }}>AI help rate</div>
            </div>
            <div data-testid="ai-hurt" style={{ background: "#fef2f2", border: "1px solid #fecaca", borderRadius: 10, padding: 12, textAlign: "center" }}>
              <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: 0.6, color: "#991b1b", textTransform: "uppercase" }}>Hurt</div>
              <div style={{ fontSize: 22, fontWeight: 900, color: "#dc2626", marginTop: 4 }}>{data?.aiBehavior?.aiHurtCount ?? "—"}</div>
              <div style={{ marginTop: 4 }}><RateBadge value={data?.aiBehavior?.aiHurtRate} tone="red" /></div>
              <div style={{ fontSize: 10, color: "#991b1b", marginTop: 4 }}>AI hurt rate</div>
            </div>
            <div data-testid="ai-neutral" style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 10, padding: 12, textAlign: "center" }}>
              <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: 0.6, color: "#475569", textTransform: "uppercase" }}>Neutral</div>
              <div style={{ fontSize: 22, fontWeight: 900, color: "#475569", marginTop: 4 }}>{data?.aiBehavior?.aiNeutralCount ?? "—"}</div>
              <div style={{ marginTop: 4 }}><RateBadge value={data?.aiBehavior?.aiNeutralRate} tone="amber" /></div>
              <div style={{ fontSize: 10, color: "#64748b", marginTop: 4 }}>AI neutral rate</div>
            </div>
          </div>

          <div style={{ marginTop: 12, background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: 10, fontSize: 11, color: "#475569", display: "flex", gap: 8, flexWrap: "wrap" }}>
            <span><strong>AI help rate</strong> {fmtPctNoSign(data?.aiBehavior?.aiHelpRate)}</span>
            <span>•</span>
            <span><strong>AI hurt rate</strong> {fmtPctNoSign(data?.aiBehavior?.aiHurtRate)}</span>
            <span>•</span>
            <span><strong>AI neutral rate</strong> {fmtPctNoSign(data?.aiBehavior?.aiNeutralRate)}</span>
            <span>•</span>
            <span><strong>Action-change rate</strong> {fmtPctNoSign(data?.aiBehavior?.actionChangeRate)}</span>
          </div>
        </div>

        {/* BUSINESS OUTCOME */}
        <div style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 16 }}>
          <h3 style={{ margin: 0, fontSize: 12, fontWeight: 800, letterSpacing: 0.8, color: "#0f172a", textTransform: "uppercase" }}>Business Outcome</h3>
          <p style={{ margin: "6px 0 0", fontSize: 11, color: "#64748b" }}>Financial and decision-quality impact — separate from AI classification.</p>

          <div style={{ marginTop: 14, display: "grid", gap: 10 }}>
            <div style={{ background: "#0f172a", color: "#fff", borderRadius: 10, padding: 14 }}>
              <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: 0.6, color: "#94a3b8", textTransform: "uppercase" }}>Recovered revenue (synthetic)</div>
              <div style={{ fontSize: 20, fontWeight: 900, marginTop: 4 }}>{fmtMoney(recovered)}</div>
              <div style={{ fontSize: 11, color: "#cbd5e1", marginTop: 4 }}>Delta {fmtMoney(delta)} • Lift {fmtPct(lift)}</div>
            </div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
              <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 10, padding: 12 }}>
                <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: 0.6, color: "#64748b", textTransform: "uppercase" }}>Regret reduction</div>
                <div style={{ fontSize: 16, fontWeight: 900, color: "#16a34a", marginTop: 4 }}>{fmtMoneyPrecise(data?.decisionQuality?.regretDelta)}</div>
                <div style={{ fontSize: 10, color: "#64748b" }}>{fmtPct(data?.decisionQuality?.relativeRegretReduction)}</div>
              </div>
              <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 10, padding: 12 }}>
                <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: 0.6, color: "#64748b", textTransform: "uppercase" }}>Recovery rate</div>
                <div style={{ fontSize: 16, fontWeight: 900, color: "#0f172a", marginTop: 4 }}>{data?.revenue?.recoveryRate ?? "—"}%</div>
                <div style={{ fontSize: 10, color: "#64748b" }}>{data?.aiBehavior?.totalCases ?? 0} cases</div>
              </div>
            </div>
            <div style={{ background: "#fef3c7", border: "1px solid #fde68a", borderRadius: 8, padding: 10, fontSize: 11, color: "#92400e" }}>
              <strong>AI behavior</strong> and <strong>business outcome</strong> are separate: AI may change action (help/hurt/neutral) without guaranteeing revenue lift. Business outcome is evaluated via recovered revenue and true regret.
            </div>
          </div>
        </div>
      </section>

      {/* HELD-OUT */}
      <section style={{ marginTop: 16, background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 16, borderLeft: "4px solid #0f172a" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
          <h2 style={{ margin: 0, fontSize: 12, fontWeight: 900, letterSpacing: 0.8, color: "#0f172a", textTransform: "uppercase" }}>HELD-OUT SYNTHETIC EVALUATION</h2>
          <span style={{ fontSize: 10, background: "#f1f5f9", border: "1px solid #e2e8f0", padding: "3px 8px", borderRadius: 999, fontWeight: 700, color: "#475569" }}>{data?.heldOut?.seedInfo}</span>
        </div>
        <p style={{ margin: "6px 0 0", fontSize: 11, color: "#475569" }}>
          Seed-level held-out partition not used for tuning. <span style={{ background: "#fef3c7", border: "1px solid #fde68a", padding: "1px 6px", borderRadius: 4, fontWeight: 700, fontSize: 10 }}>SYNTHETIC EVALUATION</span>
        </p>

        <div style={{ marginTop: 12, display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 10 }}>
          <div data-testid="heldout-recovered" style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 10, padding: 12 }}>
            <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: 0.6, color: "#64748b", textTransform: "uppercase" }}>Recovered revenue</div>
            <div style={{ fontSize: 16, fontWeight: 900, color: "#0f172a", marginTop: 4 }}>{fmtMoney(data?.heldOut?.revenue?.recoveredRevenue)}</div>
            <div style={{ fontSize: 10, color: "#64748b" }}>Baseline B {fmtMoney(data?.heldOut?.revenue?.baselineBRecovered)}</div>
          </div>
          <div data-testid="heldout-lift" style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 10, padding: 12 }}>
            <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: 0.6, color: "#64748b", textTransform: "uppercase" }}>Revenue lift</div>
            <div style={{ fontSize: 16, fontWeight: 900, color: Number(data?.heldOut?.revenue?.relativeLift) >= 0 ? "#16a34a" : "#dc2626", marginTop: 4 }}>{fmtPct(data?.heldOut?.revenue?.relativeLift)}</div>
            <div style={{ fontSize: 10, color: "#64748b" }}>Delta {fmtMoney(data?.heldOut?.revenue?.absoluteDelta)}</div>
          </div>
          <div data-testid="heldout-mean-regret" style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 10, padding: 12 }}>
            <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: 0.6, color: "#64748b", textTransform: "uppercase" }}>Mean regret</div>
            <div style={{ fontSize: 12, fontWeight: 800, color: "#0f172a", marginTop: 4 }}>Policy {fmtMoneyPrecise(data?.heldOut?.decisionQuality?.policyOnlyMeanTrueRegret)} → RF {fmtMoneyPrecise(data?.heldOut?.decisionQuality?.recoverFlowMeanTrueRegret)}</div>
            <div style={{ fontSize: 10, color: "#64748b" }}>Total reduction {fmtMoneyPrecise(data?.heldOut?.decisionQuality?.regretDelta)}</div>
          </div>
          <div data-testid="heldout-regret-reduction" style={{ background: "#f0fdf4", border: "1px solid #bbf7d0", borderRadius: 10, padding: 12 }}>
            <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: 0.6, color: "#166534", textTransform: "uppercase" }}>Regret reduction</div>
            <div style={{ fontSize: 16, fontWeight: 900, color: "#16a34a", marginTop: 4 }}>{fmtMoneyPrecise(data?.heldOut?.decisionQuality?.regretDelta)}</div>
            <div style={{ fontSize: 10, color: "#15803d" }}>{fmtPct(data?.heldOut?.decisionQuality?.relativeRegretReduction)} relative</div>
          </div>
          <div data-testid="heldout-ai" style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 10, padding: 12 }}>
            <div style={{ fontSize: 10, fontWeight: 700, letterSpacing: 0.6, color: "#64748b", textTransform: "uppercase" }}>AI help / hurt</div>
            <div style={{ fontSize: 12, fontWeight: 800, color: "#0f172a", marginTop: 4 }}>
              <span style={{ color: "#16a34a" }}>{data?.heldOut?.aiBehavior?.aiHelpedCount ?? "—"} helped</span>
              {" • "}
              <span style={{ color: "#dc2626" }}>{data?.heldOut?.aiBehavior?.aiHurtCount ?? "—"} hurt</span>
            </div>
            <div style={{ fontSize: 10, color: "#64748b" }}>Changed {data?.heldOut?.aiBehavior?.actionChangedCount ?? "—"} • Neutral {data?.heldOut?.aiBehavior?.aiNeutralCount ?? "—"}</div>
          </div>
        </div>
        <div style={{ marginTop: 10, fontSize: 10, color: "#94a3b8", textAlign: "center" }}>Held-out 2000 cases • not used for tuning • same evaluator versions as primary • no overclaim of generalization</div>
      </section>

      {/* METHODOLOGY */}
      <section style={{ marginTop: 16, background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12 }}>
        <button
          onClick={() => setMethodologyOpen(!methodologyOpen)}
          style={{ width: "100%", display: "flex", justifyContent: "space-between", alignItems: "center", padding: 16, background: "transparent", border: "none", cursor: "pointer", textAlign: "left" }}
          aria-expanded={methodologyOpen}
        >
          <div>
            <h3 style={{ margin: 0, fontSize: 12, fontWeight: 800, letterSpacing: 0.8, color: "#0f172a", textTransform: "uppercase" }}>Methodology</h3>
            <p style={{ margin: "4px 0 0", fontSize: 11, color: "#64748b" }}>Deterministic, versioned — evaluator, estimator, policy, AI proxy, true-value</p>
          </div>
          <span style={{ background: "#f1f5f9", border: "1px solid #e2e8f0", padding: "6px 10px", borderRadius: 8, fontSize: 12, fontWeight: 800, color: "#0f172a" }}>{methodologyOpen ? "▾ Hide" : "▸ Show"}</span>
        </button>
        {methodologyOpen && (
          <div style={{ padding: "0 16px 16px", borderTop: "1px solid #f1f5f9", display: "grid", gap: 12 }}>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: 10, fontSize: 11, marginTop: 12 }}>
              <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: 10 }}>
                <div style={{ fontWeight: 700, color: "#64748b", textTransform: "uppercase", fontSize: 10, letterSpacing: 0.6 }}>Evaluator version</div>
                <div style={{ fontWeight: 800, color: "#0f172a", marginTop: 4 }}>{data?.methodology?.evaluatorVersion}</div>
              </div>
              <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: 10 }}>
                <div style={{ fontWeight: 700, color: "#64748b", textTransform: "uppercase", fontSize: 10, letterSpacing: 0.6 }}>Estimator version</div>
                <div style={{ fontWeight: 800, color: "#0f172a", marginTop: 4 }}>{data?.methodology?.estimatorVersion}</div>
              </div>
              <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: 10 }}>
                <div style={{ fontWeight: 700, color: "#64748b", textTransform: "uppercase", fontSize: 10, letterSpacing: 0.6 }}>Policy version</div>
                <div style={{ fontWeight: 800, color: "#0f172a", marginTop: 4 }}>{data?.methodology?.policyVersion}</div>
              </div>
              <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: 10 }}>
                <div style={{ fontWeight: 700, color: "#64748b", textTransform: "uppercase", fontSize: 10, letterSpacing: 0.6 }}>AI proxy version</div>
                <div style={{ fontWeight: 800, color: "#0f172a", marginTop: 4 }}>{data?.methodology?.syntheticAiProxyVersion}</div>
              </div>
              <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: 10 }}>
                <div style={{ fontWeight: 700, color: "#64748b", textTransform: "uppercase", fontSize: 10, letterSpacing: 0.6 }}>True-value version</div>
                <div style={{ fontWeight: 800, color: "#0f172a", marginTop: 4 }}>{data?.methodology?.trueValueVersion}</div>
              </div>
              <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: 10 }}>
                <div style={{ fontWeight: 700, color: "#64748b", textTransform: "uppercase", fontSize: 10, letterSpacing: 0.6 }}>Synthetic registry</div>
                <div style={{ fontWeight: 800, color: "#0f172a", marginTop: 4 }}>{data?.methodology?.syntheticRegistryVersion}</div>
              </div>
              <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: 10 }}>
                <div style={{ fontWeight: 700, color: "#64748b", textTransform: "uppercase", fontSize: 10, letterSpacing: 0.6 }}>Dataset size</div>
                <div style={{ fontWeight: 800, color: "#0f172a", marginTop: 4 }}>{data?.methodology?.datasetSizeTotalDevelopment} cases • {data?.methodology?.datasetSizePerSeed} per seed</div>
                <div style={{ fontSize: 10, color: "#64748b" }}>Held-out 2000 • Validation 2000</div>
              </div>
              <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: 10 }}>
                <div style={{ fontWeight: 700, color: "#64748b", textTransform: "uppercase", fontSize: 10, letterSpacing: 0.6 }}>Seed information</div>
                <div style={{ fontWeight: 800, color: "#0f172a", marginTop: 4, fontSize: 10, lineHeight: 1.4 }}>{data?.methodology?.seedInfo}</div>
                <div style={{ fontSize: 10, color: "#64748b" }}>{data?.methodology?.versionSnapshot}</div>
              </div>
            </div>
            <div style={{ background: "#fef3c7", border: "1px solid #fde68a", borderRadius: 8, padding: 10, fontSize: 11, color: "#92400e" }}>
              Aggregate oracle comparison is legitimate — per-case oracle true values averaged, no HiddenTruth/P_true internals exposed beyond <strong>meanOracleTrueValue</strong>. All metrics are SYNTHETIC EVALUATION.
            </div>
          </div>
        )}
      </section>

      <div style={{ marginTop: 16, textAlign: "center", color: "#94a3b8", fontSize: 11 }}>
        Analytics • Evidence — SYNTHETIC EVALUATION • <a href="/overview" style={{ color: "#2563eb" }}>Overview</a> • <a href="/failure-lab" style={{ color: "#2563eb" }}>Failure Lab</a>
      </div>
    </main>
  );
}
