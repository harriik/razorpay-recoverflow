"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const links = [
  { href: "/", label: "Overview" },
  { href: "/recovery-cases", label: "Recovery Cases" },
  { href: "/analytics", label: "Analytics" },
  { href: "/evaluation", label: "Evaluation" },
  { href: "/failure-lab", label: "Failure Lab" },
];

export default function Nav() {
  const pathname = usePathname();
  return (
    <nav style={{ display: "flex", gap: 4, alignItems: "center" }}>
      {links.map((l) => {
        const active = pathname === l.href || (l.href !== "/" && pathname?.startsWith(l.href));
        return (
          <Link
            key={l.href}
            href={l.href}
            style={{
              padding: "6px 12px",
              borderRadius: 8,
              fontSize: 13,
              fontWeight: active ? 800 : 600,
              color: active ? "#fff" : "#475569",
              background: active ? "#0f172a" : "transparent",
              textDecoration: "none",
              border: active ? "1px solid #0f172a" : "1px solid transparent",
            }}
          >
            {l.label}
          </Link>
        );
      })}
    </nav>
  );
}
