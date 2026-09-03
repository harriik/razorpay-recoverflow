import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import CaseDetailPage from "./page";

vi.mock("next/navigation", () => ({
  useParams: () => ({ id: "00000000-0000-0000-0000-000000000001" }),
}));

describe("Case Detail", () => {
  const mockDetail = {
    caseId: "00000000-0000-0000-0000-000000000001",
    paymentId: "11111111-1111-1111-1111-111111111111",
    amount: "5000.0000",
    currency: "INR",
    gatewayCode: "BANK_TIMEOUT",
    failureCategory: "TEMPORARY_BANK_FAILURE",
    paymentMethod: "CARD",
    customerId: "22222222-2222-2222-2222-222222222222",
    customerEmail: "test@example.com",
    merchantId: "33333333-3333-3333-3333-333333333333",
    merchantName: "TestMerchant",
    status: "RECOVERED",
    attemptCount: 1,
    recoveredAmount: "5000.0000",
    approvedAction: "RETRY_NOW",
    pendingAction: null,
    pendingReason: null,
    escalatedReason: null,
    stoppedReason: null,
    unknownSince: null,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    recoveryActions: [
      {
        id: "aaa",
        actionType: "RETRY_NOW",
        status: "SUCCESS",
        idempotencyKey: "key",
        gatewayRef: "mock_123",
        estimatedLikelihood: 0.5,
        expectedValue: 100,
        costAmount: 10,
        syntheticFrictionProxy: 5,
        riskPenalty: 5,
        executedAt: new Date().toISOString(),
        observedAt: new Date().toISOString(),
        createdAt: new Date().toISOString(),
      },
    ],
    auditEvents: [
      {
        id: "evt1",
        correlationId: "corr1",
        eventType: "CASE_CREATED",
        fromState: "DETECTED",
        toState: "RECOVERED",
        actor: "SYSTEM",
        createdAt: new Date().toISOString(),
        payload: "{}",
      },
    ],
  };

  const mockUnknown = {
    ...mockDetail,
    status: "UNKNOWN",
    recoveredAmount: null,
    unknownSince: new Date().toISOString(),
  };

  beforeEach(() => {
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string) => {
        if (url.includes("/api/v1/recovery-cases/")) {
          if (url.includes("00000000-0000-0000-0000-000000000001")) {
            return Promise.resolve({ ok: true, json: () => Promise.resolve(mockDetail) } as any);
          }
          if (url.includes("unknown")) {
            return Promise.resolve({ ok: true, json: () => Promise.resolve(mockUnknown) } as any);
          }
        }
        return Promise.resolve({ ok: true, json: () => Promise.resolve(mockDetail) } as any);
      })
    );
  });

  it("detail page renders", async () => {
    render(<CaseDetailPage />);
    expect(await screen.findByText(/Case 00000000/)).toBeInTheDocument();
    const recovered = await screen.findAllByText("RECOVERED");
    expect(recovered.length).toBeGreaterThan(0);
  });

  it("UNKNOWN state is distinct", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() => Promise.resolve({ ok: true, json: () => Promise.resolve(mockUnknown) } as any))
    );
    render(<CaseDetailPage />);
    const unknowns = await screen.findAllByText("UNKNOWN");
    expect(unknowns.length).toBeGreaterThan(0);
    expect(await screen.findByText(/AWAITING RECONCILIATION/)).toBeInTheDocument();
    expect(unknowns[0].closest("span")?.style.background).not.toBe("");
  });

  it("audit timeline renders", async () => {
    render(<CaseDetailPage />);
    expect(await screen.findByText("Audit Timeline")).toBeInTheDocument();
    expect(await screen.findByText("CASE_CREATED")).toBeInTheDocument();
  });

  it("case not found renders", async () => {
    vi.stubGlobal("fetch", vi.fn(() => Promise.resolve({ ok: false, status: 404, json: () => Promise.resolve({ error: "not found" }) } as any)));
    render(<CaseDetailPage />);
    await waitFor(() => expect(screen.getByText("Case not found")).toBeInTheDocument());
  });

  it("backend unavailable renders", async () => {
    vi.stubGlobal("fetch", vi.fn(() => Promise.reject(new Error("Network"))));
    render(<CaseDetailPage />);
    await waitFor(() => expect(screen.getByText(/Backend unavailable/)).toBeInTheDocument());
  });
});
