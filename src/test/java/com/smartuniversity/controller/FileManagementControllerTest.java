package com.smartuniversity.controller;

import com.smartuniversity.model.Book;
import com.smartuniversity.model.ChatbotUpload;
import com.smartuniversity.repository.BookRepository;
import com.smartuniversity.repository.ChatbotUploadRepository;
import com.smartuniversity.service.S3Service;
import com.smartuniversity.service.rag.DocumentIndexService;
import com.smartuniversity.util.AuthUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** The My Uploads and library deletes must also delete a PDF's stored chunks. */
@ExtendWith(MockitoExtension.class)
class FileManagementControllerTest {

    private static final String UPLOAD_URL = "https://bucket.s3.us-east-1.amazonaws.com/chatbot/notes.pdf";
    private static final String BOOK_URL = "https://bucket.s3.us-east-1.amazonaws.com/books/algorithms.pdf";

    @Mock
    private ChatbotUploadRepository chatbotUploadRepository;

    @Mock
    private BookRepository bookRepository;

    @Mock
    private S3Service s3Service;

    @Mock
    private AuthUtils authUtils;

    @Mock
    private DocumentIndexService documentIndexService;

    @InjectMocks
    private FileManagementController controller;

    @Test
    void deletingAnUploadAlsoDeletesItsStoredChunks() {
        ChatbotUpload upload = upload(1L);
        when(authUtils.getCurrentUserId()).thenReturn(1L);
        when(chatbotUploadRepository.findById(5L)).thenReturn(Optional.of(upload));
        when(s3Service.extractFileNameFromUrl(UPLOAD_URL)).thenReturn("chatbot/notes.pdf");

        ResponseEntity<?> response = controller.deleteChatbotUpload(5L);

        assertEquals(200, response.getStatusCode().value());
        verify(s3Service).deleteFile("chatbot/notes.pdf");
        verify(documentIndexService).forget("chatbot/notes.pdf");
        assertNotNull(upload.getDeletedAt());
        verify(chatbotUploadRepository).save(upload);
    }

    @Test
    void theUploadIsStillDeletedWhenChunkCleanupFails() {
        ChatbotUpload upload = upload(1L);
        when(authUtils.getCurrentUserId()).thenReturn(1L);
        when(chatbotUploadRepository.findById(5L)).thenReturn(Optional.of(upload));
        when(s3Service.extractFileNameFromUrl(UPLOAD_URL)).thenReturn("chatbot/notes.pdf");
        when(documentIndexService.forget("chatbot/notes.pdf")).thenThrow(new IllegalStateException("database busy"));

        ResponseEntity<?> response = controller.deleteChatbotUpload(5L);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(upload.getDeletedAt());
    }

    @Test
    void someoneElsesUploadIsLeftAlone() {
        when(authUtils.getCurrentUserId()).thenReturn(2L);
        when(chatbotUploadRepository.findById(5L)).thenReturn(Optional.of(upload(1L)));

        ResponseEntity<?> response = controller.deleteChatbotUpload(5L);

        assertEquals(403, response.getStatusCode().value());
        verifyNoInteractions(s3Service, documentIndexService);
    }

    @Test
    void deletingABookPdfAlsoDeletesItsStoredChunks() {
        Book book = new Book();
        book.setId(9L);
        book.setOwnerId(1L);
        book.setPdfUrl(BOOK_URL);
        when(authUtils.getCurrentUserId()).thenReturn(1L);
        when(bookRepository.findById(9L)).thenReturn(Optional.of(book));
        when(s3Service.extractFileNameFromUrl(BOOK_URL)).thenReturn("books/algorithms.pdf");

        ResponseEntity<?> response = controller.deleteBookPdf(9L);

        assertEquals(200, response.getStatusCode().value());
        verify(s3Service).deleteFile("books/algorithms.pdf");
        verify(documentIndexService).forget("books/algorithms.pdf");
        assertNull(book.getPdfUrl());
    }

    private static ChatbotUpload upload(long ownerId) {
        ChatbotUpload upload = new ChatbotUpload();
        upload.setId(5L);
        upload.setUserId(ownerId);
        upload.setFileUrl(UPLOAD_URL);
        upload.setFileName("notes.pdf");
        upload.setFileType(ChatbotUpload.FileType.PDF);
        return upload;
    }
}
