package com.smartuniversity.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartuniversity.dto.WeatherChatResponse;
import com.smartuniversity.dto.WeatherResponse;
import com.smartuniversity.exception.TokenExhaustedException;
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

import org.springframework.beans.factory.annotation.Autowired;

@Service
public class GeminiChatService {

    @Value("${gemini.api.key}")
    private String apiKey;

    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";
    private static final int MAX_REQUESTS_PER_HOUR = 10;
    private static final long HOUR_IN_MILLIS = 60 * 60 * 1000;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Map<Long, List<Long>> rateLimitMap = new ConcurrentHashMap<>();

    @Autowired
    private TokenService tokenService;

    public GeminiChatService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public WeatherChatResponse chat(String message, Long userId, WeatherResponse weatherData) {
        // Check rate limit (10 requests/hour for weather - uses more expensive model)
        if (!checkRateLimit(userId)) {
            return WeatherChatResponse.error("Rate limit exceeded. Maximum " + MAX_REQUESTS_PER_HOUR + " requests per hour allowed for weather chat.");
        }

        // Check daily token availability
        if (userId != null && tokenService != null) {
            Long estimatedTokens = 1000L; // Weather queries use more tokens due to context
            if (!tokenService.hasEnoughTokens(userId, estimatedTokens)) {
                throw new TokenExhaustedException("Daily token limit reached. Your limit resets at midnight. Please try again tomorrow.");
            }
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

        prompt.append("CRITICAL RULES:\n");
        prompt.append("1. You do NOT have access to historical/past weather data (this requires a paid API subscription).\n");
        prompt.append("2. If asked about yesterday, last week, previous days, or any PAST weather, respond helpfully: ");
        prompt.append("\"🌦️ I don't have access to historical weather data for past dates. However, I can help you with today's current conditions or the forecast for the next 5 days! What would you like to know?\"\n");
        prompt.append("3. For questions about tomorrow or future days (up to 5 days), use the FORECAST DATA below.\n");
        prompt.append("4. Do NOT show current weather when asked about tomorrow - use tomorrow's forecast data.\n\n");

        prompt.append("=== WEATHER DATA FOR UNIVERSITY OF MORATUWA ===\n\n");
        prompt.append("CURRENT CONDITIONS (RIGHT NOW):\n");

        if (weatherData != null && weatherData.getCurrent() != null) {
            WeatherResponse.CurrentWeather current = weatherData.getCurrent();
            prompt.append("- Temperature: ").append(current.getTemperature()).append("°C\n");
            prompt.append("- Condition: ").append(current.getCondition()).append("\n");
            prompt.append("- Humidity: ").append(current.getHumidity()).append("%\n");
            prompt.append("- Wind Speed: ").append(current.getWindSpeed()).append(" km/h\n");
            prompt.append("- Feels Like: ").append(current.getFeelsLike()).append("°C\n");

            if (weatherData.getDaily() != null && !weatherData.getDaily().isEmpty()) {
                prompt.append("\nFORECAST DATA (FUTURE DAYS):\n");
                List<WeatherResponse.DailyForecast> dailyList = weatherData.getDaily();
                for (int i = 0; i < dailyList.size(); i++) {
                    WeatherResponse.DailyForecast day = dailyList.get(i);
                    String dayLabel = day.getDay();
                    // Add explicit labels for tomorrow and day after
                    if (i == 0) {
                        dayLabel = "TODAY (" + day.getDay() + ")";
                    } else if (i == 1) {
                        dayLabel = "TOMORROW (" + day.getDay() + ")";
                    } else if (i == 2) {
                        dayLabel = "DAY AFTER TOMORROW (" + day.getDay() + ")";
                    }
                    prompt.append("- ").append(dayLabel).append(": ")
                           .append(day.getCondition())
                           .append(", High: ").append(day.getHigh()).append("°C")
                           .append(", Low: ").append(day.getLow()).append("°C")
                           .append(", Rain chance: ").append(day.getPrecipitation()).append("%\n");
                }
            }
        }

        prompt.append("\n=== USER QUESTION ===\n");
        prompt.append(userMessage).append("\n\n");
        
        prompt.append("=== RESPONSE INSTRUCTIONS ===\n");
        prompt.append("- If question is about YESTERDAY or any PAST day: Apologize and explain you only have current and future data\n");
        prompt.append("- If question is about TOMORROW: Use the TOMORROW forecast data above, NOT current conditions\n");
        prompt.append("- If question is about TODAY or current weather: Use CURRENT CONDITIONS\n");
        prompt.append("- Include practical advice (umbrella, sunscreen, etc.) when relevant\n");
        prompt.append("- Use weather emojis to make responses friendly\n");
        prompt.append("- Keep responses concise (under 150 words)\n");

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
