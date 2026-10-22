package com.smartuniversity.model;

import jakarta.persistence.*;

/** One passage of an {@link IndexedDocument}, with its page number and embedding. */
@Entity
@Table(name = "document_chunks",
        indexes = @Index(name = "idx_document_chunks_document_id", columnList = "document_id"))
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private IndexedDocument document;

    @Column(nullable = false)
    private Integer pageNumber;

    @Column(nullable = false)
    private Integer chunkIndex;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** The chunk's embedding as packed float32 values (see VectorMath.toBytes). */
    @Column(nullable = false, length = 12288)
    private byte[] embedding;

    public DocumentChunk() {
    }

    public DocumentChunk(IndexedDocument document, int pageNumber, int chunkIndex, String content, byte[] embedding) {
        this.document = document;
        this.pageNumber = pageNumber;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.embedding = embedding;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public IndexedDocument getDocument() {
        return document;
    }

    public void setDocument(IndexedDocument document) {
        this.document = document;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(Integer pageNumber) {
        this.pageNumber = pageNumber;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public byte[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(byte[] embedding) {
        this.embedding = embedding;
    }
}
