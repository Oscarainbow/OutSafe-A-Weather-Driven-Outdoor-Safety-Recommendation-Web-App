package com.outsafe.backend.service;

import com.outsafe.backend.model.SafetyRecommendRequest;
import com.outsafe.backend.model.SafetyRecommendResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Service layer responsible for computing safety recommendations.
 * This is where the core business logic and percentile-based evaluation will live.
 */
@Service
public class SafetyService {

    /**
     * Generate a safety recommendation based on input parameters.
     * Currently returns mock data. Will later integrate Open-Meteo API and real percentile calculations.
     */
    public SafetyRecommendResponse recommend(SafetyRecommendRequest request) {

        // Apply default values if optional parameters are missing
        LocalDate date = (request.date() == null) ? LocalDate.now() : request.date();
        int yearsBack = (request.years_back() == null || request.years_back() <= 0)
                ? 5
                : request.years_back();
        String timezone = (request.timezone() == null || request.timezone().isBlank())
                ? "auto"
                : request.timezone();

        // Mock percentile results (to be replaced by real historical comparison logic)
        Map<String, Integer> percentiles = Map.of(
                "wind", 92,
                "rain", 15,
                "cold", 85
        );

        // Simple rule-based classification for demonstration purposes
        String level;
        if (percentiles.get("wind") >= 85 || percentiles.get("cold") >= 85) {
            level = "caution";
        } else {
            level = "recommended";
        }

        // Mock overall score (0–100 scale)
        double score = 78.0;

        // Top contributing risk factors
        List<SafetyRecommendResponse.ReasonItem> reasons = List.of(
                new SafetyRecommendResponse.ReasonItem("wind", "High wind percentile", percentiles.get("wind")),
                new SafetyRecommendResponse.ReasonItem("cold", "Cold percentile", percentiles.get("cold"))
        );

        // Human-readable comparison summary
        String comparisonText = "Compared with the past " + yearsBack +
                " years on the same calendar day, wind conditions are unusually high.";

        // Additional metadata for debugging or transparency
        Map<String, Object> meta = Map.of(
                "date", date.toString(),
                "timezone", timezone,
                "data_source", "mock"
        );

        return new SafetyRecommendResponse(
                level,
                score,
                percentiles,
                yearsBack,
                reasons,
                comparisonText,
                meta
        );
    }
}