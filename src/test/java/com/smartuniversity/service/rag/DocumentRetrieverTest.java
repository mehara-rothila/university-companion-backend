package com.smartuniversity.service.rag;

import com.smartuniversity.model.DocumentChunk;
import com.smartuniversity.model.IndexedDocument;
import com.smartuniversity.repository.DocumentChunkRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentRetrieverTest {

    @Mock
    private GeminiEmbeddingClient embeddingClient;

    @Mock
    private DocumentChunkRepository chunkRepository;

    @Test
    void returnsTheMostSimilarChunksInReadingOrder() {
        IndexedDocument notes = document(1L, 3);
        when(embeddingClient.embedQuery("What stops recursion?")).thenReturn(new float[]{1, 0, 0});
        when(chunkRepository.findByDocumentIdIn(Set.of(1L))).thenReturn(List.of(
                chunk(notes, 1, 0, "Base cases stop recursion.", 1, 0, 0),
                chunk(notes, 2, 1, "The library opens at 8.", 0, 1, 0),
                chunk(notes, 3, 2, "Each call must move toward the base case.", 0.9f, 0.1f, 0)));

        List<RetrievedChunk> result = retriever(2).retrieve("What stops recursion?", List.of(notes));

        assertEquals(List.of(1, 3), result.stream().map(RetrievedChunk::pageNumber).toList());
        assertEquals(1.0, result.get(0).score(), 1e-6);
        assertTrue(result.get(1).score() > 0.9);
    }

    @Test
    void numbersChunksByTheirPdfsPositionInTheQuestion() {
        IndexedDocument first = document(10L, 1);
        IndexedDocument second = document(20L, 1);
        when(embeddingClient.embedQuery(any())).thenReturn(new float[]{1, 0, 0});
        when(chunkRepository.findByDocumentIdIn(Set.of(10L, 20L))).thenReturn(List.of(
                chunk(second, 4, 0, "From the second PDF.", 1, 0, 0),
                chunk(first, 2, 0, "From the first PDF.", 0.5f, 0.5f, 0)));

        List<RetrievedChunk> result = retriever(6).retrieve("question", List.of(first, second));

        assertEquals(2, result.size());
        assertEquals(1, result.get(0).documentNumber());
        assertEquals("From the first PDF.", result.get(0).text());
        assertEquals(2, result.get(1).documentNumber());
        assertEquals(4, result.get(1).pageNumber());
    }

    @Test
    void skipsPdfsWithNoTextWithoutCallingTheApi() {
        List<RetrievedChunk> result = retriever(6).retrieve("question", List.of(document(1L, 0)));

        assertTrue(result.isEmpty());
        verifyNoInteractions(embeddingClient, chunkRepository);
    }

    private DocumentRetriever retriever(int topK) {
        return new DocumentRetriever(embeddingClient, chunkRepository, topK);
    }

    private static IndexedDocument document(long id, int chunkCount) {
        IndexedDocument document = new IndexedDocument();
        document.setId(id);
        document.setChunkCount(chunkCount);
        return document;
    }

    private static DocumentChunk chunk(IndexedDocument document, int page, int index, String text, float... vector) {
        return new DocumentChunk(document, page, index, text, VectorMath.toBytes(VectorMath.normalize(vector)));
    }
}
