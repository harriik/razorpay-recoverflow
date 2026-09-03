import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import AnalyticsPage from "./page";

const mockAnalytics = {
  syntheticLabel: "SYNTHETIC EVALUATION",
  note: "All revenue is synthetic recovered revenue, not real merchant revenue.",
  methodology: {
    evaluatorVersion: "evaluator-v1",
    estimatorVersion: "v1",
    policyVersion: "v1",
    syntheticAiProxyVersion: "synthetic-ai-v1",
    trueValueVersion: "true-value-v1",
    syntheticRegistryVersion: "hidden-v1",
    datasetSizePerSeed: 200,
    datasetSizeTotalDevelopment: 6000,
    datasetSizeTotalHeldOut: 2000,
    datasetSizeTotalValidation: 2000,
    seedInfo: "Development 30 seeds 10000-10029, Validation 10 seeds 20000-20009, Held-out 10 seeds 30000-30009, 200 cases per seed",
    developmentSeeds: "10000-10029",
    heldOutSeeds: "30000-30009",
    validationSeeds: "20000-20009",
    versionSnapshot: "syntheticWorld=hidden-v1 estimator=v1 policy=v1 aiProxy=synthetic-ai-v1 trueValue=true-value-v1 evaluator=evaluator-v1 datasetSize=200",
  },
  revenue: {
    revenueAtRisk: "1000000.0000",
    recoveredRevenue: "420000.0000",
    baselineARecovered: "310000.0000",
    baselineBRecovered: "380000.0000",
    recoveryRate: "42.00",
    attempts: 6000,
    absoluteRecoveredRevenueDelta: "40000.0000",
    relativeAiLift: "0.1053",
    absoluteDelta: "40000.0000",
    relativeLift: "0.1053",
  },
  decisionQuality: {
    policyOnlyTotalTrueRegret: "180000.0000",
    recoverFlowTotalTrueRegret: "120000.0000",
    policyOnlyMeanTrueRegret: "30.0000",
    recoverFlowMeanTrueRegret: "20.0000",
    policyOnlyMedianTrueRegret: "25.0000",
    recoverFlowMedianTrueRegret: "15.0000",
    meanOracleTrueValue: "150.0000",
    regretDelta: "60000.0000",
    relativeRegretReduction: "0.3333",
    policyOnlyRegret: "180000.0000",
    recoverFlowRegret: "120000.0000",
    regretReduction: "60000.0000",
  },
  aiBehavior: {
    actionChangedCount: 900,
    aiHelpedCount: 300,
    aiHurtCount: 100,
    aiNeutralCount: 500,
    actionChangeRate: "0.1500",
    aiHelpRate: "0.3333",
    aiHurtRate: "0.1111",
    aiNeutralRate: "0.5556",
    totalCases: 6000,
    changed: 900,
    helped: 300,
    hurt: 100,
    neutral: 500,
    changeRate: "0.1500",
    helpRate: "0.3333",
    hurtRate: "0.1111",
    neutralRate: "0.5556",
  },
  comparison: {
    baselineA: "310000.0000",
    baselineB: "380000.0000",
    recoverFlow: "420000.0000",
    oracleMeanTrueValue: "150.0000",
    revenueAtRisk: "1000000.0000",
  },
  heldOut: {
    syntheticLabel: "HELD-OUT SYNTHETIC EVALUATION",
    note: "Seed-level held-out partition not used for tuning.",
    revenue: {
      revenueAtRisk: "350000.0000",
      recoveredRevenue: "140000.0000",
      baselineARecovered: "105000.0000",
      baselineBRecovered: "130000.0000",
      recoveryRate: "40.00",
      attempts: 2000,
      absoluteRecoveredRevenueDelta: "10000.0000",
      relativeAiLift: "0.0769",
      absoluteDelta: "10000.0000",
      relativeLift: "0.0769",
    },
    decisionQuality: {
      policyOnlyTotalTrueRegret: "62000.0000",
      recoverFlowTotalTrueRegret: "41000.0000",
      policyOnlyMeanTrueRegret: "31.0000",
      recoverFlowMeanTrueRegret: "20.5000",
      meanOracleTrueValue: "148.0000",
      regretDelta: "21000.0000",
      relativeRegretReduction: "0.3387",
    },
    aiBehavior: {
      actionChangedCount: 280,
      aiHelpedCount: 95,
      aiHurtCount: 32,
      aiNeutralCount: 153,
      actionChangeRate: "0.1400",
      aiHelpRate: "0.3393",
      aiHurtRate: "0.1143",
      aiNeutralRate: "0.5464",
      totalCases: 2000,
    },
    comparison: {
      baselineA: "105000.0000",
      baselineB: "130000.0000",
      recoverFlow: "140000.0000",
      oracleMeanTrueValue: "148.0000",
    },
    seedInfo: "10 seeds 30000-30009, 200 per seed = 2000 cases",
    totalCases: 2000,
  },
};

describe("Analytics", () => {
  beforeEach(() => {
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string) => {
        if (String(url).includes("/api/v1/analytics")) {
          return Promise.resolve({ ok: true, json: () => Promise.resolve(mockAnalytics) } as any);
        }
        return Promise.resolve({ ok: false, status: 404 } as any);
      })
    );
  });

  it("analytics page loads", async () => {
    render(<AnalyticsPage />);
    await waitFor(() => expect(screen.getByText("Analytics")).toBeInTheDocument());
    expect(screen.getAllByText(/SYNTHETIC EVALUATION/).length).toBeGreaterThan(0);
  });

  it("Baseline B vs RecoverFlow comparison renders", async () => {
    render(<AnalyticsPage />);
    await waitFor(() => expect(screen.getAllByText(/Baseline B vs RecoverFlow/).length).toBeGreaterThan(0));
    expect(screen.getByText(/Recovered revenue — Baseline B/)).toBeInTheDocument();
    expect(screen.getByText(/Recovered revenue — RecoverFlow/)).toBeInTheDocument();
  });

  it("recovered revenue renders", async () => {
    render(<AnalyticsPage />);
    await waitFor(() => expect(screen.getByTestId("recovered-revenue")).toBeInTheDocument());
    // mock recovered is 420000 -> formatted as ₹4,20,000
    expect(screen.getByTestId("recovered-revenue").textContent).toContain("4,20,000");
    expect(screen.getAllByText(/Recovered revenue/).length).toBeGreaterThan(0);
  });

  it("revenue delta renders", async () => {
    render(<AnalyticsPage />);
    await waitFor(() => expect(screen.getByTestId("revenue-delta")).toBeInTheDocument());
    expect(screen.getByTestId("revenue-delta").textContent).toMatch(/40,000/);
    expect(screen.getByTestId("relative-lift").textContent).toContain("10.53");
    expect(screen.getAllByText(/Revenue delta/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/Absolute revenue delta/).length).toBeGreaterThan(0);
  });

  it("true regret renders", async () => {
    render(<AnalyticsPage />);
    await waitFor(() => expect(screen.getByTestId("policy-regret")).toBeInTheDocument());
    expect(screen.getByTestId("policy-regret").textContent).toContain("1,80,000");
    expect(screen.getByTestId("recoverflow-regret").textContent).toContain("1,20,000");
    expect(screen.getAllByText(/True Decision Quality/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/true regret/i).length).toBeGreaterThan(0);
    expect(screen.getByText(/True decision regret measures the gap/)).toBeInTheDocument();
  });

  it("regret reduction renders", async () => {
    render(<AnalyticsPage />);
    await waitFor(() => expect(screen.getByTestId("regret-reduction")).toBeInTheDocument());
    expect(screen.getByTestId("regret-reduction").textContent).toContain("60,000");
    expect(screen.getByTestId("regret-delta").textContent).toContain("60,000");
    expect(screen.getAllByText(/Regret reduction/).length).toBeGreaterThan(0);
  });

  it("AI help/hurt/neutral renders", async () => {
    render(<AnalyticsPage />);
    await waitFor(() => expect(screen.getByTestId("ai-helped")).toBeInTheDocument());
    expect(screen.getByTestId("ai-helped").textContent).toContain("300");
    expect(screen.getByTestId("ai-hurt").textContent).toContain("100");
    expect(screen.getByTestId("ai-neutral").textContent).toContain("500");
    expect(screen.getByTestId("action-changes").textContent).toContain("900");
    expect(screen.getAllByText(/AI Behavior/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/AI help rate/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/AI hurt rate/).length).toBeGreaterThan(0);
    expect(screen.getAllByText(/Action changes/).length).toBeGreaterThan(0);
    // ensure BUSINESS OUTCOME is separate
    expect(screen.getByText("Business Outcome")).toBeInTheDocument();
  });

  it("synthetic evaluation label is visible", async () => {
    render(<AnalyticsPage />);
    await waitFor(() => expect(screen.getAllByText("SYNTHETIC EVALUATION").length).toBeGreaterThan(0));
    expect(screen.getAllByText(/Not real merchant revenue/).length).toBeGreaterThan(0);
  });

  it("held-out section renders", async () => {
    render(<AnalyticsPage />);
    await waitFor(() => expect(screen.getByText("HELD-OUT SYNTHETIC EVALUATION")).toBeInTheDocument());
    expect(screen.getByTestId("heldout-recovered").textContent).toContain("1,40,000");
    expect(screen.getByTestId("heldout-lift").textContent).toContain("7.69");
    expect(screen.getByTestId("heldout-mean-regret").textContent).toContain("31.00");
    expect(screen.getByTestId("heldout-regret-reduction").textContent).toContain("21,000");
    expect(screen.getByTestId("heldout-ai").textContent).toContain("95");
    expect(screen.getByText("Seed-level held-out partition not used for tuning.")).toBeInTheDocument();
  });

  it("backend error state", async () => {
    vi.stubGlobal("fetch", vi.fn(() => Promise.reject(new Error("Network error"))));
    render(<AnalyticsPage />);
    await waitFor(() => expect(screen.getByText(/Backend unavailable/)).toBeInTheDocument());
    expect(screen.getByText(/no synthetic values are fabricated/)).toBeInTheDocument();
  });

  it("no fake placeholder metrics appear", async () => {
    render(<AnalyticsPage />);
    await waitFor(() => expect(screen.getByTestId("recovered-revenue")).toBeInTheDocument());
    const html = document.body.innerHTML;
    // Ensure mocked values appear and not hard-coded fake like 999,999 or 123,456 that are not from mock
    expect(screen.getByTestId("recovered-revenue").textContent).toContain("4,20,000");
    // Should not contain generic placeholder like "—" for main metrics when data is present
    expect(html).not.toContain("₹999,999");
    expect(html).not.toContain("₹123,456");
    // Ensure synthetic label present, not missing
    expect(html).toContain("SYNTHETIC EVALUATION");
  });

  it("methodology expandable shows versions", async () => {
    render(<AnalyticsPage />);
    await waitFor(() => expect(screen.getByText("Analytics")).toBeInTheDocument());
    const btn = screen.getByText(/Show/);
    fireEvent.click(btn);
    await waitFor(() => expect(screen.getByText("evaluator-v1")).toBeInTheDocument());
    expect(screen.getByText("synthetic-ai-v1")).toBeInTheDocument();
    expect(screen.getByText("true-value-v1")).toBeInTheDocument();
    expect(screen.getByText("hidden-v1")).toBeInTheDocument();
    expect(screen.getAllByText(/200 per seed/).length).toBeGreaterThan(0);
  });
});
