"use client";

import { useEffect, useState } from "react";
import { useParams } from "next/navigation";
import Link from "next/link";

type Detail = {
  caseId: string;
  paymentId: string;
  amount: string;
  currency: string;
  gatewayCode: string;
  failureCategory: string;
  paymentMethod: string;
  customerId: string;
  customerEmail: string;
  merchantId: string;
  merchantName: string;
  status: string;
  attemptCount: number;
  recoveredAmount: string | null;
  approvedAction: string | null;
  pendingAction: string | null;
  pendingReason: string | null;
  escalatedReason: string | null;
  stoppedReason: string | null;
  unknownSince: string | null;
  createdAt: string;
  updatedAt: string;
  recoveryActions: any[];
  auditEvents: any[];
};

function StatusBadge({ status }: { status: string }) {
  const map: Record<string, { bg: string; color: string; border: string }> = {
    RECOVERED: { bg: "#dcfce7", color: "#166534", border: "#bbf7d0" },
    UNKNOWN: { bg: "#fef3c7", color: "#92400e", border: "#fde68a" },
    FAILED_TERMINAL: { bg: "#fee2e2", color: "#991b1b", border: "#fecaca" },
    ACTION_FAILED: { bg: "#ffedd5", color: "#9a3412", border: "#fed7aa" },
    ESCALATED: { bg: "#ede9fe", color: "#6d28d9", border: "#ddd6fe" },
    STOPPED: { bg: "#f3f4f6", color: "#374151", border: "#e5e7eb" },
    RETRY_PENDING: { bg: "#dbeafe", color: "#1e40af", border: "#bfdbfe" },
    ACTION_APPROVED: { bg: "#e0f2fe", color: "#0c4a6e", border: "#bae6fd" },
    DETECTED: { bg: "#f1f5f9", color: "#475569", border: "#e2e8f0" },
  };
  const c = map[status] || { bg: "#f3f4f6", color: "#374151", border: "#e5e7eb" };
  return <span style={{ background: c.bg, color: c.color, border: `1px solid ${c.border}`, padding: "3px 10px", borderRadius: 999, fontSize: 12, fontWeight: 800, letterSpacing: 0.5 }}>{status}</span>;
}

export default function CaseDetailPage() {
  const params = useParams<{ id: string }>();
  const id = params.id;
  const [data, setData] = useState<Detail | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const api = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

  useEffect(() => {
    if (!id) return;
    setLoading(true);
    fetch(`${api}/api/v1/recovery-cases/${id}`)
      .then(async (r) => {
        if (r.status === 404) throw new Error("NOT_FOUND");
        if (!r.ok) throw new Error(`HTTP ${r.status}`);
        return r.json();
      })
      .then((d) => {
        setData(d);
        setError(null);
      })
      .catch((e) => setError(String(e)))
      .finally(() => setLoading(false));
  }, [id, api]);

  if (loading) {
    return (
      <main style={{ maxWidth: 1200, margin: "0 auto", padding: 24 }}>
        <div style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 24, textAlign: "center", color: "#64748b" }}>
          Loading case…
        </div>
      </main>
    );
  }

  if (error?.includes("NOT_FOUND")) {
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

  const isUnknown = data.status === "UNKNOWN";
  const isRecovered = data.status === "RECOVERED";
  const elapsedHours = data.createdAt ? Math.floor((Date.now() - new Date(data.createdAt).getTime()) / 3600000) : 0;

  return (
    <main style={{ maxWidth: 1280, margin: "0 auto", padding: 24 }}>
      <Link href="/recovery-cases" style={{ color: "#2563eb", textDecoration: "none", fontSize: 13, fontWeight: 600 }}>
        ← Back to cases
      </Link>

      <div style={{ marginTop: 12, display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: 16, flexWrap: "wrap" }}>
        <div>
          <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
            <h1 style={{ margin: 0, fontSize: 22, fontWeight: 900, color: "#0f172a", letterSpacing: -0.5 }}>Case {data.caseId.slice(0, 8)}…</h1>
            <StatusBadge status={data.status} />
            {isUnknown && <span style={{ background: "#fef3c7", border: "1px solid #fde68a", color: "#92400e", padding: "2px 8px", borderRadius: 999, fontSize: 11, fontWeight: 800 }}>AWAITING RECONCILIATION</span>}
          </div>
          <div style={{ marginTop: 6, fontSize: 12, color: "#64748b", fontFamily: "monospace" }}>
            {data.caseId} • Payment {data.paymentId?.slice(0, 8)}…
          </div>
        </div>
        <div style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: 8, padding: "8px 12px", fontSize: 12 }}>
          <span style={{ color: "#64748b" }}>Updated</span> <strong>{new Date(data.updatedAt).toLocaleString()}</strong>
        </div>
      </div>

      <div style={{ marginTop: 16, display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(150px, 1fr))", gap: 12 }}>
        <div style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 14 }}>
          <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.6, color: "#64748b", textTransform: "uppercase" }}>Amount</div>
          <div style={{ fontSize: 18, fontWeight: 900, marginTop: 4 }}>₹{Number(data.amount).toLocaleString("en-IN")}</div>
          <div style={{ fontSize: 11, color: "#64748b" }}>{data.currency} • {data.paymentMethod}</div>
        </div>
        <div style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 14 }}>
          <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.6, color: "#64748b", textTransform: "uppercase" }}>Revenue at risk</div>
          <div style={{ fontSize: 18, fontWeight: 900, marginTop: 4 }}>₹{Number(data.amount).toLocaleString("en-IN")}</div>
          <div style={{ fontSize: 11, color: "#64748b" }}>Gateway: {data.gatewayCode}</div>
        </div>
        <div style={{ background: isRecovered ? "#f0fdf4" : "#fff", border: `1px solid ${isRecovered ? "#bbf7d0" : "#e2e8f0"}`, borderRadius: 12, padding: 14 }}>
          <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.6, color: "#64748b", textTransform: "uppercase" }}>Recovered</div>
          <div style={{ fontSize: 18, fontWeight: 900, marginTop: 4, color: isRecovered ? "#166534" : "#0f172a" }}>{data.recoveredAmount ? `₹${Number(data.recoveredAmount).toLocaleString("en-IN")}` : "—"}</div>
          <div style={{ fontSize: 11, color: "#64748b" }}>{isRecovered ? "Payment recovered" : "Not yet recovered"}</div>
        </div>
        <div style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 14 }}>
          <div style={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.6, color: "#64748b", textTransform: "uppercase" }}>Attempts</div>
          <div style={{ fontSize: 18, fontWeight: 900, marginTop: 4 }}>{data.attemptCount}</div>
          <div style={{ fontSize: 11, color: "#64748b" }}>Elapsed {elapsedHours}h • {data.status}</div>
        </div>
      </div>

      {isUnknown && (
        <div style={{ marginTop: 12, background: "#fffbeb", border: "1px solid #fde68a", borderRadius: 8, padding: 12, display: "flex", gap: 12, alignItems: "center" }}>
          <div style={{ width: 8, height: 8, borderRadius: 999, background: "#f59e0b", flexShrink: 0 }} />
          <div style={{ fontSize: 13, color: "#92400e" }}>
            <strong>UNKNOWN</strong> — gateway timeout, awaiting reconciliation. This is <strong>not a failure</strong>; the system will query the gateway and reconcile to <code>RECOVERED</code> or <code>ACTION_FAILED</code>.
          </div>
        </div>
      )}

      <div style={{ marginTop: 16, display: "grid", gridTemplateColumns: "1.1fr 0.9fr", gap: 16 }}>
        <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
          <section style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 16 }}>
            <h3 style={{ margin: 0, fontSize: 13, fontWeight: 800, letterSpacing: 0.6, color: "#0f172a" }}>Payment</h3>
            <div style={{ marginTop: 10, display: "grid", gap: 8, fontSize: 12 }}>
              <div style={{ display: "flex", justifyContent: "space-between" }}>
                <span style={{ color: "#64748b" }}>Payment ID</span>
                <code style={{ background: "#f1f5f9", padding: "2px 6px", borderRadius: 4, fontSize: 11 }}>{data.paymentId?.slice(0, 12)}…</code>
              </div>
              <div style={{ display: "flex", justifyContent: "space-between" }}>
                <span style={{ color: "#64748b" }}>Amount</span>
                <strong>₹{Number(data.amount).toLocaleString("en-IN")} {data.currency}</strong>
              </div>
              <div style={{ display: "flex", justifyContent: "space-between" }}>
                <span style={{ color: "#64748b" }}>Gateway code</span>
                <code style={{ background: "#f1f5f9", padding: "2px 6px", borderRadius: 4 }}>{data.gatewayCode}</code>
              </div>
              <div style={{ display: "flex", justifyContent: "space-between" }}>
                <span style={{ color: "#64748b" }}>Failure category</span>
                <span>{data.failureCategory || "—"}</span>
              </div>
              <div style={{ display: "flex", justifyContent: "space-between" }}>
                <span style={{ color: "#64748b" }}>Method</span>
                <span>{data.paymentMethod}</span>
              </div>
            </div>
          </section>

          <section style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 16 }}>
            <h3 style={{ margin: 0, fontSize: 13, fontWeight: 800, letterSpacing: 0.6, color: "#0f172a" }}>Customer</h3>
            <div style={{ marginTop: 10, display: "grid", gap: 8, fontSize: 12 }}>
              <div style={{ display: "flex", justifyContent: "space-between" }}>
                <span style={{ color: "#64748b" }}>Customer</span>
                <code style={{ background: "#f1f5f9", padding: "2px 6px", borderRadius: 4, fontSize: 11 }}>{data.customerId?.slice(0, 12)}…</code>
              </div>
              <div style={{ display: "flex", justifyContent: "space-between" }}>
                <span style={{ color: "#64748b" }}>Email</span>
                <span>{data.customerEmail || "—"}</span>
              </div>
              <div style={{ display: "flex", justifyContent: "space-between" }}>
                <span style={{ color: "#64748b" }}>Merchant</span>
                <span>{data.merchantName || data.merchantId?.slice(0, 8)}</span>
              </div>
            </div>
          </section>

          <section style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 16 }}>
            <h3 style={{ margin: 0, fontSize: 13, fontWeight: 800, letterSpacing: 0.6, color: "#0f172a" }}>Recovery State</h3>
            <div style={{ marginTop: 10, display: "flex", alignItems: "center", gap: 12 }}>
              <div style={{ fontSize: 36 }}>{
                data.status === "RECOVERED" ? "✅" :
                data.status === "UNKNOWN" ? "⏳" :
                data.status === "FAILED_TERMINAL" ? "⛔" :
                data.status === "ACTION_FAILED" ? "⚠️" :
                data.status === "ESCALATED" ? "⬆️" :
                data.status === "STOPPED" ? "🛑" : "▫️"
              }</div>
              <div>
                <div style={{ fontSize: 14, fontWeight: 800 }}><StatusBadge status={data.status} /></div>
                <div style={{ fontSize: 11, color: "#64748b", marginTop: 4 }}>
                  {data.status === "UNKNOWN" && "Not a failure — pending reconciliation (no blind retry)"}
                  {data.status === "RECOVERED" && "Money recovered — gateway confirmed PAYMENT_RECOVERED"}
                  {data.status === "FAILED_TERMINAL" && "Terminal failure — no further retry"}
                  {data.status === "ACTION_FAILED" && "Retryable failure — can try next candidate"}
                  {data.status === "RETRY_PENDING" && `Waiting: ${data.pendingAction || ""} (${data.pendingReason || ""})`}
                </div>
              </div>
            </div>
            <div style={{ marginTop: 12, display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8, fontSize: 11 }}>
              <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: 8 }}>
                <div style={{ color: "#64748b", fontWeight: 700 }}>Approved action</div>
                <div style={{ fontWeight: 800, marginTop: 2 }}>{data.approvedAction || "—"}</div>
              </div>
              <div style={{ background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: 8 }}>
                <div style={{ color: "#64748b", fontWeight: 700 }}>Pending</div>
                <div style={{ fontWeight: 800, marginTop: 2 }}>{data.pendingAction || "—"}</div>
                <div style={{ fontSize: 10, color: "#64748b" }}>{data.pendingReason || ""}</div>
              </div>
            </div>
          </section>
        </div>

        <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
          <section style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 16 }}>
            <h3 style={{ margin: 0, fontSize: 13, fontWeight: 800, letterSpacing: 0.6, color: "#0f172a" }}>Recovery Actions</h3>
            {data.recoveryActions.length === 0 ? (
              <div style={{ marginTop: 10, fontSize: 12, color: "#64748b", background: "#f8fafc", border: "1px dashed #cbd5e1", padding: 12, borderRadius: 8, textAlign: "center" }}>
                No actions yet — decision pending.
              </div>
            ) : (
              <div style={{ marginTop: 10, display: "flex", flexDirection: "column", gap: 8 }}>
                {data.recoveryActions.map((a: any) => (
                  <div key={a.id} style={{ border: "1px solid #e2e8f0", borderRadius: 8, padding: 10, background: a.status === "SUCCESS" ? "#f0fdf4" : a.status === "UNKNOWN" ? "#fffbeb" : "#fff" }}>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                      <strong style={{ fontSize: 12 }}>{a.actionType}</strong>
                      <span style={{ fontSize: 11, padding: "2px 6px", borderRadius: 6, background: a.status === "SUCCESS" ? "#dcfce7" : a.status === "FAILED" ? "#fee2e2" : "#fef3c7", fontWeight: 700 }}>{a.status}</span>
                    </div>
                    <div style={{ fontSize: 11, color: "#64748b", marginTop: 4, display: "grid", gridTemplateColumns: "1fr 1fr", gap: 4 }}>
                      <span>EV: {a.expectedValue ?? "—"}</span>
                      <span>P: {a.estimatedLikelihood ?? "—"}</span>
                      <span>Cost: ₹{a.costAmount ?? 0}</span>
                      <span style={{ color: "#94a3b8" }}>Friction: ₹{a.syntheticFrictionProxy ?? 0} (demo)</span>
                    </div>
                    <div style={{ fontSize: 10, color: "#64748b", marginTop: 4, fontFamily: "monospace" }}>
                      {a.idempotencyKey} • {a.gatewayRef || "no ref"} • {a.executedAt ? new Date(a.executedAt).toLocaleString() : "not executed"}
                    </div>
                  </div>
                ))}
              </div>
            )}
            <div style={{ fontSize: 10, color: "#94a3b8", marginTop: 6 }}>Friction is a demo proxy, not a real financial cost.</div>
          </section>

          <section style={{ background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 16 }}>
            <h3 style={{ margin: 0, fontSize: 13, fontWeight: 800, letterSpacing: 0.6, color: "#0f172a" }}>Audit Timeline</h3>
            <div style={{ marginTop: 10, display: "flex", flexDirection: "column", gap: 8 }}>
              {data.auditEvents.length === 0 ? (
                <div style={{ fontSize: 12, color: "#64748b" }}>No audit events</div>
              ) : (
                data.auditEvents.map((ev: any) => (
                  <div key={ev.id} style={{ display: "flex", gap: 10, alignItems: "flex-start", fontSize: 11, background: "#f8fafc", border: "1px solid #e2e8f0", borderRadius: 8, padding: "8px 10px" }}>
                    <div style={{ width: 6, height: 6, borderRadius: 999, background: "#0f172a", marginTop: 6, flexShrink: 0 }} />
                    <div style={{ flex: 1 }}>
                      <div style={{ display: "flex", justifyContent: "space-between", gap: 8 }}>
                        <strong style={{ color: "#0f172a" }}>{ev.eventType}</strong>
                        <span style={{ color: "#64748b", fontSize: 10 }}>{new Date(ev.createdAt).toLocaleString()}</span>
                      </div>
                      <div style={{ color: "#475569", marginTop: 2 }}>
                        {ev.fromState} → {ev.toState} • Actor: <strong>{ev.actor}</strong>
                      </div>
                      <div style={{ fontFamily: "monospace", fontSize: 10, color: "#94a3b8", marginTop: 2 }}>
                        corr: {ev.correlationId?.slice(0, 8)}… • {ev.payload}
                      </div>
                    </div>
                  </div>
                ))
              )}
            </div>
          </section>
        </div>
      </div>
    </main>
  );
}
