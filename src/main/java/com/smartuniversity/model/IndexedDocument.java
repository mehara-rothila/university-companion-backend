package com.smartuniversity.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A PDF that has been split into chunks and embedded, so questions about it can be answered
 * from its most relevant passages instead of from its full text.
 */
@Entity
@Table(name = "indexed_documents")
public class IndexedDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Where the PDF lives in S3. Each file is indexed once. */
    @Column(nullable = false, unique = true, length = 1000)
    private String s3Key;

    /** Pages read and chunked; lower than {@link #totalPages} when the PDF passed the page limit. */
    @Column(nullable = false)
    private Integer pagesIndexed;

    @Column(nullable = false)
    private Integer totalPages;

    @Column(nullable = false)
    private Integer chunkCount;

    /** Model and vector size used for the chunk embeddings, e.g. "gemini-embedding-2/768". */
    @Column(nullable = false, length = 100)
    private String embeddingModel;

    @Column(nullable = false, updatable = false)
    private LocalDateTime indexedAt;

    @PrePersist
    protected void onCreate() {
        indexedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getS3Key() {
        return s3Key;
    }

    public void setS3Key(String s3Key) {
        this.s3Key = s3Key;
    }

    public Integer getPagesIndexed() {
        return pagesIndexed;
    }

    public void setPagesIndexed(Integer pagesIndexed) {
        this.pagesIndexed = pagesIndexed;
    }

    public Integer getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(Integer totalPages) {
        this.totalPages = totalPages;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(Integer chunkCount) {
        this.chunkCount = chunkCount;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public LocalDateTime getIndexedAt() {
        return indexedAt;
    }

    public void setIndexedAt(LocalDateTime indexedAt) {
        this.indexedAt = indexedAt;
    }
}
