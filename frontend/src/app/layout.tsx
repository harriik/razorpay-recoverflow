import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "RecoverFlow",
  description: "AI Revenue Recovery Decision & Orchestration Engine — Phase 1 Bootstrap",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body style={{ fontFamily: "system-ui, sans-serif", margin: 0 }}>{children}</body>
    </html>
  );
}
