package com.smartuniversity.service.rag;

import com.smartuniversity.model.DocumentChunk;
import com.smartuniversity.model.IndexedDocument;
import com.smartuniversity.repository.DocumentChunkRepository;
import com.smartuniversity.repository.IndexedDocumentRepository;
import com.smartuniversity.service.S3Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Indexes a PDF the first time it is asked about: reads it from S3, splits it into chunks,
 * embeds them and stores the result, so later questions only need a similarity search.
 */
@Service
public class DocumentIndexService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIndexService.class);

    private final S3Service s3Service;
    private final PdfPageReader pdfPageReader;
    private final TextChunker textChunker;
    private final GeminiEmbeddingClient embeddingClient;
    private final IndexedDocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final TransactionTemplate transactionTemplate;
    private final int maxPages;
    private final int maxChunks;

    public DocumentIndexService(S3Service s3Service,
                                PdfPageReader pdfPageReader,
                                TextChunker textChunker,
                                GeminiEmbeddingClient embeddingClient,
                                IndexedDocumentRepository documentRepository,
                                DocumentChunkRepository chunkRepository,
                                PlatformTransactionManager transactionManager,
                                @Value("${rag.max-pages:300}") int maxPages,
                                @Value("${rag.max-chunks:800}") int maxChunks) {
        this.s3Service = s3Service;
        this.pdfPageReader = pdfPageReader;
        this.textChunker = textChunker;
        this.embeddingClient = embeddingClient;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.maxPages = maxPages;
        this.maxChunks = maxChunks;
    }

    /**
     * Returns the indexed document for this S3 key, indexing it first if it is new or was
     * embedded with a different model than the current one.
     */
    public IndexedDocument indexIfNeeded(String s3Key) throws IOException {
        Optional<IndexedDocument> existing = documentRepository.findByS3Key(s3Key);
        if (existing.isPresent() && existing.get().getEmbeddingModel().equals(embeddingClient.modelId())) {
            return existing.get();
        }

        PdfPageReader.PdfPages pages = pdfPageReader.read(s3Service.getFileBytes(s3Key), maxPages);
        List<TextChunk> chunks = textChunker.chunk(pages.pages());
        if (chunks.size() > maxChunks) {
            log.warn("{} produced {} chunks; indexing only the first {}", s3Key, chunks.size(), maxChunks);
            chunks = chunks.subList(0, maxChunks);
        }

        // Embedding is the slow step and the one most likely to fail, so it happens before
        // anything is written. A PDF with no extractable text (a scan) is still recorded, with
        // no chunks, so it isn't downloaded and parsed again on every question.
        List<float[]> vectors = chunks.isEmpty()
                ? List.of()
                : embeddingClient.embedDocuments(chunks.stream().map(TextChunk::text).toList());

        try {
            return save(s3Key, existing.orElse(null), pages, chunks, vectors);
        } catch (DataIntegrityViolationException e) {
            // Another request indexed the same file at the same moment; use its result.
            return documentRepository.findByS3Key(s3Key).orElseThrow(() -> e);
        }
    }

    /** Deletes a PDF's stored chunks, e.g. when its upload is deleted. */
    public void forget(String s3Key) {
        transactionTemplate.executeWithoutResult(status ->
                documentRepository.findByS3Key(s3Key).ifPresent(document -> {
                    chunkRepository.deleteByDocumentId(document.getId());
                    documentRepository.delete(document);
                }));
    }

    private IndexedDocument save(String s3Key, IndexedDocument stale, PdfPageReader.PdfPages pages,
                                 List<TextChunk> chunks, List<float[]> vectors) {
        return transactionTemplate.execute(status -> {
            if (stale != null) {
                chunkRepository.deleteByDocumentId(stale.getId());
                documentRepository.delete(stale);
                documentRepository.flush(); // frees the unique s3Key before inserting again
            }

            IndexedDocument document = new IndexedDocument();
            document.setS3Key(s3Key);
            document.setPagesIndexed(pages.pages().size());
            document.setTotalPages(pages.totalPages());
            document.setChunkCount(chunks.size());
            document.setEmbeddingModel(embeddingClient.modelId());
            IndexedDocument saved = documentRepository.save(document);

            List<DocumentChunk> rows = new ArrayList<>(chunks.size());
            for (int i = 0; i < chunks.size(); i++) {
                TextChunk chunk = chunks.get(i);
                rows.add(new DocumentChunk(saved, chunk.pageNumber(), chunk.index(), chunk.text(),
                        VectorMath.toBytes(vectors.get(i))));
            }
            chunkRepository.saveAll(rows);

            log.info("Indexed {}: {} of {} pages, {} chunks", s3Key, pages.pages().size(), pages.totalPages(), rows.size());
            return saved;
        });
    }
}
