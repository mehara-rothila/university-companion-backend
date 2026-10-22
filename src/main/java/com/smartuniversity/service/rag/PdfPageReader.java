package com.smartuniversity.service.rag;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts a PDF's text one page at a time, so each chunk built from it can remember the
 * page it came from and answers can cite that page.
 */
@Component
public class PdfPageReader {

    /**
     * Reads at most {@code maxPages} pages.
     *
     * @return the text of each page read (index 0 is page 1) and the PDF's total page count
     */
    public PdfPages read(byte[] pdfBytes, int maxPages) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int totalPages = document.getNumberOfPages();
            int pagesToRead = Math.min(totalPages, maxPages);

            PDFTextStripper stripper = new PDFTextStripper();
            List<String> pages = new ArrayList<>(pagesToRead);
            for (int page = 1; page <= pagesToRead; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                pages.add(stripper.getText(document));
            }
            return new PdfPages(pages, totalPages);
        }
    }

    public record PdfPages(List<String> pages, int totalPages) {
    }
}
