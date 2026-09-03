"use client";

import { useEffect, useState } from "react";
import Link from "next/link";

type CaseRow = {
  caseId: string;
  paymentId: string;
  amount: string;
  currency: string;
  gatewayCode: string;
  paymentMethod: string;
  customerId: string;
  status: string;
  attemptCount: number;
  recoveredAmount: string | null;
  approvedAction: string | null;
  createdAt: string;
  updatedAt: string;
  elapsedHours: number;
};

function StatusBadge({ status }: { status: string }) {
  const map: Record<string, { bg: string; color: string }> = {
    RECOVERED: { bg: "#dcfce7", color: "#166534" },
    UNKNOWN: { bg: "#fef3c7", color: "#92400e" },
    FAILED_TERMINAL: { bg: "#fee2e2", color: "#991b1b" },
    ACTION_FAILED: { bg: "#ffedd5", color: "#9a3412" },
    ESCALATED: { bg: "#ede9fe", color: "#6d28d9" },
    STOPPED: { bg: "#f3f4f6", color: "#374151" },
    RETRY_PENDING: { bg: "#dbeafe", color: "#1e40af" },
    ACTION_APPROVED: { bg: "#e0f2fe", color: "#0c4a6e" },
  };
  const c = map[status] || { bg: "#f3f4f6", color: "#374151" };
  return <span style={{ background: c.bg, color: c.color, padding: "2px 8px", borderRadius: 12, fontSize: 11, fontWeight: 700 }}>{status}</span>;
}

export default function RecoveryCasesPage() {
  const [cases, setCases] = useState<CaseRow[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [size] = useState(10);
  const [status, setStatus] = useState("");
  const [gateway, setGateway] = useState("");
  const [q, setQ] = useState("");
  const [sort, setSort] = useState("updatedAt");
  const [dir, setDir] = useState("DESC");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const api = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

  const fetchCases = () => {
    setLoading(true);
    const params = new URLSearchParams({ page: String(page), size: String(size), sort, direction: dir });
    if (status) params.set("status", status);
    if (gateway) params.set("gatewayCode", gateway);
    if (q) params.set("q", q);
    fetch(`${api}/api/v1/recovery-cases?${params}`)
      .then(async (r) => {
        if (!r.ok) throw new Error(`HTTP ${r.status}`);
        return r.json();
      })
      .then((data) => {
        setCases(data.content || []);
        setTotal(data.totalElements || 0);
        setError(null);
      })
      .catch((e) => setError(String(e)))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    fetchCases();
  }, [page, status, gateway, sort, dir]);

  // Debounce q
  useEffect(() => {
    const t = setTimeout(() => {
      setPage(0);
      fetchCases();
    }, 400);
    return () => clearTimeout(t);
  }, [q]);

  const totalPages = Math.ceil(total / size);

  return (
    <main style={{ maxWidth: 1280, margin: "0 auto", padding: 24 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 16, flexWrap: "wrap" }}>
        <div>
          <h1 style={{ margin: 0, fontSize: 24, fontWeight: 900, color: "#0f172a" }}>Recovery Cases</h1>
          <p style={{ margin: "6px 0 0", color: "#475569", fontSize: 13 }}>
            Real demo data • {total} cases • <span style={{ background: "#fef3c7", padding: "1px 6px", borderRadius: 6, fontSize: 11, fontWeight: 700 }}>REAL DEMO</span> vs <span style={{ background: "#f3f4f6", padding: "1px 6px", borderRadius: 6, fontSize: 11 }}>SYNTHETIC EVALUATION</span>
          </p>
        </div>
        <Link href="/failure-lab" style={{ background: "#0f172a", color: "#fff", padding: "8px 12px", borderRadius: 8, textDecoration: "none", fontSize: 12, fontWeight: 700 }}>
          Failure Lab →
        </Link>
      </div>

      <div style={{ marginTop: 16, background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 12, display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
        <input placeholder="Search case ID / payment ID" value={q} onChange={(e) => setQ(e.target.value)} style={{ flex: 1, minWidth: 200, padding: "8px 10px", border: "1px solid #e2e8f0", borderRadius: 8, fontSize: 13 }} />
        <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }} style={{ padding: "8px 10px", border: "1px solid #e2e8f0", borderRadius: 8, fontSize: 12 }}>
          <option value="">All states</option>
          <option value="RECOVERED">RECOVERED</option>
          <option value="ACTION_FAILED">ACTION_FAILED</option>
          <option value="FAILED_TERMINAL">FAILED_TERMINAL</option>
          <option value="ESCALATED">ESCALATED</option>
          <option value="STOPPED">STOPPED</option>
          <option value="UNKNOWN">UNKNOWN</option>
          <option value="RETRY_PENDING">RETRY_PENDING</option>
          <option value="ACTION_APPROVED">ACTION_APPROVED</option>
        </select>
        <select value={gateway} onChange={(e) => { setGateway(e.target.value); setPage(0); }} style={{ padding: "8px 10px", border: "1px solid #e2e8f0", borderRadius: 8, fontSize: 12 }}>
          <option value="">All gateways</option>
          <option value="BANK_TIMEOUT">BANK_TIMEOUT</option>
          <option value="NETWORK_ERROR">NETWORK_ERROR</option>
          <option value="CARD_EXPIRED">CARD_EXPIRED</option>
          <option value="AUTH_FAILED">AUTH_FAILED</option>
          <option value="INSUFFICIENT_FUNDS">INSUFFICIENT_FUNDS</option>
        </select>
        <select value={sort} onChange={(e) => setSort(e.target.value)} style={{ padding: "8px 10px", border: "1px solid #e2e8f0", borderRadius: 8, fontSize: 12 }}>
          <option value="updatedAt">Updated</option>
          <option value="amount">Amount</option>
          <option value="createdAt">Created</option>
          <option value="status">Status</option>
        </select>
        <select value={dir} onChange={(e) => setDir(e.target.value)} style={{ padding: "8px 10px", border: "1px solid #e2e8f0", borderRadius: 8, fontSize: 12 }}>
          <option value="DESC">DESC</option>
          <option value="ASC">ASC</option>
        </select>
      </div>

      {loading && <div style={{ marginTop: 16, background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, padding: 24, textAlign: "center", color: "#64748b" }}>Loading cases…</div>}

      {error && (
        <div style={{ marginTop: 16, background: "#fef2f2", border: "1px solid #fecaca", color: "#991b1b", padding: 12, borderRadius: 8, fontSize: 12 }}>
          Backend unavailable: {error} — Ensure backend at {api} is running.
          <button onClick={fetchCases} style={{ marginLeft: 12, background: "#991b1b", color: "#fff", border: "none", padding: "4px 8px", borderRadius: 6, cursor: "pointer" }}>
            Retry
          </button>
        </div>
      )}

      {!loading && !error && cases.length === 0 && (
        <div style={{ marginTop: 16, background: "#fff", border: "1px dashed #cbd5e1", borderRadius: 12, padding: 32, textAlign: "center" }}>
          <div style={{ fontSize: 14, fontWeight: 800, color: "#0f172a" }}>No recovery cases yet</div>
          <p style={{ color: "#64748b", fontSize: 13, marginTop: 6, maxWidth: 500, margin: "6px auto 0" }}>
            No real demo cases in the database. Create a case via API or run a scenario in <a href="/failure-lab" style={{ color: "#2563eb" }}>Failure Lab</a> to generate demo data.
          </p>
          <div style={{ marginTop: 12, display: "flex", gap: 8, justifyContent: "center" }}>
            <Link href="/failure-lab" style={{ background: "#0f172a", color: "#fff", padding: "8px 12px", borderRadius: 8, textDecoration: "none", fontSize: 12, fontWeight: 700 }}>
              Open Failure Lab
            </Link>
            <Link href="/evaluation" style={{ background: "#fff", border: "1px solid #e2e8f0", color: "#0f172a", padding: "8px 12px", borderRadius: 8, textDecoration: "none", fontSize: 12, fontWeight: 700 }}>
              View Synthetic Evaluation
            </Link>
          </div>
          <div style={{ marginTop: 8, fontSize: 11, color: "#94a3b8" }}>Clearly distinguish <strong>REAL DEMO DATA</strong> from <strong>SYNTHETIC EVALUATION</strong></div>
        </div>
      )}

      {!loading && !error && cases.length > 0 && (
        <>
          <div style={{ marginTop: 16, background: "#fff", border: "1px solid #e2e8f0", borderRadius: 12, overflow: "hidden" }}>
            <div style={{ overflowX: "auto" }}>
              <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 12 }}>
                <thead style={{ background: "#f8fafc", borderBottom: "1px solid #e2e8f0" }}>
                  <tr style={{ textAlign: "left", color: "#475569", fontWeight: 700 }}>
                    <th style={{ padding: "10px 12px" }}>Case ID</th>
                    <th style={{ padding: "10px 12px" }}>Amount</th>
                    <th style={{ padding: "10px 12px" }}>Gateway</th>
                    <th style={{ padding: "10px 12px" }}>Method</th>
                    <th style={{ padding: "10px 12px" }}>State</th>
                    <th style={{ padding: "10px 12px" }}>Attempts</th>
                    <th style={{ padding: "10px 12px" }}>Recovered</th>
                    <th style={{ padding: "10px 12px" }}>Updated</th>
                  </tr>
                </thead>
                <tbody>
                  {cases.map((c) => (
                    <tr key={c.caseId} style={{ borderTop: "1px solid #f1f5f9", background: "#fff" }}>
                      <td style={{ padding: "10px 12px" }}>
                        <Link href={`/recovery-cases/${c.caseId}`} style={{ color: "#2563eb", fontWeight: 700, textDecoration: "none", fontFamily: "monospace", fontSize: 11 }}>
                          {c.caseId.slice(0, 8)}…
                        </Link>
                        <div style={{ fontSize: 10, color: "#94a3b8", fontFamily: "monospace" }}>{c.paymentId.slice(0, 8)}…</div>
                      </td>
                      <td style={{ padding: "10px 12px", fontWeight: 700 }}>₹{Number(c.amount).toLocaleString("en-IN")}</td>
                      <td style={{ padding: "10px 12px" }}>
                        <code style={{ background: "#f1f5f9", padding: "2px 6px", borderRadius: 4, fontSize: 11 }}>{c.gatewayCode}</code>
                      </td>
                      <td style={{ padding: "10px 12px" }}>{c.paymentMethod}</td>
                      <td style={{ padding: "10px 12px" }}>
                        <StatusBadge status={c.status} />
                      </td>
                      <td style={{ padding: "10px 12px", textAlign: "center" }}>{c.attemptCount}</td>
                      <td style={{ padding: "10px 12px" }}>{c.recoveredAmount ? `₹${Number(c.recoveredAmount).toLocaleString("en-IN")}` : "—"}</td>
                      <td style={{ padding: "10px 12px", color: "#64748b", fontSize: 11 }}>{new Date(c.updatedAt).toLocaleString()}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", padding: "12px 16px", borderTop: "1px solid #e2e8f0", background: "#f8fafc", fontSize: 12 }}>
              <span style={{ color: "#475569" }}>
                Page {page + 1} of {totalPages} • {total} cases
              </span>
              <div style={{ display: "flex", gap: 8 }}>
                <button disabled={page === 0} onClick={() => setPage((p) => Math.max(0, p - 1))} style={{ padding: "6px 10px", borderRadius: 6, border: "1px solid #e2e8f0", background: page === 0 ? "#f1f5f9" : "#fff", cursor: page === 0 ? "not-allowed" : "pointer" }}>
                  Previous
                </button>
                <button disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)} style={{ padding: "6px 10px", borderRadius: 6, border: "1px solid #e2e8f0", background: page + 1 >= totalPages ? "#f1f5f9" : "#fff", cursor: page + 1 >= totalPages ? "not-allowed" : "pointer" }}>
                  Next
                </button>
              </div>
            </div>
          </div>
          <div style={{ marginTop: 8, fontSize: 11, color: "#64748b", textAlign: "center" }}>
            Use pagination and filters — default sort <code>updatedAt DESC</code>
          </div>
        </>
      )}
    </main>
  );
}
