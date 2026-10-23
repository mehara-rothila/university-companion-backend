package com.smartuniversity.service.rag;

import com.smartuniversity.model.ChatbotUpload;
import com.smartuniversity.repository.ChatbotUploadRepository;
import com.smartuniversity.service.S3Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Deletes the stored chunks of PDFs that students have already deleted. Deleting from the My
 * Uploads page used to leave them behind; this clears those at startup and catches any later
 * delete whose cleanup failed. Running it again changes nothing, and it never stops the app
 * from starting.
 */
@Component
public class DeletedUploadIndexCleanup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DeletedUploadIndexCleanup.class);

    private final ChatbotUploadRepository chatbotUploadRepository;
    private final S3Service s3Service;
    private final DocumentIndexService documentIndexService;

    public DeletedUploadIndexCleanup(ChatbotUploadRepository chatbotUploadRepository, S3Service s3Service,
                                     DocumentIndexService documentIndexService) {
        this.chatbotUploadRepository = chatbotUploadRepository;
        this.s3Service = s3Service;
        this.documentIndexService = documentIndexService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int removed = 0;
            for (ChatbotUpload upload : chatbotUploadRepository.findByDeletedAtIsNotNullAndFileType(ChatbotUpload.FileType.PDF)) {
                removed += forget(upload) ? 1 : 0;
            }
            if (removed > 0) {
                log.info("Deleted the stored chunks of {} deleted PDF upload(s)", removed);
            }
        } catch (Exception e) {
            log.warn("Could not check deleted uploads for stored chunks", e);
        }
    }

    private boolean forget(ChatbotUpload upload) {
        String s3Key = s3Service.extractFileNameFromUrl(upload.getFileUrl());
        if (s3Key == null) {
            return false;
        }
        try {
            return documentIndexService.forget(s3Key);
        } catch (Exception e) {
            log.warn("Could not delete the stored chunks of deleted upload {}", upload.getId(), e);
            return false;
        }
    }
}
