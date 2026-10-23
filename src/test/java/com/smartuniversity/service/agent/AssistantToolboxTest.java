package com.smartuniversity.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AssistantToolboxTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void declaresEveryToolAndLeavesOutEmptyParameters() {
        AssistantToolbox toolbox = new AssistantToolbox(List.of(
                tool("find_events", ToolArgs.object(Map.of("query", ToolArgs.text("Keyword"))), (args, userId) -> "ok"),
                tool("my_aid", Map.of(), (args, userId) -> "ok")), objectMapper);

        JsonNode declarations = objectMapper.valueToTree(toolbox.declarations()).get(0).get("functionDeclarations");

        assertEquals(2, declarations.size());
        assertEquals("find_events", declarations.get(0).get("name").asText());
        assertEquals("Describes find_events", declarations.get(0).get("description").asText());
        assertEquals("string", declarations.get(0).at("/parameters/properties/query/type").asText());
        assertFalse(declarations.get(1).has("parameters"));
    }

    @Test
    void runsTheToolForTheSignedInUser() throws Exception {
        AssistantToolbox toolbox = new AssistantToolbox(List.of(
                tool("whoami", Map.of(), (args, userId) -> Map.of("user", userId, "asked", args.path("q").asText()))), objectMapper);

        Map<String, Object> response = toolbox.call("whoami", objectMapper.readTree("{\"q\": \"hi\"}"), 7L);

        assertEquals(Map.of("result", Map.of("user", 7L, "asked", "hi")), response);
    }

    @Test
    void givesToolsAnEmptyObjectWhenTheModelSendsNoArguments() {
        AssistantToolbox toolbox = new AssistantToolbox(List.of(
                tool("count", Map.of(), (args, userId) -> args.size())), objectMapper);

        assertEquals(Map.of("result", 0), toolbox.call("count", null, 1L));
        assertEquals(Map.of("result", 0), toolbox.call("count", objectMapper.getNodeFactory().textNode("oops"), 1L));
    }

    @Test
    void reportsUnknownTools() {
        AssistantToolbox toolbox = new AssistantToolbox(List.of(), objectMapper);

        assertTrue(toolbox.isEmpty());
        assertEquals(Map.of("error", "There is no tool called delete_everything."),
                toolbox.call("delete_everything", null, 1L));
    }

    @Test
    void passesBadArgumentMessagesBackToTheModel() {
        AssistantToolbox toolbox = new AssistantToolbox(List.of(
                tool("strict", Map.of(), (args, userId) -> {
                    throw new IllegalArgumentException("type must be one of [LOST, FOUND]");
                })), objectMapper);

        assertEquals(Map.of("error", "type must be one of [LOST, FOUND]"), toolbox.call("strict", null, 1L));
    }

    @Test
    void turnsFailuresIntoAnErrorInsteadOfThrowing() {
        AssistantToolbox toolbox = new AssistantToolbox(List.of(
                tool("broken", Map.of(), (args, userId) -> {
                    throw new IllegalStateException("database down");
                })), objectMapper);

        Map<String, Object> response = toolbox.call("broken", null, 1L);

        assertTrue(response.get("error").toString().contains("couldn't check it right now"));
        assertFalse(response.toString().contains("database down"));
    }

    @Test
    void cutsVeryLongResults() {
        AssistantToolbox toolbox = new AssistantToolbox(List.of(
                tool("chatty", Map.of(), (args, userId) -> Map.of("text", "x".repeat(20_000)))), objectMapper);

        Map<String, Object> response = toolbox.call("chatty", null, 1L);

        assertEquals(true, response.get("truncated"));
        assertEquals(AssistantToolbox.MAX_RESULT_CHARS, response.get("result").toString().length());
    }

    @FunctionalInterface
    private interface Body {
        Object run(JsonNode args, long userId) throws Exception;
    }

    private static AssistantTool tool(String name, Map<String, Object> parameters, Body body) {
        return new AssistantTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return "Describes " + name;
            }

            @Override
            public Map<String, Object> parameters() {
                return parameters;
            }

            @Override
            public Object run(JsonNode args, long userId) throws Exception {
                return body.run(args, userId);
            }
        };
    }
}
