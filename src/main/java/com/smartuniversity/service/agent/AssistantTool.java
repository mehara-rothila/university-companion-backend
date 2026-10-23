package com.smartuniversity.service.agent;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;

/**
 * Something Athena's assistant can look up while it answers, offered to Gemini through function calling.
 *
 * <p>Tools only read. The user id always comes from the signed-in user, never from the model's
 * arguments, so a prompt can't make a tool read someone else's records.</p>
 */
public interface AssistantTool {

    /** The function name the model calls, such as {@code find_upcoming_events}. */
    String name();

    /** Tells the model what the tool returns and when to use it. */
    String description();

    /**
     * The arguments as an OpenAPI-style object schema (see {@link ToolArgs#object}), or an empty map
     * when the tool takes none. Gemini rejects object schemas without properties.
     */
    Map<String, Object> parameters();

    /** Runs the tool for the signed-in user. The result must be plain JSON data: maps, lists, text and numbers. */
    Object run(JsonNode args, long userId) throws Exception;
}
