package com.outsafe.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.outsafe.backend.model.SafetyRecommendRequest;
import com.outsafe.backend.model.SafetyRecommendResponse;

class SafetyServiceTests {

    @Test
    void recommend_returnsExpectedDeterministicMockResult() {
        SafetyService service = new SafetyService();

        SafetyRecommendRequest req = new SafetyRecommendRequest(
                40.0,
                -74.0,
                null,
                LocalDate.of(2026, 3, 3),
                5,
                "auto"
        );

        SafetyRecommendResponse res = service.recommend(req);

        assertEquals("not_recommended", res.level());
        assertEquals(21.0, res.score(), 1e-9);
        assertEquals(5, res.years_back());

        assertEquals(Map.of(
                "wind", 91,
                "rain", 82,
                "cold", 60
        ), res.percentiles());

        assertEquals(2, res.reasons().size());
        assertEquals("wind", res.reasons().get(0).key());
        assertEquals("High wind percentile", res.reasons().get(0).label());
        assertEquals(91, res.reasons().get(0).pct());
        assertEquals("rain", res.reasons().get(1).key());
        assertEquals("High precipitation percentile", res.reasons().get(1).label());
        assertEquals(82, res.reasons().get(1).pct());

        assertEquals(
                "Compared with the past 5 years around 2026-03-03, high wind percentile is elevated (percentile 91).",
                res.comparison_text()
        );

        assertNotNull(res.meta());
        assertEquals("2026-03-03", res.meta().get("date"));
        assertEquals("auto", res.meta().get("timezone"));
        assertEquals("mock", res.meta().get("data_source"));
        assertEquals(79.0, (double) res.meta().get("risk_score"), 1e-9);

        @SuppressWarnings("unchecked")
        Map<String, Double> weights = (Map<String, Double>) res.meta().get("weights");
        assertEquals(Map.of("wind", 0.40, "rain", 0.30, "cold", 0.30), weights);

        @SuppressWarnings("unchecked")
        Map<String, Double> observed = (Map<String, Double>) res.meta().get("observed_raw");
        assertEquals(Map.of("wind", 12.5, "rain", 6.0, "cold", -3.0), observed);
    }

    @Test
    void recommend_appliesDefaultsForOptionalFields() {
        SafetyService service = new SafetyService();

        SafetyRecommendRequest req = new SafetyRecommendRequest(
                0.0,
                0.0,
                null,
                null,
                null,
                null
        );

        SafetyRecommendResponse res = service.recommend(req);

        assertNotNull(res);
        assertEquals(5, res.years_back());
        assertNotNull(res.percentiles());
        assertEquals(List.of("wind", "rain", "cold").stream().sorted().toList(),
                res.percentiles().keySet().stream().sorted().toList());
        assertTrue(res.score() >= 0.0 && res.score() <= 100.0);

        assertNotNull(res.meta());
        assertEquals("auto", res.meta().get("timezone"));
        assertNotNull(res.meta().get("date"));
    }
}

