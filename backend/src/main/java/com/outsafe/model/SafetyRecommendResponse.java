package com.outsafe.backend.model;

import java.util.List;
import java.util.Map;

public record SafetyRecommendResponse(
        String level,
        double score,
        Map<String, Integer> percentiles,
        int years_back,
        List<ReasonItem> reasons,
        String comparison_text,
        Map<String, Object> meta
) {
    public record ReasonItem(String key, String label, int pct) {}
}