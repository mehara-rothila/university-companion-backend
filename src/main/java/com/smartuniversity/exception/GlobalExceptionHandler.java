package com.smartuniversity.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException e) {
        String msg = e.getMessage();
        if (msg == null) {
            msg = "An error occurred";
        }
        String lower = msg.toLowerCase();

        if (lower.contains("not found")) {
            return ResponseEntity.status(404).body(Map.of("error", msg));
        }
        if (lower.contains("unauthorized") || lower.contains("authentication required")) {
            return ResponseEntity.status(401).body(Map.of("error", msg));
        }
        if (lower.contains("forbidden")) {
            return ResponseEntity.status(403).body(Map.of("error", msg));
        }
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception e) {
        return ResponseEntity.status(500).body(Map.of("error", "Internal server error"));
    }
}
