package com.smartuniversity.service.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The tools offered to the assistant, and the one place their calls run.
 *
 * <p>A call never throws. Unknown tools, bad arguments and failures all come back as an
 * {@code error} the model can read and explain, so one broken lookup can't fail the whole answer.</p>
 */
@Component
public class AssistantToolbox {

    private static final Logger log = LoggerFactory.getLogger(AssistantToolbox.class);

    /** Results longer than this, as JSON, are cut so one tool can't flood the prompt. */
    static final int MAX_RESULT_CHARS = 12_000;

    private final Map<String, AssistantTool> tools = new LinkedHashMap<>();
    private final ObjectMapper objectMapper;

    public AssistantToolbox(List<AssistantTool> tools, ObjectMapper objectMapper) {
        tools.forEach(tool -> this.tools.put(tool.name(), tool));
        this.objectMapper = objectMapper;
    }

    public boolean isEmpty() {
        return tools.isEmpty();
    }

    /** The {@code tools} field of a Gemini request. */
    public List<Map<String, Object>> declarations() {
        List<Map<String, Object>> functions = new ArrayList<>();
        for (AssistantTool tool : tools.values()) {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.name());
            function.put("description", tool.description());
            if (!tool.parameters().isEmpty()) {
                function.put("parameters", tool.parameters());
            }
            functions.add(function);
        }
        return List.of(Map.of("functionDeclarations", functions));
    }

    /** Runs one function call for the signed-in user and returns the {@code response} to send back to the model. */
    public Map<String, Object> call(String name, JsonNode args, long userId) {
        AssistantTool tool = tools.get(name);
        if (tool == null) {
            return Map.of("error", "There is no tool called " + name + ".");
        }
        try {
            JsonNode safeArgs = args == null || !args.isObject() ? objectMapper.createObjectNode() : args;
            Object result = tool.run(safeArgs, userId);
            if (result == null) {
                return Map.of("result", Map.of());
            }
            String json = objectMapper.writeValueAsString(result);
            if (json.length() > MAX_RESULT_CHARS) {
                return Map.of("result", json.substring(0, MAX_RESULT_CHARS), "truncated", true);
            }
            return Map.of("result", result);
        } catch (IllegalArgumentException e) {
            return Map.of("error", e.getMessage());
        } catch (Exception e) {
            log.warn("Assistant tool {} failed", name, e);
            return Map.of("error", "This lookup failed, so tell the student you couldn't check it right now.");
        }
    }
}
