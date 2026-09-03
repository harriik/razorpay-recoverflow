async function getBackendHealth() {
  const apiUrl = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";
  try {
    const res = await fetch(`${apiUrl}/api/v1/health`, { cache: "no-store" });
    if (!res.ok) return { status: "DOWN", error: `HTTP ${res.status}` };
    return await res.json();
  } catch (e) {
    return { status: "UNREACHABLE", error: String(e) };
  }
}

export default async function Home() {
  const backend = await getBackendHealth();
  const backendOk = backend.status === "UP";

  return (
    <main style={{ padding: 32, maxWidth: 800, margin: "0 auto" }}>
      <h1 style={{ fontSize: 32, fontWeight: 800 }}>RecoverFlow</h1>
      <p style={{ color: "#555", marginTop: 8 }}>
        AI Revenue Recovery Decision &amp; Orchestration Engine — Phase 1 Bootstrap
      </p>
      <div style={{ marginTop: 16 }}>
        <a href="/failure-lab" style={{ background: "#111827", color: "#fff", padding: "10px 16px", borderRadius: 8, textDecoration: "none", fontWeight: 700, fontSize: 13 }}>
          Open Failure Lab →
        </a>
      </div>

      <section
        style={{
          marginTop: 24,
          padding: 16,
          border: "1px solid #e5e7eb",
          borderRadius: 8,
          background: "#f9fafb",
        }}
      >
        <h2 style={{ fontSize: 16, fontWeight: 700 }}>System Status</h2>
        <div style={{ marginTop: 12, display: "grid", gap: 8, fontSize: 14 }}>
          <div>
            <strong>Frontend:</strong> <span style={{ color: "#16a34a" }}>UP</span> (Next.js 16.3.3)
          </div>
          <div>
            <strong>Backend:</strong>{" "}
            <span style={{ color: backendOk ? "#16a34a" : "#dc2626" }}>{backend.status}</span>{" "}
            <code style={{ background: "#fff", padding: "2px 6px", borderRadius: 4, border: "1px solid #e5e7eb" }}>
              {process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080"}/api/v1/health
            </code>
          </div>
          <pre
            style={{
              background: "#111827",
              color: "#e5e7eb",
              padding: 12,
              borderRadius: 6,
              overflow: "auto",
              fontSize: 12,
            }}
          >
            {JSON.stringify(backend, null, 2)}
          </pre>
        </div>
        <p style={{ marginTop: 12, fontSize: 12, color: "#6b7280" }}>
          Backend health is fetched server-side. If Docker Compose is running, this shows UP. If running
          standalone, start backend on :8080 to see UP.
        </p>
      </section>

      <section style={{ marginTop: 24, fontSize: 14, color: "#374151" }}>
        <h2 style={{ fontSize: 16, fontWeight: 700 }}>Phase 1 — Bootstrap</h2>
        <ul style={{ marginTop: 8, lineHeight: 1.8 }}>
          <li>Backend: Spring Boot 4.1.1 · Java 17 · PostgreSQL 16 · Flyway</li>
          <li>Frontend: Next.js 16.3.3 · React 19 · TypeScript 5.8</li>
          <li>Health: GET /api/v1/health + /actuator/health</li>
          <li>DB: docker-compose db on :5432</li>
        </ul>
      </section>
    </main>
  );
}
