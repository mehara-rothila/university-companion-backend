package com.smartuniversity.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartuniversity.dto.WeatherChatResponse;
import com.smartuniversity.dto.WeatherResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class GeminiChatService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-pro:generateContent";
    private static final int MAX_REQUESTS_PER_HOUR = 10;
    private static final long HOUR_IN_MILLIS = 60 * 60 * 1000;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Map<Long, List<Long>> rateLimitMap = new ConcurrentHashMap<>();

    public GeminiChatService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public WeatherChatResponse chat(String message, Long userId, WeatherResponse weatherData) {
        // Check rate limit
        if (!checkRateLimit(userId)) {
            return WeatherChatResponse.error("Rate limit exceeded. Maximum " + MAX_REQUESTS_PER_HOUR + " requests per hour allowed.");
        }

        try {
            // Build the system prompt with weather context
            String systemPrompt = buildSystemPrompt(message, weatherData);

            // Call Gemini API
            String response = callGeminiAPI(systemPrompt);

            // Record request for rate limiting
            recordRequest(userId);

            return WeatherChatResponse.success(response);

        } catch (Exception e) {
            return WeatherChatResponse.error("Failed to get response from AI: " + e.getMessage());
        }
    }

    private boolean checkRateLimit(Long userId) {
        if (userId == null) {
            return true; // Allow anonymous requests (can be changed)
        }

        long currentTime = Instant.now().toEpochMilli();
        List<Long> requests = rateLimitMap.getOrDefault(userId, new ArrayList<>());

        // Remove requests older than 1 hour
        requests = requests.stream()
                .filter(timestamp -> currentTime - timestamp < HOUR_IN_MILLIS)
                .collect(Collectors.toList());

        rateLimitMap.put(userId, requests);

        return requests.size() < MAX_REQUESTS_PER_HOUR;
    }

    private void recordRequest(Long userId) {
        if (userId == null) {
            return;
        }

        long currentTime = Instant.now().toEpochMilli();
        List<Long> requests = rateLimitMap.getOrDefault(userId, new ArrayList<>());
        requests.add(currentTime);
        rateLimitMap.put(userId, requests);
    }

    private String buildSystemPrompt(String userMessage, WeatherResponse weatherData) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are a helpful weather assistant for the University of Moratuwa campus.\n\n");
        prompt.append("Current Weather Data:\n");

        if (weatherData != null && weatherData.getCurrent() != null) {
            WeatherResponse.CurrentWeather current = weatherData.getCurrent();
            prompt.append("- Temperature: ").append(current.getTemperature()).append("°C\n");
            prompt.append("- Condition: ").append(current.getCondition()).append("\n");
            prompt.append("- Humidity: ").append(current.getHumidity()).append("%\n");
            prompt.append("- Wind Speed: ").append(current.getWindSpeed()).append(" km/h\n");
            prompt.append("- Feels Like: ").append(current.getFeelsLike()).append("°C\n");

            if (weatherData.getDaily() != null && !weatherData.getDaily().isEmpty()) {
                prompt.append("\nForecast:\n");
                weatherData.getDaily().stream().limit(3).forEach(day -> {
                    prompt.append("- ").append(day.getDay()).append(": ")
                           .append(day.getCondition())
                           .append(", High: ").append(day.getHigh()).append("°C")
                           .append(", Low: ").append(day.getLow()).append("°C")
                           .append(", Rain: ").append(day.getPrecipitation()).append("%\n");
                });
            }
        }

        prompt.append("\nUser Question: ").append(userMessage).append("\n\n");
        prompt.append("Please provide a helpful, concise response about the weather at University of Moratuwa. ");
        prompt.append("Include practical advice for students (e.g., bring an umbrella, stay hydrated, etc.) when relevant. ");
        prompt.append("Keep responses under 150 words.");

        return prompt.toString();
    }

    private String callGeminiAPI(String prompt) throws Exception {
        String url = GEMINI_API_URL + "?key=" + apiKey;

        // Build request body
        Map<String, Object> requestBody = new HashMap<>();

        List<Map<String, Object>> contents = new ArrayList<>();
        Map<String, Object> content = new HashMap<>();

        List<Map<String, String>> parts = new ArrayList<>();
        Map<String, String> part = new HashMap<>();
        part.put("text", prompt);
        parts.add(part);

        content.put("parts", parts);
        contents.add(content);

        requestBody.put("contents", contents);

        // Generation config
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.7);
        generationConfig.put("topK", 40);
        generationConfig.put("topP", 0.95);
        generationConfig.put("maxOutputTokens", 1024);

        requestBody.put("generationConfig", generationConfig);

        // Set headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        // Make API call
        String response = restTemplate.postForObject(url, entity, String.class);

        // Parse response
        JsonNode jsonResponse = objectMapper.readTree(response);
        JsonNode candidates = jsonResponse.get("candidates");

        if (candidates != null && candidates.isArray() && candidates.size() > 0) {
            JsonNode firstCandidate = candidates.get(0);
            JsonNode contentNode = firstCandidate.get("content");

            if (contentNode != null) {
                JsonNode partsNode = contentNode.get("parts");
                if (partsNode != null && partsNode.isArray() && partsNode.size() > 0) {
                    JsonNode textNode = partsNode.get(0).get("text");
                    if (textNode != null) {
                        return textNode.asText();
                    }
                }
            }
        }

        throw new Exception("No valid response from Gemini API");
    }
}
