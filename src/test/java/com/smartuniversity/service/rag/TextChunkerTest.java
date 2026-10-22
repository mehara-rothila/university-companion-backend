package com.smartuniversity.service.rag;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextChunkerTest {

    private final TextChunker chunker = new TextChunker(100, 20);

    @Test
    void shortPageIsOneChunk() {
        List<TextChunk> chunks = chunker.chunk(List.of("A short page about recursion and base cases."));

        assertEquals(1, chunks.size());
        assertEquals(new TextChunk(1, 0, "A short page about recursion and base cases."), chunks.get(0));
    }

    @Test
    void longPageSplitsIntoBoundedOverlappingChunks() {
        List<String> sentences = sentences(40);
        List<TextChunk> chunks = chunker.chunk(List.of(String.join(" ", sentences)));

        assertTrue(chunks.size() > 1);
        for (TextChunk chunk : chunks) {
            assertTrue(chunk.text().length() <= 100, "chunk too long: " + chunk.text().length());
        }
        for (int i = 0; i + 1 < chunks.size(); i++) {
            String nextStart = chunks.get(i + 1).text().substring(0, 8);
            assertTrue(chunks.get(i).text().contains(nextStart),
                    "chunk " + (i + 1) + " should start inside chunk " + i);
        }
        for (String sentence : sentences) {
            assertTrue(chunks.stream().anyMatch(c -> c.text().contains(sentence)), "lost: " + sentence);
        }
    }

    @Test
    void endsChunksAtSentenceBoundaries() {
        List<TextChunk> chunks = chunker.chunk(List.of(String.join(" ", sentences(40))));

        for (TextChunk chunk : chunks) {
            assertTrue(chunk.text().endsWith("."), "chunk cut mid-sentence: " + chunk.text());
        }
    }

    @Test
    void keepsPageNumbersAndSkipsBlankPages() {
        List<TextChunk> chunks = chunker.chunk(List.of(
                "Page one has enough text to keep.",
                "",
                "   ",
                "Page four also has enough text to keep."));

        assertEquals(2, chunks.size());
        assertEquals(1, chunks.get(0).pageNumber());
        assertEquals(4, chunks.get(1).pageNumber());
        assertEquals(0, chunks.get(0).index());
        assertEquals(1, chunks.get(1).index());
    }

    @Test
    void skipsFragmentsTooShortToBeUseful() {
        assertTrue(chunker.chunk(List.of("12")).isEmpty());
    }

    @Test
    void keepsAllTextEvenWithoutSpaces() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            text.append(i);
        }

        List<TextChunk> chunks = chunker.chunk(List.of(text.toString()));

        StringBuilder joined = new StringBuilder();
        chunks.forEach(c -> joined.append(c.text()));
        assertEquals(text.toString(), joined.toString());
    }

    @Test
    void normalisesLineEndingsAndSpacing() {
        List<TextChunk> chunks = chunker.chunk(List.of("Line one is here.\r\n\r\n\r\n\r\nLine   two\twith tabs."));

        assertEquals("Line one is here.\n\nLine two with tabs.", chunks.get(0).text());
    }

    @Test
    void rejectsSettingsThatCouldStallChunking() {
        assertThrows(IllegalArgumentException.class, () -> new TextChunker(50, 10));
        assertThrows(IllegalArgumentException.class, () -> new TextChunker(100, 50));
        assertThrows(IllegalArgumentException.class, () -> new TextChunker(100, -1));
    }

    private static List<String> sentences(int count) {
        List<String> sentences = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            sentences.add("Item " + i + " covers topic " + i + ".");
        }
        return sentences;
    }
}
