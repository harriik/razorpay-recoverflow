import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, fireEvent, waitFor } from "@testing-library/react";
import EvaluationPage from "./page";

const mockEvaluation = {
  syntheticLabel: "SYNTHETIC EVALUATION",
  methodology: {
    evaluatorVersion: "evaluator-v1",
    estimatorVersion: "v1",
    policyVersion: "v1",
    syntheticAiProxyVersion: "synthetic-ai-v1",
    trueValueVersion: "true-value-v1",
    syntheticRegistryVersion: "hidden-v1",
    evVersion: "ev-v1",
    decisionVersion: "decision-v1",
    datasetSizePerSeed: 200,
    datasetSizeTotalDevelopment: 6000,
    datasetSizeTotalHeldOut: 2000,
    datasetSizeTotalValidation: 2000,
    seedInfo: "Development 30 seeds 10000-10029, Validation 10 seeds 20000-20009, Held-out 10 seeds 30000-30009, 200 cases per seed",
    developmentSeeds: "10000-10029",
    validationSeeds: "20000-20009",
    heldOutSeeds: "30000-30009",
    versionSnapshot: "syntheticWorld=hidden-v1 estimator=v1 policy=v1 aiProxy=synthetic-ai-v1 trueValue=true-value-v1 evaluator=evaluator-v1 datasetSize=200",
  },
  partitions: {
    DEVELOPMENT: {
      label: "DEVELOPMENT",
      seedCount: 30,
      caseCount: 6000,
      recoveredRevenue: "420000.0000",
      baselineARecovered: "310000.0000",
      baselineBRecovered: "380000.0000",
      absoluteRevenueDelta: "40000.0000",
      relativeRevenueLift: "0.1053",
      meanTrueRegretPolicyOnly: "30.0000",
      meanTrueRegretRecoverFlow: "20.0000",
      medianTrueRegretPolicyOnly: "25.0000",
      medianTrueRegretRecoverFlow: "15.0000",
      regretReduction: "60000.0000",
      relativeRegretReduction: "0.3333",
      actionChangeRate: "0.1500",
      helpRate: "0.3333",
      hurtRate: "0.1111",
      neutralRate: "0.5556",
      winCount: 18,
      lossCount: 8,
      tieCount: 4,
      revenue: {
        recoveredRevenue: "420000.0000",
        baselineARecovered: "310000.0000",
        baselineBRecovered: "380000.0000",
        absoluteRevenueDelta: "40000.0000",
        relativeRevenueLift: "0.1053",
      },
      decisionQuality: {
        policyOnlyMeanTrueRegret: "30.0000",
        recoverFlowMeanTrueRegret: "20.0000",
        policyOnlyMedianTrueRegret: "25.0000",
        recoverFlowMedianTrueRegret: "15.0000",
        regretDelta: "60000.0000",
        relativeRegretReduction: "0.3333",
      },
      aiBehavior: {
        actionChangeRate: "0.1500",
        aiHelpRate: "0.3333",
        aiHurtRate: "0.1111",
        aiNeutralRate: "0.5556",
      },
    },
    VALIDATION: {
      label: "VALIDATION",
      seedCount: 10,
      caseCount: 2000,
      recoveredRevenue: "135000.0000",
      baselineARecovered: "105000.0000",
      baselineBRecovered: "140000.0000",
      absoluteRevenueDelta: "-5000.0000",
      relativeRevenueLift: "-0.0357",
      meanTrueRegretPolicyOnly: "31.0000",
      meanTrueRegretRecoverFlow: "32.0000",
      medianTrueRegretPolicyOnly: "26.0000",
      medianTrueRegretRecoverFlow: "27.0000",
      regretReduction: "-2000.0000",
      relativeRegretReduction: "-0.0323",
      actionChangeRate: "0.1400",
      helpRate: "0.3000",
      hurtRate: "0.3500",
      neutralRate: "0.3500",
      winCount: 3,
      lossCount: 6,
      tieCount: 1,
      revenue: {
        recoveredRevenue: "135000.0000",
        baselineARecovered: "105000.0000",
        baselineBRecovered: "140000.0000",
        absoluteRevenueDelta: "-5000.0000",
        relativeRevenueLift: "-0.0357",
      },
      decisionQuality: {
        policyOnlyMeanTrueRegret: "31.0000",
        recoverFlowMeanTrueRegret: "32.0000",
        policyOnlyMedianTrueRegret: "26.0000",
        recoverFlowMedianTrueRegret: "27.0000",
        regretDelta: "-2000.0000",
        relativeRegretReduction: "-0.0323",
      },
      aiBehavior: {
        actionChangeRate: "0.1400",
        aiHelpRate: "0.3000",
        aiHurtRate: "0.3500",
        aiNeutralRate: "0.3500",
      },
    },
    HELD_OUT: {
      label: "HELD_OUT",
      seedCount: 10,
      caseCount: 2000,
      recoveredRevenue: "140000.0000",
      baselineARecovered: "105000.0000",
      baselineBRecovered: "130000.0000",
      absoluteRevenueDelta: "10000.0000",
      relativeRevenueLift: "0.0769",
      meanTrueRegretPolicyOnly: "31.0000",
      meanTrueRegretRecoverFlow: "20.5000",
      medianTrueRegretPolicyOnly: "26.5000",
      medianTrueRegretRecoverFlow: "18.0000",
      regretReduction: "21000.0000",
      relativeRegretReduction: "0.3387",
      actionChangeRate: "0.1400",
      helpRate: "0.3393",
      hurtRate: "0.1143",
      neutralRate: "0.5464",
      winCount: 7,
      lossCount: 2,
      tieCount: 1,
      revenue: {
        recoveredRevenue: "140000.0000",
        baselineARecovered: "105000.0000",
        baselineBRecovered: "130000.0000",
        absoluteRevenueDelta: "10000.0000",
        relativeRevenueLift: "0.0769",
      },
      decisionQuality: {
        policyOnlyMeanTrueRegret: "31.0000",
        recoverFlowMeanTrueRegret: "20.5000",
        policyOnlyMedianTrueRegret: "26.5000",
        recoverFlowMedianTrueRegret: "18.0000",
        regretDelta: "21000.0000",
        relativeRegretReduction: "0.3387",
      },
      aiBehavior: {
        actionChangeRate: "0.1400",
        aiHelpRate: "0.3393",
        aiHurtRate: "0.1143",
        aiNeutralRate: "0.5464",
      },
    },
  },
  development: { seedCount: 30, caseCount: 6000 },
  validation: { seedCount: 10, caseCount: 2000 },
  heldOut: {
    syntheticLabel: "HELD-OUT SYNTHETIC EVALUATION",
    seedInfo: "10 seeds 30000-30009, 200 per seed = 2000 cases",
    totalCases: 2000,
    revenue: { recoveredRevenue: "140000.0000", baselineBRecovered: "130000.0000", absoluteRevenueDelta: "10000.0000", relativeRevenueLift: "0.0769" },
    decisionQuality: { policyOnlyMeanTrueRegret: "31.0000", recoverFlowMeanTrueRegret: "20.5000", regretDelta: "21000.0000", relativeRegretReduction: "0.3387" },
    comparison: { baselineA: "105000.0000", baselineB: "130000.0000", recoverFlow: "140000.0000" },
  },
  revenue: { recoveredRevenue: "420000.0000", baselineBRecovered: "380000.0000", baselineARecovered: "310000.0000" },
  decisionQuality: { policyOnlyMeanTrueRegret: "30.0000", recoverFlowMeanTrueRegret: "20.0000", meanOracleTrueValue: "150.0000", regretDelta: "60000.0000" },
  aiBehavior: { actionChangeRate: "0.1500", aiHelpRate: "0.3333", aiHurtRate: "0.1111" },
  comparison: { baselineA: "310000.0000", baselineB: "380000.0000", recoverFlow: "420000.0000", oracleMeanTrueValue: "150.0000" },
  aiQuality: {
    LOW: {
      quality: "LOW",
      measuredAccuracy: 0.44,
      recoveredRevenue: "395000.0000",
      revenueDeltaVsPolicy: "15000.0000",
      relativeRevenueLift: "0.0395",
      meanRegret: "22.5000",
      regretDelta: "45000.0000",
      helpRate: 0.28,
      hurtRate: 0.15,
      neutralRate: 0.57,
      actionChangeRate: 0.12,
      totalCases: 6000,
    },
    MEDIUM: {
      quality: "MEDIUM",
      measuredAccuracy: 0.65,
      recoveredRevenue: "415000.0000",
      revenueDeltaVsPolicy: "35000.0000",
      relativeRevenueLift: "0.0921",
      meanRegret: "20.5000",
      regretDelta: "57000.0000",
      helpRate: 0.32,
      hurtRate: 0.12,
      neutralRate: 0.56,
      actionChangeRate: 0.14,
      totalCases: 6000,
    },
    HIGH: {
      quality: "HIGH",
      measuredAccuracy: 0.77,
      recoveredRevenue: "430000.0000",
      revenueDeltaVsPolicy: "50000.0000",
      relativeRevenueLift: "0.1316",
      meanRegret: "19.0000",
      regretDelta: "66000.0000",
      helpRate: 0.35,
      hurtRate: 0.10,
      neutralRate: 0.55,
      actionChangeRate: 0.16,
      totalCases: 6000,
    },
    policyOnlyRecovered: "380000.0000",
    experimentSeeds: "10000-10029 (DEVELOPMENT, 30 seeds, 200 per seed)",
    note: "Accuracy shown is measured true-category accuracy of the synthetic observable-only AI proxy under this evaluation configuration.",
  },
};

describe("Evaluation", () => {
  beforeEach(() => {
    vi.stubGlobal(
      "fetch",
      vi.fn((url: string) => {
        if (String(url).includes("/api/v1/analytics")) {
          return Promise.resolve({ ok: true, json: () => Promise.resolve(mockEvaluation) } as any);
        }
        return Promise.resolve({ ok: false, status: 404 } as any);
      })
    );
  });

  it("evaluation page loads", async () => {
    render(<EvaluationPage />);
    await waitFor(() => expect(screen.getByText("Evaluation")).toBeInTheDocument());
    expect(screen.getAllByText(/SYNTHETIC EVALUATION/).length).toBeGreaterThan(0);
  });

  it("development section", async () => {
    render(<EvaluationPage />);
    await waitFor(() => expect(screen.getByTestId("section-DEVELOPMENT")).toBeInTheDocument());
    expect(screen.getByTestId("partition-recovered").textContent).toContain("4,20,000");
    expect(screen.getByTestId("partition-delta").textContent).toContain("40,000");
    expect(screen.getByTestId("partition-lift").textContent).toContain("10.53");
    expect(screen.getByTestId("partition-mean-regret").textContent).toContain("30.00");
    expect(screen.getByTestId("partition-wlt").textContent).toContain("18 / 8 / 4");
  });

  it("validation section", async () => {
    render(<EvaluationPage />);
    await waitFor(() => expect(screen.getByTestId("section-DEVELOPMENT")).toBeInTheDocument());
    fireEvent.click(screen.getByTestId("tab-VALIDATION"));
    await waitFor(() => expect(screen.getByTestId("section-VALIDATION")).toBeInTheDocument());
    expect(screen.getByTestId("partition-recovered").textContent).toContain("1,35,000");
    expect(screen.getByTestId("partition-wlt").textContent).toContain("3 / 6 / 1");
  });

  it("held-out section", async () => {
    render(<EvaluationPage />);
    await waitFor(() => expect(screen.getByTestId("section-DEVELOPMENT")).toBeInTheDocument());
    fireEvent.click(screen.getByTestId("tab-HELD_OUT"));
    await waitFor(() => expect(screen.getByTestId("section-HELD_OUT")).toBeInTheDocument());
    expect(screen.getByTestId("partition-recovered").textContent).toContain("1,40,000");
    expect(screen.getByTestId("partition-lift").textContent).toContain("7.69");
    expect(screen.getByTestId("partition-mean-regret").textContent).toContain("31.00");
  });

  it("held-out label", async () => {
    render(<EvaluationPage />);
    await waitFor(() => expect(screen.getAllByText("HELD-OUT SYNTHETIC EVALUATION").length).toBeGreaterThan(0));
    expect(screen.getAllByText(/not used for tuning/).length).toBeGreaterThan(0);
  });

  it("baseline comparison", async () => {
    render(<EvaluationPage />);
    await waitFor(() => expect(screen.getByTestId("baseline-a")).toBeInTheDocument());
    expect(screen.getByTestId("baseline-a").textContent).toContain("3,10,000");
    expect(screen.getByTestId("baseline-b").textContent).toContain("3,80,000");
    expect(screen.getByTestId("recoverflow-rev").textContent).toContain("4,20,000");
    expect(screen.getByTestId("oracle-regret").textContent).toContain("0.00");
    expect(screen.getAllByText(/evaluator-only reference/).length).toBeGreaterThan(0);
  });

  it("regret metrics", async () => {
    render(<EvaluationPage />);
    await waitFor(() => expect(screen.getByTestId("regret-cascade")).toBeInTheDocument());
    expect(screen.getByText(/True decision regret is the gap/)).toBeInTheDocument();
    expect(screen.getByTestId("regret-cascade").textContent).toContain("30.00");
    expect(screen.getAllByText(/Regret reduction/).length).toBeGreaterThan(0);
  });

  it("AI help/hurt", async () => {
    render(<EvaluationPage />);
    await waitFor(() => expect(screen.getByTestId("partition-help-rate")).toBeInTheDocument());
    expect(screen.getByTestId("partition-help-rate").textContent).toContain("33.3");
    expect(screen.getByTestId("partition-hurt-rate").textContent).toContain("11.1");
    expect(screen.getByTestId("partition-change-rate").textContent).toContain("15.0");
    expect(screen.getByTestId("partition-neutral-rate").textContent).toContain("55.6");
  });

  it("AI quality table", async () => {
    render(<EvaluationPage />);
    await waitFor(() => expect(screen.getByTestId("ai-quality-section")).toBeInTheDocument());
    expect(screen.getByTestId("ai-quality-low-accuracy").textContent).toContain("44.0");
    expect(screen.getByTestId("ai-quality-medium-accuracy").textContent).toContain("65.0");
    expect(screen.getByTestId("ai-quality-high-accuracy").textContent).toContain("77.0");
    expect(screen.getByTestId("ai-quality-low-recovered").textContent).toContain("3,95,000");
    expect(screen.getAllByText(/measured true-category accuracy/).length).toBeGreaterThan(0);
    // ensure not labeled 50/75/90
    expect(screen.queryByText(/50%/)).toBeNull();
  });

  it("methodology section", async () => {
    render(<EvaluationPage />);
    await waitFor(() => expect(screen.getByText("Evaluation")).toBeInTheDocument());
    fireEvent.click(screen.getByTestId("methodology-toggle"));
    await waitFor(() => expect(screen.getByTestId("methodology-content")).toBeInTheDocument());
    expect(screen.getByText("hidden-v1")).toBeInTheDocument();
    expect(screen.getByText("evaluator-v1")).toBeInTheDocument();
    expect(screen.getByText("ev-v1")).toBeInTheDocument();
    expect(screen.getByText("synthetic-ai-v1")).toBeInTheDocument();
    expect(screen.getByText("true-value-v1")).toBeInTheDocument();
    expect(screen.getByText("10000-10029")).toBeInTheDocument();
    expect(screen.getByText("30000-30009")).toBeInTheDocument();
    expect(screen.getAllByText(/Deterministic evaluation: same seed/).length).toBeGreaterThan(0);
  });

  it("synthetic evaluation label", async () => {
    render(<EvaluationPage />);
    await waitFor(() => expect(screen.getAllByText("SYNTHETIC EVALUATION").length).toBeGreaterThan(0));
  });

  it("negative/worse result renders honestly", async () => {
    render(<EvaluationPage />);
    await waitFor(() => expect(screen.getByTestId("section-DEVELOPMENT")).toBeInTheDocument());
    fireEvent.click(screen.getByTestId("tab-VALIDATION"));
    await waitFor(() => expect(screen.getByTestId("partition-lift").textContent).toContain("-3.57"));
    expect(screen.getByTestId("partition-delta").textContent).toContain("-5,000");
    expect(screen.getByTestId("partition-regret-reduction").textContent).toContain("-2,000");
    // honest display, not hidden
  });

  it("backend error state", async () => {
    vi.stubGlobal("fetch", vi.fn(() => Promise.reject(new Error("Network error"))));
    render(<EvaluationPage />);
    await waitFor(() => expect(screen.getByText(/Backend unavailable/)).toBeInTheDocument());
  });

  it("no fake placeholder values", async () => {
    render(<EvaluationPage />);
    await waitFor(() => expect(screen.getByTestId("baseline-a")).toBeInTheDocument());
    const html = document.body.innerHTML;
    expect(html).not.toContain("₹999,999");
    expect(html).not.toContain("₹123,456");
    expect(screen.getByTestId("baseline-a").textContent).toContain("3,10,000");
  });
});
