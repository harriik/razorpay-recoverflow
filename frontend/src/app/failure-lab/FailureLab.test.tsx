import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import FailureLabPage from './page';

// Mock fetch
const mockScenarios = [
  { scenarioId: "GATEWAY_TIMEOUT", name: "Gateway Timeout", description: "Timeout", setup: "", trigger: "", expectedOutcome: "", expectedStateTransitions: ["ACTION_APPROVED -> UNKNOWN"], expectedAuditEvents: ["GATEWAY_TIMEOUT"] },
  { scenarioId: "GATEWAY_FAILURE_RETRYABLE", name: "Retryable", description: "", setup: "", trigger: "", expectedOutcome: "", expectedStateTransitions: [], expectedAuditEvents: [] },
  { scenarioId: "GATEWAY_FAILURE_TERMINAL", name: "Terminal", description: "", setup: "", trigger: "", expectedOutcome: "", expectedStateTransitions: [], expectedAuditEvents: [] },
  { scenarioId: "DUPLICATE_EXECUTION", name: "Duplicate", description: "", setup: "", trigger: "", expectedOutcome: "", expectedStateTransitions: [], expectedAuditEvents: [] },
  { scenarioId: "CONCURRENT_EXECUTION", name: "Concurrent", description: "", setup: "", trigger: "", expectedOutcome: "", expectedStateTransitions: [], expectedAuditEvents: [] },
  { scenarioId: "UNKNOWN_RECONCILIATION_SUCCESS", name: "Unknown Success", description: "", setup: "", trigger: "", expectedOutcome: "", expectedStateTransitions: [], expectedAuditEvents: [] },
  { scenarioId: "UNKNOWN_RECONCILIATION_FAILURE", name: "Unknown Failure", description: "", setup: "", trigger: "", expectedOutcome: "", expectedStateTransitions: [], expectedAuditEvents: [] },
  { scenarioId: "STALE_POLICY_APPROVAL", name: "Stale Policy", description: "", setup: "", trigger: "", expectedOutcome: "", expectedStateTransitions: [], expectedAuditEvents: [] },
  { scenarioId: "CUSTOMER_OPT_OUT_BEFORE_EXECUTION", name: "Opt Out", description: "", setup: "", trigger: "", expectedOutcome: "", expectedStateTransitions: [], expectedAuditEvents: [] },
  { scenarioId: "AI_RECOMMENDS_BLOCKED_ACTION", name: "AI Blocked", description: "", setup: "", trigger: "", expectedOutcome: "", expectedStateTransitions: [], expectedAuditEvents: [] },
];

const mockResult = {
  scenarioId: "GATEWAY_TIMEOUT",
  status: "TIMEOUT",
  caseId: "00000000-0000-0000-0000-000000000001",
  beforeState: "ACTION_APPROVED",
  afterState: "UNKNOWN",
  selectedAction: "RETRY_NOW",
  policyResult: null,
  gatewayResult: { status: "TIMEOUT" },
  reconciliationResult: null,
  auditSummary: "GATEWAY_TIMEOUT",
  gatewayCallCount: 1,
  gatewayInvocations: 1,
  executionRequests: 1,
  duplicatesPrevented: 0,
  policyDecisionResult: null,
  idempotencyResult: "NEW",
  auditEvents: ["EXECUTION_RESERVED", "GATEWAY_TIMEOUT"],
  correlationId: "corr-123",
};

const blockedResult = {
  ...mockResult,
  scenarioId: "STALE_POLICY_APPROVAL",
  afterState: "STOPPED",
  gatewayCallCount: 0,
  gatewayInvocations: 0,
  executionRequests: 1,
  duplicatesPrevented: 0,
  policyDecisionResult: "STOP",
};

describe("Failure Lab", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn((url: string) => {
      if (url.includes("/scenarios") && !url.includes("/run") && !url.includes("/reset")) {
        return Promise.resolve({ ok: true, json: () => Promise.resolve(mockScenarios) } as any);
      }
      if (url.includes("/run")) {
        const isBlocked = url.includes("STALE_POLICY") || url.includes("CUSTOMER_OPT") || url.includes("AI_RECOMMENDS");
        return Promise.resolve({ ok: true, json: () => Promise.resolve(isBlocked ? blockedResult : mockResult) } as any);
      }
      if (url.includes("/reset")) {
        return Promise.resolve({ ok: true, json: () => Promise.resolve({ status: "RESET" }) } as any);
      }
      return Promise.resolve({ ok: false, status: 404 } as any);
    }));
  });

  it("scenario list renders 10 cards", async () => {
    render(<FailureLabPage />);
    await waitFor(() => expect(screen.getByText("Failure Lab")).toBeInTheDocument());
    expect(await screen.findByText("Gateway Timeout")).toBeInTheDocument();
  });

  it("gateway call count renders", async () => {
    render(<FailureLabPage />);
    await waitFor(() => expect(screen.getByText("Gateway Timeout")).toBeInTheDocument());
    const btn = screen.getAllByText("Run Scenario")[0];
    fireEvent.click(btn);
    await waitFor(() => expect(screen.getByText(/Gateway invocations/)).toBeInTheDocument());
  });

  it("UNKNOWN timeline renders", async () => {
    render(<FailureLabPage />);
    await waitFor(() => expect(screen.getByText("Gateway Timeout")).toBeInTheDocument());
    const btn = screen.getAllByText("Run Scenario")[0];
    fireEvent.click(btn);
    await waitFor(() => expect(screen.getByText("LIVE EXECUTION TIMELINE")).toBeInTheDocument());
    const unknowns = await screen.findAllByText("UNKNOWN");
    expect(unknowns.length).toBeGreaterThan(0);
  });

  it("policy-blocked case renders zero gateway calls", async () => {
    render(<FailureLabPage />);
    await waitFor(() => expect(screen.getByText("Stale Policy")).toBeInTheDocument());
    const cards = screen.getAllByText("Run Scenario");
    fireEvent.click(cards[7]);
    await waitFor(() => expect(screen.getByText(/Gateway calls: 0/)).toBeInTheDocument());
  });

  it("reset works", async () => {
    render(<FailureLabPage />);
    await waitFor(() => expect(screen.getByText("Gateway Timeout")).toBeInTheDocument());
    const btn = screen.getAllByText("Run Scenario")[0];
    fireEvent.click(btn);
    await waitFor(() => expect(screen.getByText("LIVE EXECUTION TIMELINE")).toBeInTheDocument());
    const resetBtn = screen.getAllByText("Reset")[0];
    fireEvent.click(resetBtn);
    await waitFor(() => expect(screen.queryByText("LIVE EXECUTION TIMELINE")).not.toBeInTheDocument());
  });

  it("backend error state renders", async () => {
    vi.stubGlobal("fetch", vi.fn(() => Promise.reject(new Error("Network error"))));
    render(<FailureLabPage />);
    await waitFor(() => expect(screen.getByText(/Backend unavailable/)).toBeInTheDocument());
  });
});
