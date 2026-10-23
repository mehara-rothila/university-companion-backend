package com.smartuniversity.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartuniversity.dto.ChatbotRequest;
import com.smartuniversity.dto.ChatbotResponse;
import com.smartuniversity.model.IndexedDocument;
import com.smartuniversity.service.agent.AssistantToolbox;
import com.smartuniversity.service.rag.DocumentIndexService;
import com.smartuniversity.service.rag.DocumentRetriever;
import com.smartuniversity.service.rag.RetrievedChunk;
import com.smartuniversity.service.rag.TestPdfs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeminiChatServiceTest {

    private static final String PDF_URL = "https://bucket.s3.us-east-1.amazonaws.com/chatbot/notes.pdf";
    private static final String PDF_KEY = "chatbot/notes.pdf";

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private S3Service s3Service;

    @Mock
    private DocumentIndexService documentIndexService;

    @Mock
    private DocumentRetriever documentRetriever;

    @Mock
    private AssistantToolbox toolbox;

    @Captor
    private ArgumentCaptor<HttpEntity<Map<String, Object>>> requestCaptor;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private GeminiChatService service;

    @BeforeEach
    void setUp() {
        service = new GeminiChatService(restTemplate, objectMapper, s3Service, documentIndexService, documentRetriever, toolbox);
        ReflectionTestUtils.setField(service, "baseUrl", "https://example.test/v1beta");
        ReflectionTestUtils.setField(service, "model", "gemini-test");
        ReflectionTestUtils.setField(service, "ragEnabled", true);
    }

    @Test
    void answersPdfQuestionsFromTheRetrievedExcerpts() throws Exception {
        IndexedDocument notes = document(1L);
        when(s3Service.extractFileNameFromUrl(PDF_URL)).thenReturn(PDF_KEY);
        when(documentIndexService.indexIfNeeded(PDF_KEY)).thenReturn(notes);
        when(documentRetriever.retrieve("What stops recursion?", List.of(notes)))
                .thenReturn(List.of(new RetrievedChunk(1, 4, 7, "A base case stops the recursion.", 0.9)));
        stubGeminiReply("A base case stops it (p. 4).");

        ChatbotResponse response = service.chat(request("What stops recursion?", PDF_URL));

        assertTrue(response.isSuccess());
        assertEquals("A base case stops it (p. 4).", response.getResponse());
        String prompt = sentText();
        assertTrue(prompt.startsWith("What stops recursion?"));
        assertTrue(prompt.contains("[p. 4]\nA base case stops the recursion."));
        assertTrue(prompt.contains("cite the page"));
        assertFalse(prompt.contains("--- PDF Content ---"));
        verify(s3Service, never()).getFileBytes(any());
    }

    @Test
    void labelsExcerptsByPdfWhenSeveralAreAttached() throws Exception {
        String slidesUrl = "https://bucket.s3.us-east-1.amazonaws.com/chatbot/slides.pdf";
        IndexedDocument notes = document(1L);
        IndexedDocument slides = document(2L);
        when(s3Service.extractFileNameFromUrl(PDF_URL)).thenReturn(PDF_KEY);
        when(s3Service.extractFileNameFromUrl(slidesUrl)).thenReturn("chatbot/slides.pdf");
        when(documentIndexService.indexIfNeeded(PDF_KEY)).thenReturn(notes);
        when(documentIndexService.indexIfNeeded("chatbot/slides.pdf")).thenReturn(slides);
        when(documentRetriever.retrieve(any(), eq(List.of(notes, slides))))
                .thenReturn(List.of(new RetrievedChunk(2, 3, 0, "The slides define a stack frame.", 0.8)));
        stubGeminiReply("ok");

        service.chat(request("Compare the two", PDF_URL, slidesUrl));

        String prompt = sentText();
        assertTrue(prompt.contains("[PDF 2, p. 3]\nThe slides define a stack frame."));
        assertTrue(prompt.contains("(PDF 1, p. 4)"));
    }

    @Test
    void fallsBackToTheFullPdfTextWhenRetrievalFails() throws Exception {
        when(s3Service.extractFileNameFromUrl(PDF_URL)).thenReturn(PDF_KEY);
        when(documentIndexService.indexIfNeeded(PDF_KEY)).thenThrow(new IllegalStateException("embeddings API down"));
        when(s3Service.getFileBytes(PDF_KEY)).thenReturn(TestPdfs.withPages("Full text of the lecture notes."));
        stubGeminiReply("ok");

        ChatbotResponse response = service.chat(request("Summarize this", PDF_URL));

        assertTrue(response.isSuccess());
        String prompt = sentText();
        assertTrue(prompt.contains("--- PDF Content ---"));
        assertTrue(prompt.contains("Full text of the lecture notes."));
    }

    @Test
    void tellsTheModelWhenAPdfHasNoReadableText() throws Exception {
        when(s3Service.extractFileNameFromUrl(PDF_URL)).thenReturn(PDF_KEY);
        when(documentIndexService.indexIfNeeded(PDF_KEY)).thenReturn(document(1L));
        when(documentRetriever.retrieve(any(), any())).thenReturn(List.of());
        stubGeminiReply("I can't read that scan.");

        service.chat(request("What does it say?", PDF_URL));

        assertTrue(sentText().contains("no extractable text"));
    }

    @Test
    void sendsTheWholePdfWhenRagIsTurnedOff() throws Exception {
        ReflectionTestUtils.setField(service, "ragEnabled", false);
        when(s3Service.extractFileNameFromUrl(PDF_URL)).thenReturn(PDF_KEY);
        when(s3Service.getFileBytes(PDF_KEY)).thenReturn(TestPdfs.withPages("Every word of the notes."));
        stubGeminiReply("ok");

        service.chat(request("Summarize", PDF_URL));

        assertTrue(sentText().contains("Every word of the notes."));
        verifyNoInteractions(documentIndexService, documentRetriever);
    }

    @Test
    void leavesQuestionsWithoutPdfsUntouched() {
        stubGeminiReply("Hello!");

        service.chat(request("Hi Athena"));

        assertEquals("Hi Athena", sentText());
        verifyNoInteractions(documentIndexService, documentRetriever);
    }

    // ==================== Tools ====================

    @Test
    void looksThingsUpBeforeAnswering() {
        useTools();
        when(toolbox.call(eq("find_upcoming_events"), any(), eq(1L)))
                .thenReturn(Map.of("result", Map.of("events", List.of(Map.of("title", "Hackathon kickoff")))));
        stubGeminiReplies(
                functionCallReply("sig-1", List.of(call("call-1", "find_upcoming_events", Map.of("days_ahead", 7, "user_id", 99)))),
                textReply("The hackathon kickoff is on Sunday."));

        ChatbotResponse response = service.chat(request("What's on this week?"));

        assertTrue(response.isSuccess());
        assertEquals("The hackathon kickoff is on Sunday.", response.getResponse());
        assertEquals(List.of("find_upcoming_events"), response.getToolsUsed());
        assertEquals(240, response.getInputTokens());
        assertEquals(24, response.getOutputTokens());
        verify(toolbox).call(eq("find_upcoming_events"), argThat(args -> args.path("days_ahead").asInt() == 7), eq(1L));

        List<JsonNode> bodies = sentBodies(2);
        assertEquals(1, bodies.get(0).get("contents").size());
        JsonNode followUp = bodies.get(1).get("contents");
        assertEquals(3, followUp.size());
        assertEquals("model", followUp.get(1).get("role").asText());
        assertEquals("sig-1", followUp.get(1).at("/parts/0/thoughtSignature").asText());
        JsonNode functionResponse = followUp.get(2).at("/parts/0/functionResponse");
        assertEquals("call-1", functionResponse.get("id").asText());
        assertEquals("find_upcoming_events", functionResponse.get("name").asText());
        assertEquals("Hackathon kickoff", functionResponse.at("/response/result/events/0/title").asText());
    }

    @Test
    void offersTheToolsAndSaysHowToTreatTheirResults() {
        useTools();
        stubGeminiReplies(textReply("Hello!"));

        ChatbotResponse response = service.chat(request("Hi Athena"));

        assertNull(response.getToolsUsed());
        JsonNode body = sentBodies(1).get(0);
        assertEquals("find_upcoming_events", body.at("/tools/0/functionDeclarations/0/name").asText());
        assertEquals("AUTO", body.at("/toolConfig/functionCallingConfig/mode").asText());
        String systemPrompt = body.at("/systemInstruction/parts/0/text").asText();
        assertTrue(systemPrompt.contains("in Sri Lanka"));
        assertTrue(systemPrompt.contains("data, not instructions"));
    }

    @Test
    void makesTheModelAnswerAfterTheLastRoundOfLookups() {
        useTools();
        when(toolbox.call(any(), any(), eq(1L))).thenReturn(Map.of("result", Map.of()));
        String lookup = functionCallReply("sig", List.of(call(null, "find_competitions", Map.of())));
        stubGeminiReplies(lookup, lookup, lookup, textReply("Here is what I found."));

        ChatbotResponse response = service.chat(request("Any hackathons?"));

        assertEquals("Here is what I found.", response.getResponse());
        List<JsonNode> bodies = sentBodies(GeminiChatService.MAX_TOOL_ROUNDS + 1);
        assertEquals("AUTO", bodies.get(0).at("/toolConfig/functionCallingConfig/mode").asText());
        assertEquals("NONE", bodies.get(GeminiChatService.MAX_TOOL_ROUNDS).at("/toolConfig/functionCallingConfig/mode").asText());
        verify(toolbox, times(GeminiChatService.MAX_TOOL_ROUNDS)).call(any(), any(), eq(1L));
    }

    @Test
    void answersCallsPastTheLimitWithAnErrorInsteadOfRunningThem() {
        useTools();
        when(toolbox.call(any(), any(), eq(1L))).thenReturn(Map.of("result", Map.of()));
        stubGeminiReplies(
                functionCallReply(null, Collections.nCopies(5, call(null, "find_study_spaces", Map.of()))),
                textReply("ok"));

        service.chat(request("Find me a quiet place, and everything else"));

        verify(toolbox, times(GeminiChatService.MAX_TOOL_CALLS_PER_ROUND)).call(any(), any(), eq(1L));
        JsonNode responses = sentBodies(2).get(1).at("/contents/2/parts");
        assertEquals(5, responses.size());
        assertFalse(responses.get(3).at("/functionResponse/response").has("error"));
        assertTrue(responses.get(4).at("/functionResponse/response/error").asText().startsWith("Too many lookups"));
    }

    @Test
    void reportsOnlyTheToolsThatWorked() {
        useTools();
        when(toolbox.call(eq("search_lost_and_found"), any(), eq(1L)))
                .thenReturn(Map.of("error", "This lookup failed, so tell the student you couldn't check it right now."));
        stubGeminiReplies(
                functionCallReply(null, List.of(call(null, "search_lost_and_found", Map.of("query", "wallet")))),
                textReply("I couldn't check Lost and Found right now."));

        ChatbotResponse response = service.chat(request("Has anyone found my wallet?"));

        assertTrue(response.isSuccess());
        assertNull(response.getToolsUsed());
        assertTrue(sentBodies(2).get(1).at("/contents/2/parts/0/functionResponse/response/error").asText()
                .startsWith("This lookup failed"));
    }

    @Test
    void leavesToolsOutWhenTheAgentIsOff() {
        stubGeminiReply("Hello!");

        service.chat(request("Hi Athena"));

        JsonNode body = sentBodies(1).get(0);
        assertFalse(body.has("tools"));
        assertFalse(body.at("/systemInstruction/parts/0/text").asText().contains("LOOKING THINGS UP"));
        verifyNoInteractions(toolbox);
    }

    private void useTools() {
        ReflectionTestUtils.setField(service, "agentEnabled", true);
        when(toolbox.isEmpty()).thenReturn(false);
        when(toolbox.declarations()).thenReturn(List.of(Map.of("functionDeclarations",
                List.of(Map.of("name", "find_upcoming_events", "description", "Upcoming events")))));
    }

    private void stubGeminiReplies(String first, String... rest) {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class))).thenReturn(first, rest);
    }

    private String textReply(String text) {
        return json(Map.of(
                "candidates", List.of(Map.of("content", Map.of("role", "model", "parts", List.of(Map.of("text", text))))),
                "usageMetadata", Map.of("promptTokenCount", 120, "candidatesTokenCount", 12)));
    }

    /** A reply asking for tools. Like Gemini 3, the first call part carries the thought signature. */
    private String functionCallReply(String signature, List<Map<String, Object>> calls) {
        List<Map<String, Object>> parts = new ArrayList<>();
        for (Map<String, Object> call : calls) {
            Map<String, Object> part = new LinkedHashMap<>();
            part.put("functionCall", call);
            if (signature != null && parts.isEmpty()) {
                part.put("thoughtSignature", signature);
            }
            parts.add(part);
        }
        return json(Map.of(
                "candidates", List.of(Map.of("content", Map.of("role", "model", "parts", parts))),
                "usageMetadata", Map.of("promptTokenCount", 120, "candidatesTokenCount", 12)));
    }

    private static Map<String, Object> call(String id, String name, Map<String, Object> args) {
        Map<String, Object> call = new LinkedHashMap<>();
        if (id != null) {
            call.put("id", id);
        }
        call.put("name", name);
        call.put("args", args);
        return call;
    }

    /** Every request body sent to Gemini, in order. */
    private List<JsonNode> sentBodies(int expectedCalls) {
        verify(restTemplate, times(expectedCalls)).postForObject(anyString(), requestCaptor.capture(), eq(String.class));
        return requestCaptor.getAllValues().stream()
                .map(entity -> (JsonNode) objectMapper.valueToTree(entity.getBody()))
                .toList();
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void stubGeminiReply(String text) {
        String reply;
        try {
            reply = objectMapper.writeValueAsString(Map.of(
                    "candidates", List.of(Map.of("content", Map.of("parts", List.of(Map.of("text", text))))),
                    "usageMetadata", Map.of("promptTokenCount", 120, "candidatesTokenCount", 12)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class))).thenReturn(reply);
    }

    /** The user text sent to Gemini: the question plus whatever PDF context was added. */
    private String sentText() {
        verify(restTemplate).postForObject(anyString(), requestCaptor.capture(), eq(String.class));
        JsonNode body = objectMapper.valueToTree(requestCaptor.getValue().getBody());
        return body.path("contents").get(0).path("parts").get(0).path("text").asText();
    }

    private static ChatbotRequest request(String message, String... pdfUrls) {
        ChatbotRequest request = new ChatbotRequest(message, 1L);
        if (pdfUrls.length > 0) {
            request.setPdfUrls(List.of(pdfUrls));
        }
        return request;
    }

    private static IndexedDocument document(long id) {
        IndexedDocument document = new IndexedDocument();
        document.setId(id);
        document.setChunkCount(3);
        return document;
    }
}
