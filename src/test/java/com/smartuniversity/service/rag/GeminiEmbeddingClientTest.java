package com.smartuniversity.service.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeminiEmbeddingClientTest {

    private static final String URL = "https://example.test/v1beta/models/gemini-embedding-2:batchEmbedContents";

    @Mock
    private RestTemplate restTemplate;

    @Captor
    private ArgumentCaptor<HttpEntity<Map<String, Object>>> requestCaptor;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private GeminiEmbeddingClient client;

    @BeforeEach
    void setUp() {
        client = new GeminiEmbeddingClient(restTemplate, objectMapper, "test-key",
                "https://example.test/v1beta", "gemini-embedding-2", 3, 0);
    }

    @Test
    void embedsDocumentsWithTheDocumentPrefixAndReturnsUnitVectors() {
        when(restTemplate.postForObject(eq(URL), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response(new float[]{3, 4, 0}, new float[]{0, 0, 2}));

        List<float[]> vectors = client.embedDocuments(List.of("first chunk", "second chunk"));

        assertEquals(2, vectors.size());
        assertArrayEquals(new float[]{0.6f, 0.8f, 0}, vectors.get(0), 1e-6f);
        assertArrayEquals(new float[]{0, 0, 1}, vectors.get(1), 1e-6f);

        verify(restTemplate).postForObject(eq(URL), requestCaptor.capture(), eq(String.class));
        HttpEntity<Map<String, Object>> request = requestCaptor.getValue();
        assertEquals("test-key", request.getHeaders().getFirst("x-goog-api-key"));
        JsonNode first = objectMapper.valueToTree(request.getBody()).path("requests").get(0);
        assertEquals("models/gemini-embedding-2", first.path("model").asText());
        assertEquals("title: none | text: first chunk", first.path("content").path("parts").get(0).path("text").asText());
        assertEquals(3, first.path("outputDimensionality").asInt());
    }

    @Test
    void embedsQueriesWithTheQueryPrefix() {
        when(restTemplate.postForObject(eq(URL), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response(new float[]{0, 1, 0}));

        float[] vector = client.embedQuery("What is a base case?");

        assertArrayEquals(new float[]{0, 1, 0}, vector, 1e-6f);
        verify(restTemplate).postForObject(eq(URL), requestCaptor.capture(), eq(String.class));
        assertEquals("task: search result | query: What is a base case?", textsIn(requestCaptor.getValue()).get(0));
    }

    @Test
    void splitsLargeInputsIntoBatches() {
        when(restTemplate.postForObject(eq(URL), any(HttpEntity.class), eq(String.class)))
                .thenAnswer(invocation -> {
                    HttpEntity<Map<String, Object>> request = invocation.getArgument(1);
                    float[][] vectors = new float[textsIn(request).size()][];
                    for (int i = 0; i < vectors.length; i++) {
                        vectors[i] = new float[]{1, 0, 0};
                    }
                    return response(vectors);
                });

        List<String> texts = new ArrayList<>(Collections.nCopies(70, "chunk"));
        List<float[]> vectors = client.embedDocuments(texts);

        assertEquals(70, vectors.size());
        verify(restTemplate, times(3)).postForObject(eq(URL), requestCaptor.capture(), eq(String.class));
        List<Integer> batchSizes = requestCaptor.getAllValues().stream().map(r -> textsIn(r).size()).toList();
        assertEquals(List.of(32, 32, 6), batchSizes);
    }

    @Test
    void retriesWhenTheApiIsBusy() {
        when(restTemplate.postForObject(eq(URL), any(HttpEntity.class), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
                        HttpHeaders.EMPTY, null, null))
                .thenReturn(response(new float[]{1, 0, 0}));

        assertEquals(1, client.embedDocuments(List.of("chunk")).size());
        verify(restTemplate, times(2)).postForObject(eq(URL), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void givesUpAfterThreeBusyResponses() {
        when(restTemplate.postForObject(eq(URL), any(HttpEntity.class), eq(String.class)))
                .thenThrow(HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "Unavailable",
                        HttpHeaders.EMPTY, null, null));

        assertThrows(RestClientResponseException.class, () -> client.embedDocuments(List.of("chunk")));
        verify(restTemplate, times(3)).postForObject(eq(URL), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void doesNotRetryOtherErrors() {
        when(restTemplate.postForObject(eq(URL), any(HttpEntity.class), eq(String.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request",
                        HttpHeaders.EMPTY, null, null));

        assertThrows(RestClientResponseException.class, () -> client.embedDocuments(List.of("chunk")));
        verify(restTemplate, times(1)).postForObject(eq(URL), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void rejectsAResponseWithTheWrongNumberOfVectors() {
        when(restTemplate.postForObject(eq(URL), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response(new float[]{1, 0, 0}));

        assertThrows(IllegalStateException.class, () -> client.embedDocuments(List.of("one", "two")));
    }

    @Test
    void rejectsVectorsOfTheWrongSize() {
        when(restTemplate.postForObject(eq(URL), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response(new float[]{1, 0}));

        assertThrows(IllegalStateException.class, () -> client.embedDocuments(List.of("one")));
    }

    @Test
    void modelIdNamesTheModelAndSize() {
        assertEquals("gemini-embedding-2/3", client.modelId());
    }

    @SuppressWarnings("unchecked")
    private List<String> textsIn(HttpEntity<Map<String, Object>> request) {
        List<Map<String, Object>> requests = (List<Map<String, Object>>) request.getBody().get("requests");
        return requests.stream()
                .map(r -> objectMapper.valueToTree(r).path("content").path("parts").get(0).path("text").asText())
                .toList();
    }

    private String response(float[]... vectors) {
        List<Map<String, Object>> embeddings = new ArrayList<>();
        for (float[] vector : vectors) {
            List<Float> values = new ArrayList<>();
            for (float v : vector) {
                values.add(v);
            }
            embeddings.add(Map.of("values", values));
        }
        try {
            return objectMapper.writeValueAsString(Map.of("embeddings", embeddings));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
