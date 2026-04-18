package com.outsafe.backend.controller;

import com.outsafe.backend.model.PredictionRequest;
import com.outsafe.backend.model.PredictionResponse;
import com.outsafe.backend.service.RfWeatherService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/predict")
@CrossOrigin
public class PredictionController {

    private final RfWeatherService rfWeatherService;

    public PredictionController(RfWeatherService rfWeatherService) {
        this.rfWeatherService = rfWeatherService;
    }

    @PostMapping("/24h")
    public PredictionResponse predict24h(@RequestBody PredictionRequest request) {
        return rfWeatherService.predict24h(request);
    }
}