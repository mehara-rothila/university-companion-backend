package com.smartuniversity.repository;

import com.smartuniversity.model.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    List<DocumentChunk> findByDocumentIdIn(Collection<Long> documentIds);

    void deleteByDocumentId(Long documentId);
}
