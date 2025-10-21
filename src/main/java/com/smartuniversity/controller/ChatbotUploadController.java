package com.smartuniversity.controller;

import com.smartuniversity.model.ChatbotUpload;
import com.smartuniversity.repository.ChatbotUploadRepository;
import com.smartuniversity.service.S3Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/chatbot/uploads")
public class ChatbotUploadController {

    @Autowired
    private ChatbotUploadRepository chatbotUploadRepository;

    @Autowired
    private S3Service s3Service;

    @PostMapping
    public ResponseEntity<?> saveUpload(@RequestBody Map<String, Object> uploadData) {
        try {
            ChatbotUpload upload = new ChatbotUpload();
            upload.setUserId(Long.valueOf(uploadData.get("userId").toString()));
            upload.setFileUrl((String) uploadData.get("fileUrl"));
            upload.setFileName((String) uploadData.get("fileName"));
            upload.setFileType(ChatbotUpload.FileType.valueOf(((String) uploadData.get("fileType")).toUpperCase()));

            if (uploadData.containsKey("fileSize") && uploadData.get("fileSize") != null) {
                upload.setFileSize(Long.valueOf(uploadData.get("fileSize").toString()));
            }

            ChatbotUpload savedUpload = chatbotUploadRepository.save(upload);

            Map<String, Object> response = new HashMap<>();
            response.put("id", savedUpload.getId());
            response.put("message", "File tracked successfully");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Failed to save upload: " + e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUpload(@PathVariable Long id, @RequestParam Long userId) {
        try {
            ChatbotUpload upload = chatbotUploadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Upload not found"));

            // Verify ownership
            if (!upload.getUserId().equals(userId)) {
                return ResponseEntity.status(403).body(Map.of("error", "Unauthorized"));
            }

            // Delete from S3
            if (upload.getFileUrl() != null && !upload.getFileUrl().isEmpty()) {
                String fileName = s3Service.extractFileNameFromUrl(upload.getFileUrl());
                s3Service.deleteFile(fileName);
            }

            // Soft delete
            upload.setDeletedAt(LocalDateTime.now());
            chatbotUploadRepository.save(upload);

            return ResponseEntity.ok(Map.of("message", "File deleted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
