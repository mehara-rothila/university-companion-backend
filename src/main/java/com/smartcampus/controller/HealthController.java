package com.smartcampus.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    
    @GetMapping("/health")
    public String health() {
        return "Smart Campus Backend is running!";
    }
    
    @GetMapping("/api/status")
    public String status() {
        return "OK";
    }
}