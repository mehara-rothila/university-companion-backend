package com.smartuniversity.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;

public class UserFileResponse {
    private Long id;
    private String fileUrl;
    private String fileName;
    private String fileType; // "image" or "pdf"
    private String source; // "lost_found", "book_photo", "book_pdf"
    private String sourceTitle; // Title of the item/book
    private Long fileSize; // in bytes, null for images
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss", timezone = "UTC")
    private LocalDateTime uploadedAt;
    
    private String category; // Additional context

    public UserFileResponse() {}

    public UserFileResponse(Long id, String fileUrl, String fileName, String fileType,
                           String source, String sourceTitle, Long fileSize,
                           LocalDateTime uploadedAt, String category) {
        this.id = id;
        this.fileUrl = fileUrl;
        this.fileName = fileName;
        this.fileType = fileType;
        this.source = source;
        this.sourceTitle = sourceTitle;
        this.fileSize = fileSize;
        this.uploadedAt = uploadedAt;
        this.category = category;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getSourceTitle() { return sourceTitle; }
    public void setSourceTitle(String sourceTitle) { this.sourceTitle = sourceTitle; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
