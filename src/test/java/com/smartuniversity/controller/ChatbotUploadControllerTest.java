package com.smartuniversity.controller;

import com.smartuniversity.model.ChatbotUpload;
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

@ExtendWith(MockitoExtension.class)
class ChatbotUploadControllerTest {

    private static final String URL = "https://bucket.s3.us-east-1.amazonaws.com/chatbot/notes.pdf";

    @Mock
    private ChatbotUploadRepository chatbotUploadRepository;

    @Mock
    private S3Service s3Service;

    @Mock
    private AuthUtils authUtils;

    @Mock
    private DocumentIndexService documentIndexService;

    @InjectMocks
    private ChatbotUploadController controller;

    @Test
    void deletingAnUploadAlsoDeletesItsStoredChunks() {
        ChatbotUpload upload = upload(1L);
        when(authUtils.getCurrentUserId()).thenReturn(1L);
        when(chatbotUploadRepository.findById(5L)).thenReturn(Optional.of(upload));
        when(s3Service.extractFileNameFromUrl(URL)).thenReturn("chatbot/notes.pdf");

        ResponseEntity<?> response = controller.deleteUpload(5L);

        assertEquals(200, response.getStatusCode().value());
        verify(s3Service).deleteFile("chatbot/notes.pdf");
        verify(documentIndexService).forget("chatbot/notes.pdf");
        assertNotNull(upload.getDeletedAt());
    }

    @Test
    void someoneElsesUploadIsLeftAlone() {
        when(authUtils.getCurrentUserId()).thenReturn(2L);
        when(chatbotUploadRepository.findById(5L)).thenReturn(Optional.of(upload(1L)));

        ResponseEntity<?> response = controller.deleteUpload(5L);

        assertEquals(403, response.getStatusCode().value());
        verifyNoInteractions(s3Service, documentIndexService);
    }

    private static ChatbotUpload upload(long ownerId) {
        ChatbotUpload upload = new ChatbotUpload();
        upload.setId(5L);
        upload.setUserId(ownerId);
        upload.setFileUrl(URL);
        upload.setFileName("notes.pdf");
        upload.setFileType(ChatbotUpload.FileType.PDF);
        return upload;
    }
}
