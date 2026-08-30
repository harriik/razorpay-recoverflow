package com.recoverflow.evaluation;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class EvaluationControllerTest {

    @Autowired EvaluationService service;

    @Test
    void postRunAndGetAndAblation() {
        EvaluationRun run = service.run(12345L, 500, "v1", "v1");
        assertNotNull(run.getId());
        var fetched = service.getRun(run.getId()).orElseThrow();
        assertEquals(12345L, fetched.getSeed());
        assertEquals(500, fetched.getDatasetSize());
        assertNotNull(fetched.getBaselineAResult());
        assertNotNull(fetched.getBaselineBResult());
        assertNotNull(fetched.getRecoverflowResult());
        assertNotNull(fetched.getAblationSummary());

        var cached = service.getCachedResult(run.getId()).orElseThrow();
        assertEquals(500, cached.ablation().total());
    }

    @Test
    void reproducibilityViaSameSeed() {
        EvaluationRun r1 = service.run(99999L, 300, "v1", "v1");
        EvaluationRun r2 = service.run(99999L, 300, "v1", "v1");
        // Same seed must produce same recoveredRevenue
        assertEquals(r1.getBaselineAResult(), r2.getBaselineAResult());
        assertEquals(r1.getBaselineBResult(), r2.getBaselineBResult());
        assertEquals(r1.getRecoverflowResult(), r2.getRecoverflowResult());
    }

    @Test
    void atLeast3000CasesPerformance() {
        EvaluationRun run = service.run(777L, 3000, "v1", "v1");
        assertEquals(3000, run.getDatasetSize());
        var cached = service.getCachedResult(run.getId()).orElseThrow();
        assertEquals(3000, cached.dataset().size());
    }
}
