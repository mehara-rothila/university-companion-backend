package com.smartuniversity.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartuniversity.dto.ChatbotRequest;
import com.smartuniversity.dto.ChatbotResponse;
import com.smartuniversity.model.IndexedDocument;
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

    @Captor
    private ArgumentCaptor<HttpEntity<Map<String, Object>>> requestCaptor;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private GeminiChatService service;

    @BeforeEach
    void setUp() {
        service = new GeminiChatService(restTemplate, objectMapper, s3Service, documentIndexService, documentRetriever);
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
