package com.smartuniversity.service.rag;

import com.smartuniversity.model.ChatbotUpload;
import com.smartuniversity.model.IndexedDocument;
import com.smartuniversity.repository.ChatbotUploadRepository;
import com.smartuniversity.repository.IndexedDocumentRepository;
import com.smartuniversity.service.S3Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaleIndexCleanupTest {

    @Mock
    private ChatbotUploadRepository chatbotUploadRepository;

    @Mock
    private IndexedDocumentRepository indexedDocumentRepository;

    @Mock
    private S3Service s3Service;

    @Mock
    private DocumentIndexService documentIndexService;

    @InjectMocks
    private StaleIndexCleanup cleanup;

    @BeforeEach
    void noDeletedUploadsByDefault() {
        lenient().when(chatbotUploadRepository.findByDeletedAtIsNotNullAndFileType(ChatbotUpload.FileType.PDF)).thenReturn(List.of());
        lenient().when(indexedDocumentRepository.findAll()).thenReturn(List.of());
    }

    @Test
    void deletesTheChunksOfDeletedUploads() {
        when(chatbotUploadRepository.findByDeletedAtIsNotNullAndFileType(ChatbotUpload.FileType.PDF))
                .thenReturn(List.of(deleted(1L, "notes"), deleted(2L, "slides")));
        when(s3Service.extractFileNameFromUrl(url("notes"))).thenReturn("chatbot/notes.pdf");
        when(s3Service.extractFileNameFromUrl(url("slides"))).thenReturn("chatbot/slides.pdf");
        when(documentIndexService.forget(anyString())).thenReturn(true);

        cleanup.run(null);

        verify(documentIndexService).forget("chatbot/notes.pdf");
        verify(documentIndexService).forget("chatbot/slides.pdf");
    }

    @Test
    void deletesTheChunksOfPdfsNoLongerInStorage() {
        when(indexedDocumentRepository.findAll()).thenReturn(List.of(indexed("books/gone.pdf"), indexed("chatbot/kept.pdf")));
        when(s3Service.fileExists("books/gone.pdf")).thenReturn(false);
        when(s3Service.fileExists("chatbot/kept.pdf")).thenReturn(true);

        cleanup.run(null);

        verify(documentIndexService).forget("books/gone.pdf");
        verify(documentIndexService, never()).forget("chatbot/kept.pdf");
    }

    @Test
    void keepsAPdfWhenStorageCannotBeAsked() {
        when(indexedDocumentRepository.findAll()).thenReturn(List.of(indexed("chatbot/notes.pdf")));
        when(s3Service.fileExists("chatbot/notes.pdf")).thenThrow(new IllegalStateException("network down"));

        cleanup.run(null);

        verifyNoInteractions(documentIndexService);
    }

    @Test
    void keepsEverythingWhenEveryIndexedFileLooksGone() {
        when(indexedDocumentRepository.findAll()).thenReturn(List.of(indexed("a.pdf"), indexed("b.pdf"), indexed("c.pdf")));
        when(s3Service.fileExists(anyString())).thenReturn(false);

        cleanup.run(null);

        verifyNoInteractions(documentIndexService);
    }

    @Test
    void stillClearsDeletedUploadsWhenStorageLooksMisconfigured() {
        when(chatbotUploadRepository.findByDeletedAtIsNotNullAndFileType(ChatbotUpload.FileType.PDF))
                .thenReturn(List.of(deleted(1L, "notes")));
        when(s3Service.extractFileNameFromUrl(url("notes"))).thenReturn("chatbot/notes.pdf");
        when(indexedDocumentRepository.findAll()).thenReturn(List.of(indexed("a.pdf"), indexed("b.pdf"), indexed("c.pdf")));
        when(s3Service.fileExists(anyString())).thenReturn(false);

        cleanup.run(null);

        verify(documentIndexService).forget("chatbot/notes.pdf");
        verify(documentIndexService, never()).forget("a.pdf");
    }

    @Test
    void skipsDeletedUploadsThatAreNotInOurStorage() {
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

    private static IndexedDocument indexed(String s3Key) {
        IndexedDocument document = new IndexedDocument();
        document.setS3Key(s3Key);
        return document;
    }

    private static String url(String name) {
        return "https://bucket.s3.us-east-1.amazonaws.com/chatbot/" + name + ".pdf";
    }
}
