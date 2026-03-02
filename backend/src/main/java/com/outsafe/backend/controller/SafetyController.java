package com.outsafe.backend.controller;


import com.outsafe.backend.model.SafetyRecommendRequest;
import com.outsafe.backend.model.SafetyRecommendResponse;
import com.outsafe.backend.service.SafetyService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * REST controller that exposes safety recommendation endpoints to the frontend.
 */
@RestController
@RequestMapping("/api/safety")
public class SafetyController {

    private final SafetyService safetyService;

    public SafetyController(SafetyService safetyService) {
        this.safetyService = safetyService;
    }

    /**
     * GET endpoint for quick testing in browser/Postman.
     * Example:
     * /api/safety/recommend?lat=39.9&lon=116.4&date=2026-03-01&years_back=5&timezone=auto
     */
    @GetMapping("/recommend")
    public SafetyRecommendResponse recommend(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(required = false) Double elevation,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "years_back", required = false) Integer yearsBack,
            @RequestParam(required = false) String timezone
    ) {
        SafetyRecommendRequest req = new SafetyRecommendRequest(lat, lon, elevation, date, yearsBack, timezone);
        return safetyService.recommend(req);
    }
}