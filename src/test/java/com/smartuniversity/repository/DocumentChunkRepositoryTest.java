package com.smartuniversity.repository;

import com.smartuniversity.model.DocumentChunk;
import com.smartuniversity.model.IndexedDocument;
import com.smartuniversity.service.rag.VectorMath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Use the H2 settings from src/test/resources/application.properties instead of a default
// embedded database, so the whole schema builds the same way it does for the other tests.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class DocumentChunkRepositoryTest {

    @Autowired
    private IndexedDocumentRepository documents;

    @Autowired
    private DocumentChunkRepository chunks;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void storesLongTextAndEmbeddingsIntact() {
        IndexedDocument document = documents.save(document("chatbot/notes.pdf"));
        String longText = "Recursion needs a base case. ".repeat(200);
        float[] vector = VectorMath.normalize(new float[]{1, 2, 3, 4});
        chunks.save(new DocumentChunk(document, 3, 0, longText, VectorMath.toBytes(vector)));
        entityManager.flush();
        entityManager.clear();

        List<DocumentChunk> found = chunks.findByDocumentIdIn(List.of(document.getId()));

        assertEquals(1, found.size());
        assertEquals(longText, found.get(0).getContent());
        assertEquals(3, found.get(0).getPageNumber());
        assertArrayEquals(vector, VectorMath.fromBytes(found.get(0).getEmbedding()));
    }

    @Test
    void findsChunksOnlyForTheRequestedDocuments() {
        IndexedDocument wanted = documents.save(document("chatbot/a.pdf"));
        IndexedDocument other = documents.save(document("chatbot/b.pdf"));
        chunks.save(chunk(wanted, 0));
        chunks.save(chunk(wanted, 1));
        chunks.save(chunk(other, 0));

        List<DocumentChunk> found = chunks.findByDocumentIdIn(List.of(wanted.getId()));

        assertEquals(2, found.size());
        assertTrue(found.stream().allMatch(c -> c.getDocument().getId().equals(wanted.getId())));
    }

    @Test
    void findsADocumentByItsS3Key() {
        documents.save(document("chatbot/lecture-1.pdf"));

        assertTrue(documents.findByS3Key("chatbot/lecture-1.pdf").isPresent());
        assertTrue(documents.findByS3Key("chatbot/missing.pdf").isEmpty());
    }

    @Test
    void allowsEachS3KeyOnlyOnce() {
        documents.saveAndFlush(document("chatbot/dup.pdf"));

        assertThrows(DataIntegrityViolationException.class, () -> documents.saveAndFlush(document("chatbot/dup.pdf")));
    }

    @Test
    void deletesAllChunksOfADocument() {
        IndexedDocument document = documents.save(document("chatbot/gone.pdf"));
        chunks.save(chunk(document, 0));
        chunks.save(chunk(document, 1));

        chunks.deleteByDocumentId(document.getId());
        entityManager.flush();

        assertTrue(chunks.findByDocumentIdIn(List.of(document.getId())).isEmpty());
    }

    private static IndexedDocument document(String s3Key) {
        IndexedDocument document = new IndexedDocument();
        document.setS3Key(s3Key);
        document.setPagesIndexed(1);
        document.setTotalPages(1);
        document.setChunkCount(0);
        document.setEmbeddingModel("gemini-embedding-2/4");
        return document;
    }

    private static DocumentChunk chunk(IndexedDocument document, int index) {
        return new DocumentChunk(document, 1, index, "Chunk " + index + " text.", VectorMath.toBytes(new float[]{1, 0, 0, 0}));
    }
}
