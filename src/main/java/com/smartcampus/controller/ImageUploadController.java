package com.smartcampus.controller;

import com.smartcampus.service.S3Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/upload")
public class ImageUploadController {

    @Autowired
    private S3Service s3Service;

    @PostMapping("/image")
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file) {
        try {
            // Validate file type
            if (!isValidImageFile(file)) {
                return ResponseEntity.badRequest().body("Only image files (jpg, jpeg, png, gif, webp) are allowed");
            }

            // Validate file size (max 10MB)
            if (file.getSize() > 10 * 1024 * 1024) {
                return ResponseEntity.badRequest().body("File size cannot exceed 10MB");
            }

            String imageUrl = s3Service.uploadFile(file, "lost-found-images");
            
            Map<String, String> response = new HashMap<>();
            response.put("imageUrl", imageUrl);
            response.put("message", "Image uploaded successfully");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to upload image: " + e.getMessage());
        }
    }

    @DeleteMapping("/image")
    public ResponseEntity<?> deleteImage(@RequestParam("imageUrl") String imageUrl) {
        try {
            String fileName = s3Service.extractFileNameFromUrl(imageUrl);
            if (fileName != null) {
                s3Service.deleteFile(fileName);
                return ResponseEntity.ok("Image deleted successfully");
            } else {
                return ResponseEntity.badRequest().body("Invalid image URL");
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to delete image: " + e.getMessage());
        }
    }

    @GetMapping("/image/serve")
    public ResponseEntity<?> serveImage(@RequestParam("url") String s3Url) {
        try {
            // Extract filename from S3 URL
            String fileName = s3Service.extractFileNameFromUrl(s3Url);
            if (fileName == null) {
                return ResponseEntity.badRequest().body("Invalid S3 URL");
            }
            
            // Get the image bytes from S3
            byte[] imageBytes = s3Service.getFileBytes(fileName);
            
            // Determine content type based on file extension
            String contentType = "image/jpeg"; // default
            if (fileName.toLowerCase().endsWith(".png")) {
                contentType = "image/png";
            } else if (fileName.toLowerCase().endsWith(".gif")) {
                contentType = "image/gif";
            } else if (fileName.toLowerCase().endsWith(".webp")) {
                contentType = "image/webp";
            }
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(imageBytes);
                    
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to serve image: " + e.getMessage());
        }
    }

    @PostMapping("/pdf")
    public ResponseEntity<?> uploadPdf(@RequestParam("file") MultipartFile file) {
        try {
            // Validate file type
            if (!isValidPdfFile(file)) {
                return ResponseEntity.badRequest().body("Only PDF files are allowed");
            }

            // Validate file size (max 50MB)
            if (file.getSize() > 50 * 1024 * 1024) {
                return ResponseEntity.badRequest().body("File size cannot exceed 50MB");
            }

            String pdfUrl = s3Service.uploadFile(file, "library-pdfs");

            Map<String, Object> response = new HashMap<>();
            response.put("pdfUrl", pdfUrl);
            response.put("fileSize", file.getSize());
            response.put("message", "PDF uploaded successfully");

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to upload PDF: " + e.getMessage());
        }
    }

    @DeleteMapping("/pdf")
    public ResponseEntity<?> deletePdf(@RequestParam("pdfUrl") String pdfUrl) {
        try {
            String fileName = s3Service.extractFileNameFromUrl(pdfUrl);
            if (fileName != null) {
                s3Service.deleteFile(fileName);
                return ResponseEntity.ok("PDF deleted successfully");
            } else {
                return ResponseEntity.badRequest().body("Invalid PDF URL");
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to delete PDF: " + e.getMessage());
        }
    }

    @GetMapping("/pdf/serve")
    public ResponseEntity<?> servePdf(@RequestParam("url") String s3Url) {
        try {
            // Extract filename from S3 URL
            String fileName = s3Service.extractFileNameFromUrl(s3Url);
            if (fileName == null) {
                return ResponseEntity.badRequest().body("Invalid S3 URL");
            }

            // Get the PDF bytes from S3
            byte[] pdfBytes = s3Service.getFileBytes(fileName);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header("Content-Disposition", "inline; filename=\"" + fileName + "\"")
                    .body(pdfBytes);

        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to serve PDF: " + e.getMessage());
        }
    }

    private boolean isValidImageFile(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && (
                contentType.equals("image/jpeg") ||
                contentType.equals("image/jpg") ||
                contentType.equals("image/png") ||
                contentType.equals("image/gif") ||
                contentType.equals("image/webp")
        );
    }

    private boolean isValidPdfFile(MultipartFile file) {
        String contentType = file.getContentType();
        String fileName = file.getOriginalFilename();
        return contentType != null && contentType.equals("application/pdf")
                && fileName != null && fileName.toLowerCase().endsWith(".pdf");
    }
}