package com.smartuniversity.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartuniversity.dto.ChatbotRequest;
import com.smartuniversity.dto.ChatbotResponse;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class GeneralChatbotService {

    @Value("${gemini.api.key}")
    private String apiKey;

    // Using Gemini 2.5 Pro for superior performance
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-pro:generateContent";
    private static final int MAX_REQUESTS_PER_HOUR = 50;
    private static final long HOUR_IN_MILLIS = 60 * 60 * 1000;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final Map<Long, List<Long>> rateLimitMap = new ConcurrentHashMap<>();

    public GeneralChatbotService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Main chat method that handles text, images, and PDFs
     */
    public ChatbotResponse chat(ChatbotRequest request) {
        // Check rate limit
        if (!checkRateLimit(request.getUserId())) {
            return ChatbotResponse.error("Rate limit exceeded. Maximum " + MAX_REQUESTS_PER_HOUR + " requests per hour allowed.");
        }

        try {
            // Build the prompt with context
            String systemPrompt = buildSystemPrompt();
            String userPrompt = request.getMessage();

            // Process attachments if any
            List<Map<String, Object>> parts = new ArrayList<>();

            // Add text part
            Map<String, Object> textPart = new HashMap<>();
            textPart.put("text", userPrompt);
            parts.add(textPart);

            // Process images
            if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
                for (String imageUrl : request.getImageUrls()) {
                    try {
                        Map<String, Object> imagePart = processImage(imageUrl);
                        if (imagePart != null) {
                            parts.add(imagePart);
                        }
                    } catch (Exception e) {
                        System.err.println("Error processing image: " + e.getMessage());
                    }
                }
            }

            // Process PDFs (extract text and add to context)
            if (request.getPdfUrls() != null && !request.getPdfUrls().isEmpty()) {
                for (String pdfUrl : request.getPdfUrls()) {
                    try {
                        String pdfText = extractTextFromPdf(pdfUrl);
                        if (pdfText != null && !pdfText.trim().isEmpty()) {
                            Map<String, Object> pdfPart = new HashMap<>();
                            pdfPart.put("text", "\n\nPDF Content:\n" + pdfText);
                            parts.add(pdfPart);
                        }
                    } catch (Exception e) {
                        System.err.println("Error processing PDF: " + e.getMessage());
                    }
                }
            }

            // Call Gemini API with multimodal content
            String response = callGeminiAPI(systemPrompt, parts);

            // Record request for rate limiting
            recordRequest(request.getUserId());

            return ChatbotResponse.success(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ChatbotResponse.error("Failed to get response from AI: " + e.getMessage());
        }
    }

    /**
     * Extract text from PDF URL using PDFBox 3.x API
     */
    private String extractTextFromPdf(String pdfUrl) throws Exception {
        URL url = new URL(pdfUrl);
        try (InputStream inputStream = url.openStream();
             PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {

            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            // Limit text length to prevent token overflow
            if (text.length() > 50000) {
                text = text.substring(0, 50000) + "\n\n[Content truncated due to length]";
            }

            return text;
        }
    }

    /**
     * Process image URL into Gemini-compatible format
     */
    private Map<String, Object> processImage(String imageUrl) throws Exception {
        // Download image from URL
        URL url = new URL(imageUrl);
        BufferedImage image = ImageIO.read(url);

        if (image == null) {
            throw new Exception("Failed to read image from URL");
        }

        // Convert to base64
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String format = getImageFormat(imageUrl);
        ImageIO.write(image, format, baos);
        byte[] imageBytes = baos.toByteArray();
        String base64Image = Base64.getEncoder().encodeToString(imageBytes);

        // Create inline data part for Gemini
        Map<String, Object> imagePart = new HashMap<>();
        Map<String, Object> inlineData = new HashMap<>();
        inlineData.put("mimeType", "image/" + format);
        inlineData.put("data", base64Image);
        imagePart.put("inlineData", inlineData);

        return imagePart;
    }

    /**
     * Get image format from URL
     */
    private String getImageFormat(String url) {
        String lower = url.toLowerCase();
        if (lower.endsWith(".png")) return "png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "jpeg";
        if (lower.endsWith(".gif")) return "gif";
        if (lower.endsWith(".webp")) return "webp";
        return "jpeg"; // default
    }

    /**
     * Build system prompt for University Assistant
     */
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

    /**
     * Call Gemini API with multimodal content
     */
    private String callGeminiAPI(String systemPrompt, List<Map<String, Object>> parts) throws Exception {
        String url = GEMINI_API_URL + "?key=" + apiKey;

        // Build request body
        Map<String, Object> requestBody = new HashMap<>();

        // Add system instruction
        Map<String, Object> systemInstruction = new HashMap<>();
        List<Map<String, String>> systemParts = new ArrayList<>();
        Map<String, String> systemTextPart = new HashMap<>();
        systemTextPart.put("text", systemPrompt);
        systemParts.add(systemTextPart);
        systemInstruction.put("parts", systemParts);
        requestBody.put("systemInstruction", systemInstruction);

        // Add user content (text + images + PDFs)
        List<Map<String, Object>> contents = new ArrayList<>();
        Map<String, Object> content = new HashMap<>();
        content.put("parts", parts);
        contents.add(content);
        requestBody.put("contents", contents);

        // Generation config
        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("temperature", 0.7);
        generationConfig.put("topK", 40);
        generationConfig.put("topP", 0.95);
        generationConfig.put("maxOutputTokens", 2048);
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

    /**
     * Rate limiting
     */
    private boolean checkRateLimit(Long userId) {
        if (userId == null) {
            return true; // Allow anonymous requests
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
}
