package com.smartuniversity.service.rag;

import com.smartuniversity.model.IndexedDocument;
import com.smartuniversity.repository.DocumentChunkRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Picks the chunks of the attached PDFs that best match a question, by cosine similarity between
 * the question's embedding and each chunk's.
 */
@Service
public class DocumentRetriever {

    private final GeminiEmbeddingClient embeddingClient;
    private final DocumentChunkRepository chunkRepository;
    private final int topK;

    public DocumentRetriever(GeminiEmbeddingClient embeddingClient,
                             DocumentChunkRepository chunkRepository,
                             @Value("${rag.top-k:6}") int topK) {
        this.embeddingClient = embeddingClient;
        this.chunkRepository = chunkRepository;
        this.topK = topK;
    }

    /**
     * Returns the {@code top-k} most similar chunks across {@code documents}, in reading order
     * (by document, then position) so the excerpts read naturally in the prompt.
     */
    public List<RetrievedChunk> retrieve(String question, List<IndexedDocument> documents) {
        Map<Long, Integer> documentNumbers = new HashMap<>();
        for (int i = 0; i < documents.size(); i++) {
            IndexedDocument document = documents.get(i);
            if (document.getChunkCount() > 0) {
                documentNumbers.putIfAbsent(document.getId(), i + 1);
            }
        }
        if (documentNumbers.isEmpty()) {
            return List.of();
        }

        float[] query = embeddingClient.embedQuery(question);
        return chunkRepository.findByDocumentIdIn(documentNumbers.keySet()).stream()
                .map(chunk -> new RetrievedChunk(
                        documentNumbers.get(chunk.getDocument().getId()),
                        chunk.getPageNumber(),
                        chunk.getChunkIndex(),
                        chunk.getContent(),
                        VectorMath.dot(query, VectorMath.fromBytes(chunk.getEmbedding()))))
                .sorted(Comparator.comparingDouble(RetrievedChunk::score).reversed())
                .limit(topK)
                .sorted(Comparator.comparingInt(RetrievedChunk::documentNumber)
                        .thenComparingInt(RetrievedChunk::chunkIndex))
                .toList();
    }
}
