package com.smartuniversity.service.rag;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.smartuniversity.dto.ChatbotRequest;
import com.smartuniversity.model.DocumentChunk;
import com.smartuniversity.model.IndexedDocument;
import com.smartuniversity.repository.DocumentChunkRepository;
import com.smartuniversity.service.GeminiChatService;
import com.smartuniversity.service.S3Service;
import com.smartuniversity.service.agent.AssistantToolbox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Opt-in retrieval evaluation over a fixed question set ({@code rag-eval/questions.json}): four
 * public PDFs, ten questions each, every question tied to the page that answers it and to short
 * phrases copied from that page.
 *
 * <p>Each PDF is indexed in memory with the production reader, chunker and embeddings, and each
 * question is run through the production retriever and prompt builder. It reports how often a chunk
 * from the answer page ranks in the top 1, 3 and 6, the mean reciprocal rank of the first such chunk,
 * whether the answer's phrases reach the prompt, and the prompt tokens with and without RAG. It also
 * checks the full-text prompt the old code sent (each PDF cut at 50,000 characters) for the answer.</p>
 *
 * <p>It calls the real Gemini API, so it only runs when these environment variables are set:</p>
 * <pre>
 * RAG_EVAL_PDF_DIR  folder holding the PDFs named in the question set (see rag-eval/README.md)
 * GEMINI_API_KEY    API key
 * RAG_EVAL_SET      optional: another question set file instead of rag-eval/questions.json
 * GEMINI_MODEL      optional: model to count tokens with (default gemini-3.5-flash)
 * </pre>
 * Embeddings are cached in {@code target/rag-eval-cache}, so a re-run only pays for token counting.
 * Results go to {@code target/rag-eval-report.md} and {@code target/rag-eval-results.json}.
 */
@EnabledIfEnvironmentVariable(named = "RAG_EVAL_PDF_DIR", matches = ".+")
class RagRetrievalEvalTest {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta";
    private static final String EMBEDDING_MODEL = "gemini-embedding-2";
    private static final int DIMENSIONS = 768;
    private static final int CHUNK_SIZE = 1200;
    private static final int OVERLAP = 200;
    private static final int TOP_K = 6;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final RestTemplate http = new RestTemplate(requestFactory());

    /** A stalled Gemini call must not hang the evaluation: 10 s to connect, 60 s without data. */
    private static org.springframework.http.client.SimpleClientHttpRequestFactory requestFactory() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(10));
        factory.setReadTimeout(java.time.Duration.ofSeconds(60));
        return factory;
    }

    record EvalSet(String description, List<EvalDocument> documents) {
    }

    record EvalDocument(String id, String file, String title, String kind, String source, String sha256,
                        int pages, List<EvalQuestion> questions) {
    }

    record EvalQuestion(String id, String question, String answer, List<Integer> pages,
                        @JsonProperty("key_phrases") List<String> keyPhrases, String type, String section) {
    }

    /** One question's outcome. {@code rank} is the 1-based rank of the first chunk from an answer page. */
    record QuestionResult(String document, String id, String type, String question, List<Integer> answerPages,
                          int rank, List<Integer> topPages, boolean answerInRagPrompt, boolean answerInFullTextPrompt,
                          int fullTextTokens, int ragTokens) {
        boolean hitAt(int k) {
            return rank >= 1 && rank <= k;
        }
    }

    @Test
    void scoreRetrievalOnTheQuestionSet() throws Exception {
        String apiKey = requiredEnv("GEMINI_API_KEY");
        String countModel = System.getenv().getOrDefault("GEMINI_MODEL", "gemini-3.5-flash");
        Path pdfDir = Path.of(requiredEnv("RAG_EVAL_PDF_DIR"));
        EvalSet set = loadSet();
        CachingEmbeddings embeddings = new CachingEmbeddings(apiKey);

        List<QuestionResult> results = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        StringBuilder docRows = new StringBuilder();

        for (EvalDocument doc : set.documents()) {
            byte[] pdf = Files.readAllBytes(pdfDir.resolve(doc.file()));
            String pdfSha = sha256(pdf);
            if (!doc.sha256().equals(pdfSha)) {
                // A re-rendered copy (the RFC has no official PDF) differs byte for byte; the phrase check below
                // still catches a copy whose text differs.
                System.out.println("Note: " + doc.file() + " is not byte-identical to the file the questions were written for");
            }

            PdfPageReader.PdfPages pages = new PdfPageReader().read(pdf, 300);
            List<TextChunk> chunks = new TextChunker(CHUNK_SIZE, OVERLAP).chunk(pages.pages());
            // Cached by the file actually read, so a different copy never reuses another copy's vectors.
            List<float[]> vectors = embeddings.embedChunks(pdfSha, chunks);

            // Every key phrase must be on one of its answer pages, or the question can't be scored fairly.
            List<EvalQuestion> valid = new ArrayList<>();
            for (EvalQuestion q : doc.questions()) {
                List<String> missing = q.keyPhrases().stream()
                        .filter(phrase -> q.pages().stream().noneMatch(p -> p >= 1 && p <= pages.pages().size()
                                && contains(pages.pages().get(p - 1), phrase)))
                        .toList();
                if (missing.isEmpty()) {
                    valid.add(q);
                } else {
                    invalid.add(q.id() + ": not on page " + q.pages() + ": " + missing);
                }
            }

            String url = urlOf("chatbot/" + doc.file());
            GeminiChatService chat = chatServiceFor(doc, pdf, chunks, vectors, embeddings);
            AtomicReference<Map<String, Object>> sent = recorderOf(chat);
            int docFull = 0;
            int docRag = 0;

            for (EvalQuestion q : valid) {
                float[] query = embeddings.embedQuery(q.question());
                List<Integer> ranked = IntStream.range(0, chunks.size()).boxed()
                        .sorted(Comparator.comparingDouble((Integer i) -> VectorMath.dot(query, vectors.get(i))).reversed())
                        .toList();
                Set<Integer> answerPages = Set.copyOf(q.pages());
                int rank = 0;
                for (int r = 0; r < ranked.size(); r++) {
                    if (answerPages.contains(chunks.get(ranked.get(r)).pageNumber())) {
                        rank = r + 1;
                        break;
                    }
                }
                List<Integer> topPages = ranked.stream().limit(TOP_K).map(i -> chunks.get(i).pageNumber()).toList();

                ReflectionTestUtils.setField(chat, "ragEnabled", false);
                chat.chat(pdfQuestion(q.question(), url));
                Map<String, Object> fullRequest = sent.get();
                ReflectionTestUtils.setField(chat, "ragEnabled", true);
                chat.chat(pdfQuestion(q.question(), url));
                Map<String, Object> ragRequest = sent.get();

                String ragText = userText(ragRequest);
                // The harness ranks chunks the same way DocumentRetriever does; the prompt must hold its top 6.
                ranked.stream().limit(TOP_K).forEach(i -> assertTrue(ragText.contains(chunks.get(i).text()),
                        q.id() + ": the prompt is missing a chunk the harness ranked in the top " + TOP_K));

                int fullTokens = countTokens(apiKey, countModel, fullRequest);
                int ragTokens = countTokens(apiKey, countModel, ragRequest);
                docFull += fullTokens;
                docRag += ragTokens;
                results.add(new QuestionResult(doc.id(), q.id(), q.type(), q.question(), q.pages(), rank, topPages,
                        containsAll(ragText, q.keyPhrases()), containsAll(userText(fullRequest), q.keyPhrases()),
                        fullTokens, ragTokens));
            }

            List<QuestionResult> mine = results.stream().filter(r -> r.document().equals(doc.id())).toList();
            docRows.append(String.format("| %s | %d | %d | %d | %s | %s | %s | %.2f | %s | %s | %,d | %,d | %.1f× |%n",
                    doc.id(), pages.totalPages(), chunks.size(), mine.size(),
                    share(mine, r -> r.hitAt(1)), share(mine, r -> r.hitAt(3)), share(mine, r -> r.hitAt(TOP_K)), mrr(mine),
                    share(mine, QuestionResult::answerInFullTextPrompt), share(mine, QuestionResult::answerInRagPrompt),
                    docFull, docRag, docRag == 0 ? 0 : (double) docFull / docRag));
        }

        String report = report(set, results, docRows.toString(), invalid, countModel);
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target", "rag-eval-report.md"), report);
        objectMapper.writeValue(Path.of("target", "rag-eval-results.json").toFile(), results);
        System.out.println(report);

        assertTrue(invalid.isEmpty(), "Questions whose key phrases are not on their answer pages: " + invalid);
        assertTrue(results.stream().mapToInt(QuestionResult::ragTokens).sum()
                < results.stream().mapToInt(QuestionResult::fullTextTokens).sum(), "RAG should send fewer prompt tokens");
    }

    // ==================== Report ====================

    private String report(EvalSet set, List<QuestionResult> results, String docRows, List<String> invalid, String countModel) {
        int full = results.stream().mapToInt(QuestionResult::fullTextTokens).sum();
        int rag = results.stream().mapToInt(QuestionResult::ragTokens).sum();
        StringBuilder out = new StringBuilder("# RAG retrieval evaluation\n\n")
                .append(String.format("%d questions over %d PDFs · %s, %d dims · %d-character chunks, %d overlap · top %d · tokens counted on %s%n%n",
                        results.size(), set.documents().size(), EMBEDDING_MODEL, DIMENSIONS, CHUNK_SIZE, OVERLAP, TOP_K, countModel))
                .append("| | Questions | Hit@1 | Hit@3 | Hit@6 | MRR | Answer in old full-text prompt | Answer in RAG prompt | Full-text tokens | RAG tokens | Saving |\n")
                .append("|---|---|---|---|---|---|---|---|---|---|---|\n")
                .append(String.format("| **All** | %d | %s | %s | %s | %.2f | %s | %s | %,d | %,d | %.1f× |%n%n",
                        results.size(), share(results, r -> r.hitAt(1)), share(results, r -> r.hitAt(3)),
                        share(results, r -> r.hitAt(TOP_K)), mrr(results), share(results, QuestionResult::answerInFullTextPrompt),
                        share(results, QuestionResult::answerInRagPrompt), full, rag, rag == 0 ? 0 : (double) full / rag))
                .append("## By document\n\n")
                .append("| Document | Pages | Chunks | Questions | Hit@1 | Hit@3 | Hit@6 | MRR | Answer in old prompt | Answer in RAG prompt | Full-text tokens | RAG tokens | Saving |\n")
                .append("|---|---|---|---|---|---|---|---|---|---|---|---|---|\n")
                .append(docRows)
                .append("\n## By question type\n\n| Type | Questions | Hit@1 | Hit@6 | MRR |\n|---|---|---|---|---|\n");
        for (String type : List.of("concept", "fact", "exact-term", "number")) {
            List<QuestionResult> typed = results.stream().filter(r -> type.equals(r.type())).toList();
            if (!typed.isEmpty()) {
                out.append(String.format("| %s | %d | %s | %s | %.2f |%n", type, typed.size(),
                        share(typed, r -> r.hitAt(1)), share(typed, r -> r.hitAt(TOP_K)), mrr(typed)));
            }
        }
        List<QuestionResult> misses = results.stream().filter(r -> !r.hitAt(TOP_K) || !r.answerInRagPrompt()).toList();
        out.append("\n## Misses\n\n");
        if (misses.isEmpty()) {
            out.append("None: every answer page ranked in the top ").append(TOP_K).append(" and every answer reached the prompt.\n");
        } else {
            out.append("| Question | Type | Answer page | Top-6 pages | First answer-page rank | Answer in RAG prompt |\n|---|---|---|---|---|---|\n");
            for (QuestionResult r : misses) {
                out.append(String.format("| %s: %s | %s | %s | %s | %s | %s |%n", r.id(), r.question(), r.type(), r.answerPages(),
                        r.topPages(), r.rank() == 0 ? "not found" : r.rank(), r.answerInRagPrompt() ? "yes" : "no"));
            }
        }
        out.append("\nHit@k: a chunk from an answer page ranked in the top k. MRR: mean of 1 / rank of the first such chunk. ")
                .append("\"Answer in prompt\": every key phrase of the question appears in what Gemini is sent.\n");
        if (!invalid.isEmpty()) {
            out.append("\n## Questions left out (key phrases not found on their pages)\n\n");
            invalid.forEach(line -> out.append("- ").append(line).append('\n'));
        }
        return out.toString();
    }

    private static String share(List<QuestionResult> results, java.util.function.Predicate<QuestionResult> test) {
        long n = results.stream().filter(test).count();
        return results.isEmpty() ? "-" : n + "/" + results.size();
    }

    private static double mrr(List<QuestionResult> results) {
        return results.stream().mapToDouble(r -> r.rank() == 0 ? 0 : 1.0 / r.rank()).average().orElse(0);
    }

    // ==================== Production pipeline, in memory ====================

    private GeminiChatService chatServiceFor(EvalDocument doc, byte[] pdf, List<TextChunk> chunks, List<float[]> vectors,
                                             GeminiEmbeddingClient embeddings) throws Exception {
        String key = "chatbot/" + doc.file();
        IndexedDocument indexed = new IndexedDocument();
        indexed.setId(1L);
        indexed.setChunkCount(chunks.size());
        List<DocumentChunk> rows = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            TextChunk chunk = chunks.get(i);
            rows.add(new DocumentChunk(indexed, chunk.pageNumber(), chunk.index(), chunk.text(), VectorMath.toBytes(vectors.get(i))));
        }
        DocumentChunkRepository chunkRepository = mock(DocumentChunkRepository.class);
        when(chunkRepository.findByDocumentIdIn(any())).thenReturn(rows);
        DocumentIndexService indexService = mock(DocumentIndexService.class);
        when(indexService.indexIfNeeded(key)).thenReturn(indexed);
        S3Service s3Service = mock(S3Service.class);
        when(s3Service.extractFileNameFromUrl(urlOf(key))).thenReturn(key);
        when(s3Service.getFileBytes(key)).thenReturn(pdf);

        RestTemplate recorder = mock(RestTemplate.class);
        return new GeminiChatService(recorder, objectMapper, s3Service, indexService,
                new DocumentRetriever(embeddings, chunkRepository, TOP_K), new AssistantToolbox(List.of(), objectMapper));
    }

    /** Records the request the chat service would send to Gemini instead of sending it. */
    private AtomicReference<Map<String, Object>> recorderOf(GeminiChatService chat) {
        RestTemplate recorder = (RestTemplate) ReflectionTestUtils.getField(chat, "restTemplate");
        AtomicReference<Map<String, Object>> sent = new AtomicReference<>();
        when(recorder.postForObject(anyString(), any(HttpEntity.class), eq(String.class))).thenAnswer(invocation -> {
            HttpEntity<Map<String, Object>> request = invocation.getArgument(1);
            sent.set(request.getBody());
            return "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ok\"}]}}]}";
        });
        return sent;
    }

    private static ChatbotRequest pdfQuestion(String question, String url) {
        ChatbotRequest request = new ChatbotRequest(question, 1L);
        request.setPdfUrls(List.of(url));
        return request;
    }

    private static String urlOf(String key) {
        return "https://bucket.s3.amazonaws.com/" + key;
    }

    private String userText(Map<String, Object> request) {
        return objectMapper.valueToTree(request).path("contents").path(0).path("parts").path(0).path("text").asText();
    }

    private int countTokens(String apiKey, String model, Map<String, Object> generateRequest) throws Exception {
        Map<String, Object> request = Map.of("generateContentRequest", Map.of(
                "model", "models/" + model,
                "systemInstruction", generateRequest.get("systemInstruction"),
                "contents", generateRequest.get("contents")));
        String response = withQuotaRetry(() -> http.postForObject(BASE_URL + "/models/" + model + ":countTokens",
                new HttpEntity<>(request, headers(apiKey)), String.class));
        return objectMapper.readTree(response).path("totalTokens").asInt();
    }

    // ==================== Embeddings, cached on disk ====================

    /** Texts embedded per minute, under the free tier's limit of 100. */
    private static final int SLICE = 90;

    /** Runs a call, waiting out the per-minute quota (HTTP 429) up to five times. */
    private static <T> T withQuotaRetry(java.util.concurrent.Callable<T> call) {
        for (int attempt = 1; ; attempt++) {
            try {
                return call.call();
            } catch (org.springframework.web.client.HttpClientErrorException.TooManyRequests e) {
                if (attempt == 5) {
                    throw e;
                }
                System.out.println("quota reached, waiting a minute (attempt " + attempt + ")");
                try {
                    Thread.sleep(65_000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** The production embeddings client, with vectors cached so a re-run doesn't re-embed anything. */
    private class CachingEmbeddings extends GeminiEmbeddingClient {

        private final Path dir = Path.of("target", "rag-eval-cache");
        private final Map<String, String> queries;

        CachingEmbeddings(String apiKey) throws Exception {
            super(http, objectMapper, apiKey, BASE_URL, EMBEDDING_MODEL, DIMENSIONS, 2000);
            Files.createDirectories(dir);
            Path file = dir.resolve(modelId().replace('/', '-') + "-queries.json");
            queries = Files.exists(file)
                    ? objectMapper.readValue(file.toFile(), objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, String.class))
                    : new LinkedHashMap<>();
        }

        List<float[]> embedChunks(String sha256, List<TextChunk> chunks) throws Exception {
            Path file = dir.resolve(sha256 + "-" + modelId().replace('/', '-') + "-" + CHUNK_SIZE + "-" + OVERLAP + ".json");
            if (Files.exists(file)) {
                JsonNode cached = objectMapper.readTree(file.toFile());
                if (cached.size() == chunks.size()) {
                    List<float[]> vectors = new ArrayList<>();
                    cached.forEach(v -> vectors.add(VectorMath.fromBytes(Base64.getDecoder().decode(v.asText()))));
                    return vectors;
                }
            }
            // The free tier allows about 100 embedded texts a minute, so embed in paced slices.
            List<String> texts = chunks.stream().map(TextChunk::text).toList();
            List<float[]> vectors = new ArrayList<>();
            for (int from = 0; from < texts.size(); from += SLICE) {
                if (from > 0) {
                    Thread.sleep(61_000);
                }
                List<String> slice = texts.subList(from, Math.min(from + SLICE, texts.size()));
                vectors.addAll(withQuotaRetry(() -> embedDocuments(slice)));
                System.out.printf("embedded %d/%d chunks%n", vectors.size(), texts.size());
            }
            objectMapper.writeValue(file.toFile(), vectors.stream().map(v -> Base64.getEncoder().encodeToString(VectorMath.toBytes(v))).toList());
            return vectors;
        }

        @Override
        public float[] embedQuery(String question) {
            String hit = queries.get(question);
            if (hit != null) {
                return VectorMath.fromBytes(Base64.getDecoder().decode(hit));
            }
            float[] vector = withQuotaRetry(() -> super.embedQuery(question));
            queries.put(question, Base64.getEncoder().encodeToString(VectorMath.toBytes(vector)));
            try {
                objectMapper.writeValue(dir.resolve(modelId().replace('/', '-') + "-queries.json").toFile(), queries);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            return vector;
        }
    }

    // ==================== Helpers ====================

    private EvalSet loadSet() throws Exception {
        String override = System.getenv("RAG_EVAL_SET");
        if (override != null && !override.isBlank()) {
            return objectMapper.readValue(Path.of(override).toFile(), EvalSet.class);
        }
        try (InputStream in = getClass().getResourceAsStream("/rag-eval/questions.json")) {
            return objectMapper.readValue(in, EvalSet.class);
        }
    }

    /**
     * Matching that ignores what PDF text extraction changes: case, line breaks, ligatures, curly
     * quotes and dashes. A hyphen at a line break may or may not belong to the word, so both readings count.
     */
    static boolean contains(String text, String phrase) {
        String needle = normalize(phrase, true);
        return normalize(text, true).contains(needle) || normalize(text, false).contains(needle);
    }

    private static boolean containsAll(String text, List<String> phrases) {
        return phrases.stream().allMatch(phrase -> contains(text, phrase));
    }

    private static String normalize(String text, boolean keepBreakHyphens) {
        String s = text.replace("ﬁ", "fi").replace("ﬂ", "fl").replace("ﬀ", "ff")
                .replace("ﬃ", "ffi").replace("ﬄ", "ffl").replace("­", "")
                .replace('’', '\'').replace('‘', '\'').replace('“', '"').replace('”', '"')
                .replace('–', '-').replace('—', '-').replace('−', '-').replace(' ', ' ');
        s = keepBreakHyphens ? s.replaceAll("-\\s*\\R\\s*", "-") : s.replaceAll("-\\s*\\R\\s*", "");
        return s.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT).trim();
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
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
}
