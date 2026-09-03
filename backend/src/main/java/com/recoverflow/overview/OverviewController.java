package com.recoverflow.overview;

import com.recoverflow.recovery.RecoveryCase;
import com.recoverflow.recovery.RecoveryCaseRepository;
import com.recoverflow.recovery.RecoveryCaseStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/overview")
public class OverviewController {

    private final RecoveryCaseRepository caseRepo;

    public OverviewController(RecoveryCaseRepository caseRepo) {
        this.caseRepo = caseRepo;
    }

    @GetMapping
    public Map<String, Object> overview() {
        List<RecoveryCase> all = caseRepo.findAll();
        BigDecimal revenueAtRisk = all.stream().map(RecoveryCase::getAmount).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(4, RoundingMode.HALF_UP);
        BigDecimal recovered = all.stream().filter(c -> c.getStatus() == RecoveryCaseStatus.RECOVERED).map(c -> c.getRecoveredAmount() != null ? c.getRecoveredAmount() : c.getAmount()).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(4, RoundingMode.HALF_UP);
        BigDecimal recoveryRate = revenueAtRisk.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO.setScale(2) : recovered.multiply(new BigDecimal("100")).divide(revenueAtRisk, 2, RoundingMode.HALF_UP);
        long active = all.stream().filter(c -> !Set.of(RecoveryCaseStatus.RECOVERED, RecoveryCaseStatus.FAILED_TERMINAL, RecoveryCaseStatus.STOPPED, RecoveryCaseStatus.ESCALATED).contains(c.getStatus())).count();
        long recoveredToday = all.stream().filter(c -> c.getStatus() == RecoveryCaseStatus.RECOVERED && c.getUpdatedAt() != null && c.getUpdatedAt().isAfter(Instant.now().minus(24, ChronoUnit.HOURS))).count();
        long escalated = all.stream().filter(c -> c.getStatus() == RecoveryCaseStatus.ESCALATED).count();
        long unknown = all.stream().filter(c -> c.getStatus() == RecoveryCaseStatus.UNKNOWN).count();
        long total = all.size();

        // Recovery by intervention (from recovered cases, group by approvedAction)
        Map<String, Long> byIntervention = all.stream().filter(c -> c.getStatus() == RecoveryCaseStatus.RECOVERED)
                .collect(Collectors.groupingBy(c -> c.getApprovedAction() != null ? c.getApprovedAction() : "UNKNOWN", Collectors.counting()));
        // Recovery by failure category
        Map<String, Long> byFailureCategory = all.stream().filter(c -> c.getStatus() == RecoveryCaseStatus.RECOVERED)
                .collect(Collectors.groupingBy(c -> c.getFailureCategory() != null ? c.getFailureCategory() : (c.getFailureCode() != null ? c.getFailureCode() : "UNKNOWN"), Collectors.counting()));
        // Baseline vs RecoverFlow: we don't have baseline data here, so we return synthetic evaluation placeholder that frontend will label
        // For real data, we just return recovered vs at risk
        Map<String, Object> trend = Map.of(
                "labels", List.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
                "recovered", List.of(0, 0, 0, 0, 0, 0, recovered.longValue()) // placeholder, frontend will handle empty
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("revenueAtRisk", revenueAtRisk);
        result.put("recoveredRevenue", recovered);
        result.put("recoveryRate", recoveryRate);
        result.put("activeRecoveryCases", active);
        result.put("recoveriesToday", recoveredToday);
        result.put("escalatedCases", escalated);
        result.put("unknownCases", unknown);
        result.put("totalCases", total);
        result.put("byIntervention", byIntervention);
        result.put("byFailureCategory", byFailureCategory);
        result.put("trend", trend);
        result.put("source", total == 0 ? "SYNTHETIC_EVALUATION_EMPTY" : "REAL_DEMO");
        result.put("syntheticLabel", "SYNTHETIC EVALUATION where applicable – not real merchant revenue");
        return result;
    }
}
