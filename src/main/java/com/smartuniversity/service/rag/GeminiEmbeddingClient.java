package com.smartuniversity.service.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Turns text into embedding vectors with the Gemini embeddings API ({@code batchEmbedContents}).
 *
 * <p>gemini-embedding-2 has no task-type field. Instead, Google asks for a short prefix that marks
 * each text as a document to be searched or as a search query, and this client adds it. Returned
 * vectors are normalised, so the dot product of two of them is their cosine similarity.</p>
 */
@Component
public class GeminiEmbeddingClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiEmbeddingClient.class);

    /** Texts sent per batchEmbedContents call. */
    static final int BATCH_SIZE = 32;

    private static final int MAX_ATTEMPTS = 3;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final int dimensions;
    private final long retryBackoffMillis;

    public GeminiEmbeddingClient(RestTemplate restTemplate,
                                 ObjectMapper objectMapper,
                                 @Value("${gemini.api.key}") String apiKey,
                                 @Value("${gemini.api.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
                                 @Value("${gemini.embedding.model:gemini-embedding-2}") String model,
                                 @Value("${gemini.embedding.dimensions:768}") int dimensions,
                                 @Value("${gemini.embedding.retry-backoff-ms:2000}") long retryBackoffMillis) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
        this.dimensions = dimensions;
        this.retryBackoffMillis = retryBackoffMillis;
    }

    /**
     * Names the kind of vector this client produces. Vectors from a different model or size
     * can't be compared with these, so stored chunks are re-embedded when this changes.
     */
    public String modelId() {
        return model + "/" + dimensions;
    }

    /** Embeds chunks of a document so they can be searched later. */
    public List<float[]> embedDocuments(List<String> texts) {
        return embed(texts.stream().map(text -> "title: none | text: " + text).toList());
    }

    /** Embeds a question to search the document chunks with. */
    public float[] embedQuery(String question) {
        return embed(List.of("task: search result | query: " + question)).get(0);
    }

    private List<float[]> embed(List<String> inputs) {
        List<float[]> vectors = new ArrayList<>(inputs.size());
        for (int from = 0; from < inputs.size(); from += BATCH_SIZE) {
            vectors.addAll(embedBatch(inputs.subList(from, Math.min(from + BATCH_SIZE, inputs.size()))));
        }
        return vectors;
    }

    private List<float[]> embedBatch(List<String> batch) {
        String url = baseUrl + "/models/" + model + ":batchEmbedContents";

        List<Map<String, Object>> requests = batch.stream()
                .map(text -> Map.<String, Object>of(
                        "model", "models/" + model,
                        "content", Map.of("parts", List.of(Map.of("text", text))),
                        "outputDimensionality", dimensions))
                .toList();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);

        String response = postWithRetry(url, new HttpEntity<>(Map.of("requests", requests), headers));
        return parse(response, batch.size());
    }

    /** Retries 429 and 503 responses, which Gemini returns when it is rate limiting or overloaded. */
    private String postWithRetry(String url, HttpEntity<Map<String, Object>> request) {
        for (int attempt = 1; ; attempt++) {
            try {
                return restTemplate.postForObject(url, request, String.class);
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                if ((status != 429 && status != 503) || attempt == MAX_ATTEMPTS) {
                    throw e;
                }
                long waitMillis = retryBackoffMillis << (attempt - 1);
                log.warn("Gemini embeddings returned {}; retrying in {} ms (attempt {} of {})",
                        status, waitMillis, attempt + 1, MAX_ATTEMPTS);
                sleep(waitMillis);
            }
        }
    }

    private List<float[]> parse(String response, int expectedCount) {
        JsonNode embeddings;
        try {
            embeddings = objectMapper.readTree(response).path("embeddings");
        } catch (Exception e) {
            throw new IllegalStateException("Gemini embeddings returned invalid JSON", e);
        }
        if (!embeddings.isArray() || embeddings.size() != expectedCount) {
            throw new IllegalStateException("Expected " + expectedCount + " embeddings, got "
                    + (embeddings.isArray() ? embeddings.size() : "none"));
        }

        List<float[]> vectors = new ArrayList<>(expectedCount);
        for (JsonNode embedding : embeddings) {
            JsonNode values = embedding.path("values");
            if (values.size() != dimensions) {
                throw new IllegalStateException("Expected " + dimensions + " values per embedding, got " + values.size());
            }
            float[] vector = new float[dimensions];
            for (int i = 0; i < dimensions; i++) {
                vector[i] = (float) values.get(i).asDouble();
            }
            vectors.add(VectorMath.normalize(vector));
        }
        return vectors;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting to retry Gemini embeddings", e);
        }
    }
}
