package com.smartuniversity.controller;

import com.smartuniversity.dto.WeatherChatRequest;
import com.smartuniversity.dto.WeatherChatResponse;
import com.smartuniversity.dto.WeatherResponse;
import com.smartuniversity.exception.TokenExhaustedException;
import com.smartuniversity.service.GeminiChatService;
import com.smartuniversity.service.WeatherService;
import com.smartuniversity.util.AuthUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weather/chat")
public class WeatherChatController {

    private final GeminiChatService geminiChatService;
    private final WeatherService weatherService;
    private final AuthUtils authUtils;

    public WeatherChatController(GeminiChatService geminiChatService, WeatherService weatherService, AuthUtils authUtils) {
        this.geminiChatService = geminiChatService;
        this.weatherService = weatherService;
        this.authUtils = authUtils;
    }

    @PostMapping
    public ResponseEntity<WeatherChatResponse> chat(@RequestBody WeatherChatRequest request) {
        try {
            Long userId = authUtils.getCurrentUserId();
            if (userId == null) {
                return ResponseEntity.status(401)
                        .body(WeatherChatResponse.error("Authentication required"));
            }

            // Validate request
            if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(WeatherChatResponse.error("Message cannot be empty"));
            }

            if (request.getMessage().length() > GeminiChatService.MAX_MESSAGE_LENGTH) {
                return ResponseEntity.badRequest()
                        .body(WeatherChatResponse.error("Message too long. Maximum " + GeminiChatService.MAX_MESSAGE_LENGTH + " characters allowed."));
            }

            // Get current weather data to provide context to AI
            WeatherResponse weatherData = weatherService.getWeather();

            // Call Gemini AI service with JWT-derived userId
            WeatherChatResponse response = geminiChatService.chat(
                    request.getMessage(),
                    userId,
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
