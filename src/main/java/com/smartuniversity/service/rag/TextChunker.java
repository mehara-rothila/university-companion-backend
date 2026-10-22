package com.smartuniversity.service.rag;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits page text into overlapping chunks.
 *
 * <p>Each chunk ends at a paragraph break, sentence end or space where possible, and starts a
 * little before the previous one ended, so a sentence cut at a boundary still appears whole in
 * one chunk. Chunks never cross pages, which keeps every chunk's page number exact.</p>
 */
@Component
public class TextChunker {

    /** Chunks with less real text than this (page numbers, stray headers) aren't worth embedding. */
    private static final int MIN_CHUNK_CHARS = 25;

    private final int chunkSize;
    private final int overlap;

    public TextChunker(@Value("${rag.chunk-size:1200}") int chunkSize,
                       @Value("${rag.chunk-overlap:200}") int overlap) {
        // A chunk always ends at least half its size past its start, so an overlap below half the
        // size guarantees every new chunk starts further along than the last.
        if (chunkSize < 100 || overlap < 0 || overlap >= chunkSize / 2) {
            throw new IllegalArgumentException(
                    "Need chunkSize >= 100 and 0 <= overlap < chunkSize / 2, got " + chunkSize + " and " + overlap);
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    /**
     * Chunks every page in order.
     *
     * @param pages page texts, where index 0 is page 1
     */
    public List<TextChunk> chunk(List<String> pages) {
        List<TextChunk> chunks = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            String text = normalize(pages.get(i));
            int start = 0;
            while (start < text.length()) {
                int end = text.length() - start <= chunkSize ? text.length() : breakPoint(text, start);
                String piece = text.substring(start, end).trim();
                if (piece.replaceAll("\\s", "").length() >= MIN_CHUNK_CHARS) {
                    chunks.add(new TextChunk(i + 1, chunks.size(), piece));
                }
                if (end >= text.length()) {
                    break;
                }
                // Step back by the overlap, but never past the previous start and never beyond
                // this chunk's end, so the loop always advances and no text is skipped.
                start = Math.min(wordStart(text, Math.max(end - overlap, start + 1)), end);
            }
        }
        return chunks;
    }

    /** Where a chunk starting at {@code start} should end: the last good break before the size limit. */
    private int breakPoint(String text, int start) {
        int limit = start + chunkSize;
        int earliest = start + chunkSize / 2;

        int paragraph = text.lastIndexOf("\n\n", limit);
        if (paragraph >= earliest) {
            return paragraph;
        }
        for (int i = limit - 1; i >= earliest; i--) {
            char c = text.charAt(i);
            if ((c == '.' || c == '?' || c == '!') && Character.isWhitespace(text.charAt(i + 1))) {
                return i + 1;
            }
        }
        for (int i = limit; i >= earliest; i--) {
            if (Character.isWhitespace(text.charAt(i))) {
                return i;
            }
        }
        return limit;
    }

    /** Moves {@code pos} forward to the start of the next word so overlaps don't begin mid-word. */
    private static int wordStart(String text, int pos) {
        while (pos < text.length() && !Character.isWhitespace(text.charAt(pos - 1))) {
            pos++;
        }
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
        return pos;
    }

    /** Unifies line endings and collapses the runs of spaces and blank lines PDF extraction leaves behind. */
    private static String normalize(String text) {
        return text.replace("\r", "")
                .replaceAll("[ \\t\\x0B\\f]+", " ")
                .replaceAll(" *\n *", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }
}
