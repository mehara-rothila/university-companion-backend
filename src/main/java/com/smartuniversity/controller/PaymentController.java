package com.smartuniversity.controller;

import com.smartuniversity.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    /**
     * Confirm Stripe payment - called by frontend after successful checkout
     */
    @PostMapping("/stripe/confirm")
    public ResponseEntity<Map<String, Object>> confirmStripePayment(
            @RequestBody Map<String, String> request) {

        String sessionId = request.get("sessionId");

        System.out.println("=== STRIPE CONFIRM REQUEST ===");
        System.out.println("Session ID: " + sessionId);

        if (sessionId == null || sessionId.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "ERROR",
                "message", "Session ID is required"
            ));
        }

        Map<String, Object> result = paymentService.confirmStripePayment(sessionId);
        return ResponseEntity.ok(result);
    }

    /**
     * Get payment status by session ID
     */
    @GetMapping("/status/{sessionId}")
    public ResponseEntity<Map<String, Object>> getPaymentStatus(@PathVariable String sessionId) {
        Map<String, Object> status = paymentService.getPaymentStatus(sessionId);
        return ResponseEntity.ok(status);
    }

    /**
     * Stripe webhook endpoint
     */
    @PostMapping("/stripe/webhook")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {

        System.out.println("Received Stripe webhook");

        boolean processed = paymentService.processStripeWebhook(payload, sigHeader);

        if (processed) {
            return ResponseEntity.ok("Webhook processed");
        } else {
            return ResponseEntity.badRequest().body("Webhook processing failed");
        }
    }
}
