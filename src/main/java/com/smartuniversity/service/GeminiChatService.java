package com.smartuniversity.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartuniversity.dto.ChatbotRequest;
import com.smartuniversity.dto.ChatbotResponse;
import com.smartuniversity.dto.WeatherChatResponse;
import com.smartuniversity.dto.WeatherResponse;
import com.smartuniversity.exception.TokenExhaustedException;
import com.smartuniversity.model.TokenTransaction;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Chat service backed by Google's Gemini API (native generateContent endpoint).
 *
 * <p>This mirrors {@link KimiChatService} so the controllers can switch providers
 * without any other changes. It keeps the same public surface — {@code chat(ChatbotRequest)},
 * {@code chat(String, Long, WeatherResponse)} and {@code MAX_MESSAGE_LENGTH} — plus the
 * same rate limiting, token accounting, image (vision) and PDF handling.</p>
 *
 * <p>The Gemini native API is used (not the OpenAI-compatible endpoint) because the new
 * {@code AQ.}-prefixed API keys currently fail on the OpenAI-compatible endpoint.</p>
 */
@Service
public class GeminiChatService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.base-url:https://generativelanguage.googleapis.com/v1beta}")
    private String baseUrl;

    @Value("${gemini.api.model:gemini-3.5-flash}")
    private String model;

    /**
     * Thinking budget passed to the model. 0 disables thinking (faster/cheaper — good for a
     * chatbot) and is valid for the flash tier. A negative value omits the field entirely so
     * the model decides (use this for the *-pro models, which require thinking to be enabled).
     */
    @Value("${gemini.api.thinking-budget:0}")
    private int thinkingBudget;

    private static final int MAX_REQUESTS_PER_HOUR = 50;
    private static final int MAX_WEATHER_REQUESTS_PER_HOUR = 10;
    private static final int MAX_ANONYMOUS_REQUESTS_PER_HOUR = 5;
    private static final long HOUR_IN_MILLIS = 60 * 60 * 1000;
    public static final int MAX_MESSAGE_LENGTH = 500;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final S3Service s3Service;
    private final Map<Long, List<Long>> rateLimitMap = new ConcurrentHashMap<>();
    private final Map<Long, List<Long>> weatherRateLimitMap = new ConcurrentHashMap<>();
    private final Map<String, List<Long>> anonymousRateLimitMap = new ConcurrentHashMap<>();

    @Autowired
    private TokenService tokenService;

    public GeminiChatService(RestTemplate restTemplate, ObjectMapper objectMapper, S3Service s3Service) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.s3Service = s3Service;
    }

    // ==================== General Chat ====================

    public ChatbotResponse chat(ChatbotRequest request) {
        if (!checkRateLimit(request.getUserId(), rateLimitMap, MAX_REQUESTS_PER_HOUR)) {
            return ChatbotResponse.error("Rate limit exceeded. Maximum " + MAX_REQUESTS_PER_HOUR + " requests per hour allowed.");
        }

        if (request.getUserId() != null && tokenService != null) {
            Long estimatedTokens = 500L;
            if (!tokenService.hasEnoughTokens(request.getUserId(), estimatedTokens)) {
                throw new TokenExhaustedException("Daily token limit reached. Your limit resets at midnight. Please try again tomorrow.");
            }
        }

        try {
            String systemPrompt = buildSystemPrompt();
            List<Map<String, Object>> contents = buildContents(request);
            GeminiResponse geminiResponse = callGeminiAPI(systemPrompt, contents, 0.7, 8192);

            recordRequest(request.getUserId(), rateLimitMap);

            if (request.getUserId() != null && tokenService != null) {
                Integer inputTokens = geminiResponse.inputTokens != null ? geminiResponse.inputTokens : 0;
                Integer outputTokens = geminiResponse.outputTokens != null ? geminiResponse.outputTokens : 0;
                tokenService.consumeTokensWithDetails(request.getUserId(), inputTokens, outputTokens,
                    TokenTransaction.TransactionType.CHAT,
                    "Chat message with " + (request.getImageUrls() != null ? request.getImageUrls().size() : 0) +
                    " images and " + (request.getPdfUrls() != null ? request.getPdfUrls().size() : 0) + " PDFs");
            }

            ChatbotResponse response = ChatbotResponse.success(geminiResponse.text);
            response.setInputTokens(geminiResponse.inputTokens);
            response.setOutputTokens(geminiResponse.outputTokens);
            response.setTokensUsed(geminiResponse.getTotalTokens());
            return response;

        } catch (TokenExhaustedException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            return ChatbotResponse.error("Failed to get response from AI: " + e.getMessage());
        }
    }

    // ==================== Weather Chat ====================

    public WeatherChatResponse chat(String message, Long userId, WeatherResponse weatherData) {
        if (!checkRateLimit(userId, weatherRateLimitMap, MAX_WEATHER_REQUESTS_PER_HOUR)) {
            return WeatherChatResponse.error("Rate limit exceeded. Maximum " + MAX_WEATHER_REQUESTS_PER_HOUR + " requests per hour allowed for weather chat.");
        }

        if (userId != null && tokenService != null) {
            Long estimatedTokens = 1000L;
            if (!tokenService.hasEnoughTokens(userId, estimatedTokens)) {
                throw new TokenExhaustedException("Daily token limit reached. Your limit resets at midnight. Please try again tomorrow.");
            }
        }

        try {
            String systemPrompt = buildWeatherSystemPrompt(weatherData);
            List<Map<String, Object>> contents = new ArrayList<>();
            contents.add(Map.of("role", "user", "parts", List.of(Map.of("text", message))));

            GeminiResponse geminiResponse = callGeminiAPI(systemPrompt, contents, 0.7, 1024);

            recordRequest(userId, weatherRateLimitMap);

            if (userId != null && tokenService != null) {
                Long estimatedTokens = 1000L;
                tokenService.consumeTokens(userId, estimatedTokens,
                    TokenTransaction.TransactionType.CHAT, "Weather chat query");
            }

            return WeatherChatResponse.success(geminiResponse.text);

        } catch (Exception e) {
            return WeatherChatResponse.error("Failed to get response from AI: " + e.getMessage());
        }
    }

    // ==================== Message Building ====================

    private List<Map<String, Object>> buildContents(ChatbotRequest request) throws Exception {
        List<Map<String, Object>> parts = new ArrayList<>();

        // Add text (+ extracted PDF text)
        StringBuilder textBuilder = new StringBuilder(request.getMessage());

        if (request.getPdfUrls() != null && !request.getPdfUrls().isEmpty()) {
            for (String pdfUrl : request.getPdfUrls()) {
                try {
                    String pdfText = extractTextFromPdf(pdfUrl);
                    if (pdfText != null && !pdfText.trim().isEmpty()) {
                        textBuilder.append("\n\n--- PDF Content ---\n").append(pdfText).append("\n--- End of PDF ---\n");
                    }
                } catch (Exception e) {
                    System.err.println("Failed to process PDF: " + e.getMessage());
                }
            }
        }

        parts.add(Map.of("text", textBuilder.toString()));

        // Process images (base64 inline data for vision)
        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            for (String imageUrl : request.getImageUrls()) {
                try {
                    String base64Image = processImageToBase64(imageUrl);
                    String mimeType = "image/" + getImageFormat(imageUrl);
                    parts.add(Map.of(
                        "inlineData", Map.of("mimeType", mimeType, "data", base64Image)
                    ));
                } catch (Exception e) {
                    System.err.println("Failed to process image: " + e.getMessage());
                }
            }
        }

        List<Map<String, Object>> contents = new ArrayList<>();
        contents.add(Map.of("role", "user", "parts", parts));
        return contents;
    }

    // ==================== API Call ====================

    private GeminiResponse callGeminiAPI(String systemPrompt, List<Map<String, Object>> contents,
                                         double temperature, int maxOutputTokens) throws Exception {
        String url = baseUrl + "/models/" + model + ":generateContent";

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", temperature);
        generationConfig.put("maxOutputTokens", maxOutputTokens);
        if (thinkingBudget >= 0) {
            generationConfig.put("thinkingConfig", Map.of("thinkingBudget", thinkingBudget));
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))));
        requestBody.put("contents", contents);
        requestBody.put("generationConfig", generationConfig);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        String response = restTemplate.postForObject(url, entity, String.class);

        JsonNode jsonResponse = objectMapper.readTree(response);

        Integer inputTokens = null;
        Integer outputTokens = null;
        JsonNode usage = jsonResponse.get("usageMetadata");
        if (usage != null) {
            inputTokens = usage.has("promptTokenCount") ? usage.get("promptTokenCount").asInt() : null;
            outputTokens = usage.has("candidatesTokenCount") ? usage.get("candidatesTokenCount").asInt() : null;
        }

        JsonNode candidates = jsonResponse.get("candidates");
        if (candidates != null && candidates.isArray() && candidates.size() > 0) {
            JsonNode parts = candidates.get(0).path("content").path("parts");
            if (parts.isArray()) {
                StringBuilder text = new StringBuilder();
                for (JsonNode part : parts) {
                    // Skip "thought" parts — keep only the visible answer text
                    if (part.path("thought").asBoolean(false)) {
                        continue;
                    }
                    if (part.has("text")) {
                        text.append(part.get("text").asText());
                    }
                }
                if (text.length() > 0) {
                    return new GeminiResponse(text.toString(), inputTokens, outputTokens);
                }
            }

            // No usable text — surface the finish reason to aid debugging (e.g. SAFETY, MAX_TOKENS)
            String finishReason = candidates.get(0).path("finishReason").asText("");
            if (!finishReason.isEmpty()) {
                throw new Exception("Gemini returned no text (finishReason: " + finishReason + ")");
            }
        }

        throw new Exception("No valid response from Gemini API");
    }

    // ==================== Helpers ====================

    private String processImageToBase64(String imageUrl) throws Exception {
        String s3Key = s3Service.extractFileNameFromUrl(imageUrl);
        byte[] imageBytes;
        if (s3Key != null) {
            imageBytes = s3Service.getFileBytes(s3Key);
        } else {
            throw new IllegalArgumentException("Only files uploaded to our storage are supported");
        }
        return Base64.getEncoder().encodeToString(imageBytes);
    }

    private String extractTextFromPdf(String pdfUrl) throws Exception {
        String s3Key = s3Service.extractFileNameFromUrl(pdfUrl);
        byte[] pdfBytes;
        if (s3Key != null) {
            pdfBytes = s3Service.getFileBytes(s3Key);
        } else {
            throw new IllegalArgumentException("Only files uploaded to our storage are supported");
        }

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            if (text.length() > 50000) {
                text = text.substring(0, 50000) + "\n\n[Content truncated due to length]";
            }
            return text;
        }
    }

    private String getImageFormat(String url) {
        String lower = url.toLowerCase();
        if (lower.endsWith(".png")) return "png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "jpeg";
        if (lower.endsWith(".gif")) return "gif";
        if (lower.endsWith(".webp")) return "webp";
        return "jpeg";
    }

    // ==================== Prompts ====================

    private String buildSystemPrompt() {
        return """
You are Athena, the AI assistant for University of Moratuwa. You are a knowledgeable, friendly, and helpful university companion designed to assist students with their academic and campus life.

🏛️ **YOUR ROLE:**
- Help students with academic planning, assignments, and study schedules
- Provide information about campus services, buildings, and facilities
- Assist with navigation, dining options, and weather updates
- Support student wellness and mental health
- Help with Lost & Found, Financial Aid, and Library services
- Answer questions about uploaded documents, images, and files

🎯 **RESPONSE GUIDELINES:**
- Be warm, empathetic, and supportive
- Provide specific, actionable advice
- When analyzing images or PDFs, be thorough and detailed
- Reference actual university services and locations
- Always prioritize student safety and wellbeing
- Keep responses clear and concise

📄 **FILE HANDLING:**
- When students upload images, describe what you see and provide relevant insights
- When students upload PDFs, analyze the content and answer questions about it
- Extract key information and help students understand their documents
- Offer to help with assignments, notes, forms, or any academic documents

Remember: You represent University of Moratuwa and should embody the values of academic excellence, student support, and community care.
""";
    }

    private String buildWeatherSystemPrompt(WeatherResponse weatherData) {
        var current = weatherData.getCurrent();
        String weatherInfo = """
Current Weather in Moratuwa, Sri Lanka:
- Condition: %s
- Temperature: %d°C
- Feels Like: %d°C
- Humidity: %d%%
- Wind Speed: %d km/h
- Pressure: %d hPa

You are Athena Weather, a helpful weather assistant for University of Moratuwa students.
Provide weather-related advice, suggest appropriate clothing, and give campus-specific tips.
Be concise but helpful.
""".formatted(
                current.getCondition(),
                current.getTemperature(),
                current.getFeelsLike(),
                current.getHumidity(),
                current.getWindSpeed(),
                current.getPressure()
        );
        return weatherInfo;
    }

    // ==================== Rate Limiting ====================

    private boolean checkRateLimit(Long userId, Map<Long, List<Long>> limitMap, int maxRequests) {
        if (userId == null) {
            long currentTime = Instant.now().toEpochMilli();
            String anonKey = "anonymous";
            List<Long> requests = anonymousRateLimitMap.getOrDefault(anonKey, new ArrayList<>());
            requests = requests.stream()
                    .filter(timestamp -> currentTime - timestamp < HOUR_IN_MILLIS)
                    .collect(Collectors.toList());
            anonymousRateLimitMap.put(anonKey, requests);
            return requests.size() < MAX_ANONYMOUS_REQUESTS_PER_HOUR;
        }

        long currentTime = Instant.now().toEpochMilli();
        List<Long> requests = limitMap.getOrDefault(userId, new ArrayList<>());
        requests = requests.stream()
                .filter(timestamp -> currentTime - timestamp < HOUR_IN_MILLIS)
                .collect(Collectors.toList());
        limitMap.put(userId, requests);
        return requests.size() < maxRequests;
    }

    private void recordRequest(Long userId, Map<Long, List<Long>> limitMap) {
        if (userId == null) return;
        limitMap.computeIfAbsent(userId, k -> new ArrayList<>()).add(Instant.now().toEpochMilli());
    }

    // ==================== Response Class ====================

    private static class GeminiResponse {
        public String text;
        public Integer inputTokens;
        public Integer outputTokens;

        public GeminiResponse(String text, Integer inputTokens, Integer outputTokens) {
            this.text = text;
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
        }

        public Integer getTotalTokens() {
            return (inputTokens != null ? inputTokens : 0) + (outputTokens != null ? outputTokens : 0);
        }
    }
}
