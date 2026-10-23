package com.smartuniversity.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartuniversity.model.ChatbotUpload;
import com.smartuniversity.model.IndexedDocument;
import com.smartuniversity.repository.ChatbotUploadRepository;
import com.smartuniversity.service.S3Service;
import com.smartuniversity.service.rag.DocumentIndexService;
import com.smartuniversity.service.rag.DocumentRetriever;
import com.smartuniversity.service.rag.RetrievedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Searches the PDFs the signed-in student uploaded, with the same retrieval as attached PDFs, so the
 * assistant can answer "what did my notes say about..." without the student attaching anything.
 */
@Component
public class MyDocumentsTool implements AssistantTool {

    private static final Logger log = LoggerFactory.getLogger(MyDocumentsTool.class);

    /** Only the newest PDFs are searched, so a first search doesn't have to index dozens of files. */
    static final int MAX_DOCUMENTS = 5;

    private final ChatbotUploadRepository chatbotUploadRepository;
    private final S3Service s3Service;
    private final DocumentIndexService documentIndexService;
    private final DocumentRetriever documentRetriever;

    public MyDocumentsTool(ChatbotUploadRepository chatbotUploadRepository, S3Service s3Service,
                           DocumentIndexService documentIndexService, DocumentRetriever documentRetriever) {
        this.chatbotUploadRepository = chatbotUploadRepository;
        this.s3Service = s3Service;
        this.documentIndexService = documentIndexService;
        this.documentRetriever = documentRetriever;
    }

    @Override
    public String name() {
        return "search_my_documents";
    }

    @Override
    public String description() {
        return "Searches the PDFs the signed-in student uploaded to Athena (their notes, slides and papers) and returns "
                + "the passages that best match, with file names and page numbers. Use it when they ask about their own "
                + "notes or documents without attaching them, and cite the file and page.";
    }

    @Override
    public Map<String, Object> parameters() {
        return ToolArgs.object(Map.of(
                "query", ToolArgs.text("What to look for, phrased as a question or topic.")), "query");
    }

    @Override
    public Object run(JsonNode args, long userId) {
        String query = ToolArgs.text(args, "query")
                .orElseThrow(() -> new IllegalArgumentException("query is required: say what to look for in the student's PDFs"));
        List<ChatbotUpload> pdfs = chatbotUploadRepository.findByUserIdAndDeletedAtIsNullOrderByUploadedAtDesc(userId).stream()
                .filter(upload -> upload.getFileType() == ChatbotUpload.FileType.PDF)
                .limit(MAX_DOCUMENTS)
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        if (pdfs.isEmpty()) {
            result.put("excerpts", List.of());
            result.put("note", "The student hasn't uploaded any PDFs. They can attach one to the chat or upload it in My Uploads.");
            return result;
        }

        List<IndexedDocument> documents = new ArrayList<>();
        List<String> searched = new ArrayList<>();
        List<String> unreadable = new ArrayList<>();
        for (ChatbotUpload pdf : pdfs) {
            String fileName = pdf.getFileName() != null ? pdf.getFileName() : "PDF " + (searched.size() + unreadable.size() + 1);
            String s3Key = s3Service.extractFileNameFromUrl(pdf.getFileUrl());
            try {
                if (s3Key == null) {
                    throw new IllegalArgumentException("not in our storage");
                }
                documents.add(documentIndexService.indexIfNeeded(s3Key));
                searched.add(fileName);
            } catch (Exception e) {
                log.warn("Could not index upload {} for the assistant", pdf.getId(), e);
                unreadable.add(fileName);
            }
        }

        result.put("searched_files", searched);
        if (!unreadable.isEmpty()) {
            result.put("unreadable_files", unreadable);
        }
        List<RetrievedChunk> chunks = documents.isEmpty() ? List.of() : documentRetriever.retrieve(query, documents);
        result.put("excerpts", chunks.stream().map(chunk -> excerpt(chunk, searched)).toList());
        if (chunks.isEmpty()) {
            result.put("note", documents.isEmpty()
                    ? "None of the student's PDFs could be read."
                    : "Nothing readable in the student's PDFs matches. Scanned PDFs have no text to search.");
        }
        return result;
    }

    private static Map<String, Object> excerpt(RetrievedChunk chunk, List<String> fileNames) {
        Map<String, Object> excerpt = new LinkedHashMap<>();
        excerpt.put("file", fileNames.get(chunk.documentNumber() - 1));
        excerpt.put("page", chunk.pageNumber());
        excerpt.put("text", ToolArgs.clip(chunk.text(), 1_200));
        return excerpt;
    }
}
