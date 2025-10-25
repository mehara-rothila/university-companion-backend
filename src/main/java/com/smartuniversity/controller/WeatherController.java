package com.smartuniversity.controller;

import com.smartuniversity.dto.WeatherResponse;
import com.smartuniversity.service.WeatherService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/weather")
@CrossOrigin(origins = {"http://localhost:3000", "https://*.netlify.app"})
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping("/current")
    public ResponseEntity<WeatherResponse> getCurrentWeather() {
        try {
            WeatherResponse weather = weatherService.getWeather();
            return ResponseEntity.ok(weather);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Weather service is running");
    }
}
