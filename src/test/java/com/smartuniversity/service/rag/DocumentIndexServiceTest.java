package com.smartuniversity.service.rag;

import com.smartuniversity.model.DocumentChunk;
import com.smartuniversity.model.IndexedDocument;
import com.smartuniversity.repository.DocumentChunkRepository;
import com.smartuniversity.repository.IndexedDocumentRepository;
import com.smartuniversity.service.S3Service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentIndexServiceTest {

    private static final String KEY = "chatbot/notes.pdf";
    private static final String MODEL = "gemini-embedding-2/3";

    @Mock
    private S3Service s3Service;

    @Mock
    private GeminiEmbeddingClient embeddingClient;

    @Mock
    private IndexedDocumentRepository documentRepository;

    @Mock
    private DocumentChunkRepository chunkRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Captor
    private ArgumentCaptor<List<DocumentChunk>> rowsCaptor;

    @Test
    void returnsAnAlreadyIndexedDocumentWithoutDownloadingIt() throws Exception {
        IndexedDocument existing = indexed(5L, MODEL);
        when(documentRepository.findByS3Key(KEY)).thenReturn(Optional.of(existing));
        when(embeddingClient.modelId()).thenReturn(MODEL);

        assertSame(existing, service(300).indexIfNeeded(KEY));
        verifyNoInteractions(s3Service);
        verify(embeddingClient, never()).embedDocuments(any());
    }

    @Test
    void indexesANewPdfPageByPage() throws Exception {
        when(documentRepository.findByS3Key(KEY)).thenReturn(Optional.empty());
        when(s3Service.getFileBytes(KEY)).thenReturn(TestPdfs.withPages(
                "Recursion needs a base case to stop.", "Binary search halves the range each step."));
        stubEmbeddingAndSave(7L);

        IndexedDocument result = service(300).indexIfNeeded(KEY);

        assertEquals(7L, result.getId());
        assertEquals(KEY, result.getS3Key());
        assertEquals(2, result.getPagesIndexed());
        assertEquals(2, result.getTotalPages());
        assertEquals(2, result.getChunkCount());
        assertEquals(MODEL, result.getEmbeddingModel());

        verify(chunkRepository).saveAll(rowsCaptor.capture());
        List<DocumentChunk> rows = rowsCaptor.getValue();
        assertEquals(List.of(1, 2), rows.stream().map(DocumentChunk::getPageNumber).toList());
        assertTrue(rows.get(0).getContent().contains("base case"));
        assertTrue(rows.get(1).getContent().contains("Binary search"));
        assertSame(result, rows.get(0).getDocument());
        assertEquals(3, VectorMath.fromBytes(rows.get(0).getEmbedding()).length);
    }

    @Test
    void reindexesADocumentEmbeddedWithAnOlderModel() throws Exception {
        IndexedDocument stale = indexed(5L, "text-embedding-004/768");
        when(documentRepository.findByS3Key(KEY)).thenReturn(Optional.of(stale));
        when(s3Service.getFileBytes(KEY)).thenReturn(TestPdfs.withPages("Recursion needs a base case to stop."));
        stubEmbeddingAndSave(8L);

        IndexedDocument result = service(300).indexIfNeeded(KEY);

        verify(chunkRepository).deleteByDocumentId(5L);
        verify(documentRepository).delete(stale);
        assertEquals(8L, result.getId());
        assertEquals(MODEL, result.getEmbeddingModel());
    }

    @Test
    void readsOnlyUpToThePageLimit() throws Exception {
        when(documentRepository.findByS3Key(KEY)).thenReturn(Optional.empty());
        when(s3Service.getFileBytes(KEY)).thenReturn(TestPdfs.withPages(
                "Recursion needs a base case to stop.", "Binary search halves the range each step."));
        stubEmbeddingAndSave(7L);

        IndexedDocument result = service(1).indexIfNeeded(KEY);

        assertEquals(1, result.getPagesIndexed());
        assertEquals(2, result.getTotalPages());
        assertEquals(1, result.getChunkCount());
    }

    @Test
    void recordsAPdfWithNoTextWithoutCallingTheEmbeddingApi() throws Exception {
        when(documentRepository.findByS3Key(KEY)).thenReturn(Optional.empty());
        when(s3Service.getFileBytes(KEY)).thenReturn(TestPdfs.withPages("", ""));
        when(embeddingClient.modelId()).thenReturn(MODEL);
        when(documentRepository.save(any(IndexedDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IndexedDocument result = service(300).indexIfNeeded(KEY);

        assertEquals(0, result.getChunkCount());
        verify(embeddingClient, never()).embedDocuments(any());
    }

    @Test
    void usesTheOtherRequestsResultWhenTwoIndexTheSamePdfAtOnce() throws Exception {
        IndexedDocument winner = indexed(9L, MODEL);
        when(documentRepository.findByS3Key(KEY)).thenReturn(Optional.empty()).thenReturn(Optional.of(winner));
        when(s3Service.getFileBytes(KEY)).thenReturn(TestPdfs.withPages("Recursion needs a base case to stop."));
        when(embeddingClient.modelId()).thenReturn(MODEL);
        when(embeddingClient.embedDocuments(anyList())).thenAnswer(invocation -> unitVectors(invocation.getArgument(0)));
        when(documentRepository.save(any(IndexedDocument.class))).thenThrow(new DataIntegrityViolationException("duplicate s3_key"));

        assertSame(winner, service(300).indexIfNeeded(KEY));
    }

    @Test
    void forgetDeletesTheChunksAndTheDocument() {
        IndexedDocument document = indexed(5L, MODEL);
        when(documentRepository.findByS3Key(KEY)).thenReturn(Optional.of(document));

        assertTrue(service(300).forget(KEY));

        verify(chunkRepository).deleteByDocumentId(5L);
        verify(documentRepository).delete(document);
    }

    @Test
    void forgetIgnoresFilesThatWereNeverIndexed() {
        when(documentRepository.findByS3Key(KEY)).thenReturn(Optional.empty());

        assertFalse(service(300).forget(KEY));

        verifyNoInteractions(chunkRepository);
        verify(documentRepository, never()).delete(any());
    }

    private DocumentIndexService service(int maxPages) {
        return new DocumentIndexService(s3Service, new PdfPageReader(), new TextChunker(1200, 200),
                embeddingClient, documentRepository, chunkRepository, transactionManager, maxPages, 800);
    }

    private void stubEmbeddingAndSave(long newId) {
        when(embeddingClient.modelId()).thenReturn(MODEL);
        when(embeddingClient.embedDocuments(anyList())).thenAnswer(invocation -> unitVectors(invocation.getArgument(0)));
        when(documentRepository.save(any(IndexedDocument.class))).thenAnswer(invocation -> {
            IndexedDocument document = invocation.getArgument(0);
            document.setId(newId);
            return document;
        });
    }

    private static List<float[]> unitVectors(List<?> texts) {
        List<float[]> vectors = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            vectors.add(new float[]{1, 0, 0});
        }
        return vectors;
    }

    private static IndexedDocument indexed(long id, String model) {
        IndexedDocument document = new IndexedDocument();
        document.setId(id);
        document.setS3Key(KEY);
        document.setPagesIndexed(1);
        document.setTotalPages(1);
        document.setChunkCount(1);
        document.setEmbeddingModel(model);
        return document;
    }
}
