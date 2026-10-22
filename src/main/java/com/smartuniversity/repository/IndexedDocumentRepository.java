package com.smartuniversity.repository;

import com.smartuniversity.model.IndexedDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IndexedDocumentRepository extends JpaRepository<IndexedDocument, Long> {

    Optional<IndexedDocument> findByS3Key(String s3Key);
}
