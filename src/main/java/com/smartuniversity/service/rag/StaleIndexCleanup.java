package com.smartuniversity.service.rag;

import com.smartuniversity.model.ChatbotUpload;
import com.smartuniversity.model.IndexedDocument;
import com.smartuniversity.repository.ChatbotUploadRepository;
import com.smartuniversity.repository.IndexedDocumentRepository;
import com.smartuniversity.service.S3Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Deletes stored chunks that should no longer exist: those of PDF uploads a student has deleted,
 * and those of any indexed PDF whose file is no longer in storage (deleted some other way, or
 * deleted while its chunk cleanup failed). It runs once at startup and is idempotent.
 *
 * <p>Only S3's own "not found" counts as gone: a file S3 can't be asked about keeps its chunks.
 * If every indexed file looks gone at once, the storage settings are more likely wrong than the
 * files deleted, so that part is skipped. It never stops the app from starting.</p>
 */
@Component
public class StaleIndexCleanup implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StaleIndexCleanup.class);

    /** Below this many indexed files, "all of them are gone" is plausible and isn't treated as a misconfiguration. */
    static final int MISCONFIGURATION_THRESHOLD = 3;

    private final ChatbotUploadRepository chatbotUploadRepository;
    private final IndexedDocumentRepository indexedDocumentRepository;
    private final S3Service s3Service;
    private final DocumentIndexService documentIndexService;

    public StaleIndexCleanup(ChatbotUploadRepository chatbotUploadRepository,
                             IndexedDocumentRepository indexedDocumentRepository,
                             S3Service s3Service, DocumentIndexService documentIndexService) {
        this.chatbotUploadRepository = chatbotUploadRepository;
        this.indexedDocumentRepository = indexedDocumentRepository;
        this.s3Service = s3Service;
        this.documentIndexService = documentIndexService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            Set<String> stale = new LinkedHashSet<>();
            for (ChatbotUpload upload : chatbotUploadRepository.findByDeletedAtIsNotNullAndFileType(ChatbotUpload.FileType.PDF)) {
                String s3Key = s3Service.extractFileNameFromUrl(upload.getFileUrl());
                if (s3Key != null) {
                    stale.add(s3Key);
                }
            }

            List<IndexedDocument> indexed = indexedDocumentRepository.findAll();
            List<String> missing = new ArrayList<>();
            for (IndexedDocument document : indexed) {
                if (!stale.contains(document.getS3Key()) && isGone(document.getS3Key())) {
                    missing.add(document.getS3Key());
                }
            }
            if (indexed.size() >= MISCONFIGURATION_THRESHOLD && missing.size() == indexed.size()) {
                log.warn("All {} indexed PDFs look missing from storage; check the S3 settings. Keeping their chunks.", indexed.size());
            } else {
                stale.addAll(missing);
            }

            long removed = stale.stream().filter(this::forget).count();
            if (removed > 0) {
                log.info("Deleted the stored chunks of {} PDF(s) that were deleted or are no longer in storage", removed);
            }
        } catch (Exception e) {
            log.warn("Could not check for stale stored chunks", e);
        }
    }

    private boolean isGone(String s3Key) {
        try {
            return !s3Service.fileExists(s3Key);
        } catch (Exception e) {
            log.debug("Could not check {} in storage; keeping its chunks", s3Key, e);
            return false;
        }
    }

    private boolean forget(String s3Key) {
        try {
            return documentIndexService.forget(s3Key);
        } catch (Exception e) {
            log.warn("Could not delete the stored chunks of {}", s3Key, e);
            return false;
        }
    }
}
