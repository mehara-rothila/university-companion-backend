package com.smartuniversity.service.rag;

import com.smartuniversity.model.ChatbotUpload;
import com.smartuniversity.repository.ChatbotUploadRepository;
import com.smartuniversity.service.S3Service;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeletedUploadIndexCleanupTest {

    @Mock
    private ChatbotUploadRepository chatbotUploadRepository;

    @Mock
    private S3Service s3Service;

    @Mock
    private DocumentIndexService documentIndexService;

    @InjectMocks
    private DeletedUploadIndexCleanup cleanup;

    @Test
    void deletesTheStoredChunksOfEveryDeletedPdf() {
        when(chatbotUploadRepository.findByDeletedAtIsNotNullAndFileType(ChatbotUpload.FileType.PDF))
                .thenReturn(List.of(deleted(1L, "notes"), deleted(2L, "slides")));
        when(s3Service.extractFileNameFromUrl(url("notes"))).thenReturn("chatbot/notes.pdf");
        when(s3Service.extractFileNameFromUrl(url("slides"))).thenReturn("chatbot/slides.pdf");
        when(documentIndexService.forget("chatbot/notes.pdf")).thenReturn(true);
        when(documentIndexService.forget("chatbot/slides.pdf")).thenReturn(false);

        cleanup.run(null);

        verify(documentIndexService).forget("chatbot/notes.pdf");
        verify(documentIndexService).forget("chatbot/slides.pdf");
    }

    @Test
    void skipsFilesThatAreNotInOurStorage() {
        when(chatbotUploadRepository.findByDeletedAtIsNotNullAndFileType(ChatbotUpload.FileType.PDF))
                .thenReturn(List.of(deleted(1L, "elsewhere")));
        when(s3Service.extractFileNameFromUrl(url("elsewhere"))).thenReturn(null);

        cleanup.run(null);

        verifyNoInteractions(documentIndexService);
    }

    @Test
    void keepsGoingWhenOneCleanupFails() {
        when(chatbotUploadRepository.findByDeletedAtIsNotNullAndFileType(ChatbotUpload.FileType.PDF))
                .thenReturn(List.of(deleted(1L, "broken"), deleted(2L, "notes")));
        when(s3Service.extractFileNameFromUrl(url("broken"))).thenReturn("chatbot/broken.pdf");
        when(s3Service.extractFileNameFromUrl(url("notes"))).thenReturn("chatbot/notes.pdf");
        when(documentIndexService.forget("chatbot/broken.pdf")).thenThrow(new IllegalStateException("database busy"));

        cleanup.run(null);

        verify(documentIndexService).forget("chatbot/notes.pdf");
    }

    @Test
    void neverStopsTheAppFromStarting() {
        when(chatbotUploadRepository.findByDeletedAtIsNotNullAndFileType(ChatbotUpload.FileType.PDF))
                .thenThrow(new IllegalStateException("database down"));

        assertDoesNotThrow(() -> cleanup.run(null));
        verifyNoInteractions(documentIndexService);
    }

    private static ChatbotUpload deleted(long id, String name) {
        ChatbotUpload upload = new ChatbotUpload();
        upload.setId(id);
        upload.setFileUrl(url(name));
        upload.setFileType(ChatbotUpload.FileType.PDF);
        upload.setDeletedAt(LocalDateTime.of(2026, 10, 1, 9, 0));
        return upload;
    }

    private static String url(String name) {
        return "https://bucket.s3.us-east-1.amazonaws.com/chatbot/" + name + ".pdf";
    }
}
