package com.smartuniversity.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ToolArgsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private enum Kind { LOST, FOUND }

    @Test
    void readsTextTrimmedAndCut() throws Exception {
        JsonNode args = json("{\"query\": \"  blue bottle  \", \"blank\": \"   \", \"long\": \"" + "x".repeat(150) + "\", \"count\": 3}");

        assertEquals(Optional.of("blue bottle"), ToolArgs.text(args, "query"));
        assertEquals(Optional.empty(), ToolArgs.text(args, "blank"));
        assertEquals(Optional.empty(), ToolArgs.text(args, "missing"));
        assertEquals(ToolArgs.MAX_TEXT_LENGTH, ToolArgs.text(args, "long").orElseThrow().length());
        assertEquals(Optional.of("3"), ToolArgs.text(args, "count"));
    }

    @Test
    void ignoresTextGivenAsAnObjectOrList() throws Exception {
        JsonNode args = json("{\"query\": {\"nested\": true}, \"list\": [\"a\"]}");

        assertEquals(Optional.empty(), ToolArgs.text(args, "query"));
        assertEquals(Optional.empty(), ToolArgs.text(args, "list"));
    }

    @Test
    void keepsNumbersInRange() throws Exception {
        JsonNode args = json("{\"big\": 500, \"small\": -4, \"text\": \"12\", \"junk\": \"soon\", \"decimal\": 7.9}");

        assertEquals(90, ToolArgs.number(args, "big", 30, 1, 90));
        assertEquals(1, ToolArgs.number(args, "small", 30, 1, 90));
        assertEquals(12, ToolArgs.number(args, "text", 30, 1, 90));
        assertEquals(30, ToolArgs.number(args, "junk", 30, 1, 90));
        assertEquals(30, ToolArgs.number(args, "missing", 30, 1, 90));
        assertEquals(7, ToolArgs.number(args, "decimal", 30, 1, 90));
    }

    @Test
    void readsFlags() throws Exception {
        JsonNode args = json("{\"yes\": true, \"text\": \"TRUE\", \"no\": false, \"other\": \"maybe\"}");

        assertTrue(ToolArgs.flag(args, "yes"));
        assertTrue(ToolArgs.flag(args, "text"));
        assertFalse(ToolArgs.flag(args, "no"));
        assertFalse(ToolArgs.flag(args, "other"));
        assertFalse(ToolArgs.flag(args, "missing"));
    }

    @Test
    void matchesChoicesIgnoringCaseAndExplainsBadOnes() throws Exception {
        assertEquals(Optional.of(Kind.FOUND), ToolArgs.choice(json("{\"type\": \"found\"}"), "type", Kind.class));
        assertEquals(Optional.empty(), ToolArgs.choice(json("{}"), "type", Kind.class));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> ToolArgs.choice(json("{\"type\": \"stolen\"}"), "type", Kind.class));
        assertEquals("type must be one of [LOST, FOUND]", error.getMessage());
    }

    @Test
    void scoresHowManyQueryWordsAppear() {
        assertEquals(3, ToolArgs.matchScore("Blue water bottle", "Water bottle", "blue lid"));
        assertEquals(2, ToolArgs.matchScore("Blue water bottle", "Water bottle"));
        assertEquals(1, ToolArgs.matchScore("my ID card", "Student ID", null));
        assertEquals(0, ToolArgs.matchScore("the and of", "the and of"));
        assertEquals(0, ToolArgs.matchScore("umbrella", "Water bottle"));
    }

    @Test
    void matchesSinhalaAndTamilWords() {
        assertEquals(1, ToolArgs.matchScore("පුස්තකාලය", "ප්‍රධාන පුස්තකාලය"));
        assertEquals(1, ToolArgs.matchScore("நூலகம்", "பிரதான நூலகம்"));
    }

    @Test
    void knowsWhenAQueryHasNothingToSearchFor() {
        assertTrue(ToolArgs.isSearchable("calculator"));
        assertFalse(ToolArgs.isSearchable("the and"));
        assertFalse(ToolArgs.isSearchable("?!"));
    }

    @Test
    void ranksBestMatchesFirstAndKeepsTheOrderOfTies() {
        List<String> items = List.of("red pen", "blue water bottle", "water bottle", "blue cap", "notebook");

        List<String> matches = ToolArgs.bestMatches(items, "blue water bottle", item -> new String[]{item});

        assertEquals(List.of("blue water bottle", "water bottle", "blue cap"), matches);
    }

    @Test
    void clipsLongTextWithAnEllipsis() {
        assertEquals("short", ToolArgs.clip("  short ", 10));
        assertEquals("abcde…", ToolArgs.clip("abcdefghij", 5));
        assertNull(ToolArgs.clip(null, 5));
    }

    @Test
    void writesDatesTheModelCanReasonAbout() {
        assertEquals("Fri 23 Oct 2026, 14:00", ToolArgs.when(LocalDateTime.of(2026, 10, 23, 14, 0)));
        assertNull(ToolArgs.when(null));
    }

    private JsonNode json(String text) throws Exception {
        return objectMapper.readTree(text);
    }
}
