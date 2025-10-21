package com.smartuniversity.controller;

import com.smartuniversity.dto.UserFileResponse;
import com.smartuniversity.model.Book;
import com.smartuniversity.model.ChatbotUpload;
import com.smartuniversity.model.LostFoundItem;
import com.smartuniversity.repository.BookRepository;
import com.smartuniversity.repository.ChatbotUploadRepository;
import com.smartuniversity.repository.LostFoundItemRepository;
import com.smartuniversity.service.S3Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/files")
public class FileManagementController {

    @Autowired
    private LostFoundItemRepository lostFoundItemRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private ChatbotUploadRepository chatbotUploadRepository;

    @Autowired
    private S3Service s3Service;

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserFiles(@PathVariable Long userId) {
        List<UserFileResponse> userFiles = new ArrayList<>();

        // Get Lost & Found item images
        List<LostFoundItem> lostFoundItems = lostFoundItemRepository.findByPostedBy_Id(userId);
        for (LostFoundItem item : lostFoundItems) {
            if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
                String fileName = s3Service.extractFileNameFromUrl(item.getImageUrl());
                userFiles.add(new UserFileResponse(
                    item.getId(),
                    item.getImageUrl(),
                    fileName,
                    "image",
                    "lost_found",
                    item.getTitle(),
                    null, // Images don't have size tracked
                    item.getCreatedAt(),
                    item.getCategory()
                ));
            }
        }

        // Get Book photos (physical books)
        List<Book> booksWithPhotos = bookRepository.findByOwnerIdAndPhotoUrlIsNotNull(userId);
        for (Book book : booksWithPhotos) {
            String fileName = s3Service.extractFileNameFromUrl(book.getPhotoUrl());
            userFiles.add(new UserFileResponse(
                book.getId(),
                book.getPhotoUrl(),
                fileName,
                "image",
                "book_photo",
                book.getTitle(),
                null,
                book.getUploadDate(),
                book.getCategory().toString()
            ));
        }

        // Get Book PDFs (digital books)
        List<Book> booksWithPdfs = bookRepository.findByOwnerIdAndPdfUrlIsNotNull(userId);
        for (Book book : booksWithPdfs) {
            String fileName = s3Service.extractFileNameFromUrl(book.getPdfUrl());
            userFiles.add(new UserFileResponse(
                book.getId(),
                book.getPdfUrl(),
                fileName,
                "pdf",
                "book_pdf",
                book.getTitle(),
                book.getFileSize(),
                book.getUploadDate(),
                book.getCategory().toString()
            ));
        }

        // Get Chatbot uploads (images, PDFs, videos uploaded via chatbot)
        List<ChatbotUpload> chatbotUploads = chatbotUploadRepository.findByUserIdAndDeletedAtIsNullOrderByUploadedAtDesc(userId);
        for (ChatbotUpload upload : chatbotUploads) {
            String fileName = upload.getFileName();
            String fileType = upload.getFileType().toString().toLowerCase();
            userFiles.add(new UserFileResponse(
                upload.getId(),
                upload.getFileUrl(),
                fileName,
                fileType,
                "chatbot_upload",
                "Chatbot Upload: " + fileName,
                upload.getFileSize(),
                upload.getUploadedAt(),
                fileType
            ));
        }

        // Sort by upload date (most recent first)
        userFiles.sort((a, b) -> b.getUploadedAt().compareTo(a.getUploadedAt()));

        return ResponseEntity.ok(userFiles);
    }

    @GetMapping("/user/{userId}/stats")
    public ResponseEntity<?> getUserFileStats(@PathVariable Long userId) {
        Map<String, Object> stats = new HashMap<>();

        // Count files
        long lostFoundCount = lostFoundItemRepository.countByPostedBy_IdAndImageUrlIsNotNull(userId);
        long bookPhotoCount = bookRepository.countByOwnerIdAndPhotoUrlIsNotNull(userId);
        long bookPdfCount = bookRepository.countByOwnerIdAndPdfUrlIsNotNull(userId);
        long chatbotUploadCount = chatbotUploadRepository.countByUserIdAndDeletedAtIsNull(userId);
        long chatbotImageCount = chatbotUploadRepository.countByUserIdAndFileTypeAndDeletedAtIsNull(userId, ChatbotUpload.FileType.IMAGE);
        long chatbotPdfCount = chatbotUploadRepository.countByUserIdAndFileTypeAndDeletedAtIsNull(userId, ChatbotUpload.FileType.PDF);
        long chatbotVideoCount = chatbotUploadRepository.countByUserIdAndFileTypeAndDeletedAtIsNull(userId, ChatbotUpload.FileType.VIDEO);

        stats.put("totalFiles", lostFoundCount + bookPhotoCount + bookPdfCount + chatbotUploadCount);
        stats.put("imageCount", lostFoundCount + bookPhotoCount + chatbotImageCount);
        stats.put("pdfCount", bookPdfCount + chatbotPdfCount);
        stats.put("videoCount", chatbotVideoCount);

        // Calculate total storage used (PDFs and chatbot uploads have size tracked)
        List<Book> booksWithPdfs = bookRepository.findByOwnerIdAndPdfUrlIsNotNull(userId);
        long totalBytes = booksWithPdfs.stream()
            .filter(book -> book.getFileSize() != null)
            .mapToLong(Book::getFileSize)
            .sum();

        // Add chatbot upload sizes
        List<ChatbotUpload> chatbotUploads = chatbotUploadRepository.findByUserIdAndDeletedAtIsNullOrderByUploadedAtDesc(userId);
        long chatbotBytes = chatbotUploads.stream()
            .filter(upload -> upload.getFileSize() != null)
            .mapToLong(ChatbotUpload::getFileSize)
            .sum();

        totalBytes += chatbotBytes;

        stats.put("totalStorageBytes", totalBytes);
        stats.put("totalStorageMB", totalBytes / (1024.0 * 1024.0));

        // File breakdown
        Map<String, Long> breakdown = new HashMap<>();
        breakdown.put("lostFoundImages", lostFoundCount);
        breakdown.put("bookPhotos", bookPhotoCount);
        breakdown.put("bookPdfs", bookPdfCount);
        breakdown.put("chatbotUploads", chatbotUploadCount);
        breakdown.put("chatbotImages", chatbotImageCount);
        breakdown.put("chatbotPdfs", chatbotPdfCount);
        breakdown.put("chatbotVideos", chatbotVideoCount);
        stats.put("breakdown", breakdown);

        return ResponseEntity.ok(stats);
    }

    @DeleteMapping("/lost-found/{itemId}/image")
    public ResponseEntity<?> deleteLostFoundImage(@PathVariable Long itemId, @RequestParam Long userId) {
        try {
            LostFoundItem item = lostFoundItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Item not found"));

            // Verify ownership
            if (!item.getPostedBy().getId().equals(userId)) {
                return ResponseEntity.status(403).body("You don't have permission to delete this file");
            }

            if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
                // Delete from S3
                String fileName = s3Service.extractFileNameFromUrl(item.getImageUrl());
                s3Service.deleteFile(fileName);

                // Update database
                item.setImageUrl(null);
                lostFoundItemRepository.save(item);

                return ResponseEntity.ok("Image deleted successfully");
            }

            return ResponseEntity.badRequest().body("No image to delete");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error deleting image: " + e.getMessage());
        }
    }

    @DeleteMapping("/book/{bookId}/photo")
    public ResponseEntity<?> deleteBookPhoto(@PathVariable Long bookId, @RequestParam Long userId) {
        try {
            Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new RuntimeException("Book not found"));

            // Verify ownership
            if (!book.getOwnerId().equals(userId)) {
                return ResponseEntity.status(403).body("You don't have permission to delete this file");
            }

            if (book.getPhotoUrl() != null && !book.getPhotoUrl().isEmpty()) {
                // Delete from S3
                String fileName = s3Service.extractFileNameFromUrl(book.getPhotoUrl());
                s3Service.deleteFile(fileName);

                // Update database
                book.setPhotoUrl(null);
                bookRepository.save(book);

                return ResponseEntity.ok("Photo deleted successfully");
            }

            return ResponseEntity.badRequest().body("No photo to delete");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error deleting photo: " + e.getMessage());
        }
    }

    @DeleteMapping("/book/{bookId}/pdf")
    public ResponseEntity<?> deleteBookPdf(@PathVariable Long bookId, @RequestParam Long userId) {
        try {
            Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new RuntimeException("Book not found"));

            // Verify ownership
            if (!book.getOwnerId().equals(userId)) {
                return ResponseEntity.status(403).body("You don't have permission to delete this file");
            }

            if (book.getPdfUrl() != null && !book.getPdfUrl().isEmpty()) {
                // Delete from S3
                String fileName = s3Service.extractFileNameFromUrl(book.getPdfUrl());
                s3Service.deleteFile(fileName);

                // Update database
                book.setPdfUrl(null);
                book.setFileSize(null);
                bookRepository.save(book);

                return ResponseEntity.ok("PDF deleted successfully");
            }

            return ResponseEntity.badRequest().body("No PDF to delete");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error deleting PDF: " + e.getMessage());
        }
    }

    @DeleteMapping("/chatbot-upload/{uploadId}")
    public ResponseEntity<?> deleteChatbotUpload(@PathVariable Long uploadId, @RequestParam Long userId) {
        try {
            ChatbotUpload upload = chatbotUploadRepository.findById(uploadId)
                .orElseThrow(() -> new RuntimeException("Upload not found"));

            // Verify ownership
            if (!upload.getUserId().equals(userId)) {
                return ResponseEntity.status(403).body("You don't have permission to delete this file");
            }

            if (upload.getFileUrl() != null && !upload.getFileUrl().isEmpty()) {
                // Delete from S3
                String fileName = s3Service.extractFileNameFromUrl(upload.getFileUrl());
                s3Service.deleteFile(fileName);

                // Soft delete: set deletedAt timestamp instead of hard delete
                upload.setDeletedAt(java.time.LocalDateTime.now());
                chatbotUploadRepository.save(upload);

                return ResponseEntity.ok("File deleted successfully");
            }

            return ResponseEntity.badRequest().body("No file to delete");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error deleting file: " + e.getMessage());
        }
    }
}
