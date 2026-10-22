package com.smartuniversity.service.rag;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class PdfPageReaderTest {

    private final PdfPageReader reader = new PdfPageReader();

    @Test
    void readsEachPageSeparately() throws IOException {
        byte[] pdf = TestPdfs.withPages("Alpha page text", "Beta page text", "Gamma page text");

        PdfPageReader.PdfPages result = reader.read(pdf, 100);

        assertEquals(3, result.totalPages());
        assertEquals(3, result.pages().size());
        assertTrue(result.pages().get(0).contains("Alpha"));
        assertTrue(result.pages().get(1).contains("Beta"));
        assertTrue(result.pages().get(2).contains("Gamma"));
        assertFalse(result.pages().get(0).contains("Beta"));
    }

    @Test
    void stopsAtMaxPagesButReportsTheRealPageCount() throws IOException {
        byte[] pdf = TestPdfs.withPages("One", "Two", "Three");

        PdfPageReader.PdfPages result = reader.read(pdf, 2);

        assertEquals(2, result.pages().size());
        assertEquals(3, result.totalPages());
    }

    @Test
    void keepsBlankPagesSoPageNumbersStayAligned() throws IOException {
        byte[] pdf = TestPdfs.withPages("First", "", "Third");

        PdfPageReader.PdfPages result = reader.read(pdf, 100);

        assertEquals(3, result.pages().size());
        assertTrue(result.pages().get(1).isBlank());
        assertTrue(result.pages().get(2).contains("Third"));
    }

    @Test
    void rejectsBytesThatAreNotAPdf() {
        byte[] notPdf = "just some text".getBytes(StandardCharsets.UTF_8);

        assertThrows(IOException.class, () -> reader.read(notPdf, 10));
    }
}
