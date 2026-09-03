import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "RecoverFlow",
  description: "AI Revenue Recovery Decision & Orchestration Engine — Phase 1 Bootstrap",
};

import Nav from "@/components/Nav";

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body style={{ fontFamily: "system-ui, sans-serif", margin: 0, background: "#f8fafc" }}>
        <header style={{ background: "#fff", borderBottom: "1px solid #e2e8f0", position: "sticky", top: 0, zIndex: 20 }}>
          <div style={{ maxWidth: 1280, margin: "0 auto", padding: "12px 24px", display: "flex", justifyContent: "space-between", alignItems: "center", gap: 16, flexWrap: "wrap" }}>
            <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
              <div style={{ width: 32, height: 32, borderRadius: 8, background: "#0f172a", color: "#fff", display: "grid", placeItems: "center", fontWeight: 900, fontSize: 14 }}>R</div>
              <div>
                <div style={{ fontWeight: 900, letterSpacing: -0.5, color: "#0f172a", lineHeight: 1 }}>RecoverFlow</div>
                <div style={{ fontSize: 11, color: "#64748b", fontWeight: 600 }}>Revenue Recovery Console</div>
              </div>
            </div>
            <Nav />
          </div>
        </header>
        {children}
      </body>
    </html>
  );
}
