package com.smartuniversity.controller;

import com.smartuniversity.dto.PaymentInitiateRequest;
import com.smartuniversity.dto.PaymentInitiateResponse;
import com.smartuniversity.model.User;
import com.smartuniversity.service.PaymentService;
import com.smartuniversity.util.AuthUtils;
import jakarta.validation.Valid;
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

    @Autowired
    private AuthUtils authUtils;

    @PostMapping("/initiate")
    public ResponseEntity<PaymentInitiateResponse> initiatePayment(
            @Valid @RequestBody PaymentInitiateRequest request,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        System.out.println("=== PAYMENT INITIATE REQUEST ===");
        System.out.println("Financial Aid ID: " + request.getFinancialAidId());
        System.out.println("Amount: " + request.getAmount());
        System.out.println("Anonymous: " + request.isAnonymous());

        User user = authUtils.getUserFromAuthHeader(authHeader);
        System.out.println("User: " + (user != null ? user.getUsername() : "null"));

        PaymentInitiateResponse response = paymentService.initiatePayment(request, user);

        System.out.println("Response success: " + response.isSuccess());
        System.out.println("Response message: " + response.getMessage());
        if (response.getPaymentUrl() != null) {
            System.out.println("Payment URL: " + response.getPaymentUrl());
        }

        // Always return 200 with response body - let frontend handle based on success flag
        return ResponseEntity.ok(response);
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> handleWebhook(
            @RequestBody Map<String, Object> webhookData) {

        System.out.println("Received payment webhook: " + webhookData);

        boolean processed = paymentService.processWebhook(webhookData);

        if (processed) {
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Webhook processed successfully"
            ));
        } else {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", "Failed to process webhook"
            ));
        }
    }

    @GetMapping("/status/{orderId}")
    public ResponseEntity<Map<String, Object>> getPaymentStatus(@PathVariable String orderId) {
        Map<String, Object> status = paymentService.getPaymentStatus(orderId);
        return ResponseEntity.ok(status);
    }

    @GetMapping("/callback")
    public ResponseEntity<Map<String, Object>> paymentCallback(
            @RequestParam(required = false) String order_id,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String payment_id) {

        System.out.println("Payment callback - Order: " + order_id + ", Status: " + status);

        Map<String, Object> result = paymentService.getPaymentStatus(order_id);
        result.put("order_id", order_id);
        result.put("payment_status", status);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/cancel")
    public ResponseEntity<Map<String, Object>> paymentCancel(
            @RequestParam(required = false) String order_id) {

        System.out.println("Payment cancelled - Order: " + order_id);

        return ResponseEntity.ok(Map.of(
            "status", "cancelled",
            "order_id", order_id != null ? order_id : "",
            "message", "Payment was cancelled by user"
        ));
    }
}
