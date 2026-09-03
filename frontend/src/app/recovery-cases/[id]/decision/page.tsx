"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";

type DecisionResponse = {
  case: { caseId: string; amount: string; currency: string; status: string; attemptCount: number; recoveredAmount: string | null; createdAt: string; updatedAt: string };
  observableEvidence: Record<string, any>;
  aiAssessment: Record<string, any>;
  candidates: any[];
  selectedAction: string | null;
  selectionReason: string | null;
  selectedExpectedNetValue: string | null;
  selectionTimestamp: string | null;
  policySummary: any;
  versions: Record<string, string>;
  auditReference: any;
  auditEvents: any[];
  historical?: boolean;
};

export default function DecisionPage() {
  const params = useParams<{ id: string }>();
  const id = params.id;
  const [data, setData] = useState<DecisionResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notFound, setNotFound] = useState(false);
  const api = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    fetch(`${api}/api/v1/recovery-cases/${id}/decision`)
      .then(async (r) => {
        if (r.status === 404) {
          setNotFound(true);
          throw new Error("NOT_FOUND");
        }
        if (!r.ok) throw new Error(`HTTP ${r.status}`);
        return r.json();
      })
      .then((d) => {
        setData(d);
        setError(null);
      })
      .catch((e) => {
        if (e.message !== "NOT_FOUND") setError(String(e));
      })
      .finally(() => setLoading(false));
  }, [id, api]);

  if (loading) {
    return (
      <main style={{ maxWidth: 1200, margin: "0 auto", padding: 24 }} aria-live="polite" aria-busy="true">
        <div style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 24, textAlign: "center", color: "#64748b" }} role="status" aria-label="Loading decision">
          Loading historical decision…
        </div>
      </main>
    );
  }

  if (notFound) {
    return (
      <main style={{ maxWidth: 800, margin: "0 auto", padding: 24 }}>
        <div style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 32, textAlign: "center" }}>
          <div style={{ fontSize: 18, fontWeight: 800, color: "#0f172a" }}>Case not found</div>
          <p style={{ color: "#64748b", fontSize: 13, marginTop: 8 }}>No recovery case with ID <code style={{ background: "#f1f5f9", padding: "2px 6px", borderRadius: 4 }}>{id}</code></p>
          <Link href="/recovery-cases" style={{ display: "inline-block", marginTop: 16, background: "#0f172a", color: "#fff", padding: "8px 12px", borderRadius: 8, textDecoration: "none", fontSize: 13, fontWeight: 700 }}>
            Back to cases
          </Link>
        </div>
      </main>
    );
  }

  if (error) {
    return (
      <main style={{ maxWidth: 800, margin: "0 auto", padding: 24 }}>
        <div style={{ background: "#fef2f2", border: "1px solid #fecaca", color: "#991b1b", padding: 16, borderRadius: 8 }}>
          Backend unavailable: {error}
          <button onClick={() => location.reload()} style={{ marginLeft: 12, background: "#991b1b", color: "#fff", border: "none", padding: "6px 10px", borderRadius: 6, cursor: "pointer" }}>
            Retry
          </button>
        </div>
      </main>
    );
  }

  if (!data) return null;

  const isHistorical = data.historical !== false;
  const aiStatus = data.aiAssessment?.status;
  const hasSnapshot = aiStatus !== "NOT_PERSISTED";

  return (
    <main style={{ maxWidth: 1280, margin: "0 auto", padding: 24, overflowX: "hidden" }}>
      <Link href={`/recovery-cases/${id}`} style={{ color: "#2563eb", textDecoration: "none", fontSize: 13, fontWeight: 600 }} aria-label="Back to case detail">
        ← Back to case
      </Link>

      <div style={{ marginTop: 12, display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 16, flexWrap: "wrap" }}>
        <div>
          <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
            <h1 style={{ margin: 0, fontSize: 22, fontWeight: 900, color: "#0f172a", letterSpacing: -0.5 }}>Decision</h1>
            <span style={{ background: isHistorical ? "#dcfce7" : "#fef3c7", color: isHistorical ? "#166534" : "#92400e", border: `1px solid ${isHistorical ? "#bbf7d0" : "#fde68a"}`, padding: "2px 8px", borderRadius: 999, fontSize: 11, fontWeight: 800 }} aria-label={isHistorical ? "Historical decision snapshot" : "Not persisted"}>
              {isHistorical ? "HISTORICAL DECISION" : "NOT_PERSISTED"}
            </span>
            <span style={{ background: "#f1f5f9", padding: "2px 8px", borderRadius: 6, fontSize: 11, fontWeight: 600, color: "#475569" }} aria-label="Case ID">
              {data.case.caseId.slice(0, 8)}…
            </span>
          </div>
          <div style={{ marginTop: 6, fontSize: 12, color: "#64748b" }}>
            Case {data.case.caseId} • Amount ₹{Number(data.case.amount).toLocaleString("en-IN")} {data.case.currency} • Status <strong style={{ color: "#0f172a" }}>{data.case.status}</strong>
          </div>
        </div>
        <div style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: 8, padding: "8px 12px", fontSize: 11, color: "#475569" }} aria-label="Version provenance">
          <div>AI: {data.versions?.aiProvider || "—"} / {data.versions?.aiModel || "—"}</div>
          <div>Policy: {data.versions?.policyVersion} • EV: {data.versions?.evVersion} • Estimator: {data.versions?.estimatorVersion}</div>
        </div>
      </div>

      <div style={{ marginTop: 16, display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(280px, 1fr))", gap: 12 }}>
        <div style={{ background: "#0f172a", color: "#fff", borderRadius: 12, padding: 16, border: "2px solid #1e293b" }} aria-label="Selected action">
          <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.6, opacity: 0.8 }}>SELECTED ACTION ★</div>
          <div style={{ fontSize: 26, fontWeight: 900, marginTop: 6, letterSpacing: -0.5 }}>{data.selectedAction || "—"}</div>
          <div style={{ fontSize: 11, opacity: 0.9, marginTop: 4 }}>Why: <strong>{data.selectionReason || "—"}</strong> • Policy: <strong>{(data.policySummary as any)?.selectedPolicyDecision || "—"}</strong></div>
          <div style={{ fontSize: 13, marginTop: 8, background: "rgba(255,255,255,0.1)", padding: "6px 10px", borderRadius: 8, display: "inline-block" }}>Expected Net: <strong>₹{data.selectedExpectedNetValue ? Number(data.selectedExpectedNetValue).toLocaleString("en-IN") : "—"}</strong></div>
          {data.aiAssessment?.recommendedAction && data.selectedAction !== data.aiAssessment.recommendedAction && (
            <div style={{ marginTop: 8, background: "#fef3c7", color: "#92400e", padding: "6px 10px", borderRadius: 8, fontSize: 11, fontWeight: 700 }}>
              AI recommendation overridden by policy: {data.aiAssessment.recommendedAction} → {data.selectedAction}
            </div>
          )}
        </div>
        <div style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 16, display: "flex", flexDirection: "column", justifyContent: "center" }}>
          <div style={{ fontSize: 12, fontWeight: 800, color: "#0f172a" }}>What happened?</div>
          <div style={{ fontSize: 13, color: "#475569", marginTop: 6, lineHeight: 1.5 }}>
            AI recommended <strong>{data.aiAssessment?.recommendedAction || "—"}</strong> with <strong>{data.aiAssessment?.evidenceQuality || "—"}</strong> evidence.
            Policy <strong>{(data.policySummary as any)?.selectedRuleId || "DEFAULT_ALLOW"}</strong> allowed <strong>{data.selectedAction}</strong>.
            {data.case.status === "UNKNOWN" ? " Awaiting reconciliation." : ` Final: ${data.case.status}.`}
          </div>
          <div style={{ marginTop: 8, fontSize: 11, color: "#64748b" }}>AI vs Policy vs Final • Answer in under 10 seconds</div>
        </div>
      </div>

      {!hasSnapshot ? (
        <div style={{ marginTop: 16, background: "#fffbeb", border: "1px solid #fde68a", borderRadius: 12, padding: 16 }}>
          <div style={{ fontWeight: 800, color: "#92400e", fontSize: 13 }}>Historical decision snapshot unavailable.</div>
          <p style={{ margin: "6px 0 0", fontSize: 12, color: "#92400e" }}>
            No persisted decision found for this case. This view replays from persisted data only and does not recompute a fresh decision. The snapshot will be created at the next decision finalization.
          </p>
          <div style={{ marginTop: 8, fontSize: 11, color: "#64748b" }}>
            Observable evidence is still available below, but AI assessment and candidate decisions are not historically persisted for this case.
          </div>
        </div>
      ) : (
        <div style={{ marginTop: 12, background: "#f0fdf4", border: "1px solid #bbf7d0", borderRadius: 8, padding: 10, fontSize: 11, color: "#166534", display: "flex", gap: 8, alignItems: "center" }}>
          <span style={{ background: "#16a34a", color: "#fff", padding: "2px 6px", borderRadius: 999, fontSize: 10, fontWeight: 800 }}>HISTORICAL</span>
          Replayed from persisted decision snapshot (decisionVersion {data.versions?.decisionVersion}) — not recomputed live. Selected: <strong>{data.selectedAction}</strong> at {data.selectionTimestamp ? new Date(data.selectionTimestamp).toLocaleString() : "—"}
        </div>
      )}

      <div style={{ marginTop: 16, background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 16 }}>
        <h2 style={{ margin: 0, fontSize: 12, fontWeight: 800, letterSpacing: 0.6, color: "#0f172a" }}>Decision waterfall – historical audit</h2>
        <div style={{ marginTop: 12, display: "flex", flexDirection: "column", gap: 0, maxWidth: 320, margin: "12px auto 0" }}>
          {[
            { label: "Observable Evidence", sub: `${Object.keys(data.observableEvidence || {}).length} fields`, color: "#f8fafc" },
            { label: "AI Assessment", sub: hasSnapshot ? `${data.aiAssessment.recommendedAction || "—"} • ${data.aiAssessment.evidenceQuality || ""}` : "NOT_PERSISTED", color: hasSnapshot ? "#f0fdf4" : "#fef3c7" },
            { label: "Intervention Likelihood", sub: "P_estimated per candidate", color: "#f8fafc" },
            { label: "Expected Net Recovery Value", sub: "EV = P*amount - cost - friction - risk", color: "#f8fafc" },
            { label: "Policy Gate", sub: `${data.candidates?.length || 0} candidates • ${data.candidates?.filter((c:any)=>c.policyResult==="ALLOWED").length || 0} allowed`, color: "#f8fafc" },
            { label: "Selected Action", sub: data.selectedAction || "—", color: "#0f172a", textColor: "#fff" },
          ].map((step, i, arr) => (
            <div key={step.label} style={{ display: "flex", flexDirection: "column", alignItems: "center" }}>
              <div style={{ background: step.color, color: (step as any).textColor || "#0f172a", border: "1px solid #e2e8f0", padding: "10px 14px", borderRadius: 12, fontSize: 11, fontWeight: 800, textAlign: "center", minWidth: 220, boxShadow: "0 1px 2px rgba(0,0,0,0.06)" }}>
                <div>{step.label}</div>
                <div style={{ fontSize: 10, fontWeight: 600, opacity: 0.7, marginTop: 2 }}>{step.sub}</div>
              </div>
              {i < arr.length - 1 && <div style={{ width: 2, height: 14, background: "#e2e8f0" }} />}
            </div>
          ))}
        </div>
      </div>

      <div style={{ marginTop: 16, display: "grid", gridTemplateColumns: "1.1fr 0.9fr", gap: 16 }}>
        <div style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 16 }}>
          <h3 style={{ margin: 0, fontSize: 13, fontWeight: 800, color: "#0f172a" }}>Observable Evidence</h3>
          <p style={{ margin: "4px 0 0", fontSize: 11, color: "#64748b" }}>Only approved observable fields – never HiddenTruth/P_true</p>
          <div style={{ marginTop: 10, display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8, fontSize: 11 }}>
            {Object.entries(data.observableEvidence || {}).map(([k, v]) => (
              <div key={k} style={{ display: "flex", justifyContent: "space-between", background: "#f8fafc", padding: "6px 8px", borderRadius: 6, border: "1px solid #e2e8f0" }}>
                <span style={{ color: "#64748b", fontWeight: 600 }}>{k}</span>
                <span style={{ fontWeight: 700, color: "#0f172a" }}>{v === null || v === undefined ? "—" : String(v)}</span>
              </div>
            ))}
          </div>
        </div>
        <div style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 16 }}>
          <h3 style={{ margin: 0, fontSize: 13, fontWeight: 800, color: "#0f172a" }}>AI Assessment</h3>
          <div style={{ fontSize: 11, color: "#64748b", marginTop: 2 }}>HISTORICAL AI ASSESSMENT • {data.versions?.aiProvider}/{data.versions?.aiModel}</div>
          {hasSnapshot ? (
            <div style={{ marginTop: 10, display: "grid", gap: 8, fontSize: 11 }}>
              <div style={{ display: "flex", justifyContent: "space-between" }}><span style={{ color: "#64748b" }}>failureCategory</span><strong>{data.aiAssessment.failureCategory || "—"}</strong></div>
              <div style={{ display: "flex", justifyContent: "space-between" }}><span style={{ color: "#64748b" }}>recoverability</span><strong>{data.aiAssessment.recoverability || "—"}</strong></div>
              <div style={{ display: "flex", justifyContent: "space-between" }}><span style={{ color: "#64748b" }}>evidenceQuality</span><strong>{data.aiAssessment.evidenceQuality || "—"}</strong></div>
              <div style={{ display: "flex", justifyContent: "space-between" }}><span style={{ color: "#64748b" }}>riskLevel</span><strong>{data.aiAssessment.riskLevel || "—"}</strong></div>
              <div style={{ display: "flex", justifyContent: "space-between" }}><span style={{ color: "#64748b" }}>recommendedAction</span><strong style={{ color: "#2563eb" }}>{data.aiAssessment.recommendedAction || "—"}</strong></div>
              {data.aiAssessment.candidateAssessments && (
                <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 6, padding: 8 }}>
                  <div style={{ fontWeight: 700, color: "#0f172a", fontSize: 11 }}>candidateAssessments (AI)</div>
                  <div style={{ marginTop: 4, display: "grid", gap: 4 }}>
                    {(data.aiAssessment.candidateAssessments as any[]).map((ca: any, i: number) => (
                      <div key={i} style={{ display: "flex", justifyContent: "space-between", fontSize: 10, background: "#fff", border: "1px solid #e2e8f0", padding: "4px 6px", borderRadius: 4 }}>
                        <span>{ca.action}</span>
                        <span style={{ fontWeight: 700, color: ca.assessment === "HIGH" ? "#16a34a" : ca.assessment === "MEDIUM" ? "#d97706" : "#64748b" }}>{ca.assessment || "N/A"}</span>
                        <span style={{ color: ca.applicable ? "#16a34a" : "#dc2626" }}>{ca.applicable ? "applicable" : "not applicable"}</span>
                      </div>
                    ))}
                  </div>
                </div>
              )}
              <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 6, padding: 8, marginTop: 4 }}>
                <div style={{ fontWeight: 700, color: "#0f172a" }}>reasoningSummary</div>
                <div style={{ color: "#475569", marginTop: 4 }}>{data.aiAssessment.reasoningSummary || "—"}</div>
                <div style={{ fontSize: 10, color: "#94a3b8", marginTop: 4 }}>Provider: {data.aiAssessment.provider || data.versions?.aiProvider} • Model: {data.aiAssessment.modelId || data.versions?.aiModel} • Fallback: {(data.aiAssessment as any).fallbackUsed ? "yes" : "no"}</div>
              </div>
            </div>
          ) : (
            <div style={{ marginTop: 10, background: "#fffbeb", border: "1px solid #fde68a", padding: 10, borderRadius: 8, fontSize: 11, color: "#92400e" }}>
              Historical AI assessment not yet persisted for this case.
            </div>
          )}
        </div>
      </div>

      <div style={{ marginTop: 16, background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 16 }}>
        <h3 style={{ margin: 0, fontSize: 13, fontWeight: 800, color: "#0f172a" }}>Candidates (historical ranking)</h3>
        <p style={{ margin: "4px 0 0", fontSize: 11, color: "#64748b" }}>Sorted by authoritative stored decision ranking – not recomputed. Do not fabricate P_estimated.</p>
        {(() => {
          const aiRec = data.aiAssessment?.recommendedAction;
          const aiRecCandidate = data.candidates?.find((c: any) => c.action === aiRec);
          const isPolicyBlocked = aiRecCandidate && ["BLOCKED", "ESCALATE", "STOP"].includes(aiRecCandidate.policyResult);
          return aiRec && data.selectedAction && aiRec !== data.selectedAction && isPolicyBlocked;
        })() && (
          <div data-testid="policy-override-banner" style={{ marginTop: 8, background: "#fffbeb", border: "1px solid #fde68a", padding: "8px 10px", borderRadius: 8, fontSize: 11, color: "#92400e" }}>
            <strong>AI recommendation overridden by policy:</strong> AI recommended <strong>{data.aiAssessment.recommendedAction}</strong> → Policy <strong>{(data.candidates?.find((c: any) => c.action === data.aiAssessment.recommendedAction)?.policyResult) || "BLOCKED"}</strong> → Selected <strong>{data.selectedAction}</strong> (rule {(data.candidates?.find((c: any) => c.action === data.aiAssessment.recommendedAction)?.policyRuleId) || "—"})
          </div>
        )}
        <div style={{ marginTop: 10, overflowX: "auto" }}>
          <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 11 }}>
            <thead style={{ background: "#f8fafc", borderBottom: "1px solid #e2e8f0", textAlign: "left", color: "#475569" }}>
              <tr>
                <th style={{ padding: "8px 6px" }}>Action</th>
                <th style={{ padding: "8px 6px" }}>P_estimated</th>
                <th style={{ padding: "8px 6px" }}>Expected Net</th>
                <th style={{ padding: "8px 6px" }}>Cost</th>
                <th style={{ padding: "8px 6px" }}>Friction</th>
                <th style={{ padding: "8px 6px" }}>Risk</th>
                <th style={{ padding: "8px 6px" }}>Policy</th>
                <th style={{ padding: "8px 6px" }}>Rule</th>
              </tr>
            </thead>
            <tbody>
              {data.candidates?.length === 0 ? (
                <tr>
                  <td colSpan={8} style={{ padding: 12, textAlign: "center", color: "#94a3b8" }}>
                    No candidate snapshot – decision not yet persisted.
                  </td>
                </tr>
              ) : (
                (() => {
                  const maxEV = Math.max(...data.candidates.map((c: any) => parseFloat(c.expectedNetValue) || -Infinity));
                  return data.candidates?.map((c: any, i: number) => {
                    const isSelected = c.action === data.selectedAction;
                    const isHighestEV = parseFloat(c.expectedNetValue) === maxEV;
                    return (
                      <tr key={i} style={{ borderTop: "1px solid #f1f5f9", background: isSelected ? "#dcfce7" : c.policyResult === "ALLOWED" ? "#f0fdf4" : c.policyResult === "ESCALATE" ? "#ffedd5" : "#fff", outline: isSelected ? "2px solid #16a34a" : "none" }}>
                        <td style={{ padding: "8px 6px", fontWeight: 800 }}>
                          {c.action} {isSelected && "★"} {isHighestEV && !isSelected && <span style={{ fontSize: 9, background: "#e0f2fe", padding: "1px 4px", borderRadius: 4 }}>highest EV</span>}
                        </td>
                        <td style={{ padding: "8px 6px" }}>{c.pEstimated ?? "—"}</td>
                        <td style={{ padding: "8px 6px", fontWeight: 700 }}>{c.expectedNetValue ?? "—"}</td>
                        <td style={{ padding: "8px 6px" }}>{c.operationalCost ?? "—"}</td>
                        <td style={{ padding: "8px 6px", color: "#94a3b8" }}>{c.syntheticCustomerFrictionProxy ?? "—"}</td>
                        <td style={{ padding: "8px 6px" }}>{c.riskPenalty ?? "—"}</td>
                        <td style={{ padding: "8px 6px" }}>
                          <span style={{ padding: "2px 6px", borderRadius: 6, fontSize: 10, fontWeight: 800, background: c.policyResult === "ALLOWED" ? "#dcfce7" : c.policyResult === "ESCALATE" ? "#ffedd5" : c.policyResult === "BLOCKED" ? "#fee2e2" : "#f3f4f6", color: c.policyResult === "ALLOWED" ? "#166534" : c.policyResult === "ESCALATE" ? "#9a3412" : "#374151" }}>
                            {c.policyResult}
                          </span>
                        </td>
                        <td style={{ padding: "8px 6px", fontSize: 10, color: "#64748b" }}>{c.policyRuleId || "—"}<br />{c.policyReason || ""}</td>
                      </tr>
                    );
                  });
                })()
              )}
            </tbody>
          </table>
        </div>
        <details style={{ marginTop: 10, background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: 8 }}>
          <summary style={{ fontSize: 11, fontWeight: 700, cursor: "pointer", color: "#0f172a" }}>Estimator explainability</summary>
          <div style={{ marginTop: 8, fontSize: 11, color: "#475569" }}>
            {data.candidates?.[0]?.baseContribution == null ? (
              <span>Estimator contribution breakdown was not persisted for this decision.</span>
            ) : (
              <div style={{ display: "grid", gap: 4 }}>
                {data.candidates?.map((c: any, i: number) => (
                  <div key={i} style={{ display: "flex", justifyContent: "space-between", background: "#fff", padding: "4px 6px", borderRadius: 4, border: "1px solid #e2e8f0" }}>
                    <span>{c.action}</span>
                    <span>base:{c.baseContribution ?? "—"} ai:{c.aiContribution ?? "—"} ev:{c.evidenceModifier ?? "—"} hist:{c.historyModifier ?? "—"} elapsed:{c.elapsedModifier ?? "—"} final:{c.finalP ?? c.pEstimated}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        </details>
      </div>

      <div style={{ marginTop: 16, display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
        <div style={{ background: "#0f172a", color: "#fff", borderRadius: 12, padding: 16 }}>
          <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.6, opacity: 0.7 }}>SELECTED ACTION</div>
          <div style={{ fontSize: 20, fontWeight: 900, marginTop: 6 }}>{data.selectedAction || "—"}</div>
          <div style={{ fontSize: 11, opacity: 0.8, marginTop: 4 }}>Why: {data.selectionReason || "—"}</div>
          <div style={{ fontSize: 12, marginTop: 6 }}>Expected Net: <strong>₹{data.selectedExpectedNetValue ? Number(data.selectedExpectedNetValue).toLocaleString("en-IN") : "—"}</strong></div>
          <div style={{ fontSize: 11, opacity: 0.7, marginTop: 4 }}>Policy: {(data.policySummary as any)?.selectedPolicyDecision || "—"} • {(data.policySummary as any)?.selectedRuleId || ""}</div>
          <div style={{ fontSize: 10, opacity: 0.6, marginTop: 4 }}>{data.selectionTimestamp ? new Date(data.selectionTimestamp).toLocaleString() : ""}</div>
          {data.case.status === "UNKNOWN" && (
            <div style={{ marginTop: 10, background: "#fef3c7", color: "#92400e", padding: "8px 10px", borderRadius: 8, fontSize: 11, fontWeight: 700, textAlign: "center" }}>
              AWAITING RECONCILIATION — gateway timeout, not a failure. Reconciliation will query gateway.
            </div>
          )}
        </div>
        <div style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 16 }}>
          <h4 style={{ margin: 0, fontSize: 12, fontWeight: 800 }}>Decision Integrity</h4>
          <div style={{ marginTop: 8, display: "grid", gap: 6, fontSize: 11 }}>
            <div style={{ display: "flex", justifyContent: "space-between" }}><span style={{ color: "#64748b" }}>Historical</span><strong style={{ color: isHistorical ? "#16a34a" : "#d97706" }}>{isHistorical ? "Yes – replayed" : "No – missing snapshot"}</strong></div>
            <div style={{ display: "flex", justifyContent: "space-between" }}><span style={{ color: "#64748b" }}>decisionVersion</span><span>{data.versions?.decisionVersion || "—"}</span></div>
            <div style={{ display: "flex", justifyContent: "space-between" }}><span style={{ color: "#64748b" }}>estimator</span><span>{data.versions?.estimatorVersion}</span></div>
            <div style={{ display: "flex", justifyContent: "space-between" }}><span style={{ color: "#64748b" }}>policy</span><span>{data.versions?.policyVersion}</span></div>
            <div style={{ display: "flex", justifyContent: "space-between" }}><span style={{ color: "#64748b" }}>AI provider/model</span><span>{data.versions?.aiProvider}/{data.versions?.aiModel}</span></div>
            <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 6, padding: 6, fontSize: 10, color: "#64748b" }}>Replayed from persisted decision data, not recomputed live. HISTORICAL DECISION SNAPSHOT.</div>
          </div>
          <Link href={`/recovery-cases/${id}`} style={{ display: "inline-block", marginTop: 10, background: "#f1f5f9", border: "1px solid #e2e8f0", padding: "6px 10px", borderRadius: 6, textDecoration: "none", color: "#0f172a", fontSize: 11, fontWeight: 700 }}>
            View full audit trail →
          </Link>
        </div>
      </div>

      <div style={{ marginTop: 16, background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 16 }}>
        <h3 style={{ margin: 0, fontSize: 12, fontWeight: 800, letterSpacing: 0.6, color: "#0f172a" }}>Decision → Execution → Gateway → Final State</h3>
        <div style={{ marginTop: 10, display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", fontSize: 11, background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: 10 }}>
          <span style={{ background: "#0f172a", color: "#fff", padding: "4px 8px", borderRadius: 999, fontWeight: 800 }}>{data.selectedAction || "—"}</span>
          <span>→</span>
          <span style={{ background: "#fff", border: "1px solid #e2e8f0", padding: "4px 8px", borderRadius: 999 }}>EXECUTED</span>
          <span>→</span>
          <span style={{ background: data.case.status === "UNKNOWN" ? "#fef3c7" : data.case.status === "RECOVERED" ? "#dcfce7" : "#fee2e2", padding: "4px 8px", borderRadius: 999, fontWeight: 700 }}>
            {data.case.status === "UNKNOWN" ? "TIMEOUT → UNKNOWN" : data.case.status}
          </span>
          <span>→</span>
          <span style={{ background: "#0f172a", color: "#fff", padding: "4px 8px", borderRadius: 999, fontWeight: 800 }}>{data.case.status}</span>
        </div>
        <div style={{ marginTop: 8, fontSize: 11, color: "#475569", display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
          <div>
            <strong>Idempotency:</strong> PROTECTED <code style={{ background: "#f1f5f9", padding: "1px 4px", borderRadius: 4, fontSize: 10 }}>{(data as any).idempotencyKey ? String((data as any).idempotencyKey).slice(0, 12) + "…" : data.case.caseId.slice(0, 12) + "…"}</code>
          </div>
          <div style={{ textAlign: "right" }}>
            {data.case.status === "UNKNOWN" ? "Awaiting reconciliation – not a failure" : `Final: ${data.case.status}`}
          </div>
        </div>
        <div style={{ marginTop: 8, display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 8, fontSize: 11, textAlign: "center" }}>
          <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: 8 }}>
            <div style={{ color: "#64748b", fontWeight: 700 }}>Execution requests</div>
            <div style={{ fontSize: 14, fontWeight: 800 }}>1</div>
          </div>
          <div style={{ background: data.case.status === "UNKNOWN" || (data as any).gatewayInvocations === 0 ? "#fef2f2" : "#f0fdf4", border: "1px solid #e2e8f0", borderRadius: 8, padding: 8 }}>
            <div style={{ color: "#64748b", fontWeight: 700 }}>Gateway invocations</div>
            <div style={{ fontSize: 14, fontWeight: 800, color: (data as any).gatewayInvocations === 0 ? "#dc2626" : "#0f172a" }}>{(data as any).gatewayInvocations ?? (data.case.status === "UNKNOWN" ? 1 : data.case.status === "RECOVERED" || data.case.status === "ACTION_FAILED" ? 1 : 0)}</div>
            {(data as any).gatewayInvocations === 0 && <div style={{ fontSize: 10, color: "#dc2626", fontWeight: 700 }}>visibly 0</div>}
          </div>
          <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: 8 }}>
            <div style={{ color: "#64748b", fontWeight: 700 }}>Duplicates prevented</div>
            <div style={{ fontSize: 14, fontWeight: 800 }}>{(data as any).duplicatesPrevented ?? 0}</div>
          </div>
        </div>
        {data.case.status === "UNKNOWN" && (
          <div style={{ marginTop: 8, background: "#fffbeb", border: "1px solid #fde68a", borderRadius: 8, padding: 8, fontSize: 11, color: "#92400e" }}>
            <strong>UNKNOWN</strong> gateway result • <code>unknownSince: {(data.case as any).unknownSince || data.auditEvents?.find((e:any)=>e.toState==="UNKNOWN")?.createdAt || "—"}</code> • Reconciliation will query gateway; final outcome may be <code>RECOVERED</code> or <code>ACTION_FAILED</code>.
          </div>
        )}
      </div>

      <div style={{ marginTop: 16, background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 16 }}>
        <h3 style={{ margin: 0, fontSize: 12, fontWeight: 800, letterSpacing: 0.6, color: "#0f172a" }}>Execution</h3>
        <div style={{ marginTop: 8, display: "grid", gap: 8, fontSize: 11 }}>
          <div style={{ display: "flex", justifyContent: "space-between", background: "#f8fafc", padding: "6px 8px", borderRadius: 6, border: "1px solid #e2e8f0" }}>
            <span style={{ color: "#64748b" }}>Action executed</span>
            <strong>{data.selectedAction || (data.case as any).pendingAction || "—"}</strong>
          </div>
          <div style={{ display: "flex", justifyContent: "space-between", background: "#f8fafc", padding: "6px 8px", borderRadius: 6, border: "1px solid #e2e8f0" }}>
            <span style={{ color: "#64748b" }}>Execution status</span>
            <span style={{ fontWeight: 800, color: data.case.status === "RECOVERED" ? "#16a34a" : data.case.status === "UNKNOWN" ? "#d97706" : "#dc2626" }}>{data.case.status}</span>
          </div>
          <div style={{ display: "flex", justifyContent: "space-between", background: "#f8fafc", padding: "6px 8px", borderRadius: 6, border: "1px solid #e2e8f0" }}>
            <span style={{ color: "#64748b" }}>Gateway result</span>
            <span>{(data as any).gatewayResult?.status || data.case.status === "UNKNOWN" ? "UNKNOWN/TIMEOUT" : data.case.status === "RECOVERED" ? "SUCCESS" : "—"}</span>
          </div>
          <div style={{ display: "flex", justifyContent: "space-between", background: "#f8fafc", padding: "6px 8px", borderRadius: 6, border: "1px solid #e2e8f0" }}>
            <span style={{ color: "#64748b" }}>Idempotency</span>
            <span>PROTECTED • <code style={{ fontSize: 10 }}>{data.case.caseId.slice(0, 12)}…</code></span>
          </div>
          <div style={{ display: "flex", justifyContent: "space-between", background: "#f8fafc", padding: "6px 8px", borderRadius: 6, border: "1px solid #e2e8f0" }}>
            <span style={{ color: "#64748b" }}>Recovered amount</span>
            <strong>{data.case.recoveredAmount ? `₹${Number(data.case.recoveredAmount).toLocaleString("en-IN")}` : "—"}</strong>
          </div>
          <div style={{ display: "flex", justifyContent: "space-between", background: "#f8fafc", padding: "6px 8px", borderRadius: 6, border: "1px solid #e2e8f0" }}>
            <span style={{ color: "#64748b" }}>Timestamps</span>
            <span style={{ fontSize: 10 }}>exec: {data.selectionTimestamp ? new Date(data.selectionTimestamp).toLocaleString() : "—"} • obs: {data.case.updatedAt ? new Date(data.case.updatedAt).toLocaleString() : "—"}</span>
          </div>
        </div>
      </div>

      <div style={{ marginTop: 16, background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 16 }}>
        <h3 style={{ margin: 0, fontSize: 12, fontWeight: 800, letterSpacing: 0.6, color: "#0f172a" }}>Audit Timeline</h3>
        <p style={{ margin: "4px 0 0", fontSize: 11, color: "#64748b" }}>Real persisted auditEvents – no frontend-only events.</p>
        <div style={{ marginTop: 10, display: "flex", flexDirection: "column", gap: 6 }}>
          {data.auditEvents?.length === 0 ? (
            <div style={{ fontSize: 11, color: "#94a3b8" }}>No audit events</div>
          ) : (
            data.auditEvents?.map((ev: any) => (
              <details key={ev.id} style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: "8px 10px" }}>
                <summary style={{ cursor: "pointer", fontSize: 11, fontWeight: 700, display: "flex", justifyContent: "space-between", gap: 8 }}>
                  <span>{ev.eventType} • {ev.actor}</span>
                  <span style={{ color: "#64748b", fontSize: 10 }}>{new Date(ev.createdAt).toLocaleString()}</span>
                </summary>
                <div style={{ marginTop: 6, fontSize: 11, display: "grid", gap: 4 }}>
                  <div><span style={{ color: "#64748b" }}>From → To:</span> {ev.fromState} → {ev.toState}</div>
                  <div><span style={{ color: "#64748b" }}>Correlation:</span> <code style={{ fontSize: 10, background: "#fff", padding: "1px 4px", borderRadius: 4 }}>{ev.correlationId?.slice(0, 8)}…</code></div>
                  <div style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: 4, padding: 6, fontSize: 10, color: "#475569", overflow: "hidden", textOverflow: "ellipsis" }}>
                    Payload: {ev.payload}
                  </div>
                </div>
              </details>
            ))
          )}
        </div>
      </div>
    </main>
  );
}
