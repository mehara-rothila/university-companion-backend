package com.smartuniversity.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartuniversity.dto.ChatbotRequest;
import com.smartuniversity.model.DocumentChunk;
import com.smartuniversity.model.IndexedDocument;
import com.smartuniversity.repository.DocumentChunkRepository;
import com.smartuniversity.service.agent.AssistantToolbox;
import com.smartuniversity.service.rag.DocumentIndexService;
import com.smartuniversity.service.rag.DocumentRetriever;
import com.smartuniversity.service.rag.GeminiEmbeddingClient;
import com.smartuniversity.service.rag.PdfPageReader;
import com.smartuniversity.service.rag.TextChunk;
import com.smartuniversity.service.rag.TextChunker;
import com.smartuniversity.service.rag.VectorMath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Opt-in check of what RAG saves. For each question it builds the exact Gemini request
 * GeminiChatService sends with RAG off (the PDF's full text) and on (retrieved excerpts), counts
 * both requests' prompt tokens with Gemini's countTokens API, and checks whether the excerpts
 * contain the expected answer.
 *
 * <p>It calls the real Gemini API, so it only runs when these environment variables are set:</p>
 * <pre>
 * GEMINI_API_KEY      API key
 * RAG_EVAL_PDF        path to a PDF
 * RAG_EVAL_QUESTIONS  questions, separated by |
 * RAG_EVAL_EXPECT     optional: text each answer should contain, separated by |
 * RAG_EVAL_ANSWER     optional: "true" to also generate the first question's RAG answer
 * GEMINI_MODEL        optional: model to count tokens with (default gemini-3.5-flash)
 * </pre>
 * The results are printed and written to {@code target/rag-token-comparison.md}.
 */
@EnabledIfEnvironmentVariable(named = "RAG_EVAL_PDF", matches = ".+")
class PdfRagTokenComparisonTest {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final String PDF_URL = "https://bucket.s3.amazonaws.com/chatbot/eval.pdf";
    private static final String PDF_KEY = "chatbot/eval.pdf";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate http = new RestTemplate();

    @Test
    void comparePromptTokensWithAndWithoutRag() throws Exception {
        String apiKey = requiredEnv("GEMINI_API_KEY");
        String model = System.getenv().getOrDefault("GEMINI_MODEL", "gemini-3.5-flash");
        Path pdfPath = Path.of(requiredEnv("RAG_EVAL_PDF"));
        byte[] pdf = Files.readAllBytes(pdfPath);
        List<String> questions = split(requiredEnv("RAG_EVAL_QUESTIONS"));
        List<String> expected = split(System.getenv().getOrDefault("RAG_EVAL_EXPECT", ""));

        // Index the PDF in memory with the production reader, chunker and embeddings client.
        GeminiEmbeddingClient embeddings = new GeminiEmbeddingClient(http, objectMapper, apiKey, BASE_URL,
                "gemini-embedding-2", 768, 2000);
        PdfPageReader.PdfPages pages = new PdfPageReader().read(pdf, 300);
        List<TextChunk> chunks = new TextChunker(1200, 200).chunk(pages.pages());
        List<float[]> vectors = embeddings.embedDocuments(chunks.stream().map(TextChunk::text).toList());

        IndexedDocument document = new IndexedDocument();
        document.setId(1L);
        document.setChunkCount(chunks.size());
        List<DocumentChunk> rows = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            TextChunk chunk = chunks.get(i);
            rows.add(new DocumentChunk(document, chunk.pageNumber(), chunk.index(), chunk.text(),
                    VectorMath.toBytes(vectors.get(i))));
        }

        DocumentChunkRepository chunkRepository = mock(DocumentChunkRepository.class);
        when(chunkRepository.findByDocumentIdIn(any())).thenReturn(rows);
        DocumentIndexService indexService = mock(DocumentIndexService.class);
        when(indexService.indexIfNeeded(PDF_KEY)).thenReturn(document);
        S3Service s3Service = mock(S3Service.class);
        when(s3Service.extractFileNameFromUrl(PDF_URL)).thenReturn(PDF_KEY);
        when(s3Service.getFileBytes(PDF_KEY)).thenReturn(pdf);

        // The chat service's own HTTP client only records the request it would send to Gemini.
        AtomicReference<Map<String, Object>> sent = new AtomicReference<>();
        RestTemplate recorder = mock(RestTemplate.class);
        when(recorder.postForObject(anyString(), any(HttpEntity.class), eq(String.class))).thenAnswer(invocation -> {
            HttpEntity<Map<String, Object>> request = invocation.getArgument(1);
            sent.set(request.getBody());
            return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ok\"}]}}]}";
        });
        GeminiChatService chat = new GeminiChatService(recorder, objectMapper, s3Service, indexService,
                new DocumentRetriever(embeddings, chunkRepository, 6), new AssistantToolbox(List.of(), objectMapper));

        StringBuilder report = new StringBuilder()
                .append("PDF: ").append(pdfPath.getFileName()).append(", ").append(pages.totalPages())
                .append(" pages, ").append(chunks.size()).append(" chunks. Model for counting: ").append(model)
                .append("\n\n| Question | Full text | RAG | Saving | Answer in excerpts |\n|---|---|---|---|---|\n");
        long totalFull = 0;
        long totalRag = 0;
        int hits = 0;
        Map<String, Object> firstRagRequest = null;

        for (int i = 0; i < questions.size(); i++) {
            String question = questions.get(i);

            ReflectionTestUtils.setField(chat, "ragEnabled", false);
            chat.chat(pdfQuestion(question));
            int fullTokens = countTokens(apiKey, model, sent.get());

            ReflectionTestUtils.setField(chat, "ragEnabled", true);
            chat.chat(pdfQuestion(question));
            int ragTokens = countTokens(apiKey, model, sent.get());
            if (firstRagRequest == null) {
                firstRagRequest = sent.get();
            }

            String found = "-";
            if (i < expected.size()) {
                boolean hit = userText(sent.get()).substring(question.length()).contains(expected.get(i));
                hits += hit ? 1 : 0;
                found = hit ? "yes" : "no";
            }
            totalFull += fullTokens;
            totalRag += ragTokens;
            report.append("| ").append(question).append(" | ").append(fullTokens).append(" | ").append(ragTokens)
                    .append(" | ").append(String.format("%.1fx", (double) fullTokens / ragTokens))
                    .append(" | ").append(found).append(" |\n");
        }

        report.append("| **Total** | **").append(totalFull).append("** | **").append(totalRag).append("** | **")
                .append(String.format("%.1fx", (double) totalFull / totalRag)).append("** | ")
                .append(expected.isEmpty() ? "-" : hits + "/" + Math.min(expected.size(), questions.size()))
                .append(" |\n");

        if ("true".equals(System.getenv("RAG_EVAL_ANSWER")) && firstRagRequest != null) {
            report.append("\nRAG answer to \"").append(questions.get(0)).append("\":\n\n> ")
                    .append(generate(apiKey, model, firstRagRequest).replace("\n", "\n> ")).append('\n');
        }

        Files.writeString(Path.of("target", "rag-token-comparison.md"), report);
        System.out.println(report);
        assertTrue(totalRag < totalFull, "RAG should send fewer prompt tokens than the full text");
    }

    private int countTokens(String apiKey, String model, Map<String, Object> generateRequest) throws Exception {
        Map<String, Object> request = Map.of("generateContentRequest", Map.of(
                "model", "models/" + model,
                "systemInstruction", generateRequest.get("systemInstruction"),
                "contents", generateRequest.get("contents")));
        String response = http.postForObject(BASE_URL + "/models/" + model + ":countTokens",
                new HttpEntity<>(request, headers(apiKey)), String.class);
        return objectMapper.readTree(response).path("totalTokens").asInt();
    }

    private String generate(String apiKey, String model, Map<String, Object> generateRequest) throws Exception {
        String response = http.postForObject(BASE_URL + "/models/" + model + ":generateContent",
                new HttpEntity<>(generateRequest, headers(apiKey)), String.class);
        StringBuilder text = new StringBuilder();
        for (JsonNode part : objectMapper.readTree(response).path("candidates").path(0).path("content").path("parts")) {
            if (!part.path("thought").asBoolean(false)) {
                text.append(part.path("text").asText());
            }
        }
        return text.toString();
    }

    private String userText(Map<String, Object> generateRequest) {
        return objectMapper.valueToTree(generateRequest).path("contents").path(0).path("parts").path(0).path("text").asText();
    }

    private static ChatbotRequest pdfQuestion(String question) {
        ChatbotRequest request = new ChatbotRequest(question, 1L);
        request.setPdfUrls(List.of(PDF_URL));
        return request;
    }

    private static HttpHeaders headers(String apiKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);
        return headers;
    }

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set");
        }
        return value;
    }

    private static List<String> split(String value) {
        return value.isBlank() ? List.of() : Arrays.stream(value.split("\\|")).map(String::trim).toList();
    }
}
