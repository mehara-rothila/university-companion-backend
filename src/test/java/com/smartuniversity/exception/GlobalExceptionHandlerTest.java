package com.smartuniversity.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleNotFound_returns404WithMessage() {
        ResponseEntity<Map<String, String>> response =
                handler.handleNotFound(new NotFoundException("Event not found"));

        assertEquals(404, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Event not found", response.getBody().get("error"));
    }

    @Test
    void handleUnauthorized_returns401WithMessage() {
        ResponseEntity<Map<String, String>> response =
                handler.handleUnauthorized(new UnauthorizedException("Login required"));

        assertEquals(401, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Login required", response.getBody().get("error"));
    }

    @Test
    void handleForbidden_returns403WithMessage() {
        ResponseEntity<Map<String, String>> response =
                handler.handleForbidden(new ForbiddenException("Admins only"));

        assertEquals(403, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Admins only", response.getBody().get("error"));
    }

    @Test
    void handleIllegalArgument_returns400WithMessage() {
        ResponseEntity<Map<String, String>> response =
                handler.handleIllegalArgument(new IllegalArgumentException("Invalid input"));

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Invalid input", response.getBody().get("error"));
    }

    @Test
    void handleRuntimeException_returns400WithMessage() {
        ResponseEntity<Map<String, String>> response =
                handler.handleRuntimeException(new RuntimeException("Something broke"));

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Something broke", response.getBody().get("error"));
    }

    @Test
    void handleException_returns500WithoutLeakingDetails() {
        ResponseEntity<Map<String, String>> response =
                handler.handleException(new Exception("secret internal detail"));

        assertEquals(500, response.getStatusCode().value());
        assertNotNull(response.getBody());
        // Generic exceptions must not leak internal messages to the client
        assertEquals("Internal server error", response.getBody().get("error"));
    }
}
