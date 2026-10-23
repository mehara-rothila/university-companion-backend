package com.smartuniversity.service.agent;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Builds tool schemas and reads tool arguments defensively: the model can send anything, so every
 * value is checked, trimmed and bounded before a tool uses it.
 */
final class ToolArgs {

    /** Longest text argument a tool accepts; longer values are cut. */
    static final int MAX_TEXT_LENGTH = 100;

    private static final Set<String> STOP_WORDS = Set.of("the", "and", "for", "with", "near", "my", "of", "in", "on", "at", "to");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm", Locale.ENGLISH);

    private ToolArgs() {
    }

    // ==================== Schemas ====================

    static Map<String, Object> object(Map<String, Object> properties, String... required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (required.length > 0) {
            schema.put("required", List.of(required));
        }
        return schema;
    }

    static Map<String, Object> text(String description) {
        return Map.of("type", "string", "description", description);
    }

    static Map<String, Object> integer(String description) {
        return Map.of("type", "integer", "description", description);
    }

    static Map<String, Object> flag(String description) {
        return Map.of("type", "boolean", "description", description);
    }

    static Map<String, Object> oneOf(String description, String... values) {
        return Map.of("type", "string", "enum", List.of(values), "description", description);
    }

    // ==================== Reading arguments ====================

    /** A trimmed text argument cut to {@link #MAX_TEXT_LENGTH}, or empty when it's missing or blank. */
    static Optional<String> text(JsonNode args, String name) {
        JsonNode value = args.path(name);
        if (!value.isValueNode() || value.isNull()) {
            return Optional.empty();
        }
        String text = value.asText().strip();
        if (text.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text);
    }

    /** A whole number kept between min and max, or the fallback when it's missing or not a number. */
    static int number(JsonNode args, String name, int fallback, int min, int max) {
        JsonNode value = args.path(name);
        int number = fallback;
        if (value.isNumber()) {
            number = value.asInt();
        } else if (value.isTextual()) {
            try {
                number = Integer.parseInt(value.asText().strip());
            } catch (NumberFormatException ignored) {
                // keep the fallback
            }
        }
        return Math.max(min, Math.min(max, number));
    }

    static boolean flag(JsonNode args, String name) {
        JsonNode value = args.path(name);
        return value.isBoolean() ? value.asBoolean() : "true".equalsIgnoreCase(value.asText(""));
    }

    /**
     * One of an enum's values, matched ignoring case. Anything else throws an
     * {@link IllegalArgumentException} whose message goes back to the model so it can correct itself.
     */
    static <E extends Enum<E>> Optional<E> choice(JsonNode args, String name, Class<E> type) {
        return text(args, name).map(value -> {
            for (E constant : type.getEnumConstants()) {
                if (constant.name().equalsIgnoreCase(value)) {
                    return constant;
                }
            }
            throw new IllegalArgumentException(name + " must be one of " + Arrays.toString(type.getEnumConstants()));
        });
    }

    // ==================== Matching and output ====================

    /**
     * How many of the query's words appear in the fields, ignoring case. Words shorter than two
     * letters and a few filler words don't count. Letters and vowel signs of any script count, so
     * Sinhala and Tamil words match too.
     */
    static int matchScore(String query, String... fields) {
        String haystack = String.join(" ", Arrays.stream(fields).filter(Objects::nonNull).toList())
                .toLowerCase(Locale.ROOT);
        int score = 0;
        for (String word : query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{M}\\p{N}]+")) {
            if (word.length() >= 2 && !STOP_WORDS.contains(word) && haystack.contains(word)) {
                score++;
            }
        }
        return score;
    }

    /** True when the query has at least one word that {@link #matchScore} would count. */
    static boolean isSearchable(String query) {
        return matchScore(query, query) > 0;
    }

    /**
     * The items that match at least one query word, best match first. Items that match equally
     * keep their original order, so a list sorted by date stays sorted within each score.
     */
    static <T> List<T> bestMatches(List<T> items, String query, Function<T, String[]> fields) {
        Map<T, Integer> scores = new IdentityHashMap<>();
        for (T item : items) {
            int score = matchScore(query, fields.apply(item));
            if (score > 0) {
                scores.put(item, score);
            }
        }
        return items.stream()
                .filter(scores::containsKey)
                .sorted(Comparator.comparing(scores::get, Comparator.reverseOrder()))
                .toList();
    }

    /** The text cut to max characters, with an ellipsis when it was cut. Null stays null. */
    static String clip(String text, int max) {
        if (text == null) {
            return null;
        }
        String stripped = text.strip();
        return stripped.length() <= max ? stripped : stripped.substring(0, max).stripTrailing() + "…";
    }

    /** A date the model can read and reason about, like "Fri 24 Oct 2026, 14:00". Null stays null. */
    static String when(LocalDateTime dateTime) {
        return dateTime == null ? null : WHEN.format(dateTime);
    }

    /** Adds the entry only when the value isn't null, so results don't fill up with empty fields. */
    static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }
}
