package com.smartuniversity.controller;

import com.smartuniversity.service.S3Service;
import com.smartuniversity.service.rag.DocumentIndexService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageUploadControllerTest {

    private static final String PDF_URL = "https://bucket.s3.us-east-1.amazonaws.com/pdfs/handout.pdf";

    @Mock
    private S3Service s3Service;

    @Mock
    private DocumentIndexService documentIndexService;

    @InjectMocks
    private ImageUploadController controller;

    @Test
    void deletingAPdfAlsoDeletesItsStoredChunks() {
        when(s3Service.extractFileNameFromUrl(PDF_URL)).thenReturn("pdfs/handout.pdf");

        ResponseEntity<?> response = controller.deletePdf(PDF_URL);

        assertEquals(200, response.getStatusCode().value());
        verify(s3Service).deleteFile("pdfs/handout.pdf");
        verify(documentIndexService).forget("pdfs/handout.pdf");
    }

    @Test
    void aFailedChunkCleanupStillReportsTheDelete() {
        when(s3Service.extractFileNameFromUrl(PDF_URL)).thenReturn("pdfs/handout.pdf");
        when(documentIndexService.forget("pdfs/handout.pdf")).thenThrow(new IllegalStateException("database busy"));

        ResponseEntity<?> response = controller.deletePdf(PDF_URL);

        assertEquals(200, response.getStatusCode().value());
        verify(s3Service).deleteFile("pdfs/handout.pdf");
    }

    @Test
    void onlyAdminsMayDeleteFilesByUrl() throws Exception {
        for (String method : new String[]{"deletePdf", "deleteImage"}) {
            PreAuthorize rule = ImageUploadController.class.getMethod(method, String.class).getAnnotation(PreAuthorize.class);
            assertNotNull(rule, method + " needs a role check");
            assertEquals("hasRole('ADMIN')", rule.value(), method);
        }
    }

    @Test
    void aUrlOutsideOurStorageDeletesNothing() {
        when(s3Service.extractFileNameFromUrl("https://example.com/other.pdf")).thenReturn(null);

        ResponseEntity<?> response = controller.deletePdf("https://example.com/other.pdf");

        assertEquals(400, response.getStatusCode().value());
        verify(s3Service, never()).deleteFile(any());
        verifyNoInteractions(documentIndexService);
    }
}
