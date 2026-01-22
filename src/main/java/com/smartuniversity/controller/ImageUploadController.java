package com.smartuniversity.controller;

import com.smartuniversity.service.S3Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/upload")
public class ImageUploadController {

    @Autowired
    private S3Service s3Service;

    @PostMapping("/image")
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file,
                                         @RequestParam(value = "folder", required = false, defaultValue = "lost-found-images") String folder) {
        try {
            // Validate file type
            if (!isValidImageFile(file)) {
                return ResponseEntity.badRequest().body("Only image files (jpg, jpeg, png, gif, webp) are allowed");
            }

            // Validate file size (max 10MB)
            if (file.getSize() > 10 * 1024 * 1024) {
                return ResponseEntity.badRequest().body("File size cannot exceed 10MB");
            }

            // Validate folder name (security measure)
            String[] allowedFolders = {"lost-found-images", "competition-images", "profile-images", "event-images"};
            if (!java.util.Arrays.asList(allowedFolders).contains(folder)) {
                folder = "lost-found-images"; // Default to lost-found if invalid folder
            }

            String imageUrl = s3Service.uploadFile(file, folder);

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
    public ResponseEntity<?> serveImage(
            @RequestParam("url") String s3Url,
            @RequestParam(value = "quality", required = false, defaultValue = "medium") String quality,
            @RequestParam(value = "maxWidth", required = false) Integer maxWidth) {
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
            boolean isPng = fileName.toLowerCase().endsWith(".png");
            boolean isGif = fileName.toLowerCase().endsWith(".gif");
            boolean isWebp = fileName.toLowerCase().endsWith(".webp");
            
            if (isPng) {
                contentType = "image/png";
            } else if (isGif) {
                contentType = "image/gif";
            } else if (isWebp) {
                contentType = "image/webp";
            }
            
            // Apply quality/size optimization for JPEG/PNG (skip GIF to preserve animation)
            if (!isGif) {
                imageBytes = optimizeImage(imageBytes, quality, maxWidth, isPng);
                // Convert PNG to JPEG for better compression (except if transparency needed)
                if (!isPng) {
                    contentType = "image/jpeg";
                }
            }
            
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header("Cache-Control", "public, max-age=86400") // Cache for 24 hours
                    .body(imageBytes);
                    
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to serve image: " + e.getMessage());
        }
    }
    
    /**
     * Optimize image quality and size
     * @param imageBytes Original image bytes
     * @param quality "low", "medium", "high", or "original"
     * @param maxWidth Maximum width in pixels (null for no resize)
     * @param keepPng Whether to keep PNG format
     * @return Optimized image bytes
     */
    private byte[] optimizeImage(byte[] imageBytes, String quality, Integer maxWidth, boolean keepPng) {
        try {
            // Read original image
            BufferedImage originalImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (originalImage == null) {
                return imageBytes; // Return original if can't read
            }
            
            // Determine quality factor
            float qualityFactor;
            int defaultMaxWidth;
            switch (quality.toLowerCase()) {
                case "low":
                    qualityFactor = 0.3f;
                    defaultMaxWidth = 400;
                    break;
                case "medium":
                    qualityFactor = 0.5f;
                    defaultMaxWidth = 800;
                    break;
                case "high":
                    qualityFactor = 0.75f;
                    defaultMaxWidth = 1200;
                    break;
                case "original":
                    return imageBytes; // No optimization
                default:
                    qualityFactor = 0.5f;
                    defaultMaxWidth = 800;
            }
            
            // Use provided maxWidth or default based on quality
            int targetMaxWidth = maxWidth != null ? maxWidth : defaultMaxWidth;
            
            // Resize if needed
            BufferedImage resizedImage = originalImage;
            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();
            
            if (originalWidth > targetMaxWidth) {
                double ratio = (double) targetMaxWidth / originalWidth;
                int newWidth = targetMaxWidth;
                int newHeight = (int) (originalHeight * ratio);
                
                resizedImage = new BufferedImage(newWidth, newHeight, 
                    keepPng ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
                Graphics2D g2d = resizedImage.createGraphics();
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                if (!keepPng) {
                    g2d.setColor(Color.WHITE);
                    g2d.fillRect(0, 0, newWidth, newHeight);
                }
                g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
                g2d.dispose();
            }
            
            // Compress and output
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            
            if (keepPng) {
                // PNG compression (lossless, but resize helps)
                ImageIO.write(resizedImage, "png", outputStream);
            } else {
                // JPEG compression with quality control
                Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
                if (!writers.hasNext()) {
                    return imageBytes;
                }
                
                ImageWriter writer = writers.next();
                ImageOutputStream ios = ImageIO.createImageOutputStream(outputStream);
                writer.setOutput(ios);
                
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(qualityFactor);
                
                // Convert to RGB if needed (remove alpha channel for JPEG)
                BufferedImage rgbImage = resizedImage;
                if (resizedImage.getType() == BufferedImage.TYPE_INT_ARGB) {
                    rgbImage = new BufferedImage(resizedImage.getWidth(), resizedImage.getHeight(), BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = rgbImage.createGraphics();
                    g.setColor(Color.WHITE);
                    g.fillRect(0, 0, rgbImage.getWidth(), rgbImage.getHeight());
                    g.drawImage(resizedImage, 0, 0, null);
                    g.dispose();
                }
                
                writer.write(null, new IIOImage(rgbImage, null, null), param);
                writer.dispose();
                ios.close();
            }
            
            return outputStream.toByteArray();
            
        } catch (Exception e) {
            // If optimization fails, return original
            System.err.println("Image optimization failed: " + e.getMessage());
            return imageBytes;
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