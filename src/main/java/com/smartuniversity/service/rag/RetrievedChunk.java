package com.smartuniversity.service.rag;

/**
 * A chunk picked to answer a question.
 *
 * @param documentNumber 1-based position of its PDF among the PDFs attached to the question
 * @param pageNumber     page the text came from
 * @param chunkIndex     position of the chunk within its PDF
 * @param text           the chunk's text
 * @param score          cosine similarity to the question, from -1 to 1
 */
public record RetrievedChunk(int documentNumber, int pageNumber, int chunkIndex, String text, double score) {
}
