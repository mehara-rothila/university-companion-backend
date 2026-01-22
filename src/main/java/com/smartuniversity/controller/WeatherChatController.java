package com.smartuniversity.controller;

import com.smartuniversity.dto.WeatherChatRequest;
import com.smartuniversity.dto.WeatherChatResponse;
import com.smartuniversity.dto.WeatherResponse;
import com.smartuniversity.exception.TokenExhaustedException;
import com.smartuniversity.service.GeminiChatService;
import com.smartuniversity.service.WeatherService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/weather/chat")
@CrossOrigin(origins = {"http://localhost:3000", "https://*.netlify.app"})
public class WeatherChatController {

    private final GeminiChatService geminiChatService;
    private final WeatherService weatherService;

    public WeatherChatController(GeminiChatService geminiChatService, WeatherService weatherService) {
        this.geminiChatService = geminiChatService;
        this.weatherService = weatherService;
    }

    @PostMapping
    public ResponseEntity<WeatherChatResponse> chat(@RequestBody WeatherChatRequest request) {
        try {
            // Validate request
            if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(WeatherChatResponse.error("Message cannot be empty"));
            }

            // Get current weather data to provide context to AI
            WeatherResponse weatherData = weatherService.getWeather();

            // Call Gemini AI service
            WeatherChatResponse response = geminiChatService.chat(
                    request.getMessage(),
                    request.getUserId(),
                    weatherData
            );

            if (response.isSuccess()) {
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(response);
            }

        } catch (TokenExhaustedException e) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(WeatherChatResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(WeatherChatResponse.error("Internal server error: " + e.getMessage()));
        }
    }
}
