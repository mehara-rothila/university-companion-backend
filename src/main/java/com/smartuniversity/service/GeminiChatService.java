package com.smartuniversity.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartuniversity.dto.ChatbotRequest;
import com.smartuniversity.dto.ChatbotResponse;
import com.smartuniversity.dto.WeatherChatResponse;
import com.smartuniversity.dto.WeatherResponse;
import com.smartuniversity.exception.TokenExhaustedException;
import com.smartuniversity.model.IndexedDocument;
import com.smartuniversity.model.TokenTransaction;
import com.smartuniversity.service.rag.DocumentIndexService;
import com.smartuniversity.service.rag.DocumentRetriever;
import com.smartuniversity.service.rag.RetrievedChunk;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Chat service backed by Google's Gemini API (native generateContent endpoint).
 *
 * <p>This mirrors the previous Kimi (Moonshot) provider so the controllers could switch
 * providers without any other changes. It keeps the same public surface — {@code chat(ChatbotRequest)},
 * {@code chat(String, Long, WeatherResponse)} and {@code MAX_MESSAGE_LENGTH} — plus the
 * same rate limiting, token accounting, image (vision) and PDF handling.</p>
 *
 * <p>The Gemini native API is used (not the OpenAI-compatible endpoint) because the new
 * {@code AQ.}-prefixed API keys currently fail on the OpenAI-compatible endpoint.</p>
 *
 * <p>Questions about attached PDFs are answered from the passages that best match the question
 * (retrieval-augmented generation, see {@link DocumentRetriever}) rather than from each PDF's
 * full text.</p>
 */
@Service
public class GeminiChatService {

    private static final Logger log = LoggerFactory.getLogger(GeminiChatService.class);

    private static final String AI_TEMPORARILY_UNAVAILABLE_MESSAGE =
        "Athena is temporarily busy because the AI service is under high demand. Please try again in a few minutes.";

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

    /** When false, PDFs are sent whole (cut at 50,000 characters each) instead of as retrieved excerpts. */
    @Value("${rag.enabled:true}")
    private boolean ragEnabled;

    private static final int MAX_REQUESTS_PER_HOUR = 50;
    private static final int MAX_WEATHER_REQUESTS_PER_HOUR = 10;
    private static final int MAX_ANONYMOUS_REQUESTS_PER_HOUR = 5;
    private static final long HOUR_IN_MILLIS = 60 * 60 * 1000;
    public static final int MAX_MESSAGE_LENGTH = 500;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final S3Service s3Service;
    private final DocumentIndexService documentIndexService;
    private final DocumentRetriever documentRetriever;
    private final Map<Long, List<Long>> rateLimitMap = new ConcurrentHashMap<>();
    private final Map<Long, List<Long>> weatherRateLimitMap = new ConcurrentHashMap<>();
    private final Map<String, List<Long>> anonymousRateLimitMap = new ConcurrentHashMap<>();

    @Autowired
    private TokenService tokenService;

    public GeminiChatService(RestTemplate restTemplate, ObjectMapper objectMapper, S3Service s3Service,
                             DocumentIndexService documentIndexService, DocumentRetriever documentRetriever) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.s3Service = s3Service;
        this.documentIndexService = documentIndexService;
        this.documentRetriever = documentRetriever;
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
        } catch (RestClientResponseException e) {
            System.err.println("Gemini API request failed: " + e.getStatusCode() + " " + e.getStatusText());
            return ChatbotResponse.error(toFriendlyProviderError(e));
        } catch (Exception e) {
            e.printStackTrace();
            return ChatbotResponse.error("Athena could not complete that request. Please try again.");
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

        } catch (RestClientResponseException e) {
            System.err.println("Gemini weather request failed: " + e.getStatusCode() + " " + e.getStatusText());
            return WeatherChatResponse.error(toFriendlyProviderError(e));
        } catch (Exception e) {
            e.printStackTrace();
            return WeatherChatResponse.error("Athena could not complete that weather request. Please try again.");
        }
    }

    // ==================== Message Building ====================

    private List<Map<String, Object>> buildContents(ChatbotRequest request) throws Exception {
        List<Map<String, Object>> parts = new ArrayList<>();

        // Add text (+ the parts of any attached PDFs that matter for this question)
        StringBuilder textBuilder = new StringBuilder(request.getMessage());

        if (request.getPdfUrls() != null && !request.getPdfUrls().isEmpty()) {
            textBuilder.append(ragEnabled
                    ? buildPdfExcerpts(request.getMessage(), request.getPdfUrls())
                    : buildFullPdfText(request.getPdfUrls()));
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

    /**
     * The passages of the attached PDFs that best match the question, labelled with page numbers.
     * PDFs are indexed the first time they are asked about. If indexing or retrieval fails (for
     * example the embeddings API is down), the full PDF text is sent instead so the chat still works.
     */
    private String buildPdfExcerpts(String question, List<String> pdfUrls) {
        try {
            List<IndexedDocument> documents = new ArrayList<>();
            for (String pdfUrl : pdfUrls) {
                String s3Key = s3Service.extractFileNameFromUrl(pdfUrl);
                if (s3Key == null) {
                    throw new IllegalArgumentException("Only files uploaded to our storage are supported");
                }
                documents.add(documentIndexService.indexIfNeeded(s3Key));
            }
            List<RetrievedChunk> excerpts = documentRetriever.retrieve(question, documents);
            log.info("PDF question: sending {} excerpts from {} PDF(s)", excerpts.size(), documents.size());
            return formatExcerpts(excerpts, documents.size());
        } catch (Exception e) {
            log.warn("PDF retrieval failed, sending the full PDF text instead", e);
            return buildFullPdfText(pdfUrls);
        }
    }

    private static String formatExcerpts(List<RetrievedChunk> excerpts, int pdfCount) {
        if (excerpts.isEmpty()) {
            return "\n\n--- PDF note ---\nThe attached PDF has no extractable text (it may be a scanned image), "
                    + "so its contents can't be read. Tell the student this.\n--- End of note ---\n";
        }

        boolean severalPdfs = pdfCount > 1;
        StringBuilder text = new StringBuilder("\n\n--- PDF excerpts ---\n")
                .append("These are the parts of the student's ").append(severalPdfs ? "PDFs" : "PDF")
                .append(" most relevant to the question. Answer from them and cite the page for each point, like ")
                .append(severalPdfs ? "(PDF 1, p. 4)" : "(p. 4)")
                .append(". If they don't cover the question, say so instead of guessing.\n");
        for (RetrievedChunk excerpt : excerpts) {
            text.append("\n[")
                    .append(severalPdfs ? "PDF " + excerpt.documentNumber() + ", " : "")
                    .append("p. ").append(excerpt.pageNumber()).append("]\n")
                    .append(excerpt.text()).append('\n');
        }
        return text.append("--- End of excerpts ---\n").toString();
    }

    /** The whole text of every PDF, cut at 50,000 characters each: used when RAG is off or fails. */
    private String buildFullPdfText(List<String> pdfUrls) {
        StringBuilder text = new StringBuilder();
        for (String pdfUrl : pdfUrls) {
            try {
                String pdfText = extractTextFromPdf(pdfUrl);
                if (pdfText != null && !pdfText.trim().isEmpty()) {
                    text.append("\n\n--- PDF Content ---\n").append(pdfText).append("\n--- End of PDF ---\n");
                }
            } catch (Exception e) {
                System.err.println("Failed to process PDF: " + e.getMessage());
            }
        }
        return text.toString();
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

    private String toFriendlyProviderError(RestClientResponseException e) {
        int statusCode = e.getStatusCode().value();
        if (statusCode == 503 || statusCode == 429) {
            return AI_TEMPORARILY_UNAVAILABLE_MESSAGE;
        }
        if (statusCode == 401 || statusCode == 403) {
            return "Athena is not configured correctly right now. Please contact an administrator.";
        }
        return "Athena could not reach the AI service. Please try again later.";
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
- When students upload PDFs, you receive the passages most relevant to their question, labelled with page numbers; answer from them and cite the pages
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
