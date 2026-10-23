package com.smartuniversity.repository;

import com.smartuniversity.model.ChatbotUpload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatbotUploadRepository extends JpaRepository<ChatbotUpload, Long> {

    List<ChatbotUpload> findByUserIdAndDeletedAtIsNullOrderByUploadedAtDesc(Long userId);

    /** Uploads a student has deleted, of one file type. */
    List<ChatbotUpload> findByDeletedAtIsNotNullAndFileType(ChatbotUpload.FileType fileType);

    Long countByUserIdAndDeletedAtIsNull(Long userId);

    Long countByUserIdAndFileTypeAndDeletedAtIsNull(Long userId, ChatbotUpload.FileType fileType);
}
