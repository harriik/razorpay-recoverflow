import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import DecisionPage from "./page";

vi.mock("next/navigation", () => ({
  useParams: () => ({ id: "00000000-0000-0000-0000-000000000001" }),
}));

const mockHistorical = {
  case: { caseId: "00000000-0000-0000-0000-000000000001", amount: "5000.0000", currency: "INR", status: "ACTION_APPROVED", attemptCount: 0, recoveredAmount: null, createdAt: new Date().toISOString(), updatedAt: new Date().toISOString() },
  observableEvidence: { amount: "5000.0000", currency: "INR", paymentMethod: "CARD", gatewayCode: "BANK_TIMEOUT", failedAt: null, elapsedHours: 5, attemptCount: 0, priorSuccessCount: 2, priorFailureCount: 1, linkAlreadySent: false },
  aiAssessment: {
    failureCategory: "TEMPORARY_BANK_FAILURE",
    recoverability: "MEDIUM",
    candidateAssessments: [
      { action: "RETRY_NOW", assessment: "HIGH", applicable: true },
      { action: "SCHEDULE_RETRY", assessment: "MEDIUM", applicable: true },
    ],
    recommendedAction: "RETRY_NOW",
    evidenceQuality: "MEDIUM",
    riskLevel: "LOW",
    reasoningSummary: "AI recommends retry",
    provider: "SYNTHETIC_AI_PROXY",
    modelId: "synthetic-ai-v1",
  },
  candidates: [
    { action: "RETRY_NOW", pEstimated: "0.5000", expectedNetValue: "100.0000", operationalCost: "0.0000", syntheticCustomerFrictionProxy: "50.0000", riskPenalty: "5.0000", policyResult: "ALLOWED", policyRuleId: "DEFAULT_ALLOW", policyReason: "permissible", baseContribution: null, aiContribution: null, evidenceModifier: null, recoverabilityModifier: null, historyModifier: null, elapsedModifier: null, finalP: "0.5000" },
    { action: "SCHEDULE_RETRY", pEstimated: "0.4000", expectedNetValue: "90.0000", operationalCost: "0.0000", syntheticCustomerFrictionProxy: "20.0000", riskPenalty: "5.0000", policyResult: "BLOCKED", policyRuleId: "RETRY_LIMIT", policyReason: "retry_limit_exceeded", baseContribution: null, aiContribution: null, evidenceModifier: null, recoverabilityModifier: null, historyModifier: null, elapsedModifier: null, finalP: "0.4000" },
    { action: "SEND_PAYMENT_LINK", pEstimated: "0.3000", expectedNetValue: "80.0000", operationalCost: "10.0000", syntheticCustomerFrictionProxy: "100.0000", riskPenalty: "5.0000", policyResult: "ESCALATE", policyRuleId: "AMOUNT_THRESHOLD", policyReason: "amount_exceeds_auto_limit", baseContribution: null, aiContribution: null, evidenceModifier: null, recoverabilityModifier: null, historyModifier: null, elapsedModifier: null, finalP: "0.3000" },
    { action: "SEND_REMINDER", pEstimated: "0.1000", expectedNetValue: "10.0000", operationalCost: "2.0000", syntheticCustomerFrictionProxy: "30.0000", riskPenalty: "5.0000", policyResult: "BLOCKED", policyRuleId: "ACTION_ELIGIBILITY", policyReason: "reminder_requires_prior_link", baseContribution: null, aiContribution: null, evidenceModifier: null, recoverabilityModifier: null, historyModifier: null, elapsedModifier: null, finalP: "0.1000" },
  ],
  selectedAction: "RETRY_NOW",
  selectionReason: "selected_highest_allowed",
  selectedExpectedNetValue: "100.0000",
  selectionTimestamp: new Date().toISOString(),
  policySummary: { selectedPolicyDecision: "ALLOWED", selectedRuleId: "DEFAULT_ALLOW", selectedReason: "permissible", allDecisions: [] },
  versions: { estimatorVersion: "v1", policyVersion: "v1", aiProvider: "SYNTHETIC_AI_PROXY", aiModel: "synthetic-ai-v1", decisionVersion: "decision-v1", evVersion: "ev-v1" },
  auditReference: { auditCount: 2, lastCorrelationId: "corr-123", eventsUrl: "/api/v1/recovery-cases/00000000-0000-0000-0000-000000000001/audit" },
  auditEvents: [],
  historical: true,
};

const mockNotPersisted = {
  ...mockHistorical,
  aiAssessment: { status: "NOT_PERSISTED", description: "not persisted" },
  candidates: [],
  selectedAction: null,
  historical: false,
};

describe("Decision View C2-2", () => {
  beforeEach(() => {
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string) => {
        if (url.includes("/decision")) {
          if (url.includes("not-persisted")) {
            return Promise.resolve({ ok: true, json: () => Promise.resolve(mockNotPersisted) } as any);
          }
          return Promise.resolve({ ok: true, json: () => Promise.resolve(mockHistorical) } as any);
        }
        return Promise.resolve({ ok: false } as any);
      })
    );
  });

  it("historical decision loads", async () => {
    render(<DecisionPage />);
    await waitFor(() => expect(screen.getAllByText("HISTORICAL DECISION").length).toBeGreaterThan(0));
  });

  it("observable evidence renders", async () => {
    render(<DecisionPage />);
    await waitFor(() => expect(screen.getAllByText("Observable Evidence").length).toBeGreaterThan(0));
    expect(await screen.findByText("BANK_TIMEOUT")).toBeInTheDocument();
    expect(screen.getAllByText("amount").length).toBeGreaterThan(0);
  });

  it("AI assessment renders", async () => {
    render(<DecisionPage />);
    await waitFor(() => expect(screen.getAllByText("AI Assessment").length).toBeGreaterThan(0));
    expect(await screen.findByText("TEMPORARY_BANK_FAILURE")).toBeInTheDocument();
    expect(screen.getAllByText("MEDIUM").length).toBeGreaterThan(0);
  });

  it("four candidates render", async () => {
    render(<DecisionPage />);
    await waitFor(() => expect(screen.getByText("Candidates (historical ranking)")).toBeInTheDocument());
    expect((await screen.findAllByText("RETRY_NOW")).length).toBeGreaterThan(0);
    expect(screen.getAllByText("SCHEDULE_RETRY").length).toBeGreaterThan(0);
    expect(screen.getByText("SEND_PAYMENT_LINK")).toBeInTheDocument();
    expect(screen.getByText("SEND_REMINDER")).toBeInTheDocument();
  });

  it("P_estimated renders", async () => {
    render(<DecisionPage />);
    await waitFor(() => expect(screen.getAllByText("0.5000").length).toBeGreaterThan(0));
  });

  it("EV renders", async () => {
    render(<DecisionPage />);
    await waitFor(() => expect(screen.getAllByText("100.0000").length).toBeGreaterThan(0));
  });

  it("policy result renders", async () => {
    render(<DecisionPage />);
    await waitFor(() => expect(screen.getAllByText("ALLOWED").length).toBeGreaterThan(0));
    expect(screen.getAllByText("BLOCKED").length).toBeGreaterThan(0);
    expect(screen.getByText("ESCALATE")).toBeInTheDocument();
  });

  it("rule ID renders", async () => {
    render(<DecisionPage />);
    await waitFor(() => expect(screen.getAllByText((_, el) => el?.textContent?.includes("DEFAULT_ALLOW") ?? false).length).toBeGreaterThan(0));
    expect(screen.getAllByText((_, el) => el?.textContent?.includes("RETRY_LIMIT") ?? false).length).toBeGreaterThan(0);
    expect(screen.getAllByText((_, el) => el?.textContent?.includes("AMOUNT_THRESHOLD") ?? false).length).toBeGreaterThan(0);
  });

  it("selected action highlighted", async () => {
    render(<DecisionPage />);
    await waitFor(() => expect(screen.getByText("SELECTED ACTION")).toBeInTheDocument());
    const selected = screen.getAllByText("RETRY_NOW");
    expect(selected.length).toBeGreaterThan(1);
    // Check for star or highlight
    expect(document.body.innerHTML).toContain("★");
  });

  it("AI recommendation displayed", async () => {
    render(<DecisionPage />);
    await waitFor(() => expect(screen.getAllByText("AI Assessment").length).toBeGreaterThan(0));
    expect(screen.getAllByText("RETRY_NOW").length).toBeGreaterThan(0);
  });

  it("policy override indication", async () => {
    // Mock case where AI recommends different from selected
    const mockOverride = {
      ...mockHistorical,
      aiAssessment: { ...mockHistorical.aiAssessment, recommendedAction: "SCHEDULE_RETRY" },
      selectedAction: "RETRY_NOW",
    };
    vi.stubGlobal(
      "fetch",
      vi.fn(() => Promise.resolve({ ok: true, json: () => Promise.resolve(mockOverride) } as any))
    );
    render(<DecisionPage />);
    await waitFor(() => expect(screen.getByText(/AI recommendation overridden by policy/)).toBeInTheDocument());
  });

  it("NOT_PERSISTED state remains honest", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(() => Promise.resolve({ ok: true, json: () => Promise.resolve(mockNotPersisted) } as any))
    );
    render(<DecisionPage />);
    await waitFor(() => expect(screen.getByText("Historical decision snapshot unavailable.")).toBeInTheDocument());
    expect(screen.getAllByText("NOT_PERSISTED").length).toBeGreaterThan(0);
  });

  it("null estimator breakdown does not invent values", async () => {
    render(<DecisionPage />);
    await waitFor(() => expect(screen.getByText("Candidates (historical ranking)")).toBeInTheDocument());
    expect(screen.getByText("Estimator contribution breakdown was not persisted for this decision.")).toBeInTheDocument();
  });
});
