package com.smartuniversity.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartuniversity.model.ChatbotUpload;
import com.smartuniversity.model.FinancialAid;
import com.smartuniversity.model.IndexedDocument;
import com.smartuniversity.model.User;
import com.smartuniversity.repository.ChatbotUploadRepository;
import com.smartuniversity.repository.FinancialAidRepository;
import com.smartuniversity.repository.UserRepository;
import com.smartuniversity.service.S3Service;
import com.smartuniversity.service.rag.DocumentIndexService;
import com.smartuniversity.service.rag.DocumentRetriever;
import com.smartuniversity.service.rag.RetrievedChunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The tools that read the signed-in student's own data: financial aid and uploaded PDFs. */
@ExtendWith(MockitoExtension.class)
class PersonalToolsTest {

    private static final long STUDENT = 5L;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private FinancialAidRepository financialAidRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChatbotUploadRepository chatbotUploadRepository;

    @Mock
    private S3Service s3Service;

    @Mock
    private DocumentIndexService documentIndexService;

    @Mock
    private DocumentRetriever documentRetriever;

    // ==================== Financial aid ====================

    @Test
    void listsOnlyTheStudentsOwnApplicationsNewestFirst() throws Exception {
        User student = new User();
        student.setId(STUDENT);
        FinancialAid older = application("Bus fare support", FinancialAid.ApplicationStatus.APPROVED, LocalDateTime.of(2026, 3, 1, 9, 0));
        older.setApprovedAmount(new BigDecimal("15000.00"));
        FinancialAid newer = application("Laptop grant", FinancialAid.ApplicationStatus.UNDER_REVIEW, LocalDateTime.of(2026, 9, 1, 9, 0));
        newer.setAdminNotes("Check income documents");
        newer.setPersonalStory("My laptop broke during exams");
        when(userRepository.findById(STUDENT)).thenReturn(Optional.of(student));
        when(financialAidRepository.findByApplicant(student)).thenReturn(List.of(older, newer));

        JsonNode result = run(new MyFinancialAidTool(financialAidRepository, userRepository), "{\"user_id\": 99}");

        verify(userRepository).findById(STUDENT);
        assertEquals(2, result.get("total").asInt());
        assertEquals("Laptop grant", result.at("/applications/0/title").asText());
        assertEquals("UNDER_REVIEW", result.at("/applications/0/status").asText());
        assertEquals("15000.00", result.at("/applications/1/approved_amount").asText());
        String text = result.toString();
        assertFalse(text.contains("income documents"));
        assertFalse(text.contains("laptop broke"));
    }

    @Test
    void givesTheRejectionReasonOnlyForRejectedApplications() throws Exception {
        User student = new User();
        FinancialAid rejected = application("Hostel fees", FinancialAid.ApplicationStatus.REJECTED, LocalDateTime.of(2026, 5, 1, 9, 0));
        rejected.setRejectionReason("Missing income certificate");
        FinancialAid pending = application("Books", FinancialAid.ApplicationStatus.PENDING, LocalDateTime.of(2026, 4, 1, 9, 0));
        pending.setRejectionReason("stale value");
        when(userRepository.findById(STUDENT)).thenReturn(Optional.of(student));
        when(financialAidRepository.findByApplicant(student)).thenReturn(List.of(rejected, pending));

        JsonNode result = run(new MyFinancialAidTool(financialAidRepository, userRepository), "{}");

        assertEquals("Missing income certificate", result.at("/applications/0/rejection_reason").asText());
        assertFalse(result.at("/applications/1").has("rejection_reason"));
    }

    @Test
    void saysSoWhenTheStudentHasNoApplications() throws Exception {
        when(userRepository.findById(STUDENT)).thenReturn(Optional.empty());

        JsonNode result = run(new MyFinancialAidTool(financialAidRepository, userRepository), "{}");

        assertEquals(0, result.get("total").asInt());
        assertTrue(result.get("note").asText().contains("no financial-aid applications"));
        verifyNoInteractions(financialAidRepository);
    }

    // ==================== Uploaded PDFs ====================

    @Test
    void searchesTheStudentsNewestPdfsAndNamesTheFiles() throws Exception {
        List<ChatbotUpload> uploads = List.of(
                upload(1L, "photo.png", ChatbotUpload.FileType.IMAGE),
                upload(2L, "networks.pdf", ChatbotUpload.FileType.PDF),
                upload(3L, "algorithms.pdf", ChatbotUpload.FileType.PDF));
        when(chatbotUploadRepository.findByUserIdAndDeletedAtIsNullOrderByUploadedAtDesc(STUDENT)).thenReturn(uploads);
        when(s3Service.extractFileNameFromUrl(any())).thenAnswer(call -> "chatbot/" + call.getArgument(0, String.class).substring(8));
        IndexedDocument networks = document(20L);
        IndexedDocument algorithms = document(30L);
        when(documentIndexService.indexIfNeeded("chatbot/networks.pdf")).thenReturn(networks);
        when(documentIndexService.indexIfNeeded("chatbot/algorithms.pdf")).thenReturn(algorithms);
        when(documentRetriever.retrieve("What is Dijkstra's algorithm?", List.of(networks, algorithms)))
                .thenReturn(List.of(new RetrievedChunk(2, 7, 3, "Dijkstra finds shortest paths.", 0.9)));

        JsonNode result = run(myDocuments(), "{\"query\": \"What is Dijkstra's algorithm?\"}");

        verify(chatbotUploadRepository).findByUserIdAndDeletedAtIsNullOrderByUploadedAtDesc(STUDENT);
        assertEquals(List.of("networks.pdf", "algorithms.pdf"), objectMapper.convertValue(result.get("searched_files"), List.class));
        assertEquals("algorithms.pdf", result.at("/excerpts/0/file").asText());
        assertEquals(7, result.at("/excerpts/0/page").asInt());
        assertEquals("Dijkstra finds shortest paths.", result.at("/excerpts/0/text").asText());
        verify(documentIndexService, never()).indexIfNeeded("chatbot/photo.png");
    }

    @Test
    void searchesAtMostFivePdfs() throws Exception {
        List<ChatbotUpload> uploads = java.util.stream.LongStream.rangeClosed(1, 8)
                .mapToObj(id -> upload(id, "notes" + id + ".pdf", ChatbotUpload.FileType.PDF)).toList();
        when(chatbotUploadRepository.findByUserIdAndDeletedAtIsNullOrderByUploadedAtDesc(STUDENT)).thenReturn(uploads);
        when(s3Service.extractFileNameFromUrl(any())).thenReturn("chatbot/key.pdf");
        when(documentIndexService.indexIfNeeded("chatbot/key.pdf")).thenReturn(document(1L));
        when(documentRetriever.retrieve(any(), any())).thenReturn(List.of());

        run(myDocuments(), "{\"query\": \"exam dates\"}");

        verify(documentIndexService, times(MyDocumentsTool.MAX_DOCUMENTS)).indexIfNeeded("chatbot/key.pdf");
    }

    @Test
    void skipsPdfsThatCannotBeReadAndSaysWhich() throws Exception {
        when(chatbotUploadRepository.findByUserIdAndDeletedAtIsNullOrderByUploadedAtDesc(STUDENT)).thenReturn(List.of(
                upload(1L, "broken.pdf", ChatbotUpload.FileType.PDF),
                upload(2L, "elsewhere.pdf", ChatbotUpload.FileType.PDF)));
        when(s3Service.extractFileNameFromUrl("https://broken.pdf")).thenReturn("chatbot/broken.pdf");
        when(s3Service.extractFileNameFromUrl("https://elsewhere.pdf")).thenReturn(null);
        when(documentIndexService.indexIfNeeded("chatbot/broken.pdf")).thenThrow(new IOException("damaged file"));

        JsonNode result = run(myDocuments(), "{\"query\": \"summary\"}");

        assertEquals(List.of("broken.pdf", "elsewhere.pdf"), objectMapper.convertValue(result.get("unreadable_files"), List.class));
        assertEquals(0, result.get("excerpts").size());
        assertEquals("None of the student's PDFs could be read.", result.get("note").asText());
        verifyNoInteractions(documentRetriever);
    }

    @Test
    void saysSoWhenTheStudentHasNoPdfs() throws Exception {
        when(chatbotUploadRepository.findByUserIdAndDeletedAtIsNullOrderByUploadedAtDesc(STUDENT))
                .thenReturn(List.of(upload(1L, "photo.png", ChatbotUpload.FileType.IMAGE)));

        JsonNode result = run(myDocuments(), "{\"query\": \"anything\"}");

        assertTrue(result.get("note").asText().contains("hasn't uploaded any PDFs"));
        verifyNoInteractions(documentIndexService, documentRetriever);
    }

    @Test
    void needsSomethingToSearchFor() {
        MyDocumentsTool tool = myDocuments();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> tool.run(objectMapper.readTree("{}"), STUDENT));
        assertTrue(error.getMessage().startsWith("query is required"));
    }

    // ==================== Helpers ====================

    private MyDocumentsTool myDocuments() {
        return new MyDocumentsTool(chatbotUploadRepository, s3Service, documentIndexService, documentRetriever);
    }

    private JsonNode run(AssistantTool tool, String args) throws Exception {
        return objectMapper.valueToTree(tool.run(objectMapper.readTree(args), STUDENT));
    }

    private static FinancialAid application(String title, FinancialAid.ApplicationStatus status, LocalDateTime created) {
        FinancialAid application = new FinancialAid();
        application.setTitle(title);
        application.setStatus(status);
        application.setCreatedAt(created);
        application.setRequestedAmount(new BigDecimal("20000.00"));
        return application;
    }

    private static ChatbotUpload upload(long id, String fileName, ChatbotUpload.FileType type) {
        ChatbotUpload upload = new ChatbotUpload();
        upload.setId(id);
        upload.setUserId(STUDENT);
        upload.setFileName(fileName);
        upload.setFileUrl("https://" + fileName);
        upload.setFileType(type);
        return upload;
    }

    private static IndexedDocument document(long id) {
        IndexedDocument document = new IndexedDocument();
        document.setId(id);
        document.setChunkCount(4);
        return document;
    }
}
