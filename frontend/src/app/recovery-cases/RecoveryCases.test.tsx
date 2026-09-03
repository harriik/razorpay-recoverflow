import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import RecoveryCasesPage from "./page";

describe("Recovery Cases", () => {
  beforeEach(() => {
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string) => {
        if (url.includes("/api/v1/recovery-cases")) {
          return Promise.resolve({
            ok: true,
            json: () =>
              Promise.resolve({
                content: [
                  {
                    caseId: "00000000-0000-0000-0000-000000000001",
                    paymentId: "11111111-1111-1111-1111-111111111111",
                    amount: "5000.0000",
                    currency: "INR",
                    gatewayCode: "BANK_TIMEOUT",
                    paymentMethod: "CARD",
                    status: "RECOVERED",
                    attemptCount: 1,
                    recoveredAmount: "5000.0000",
                    updatedAt: new Date().toISOString(),
                    createdAt: new Date().toISOString(),
                  },
                ],
                totalElements: 1,
                totalPages: 1,
                page: 0,
              }),
          } as any);
        }
        return Promise.resolve({ ok: false } as any);
      })
    );
  });

  it("cases table renders", async () => {
    render(<RecoveryCasesPage />);
    await waitFor(() => expect(screen.getByText("Recovery Cases")).toBeInTheDocument());
    const recovered = await screen.findAllByText("RECOVERED");
    expect(recovered.length).toBeGreaterThan(0);
  });

  it("case row navigation", async () => {
    render(<RecoveryCasesPage />);
    const link = await screen.findByText("00000000…");
    expect(link.closest("a")?.getAttribute("href")).toBe("/recovery-cases/00000000-0000-0000-0000-000000000001");
  });

  it("empty state", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() =>
        Promise.resolve({
          ok: true,
          json: () => Promise.resolve({ content: [], totalElements: 0, totalPages: 0, page: 0 }),
        } as any)
      )
    );
    render(<RecoveryCasesPage />);
    expect(await screen.findByText("No recovery cases yet")).toBeInTheDocument();
  });

  it("loading state", async () => {
    let resolve: any;
    vi.stubGlobal(
      "fetch",
      vi.fn(
        () =>
          new Promise((res) => {
            resolve = res;
          })
      )
    );
    render(<RecoveryCasesPage />);
    expect(screen.getByText("Loading cases…")).toBeInTheDocument();
    resolve({ ok: true, json: () => Promise.resolve({ content: [], totalElements: 0, totalPages: 0 }) });
    await waitFor(() => expect(screen.getByText("No recovery cases yet")).toBeInTheDocument());
  });

  it("error state", async () => {
    vi.stubGlobal("fetch", vi.fn(() => Promise.reject(new Error("Network"))));
    render(<RecoveryCasesPage />);
    await waitFor(() => expect(screen.getByText(/Backend unavailable/)).toBeInTheDocument());
  });
});
