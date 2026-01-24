package com.smartuniversity.controller;

import com.smartuniversity.dto.PaymentInitiateRequest;
import com.smartuniversity.dto.PaymentInitiateResponse;
import com.smartuniversity.model.User;
import com.smartuniversity.service.PaymentService;
import com.smartuniversity.util.AuthUtils;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
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

    @GetMapping(value = "/checkout", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> paymentCheckout(
            @RequestParam String orderId,
            @RequestParam String paymentUrl,
            @RequestParam String merchant_id,
            @RequestParam String return_url,
            @RequestParam String cancel_url,
            @RequestParam String notify_url,
            @RequestParam String items,
            @RequestParam String currency,
            @RequestParam String amount,
            @RequestParam String first_name,
            @RequestParam String last_name,
            @RequestParam String email,
            @RequestParam String phone,
            @RequestParam String address,
            @RequestParam String city,
            @RequestParam String country,
            @RequestParam String hash,
            @RequestParam(required = false) String custom_1,
            @RequestParam(required = false) String custom_2) {

        System.out.println("=== PAYMENT CHECKOUT REDIRECT ===");
        System.out.println("Order ID: " + orderId);
        System.out.println("Payment URL: " + paymentUrl);

        // Build auto-submitting HTML form
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>");
        html.append("<html><head><title>Redirecting to Payment...</title></head>");
        html.append("<body>");
        html.append("<h2 style='text-align:center;margin-top:50px;'>Redirecting to PayHere...</h2>");
        html.append("<p style='text-align:center;'>Please wait while we redirect you to the payment gateway.</p>");
        html.append("<form id='payhere_form' method='POST' action='").append(escapeHtml(paymentUrl)).append("'>");
        html.append("<input type='hidden' name='merchant_id' value='").append(escapeHtml(merchant_id)).append("'>");
        html.append("<input type='hidden' name='return_url' value='").append(escapeHtml(return_url)).append("'>");
        html.append("<input type='hidden' name='cancel_url' value='").append(escapeHtml(cancel_url)).append("'>");
        html.append("<input type='hidden' name='notify_url' value='").append(escapeHtml(notify_url)).append("'>");
        html.append("<input type='hidden' name='order_id' value='").append(escapeHtml(orderId)).append("'>");
        html.append("<input type='hidden' name='items' value='").append(escapeHtml(items)).append("'>");
        html.append("<input type='hidden' name='currency' value='").append(escapeHtml(currency)).append("'>");
        html.append("<input type='hidden' name='amount' value='").append(escapeHtml(amount)).append("'>");
        html.append("<input type='hidden' name='first_name' value='").append(escapeHtml(first_name)).append("'>");
        html.append("<input type='hidden' name='last_name' value='").append(escapeHtml(last_name)).append("'>");
        html.append("<input type='hidden' name='email' value='").append(escapeHtml(email)).append("'>");
        html.append("<input type='hidden' name='phone' value='").append(escapeHtml(phone)).append("'>");
        html.append("<input type='hidden' name='address' value='").append(escapeHtml(address)).append("'>");
        html.append("<input type='hidden' name='city' value='").append(escapeHtml(city)).append("'>");
        html.append("<input type='hidden' name='country' value='").append(escapeHtml(country)).append("'>");
        html.append("<input type='hidden' name='hash' value='").append(escapeHtml(hash)).append("'>");
        if (custom_1 != null) {
            html.append("<input type='hidden' name='custom_1' value='").append(escapeHtml(custom_1)).append("'>");
        }
        if (custom_2 != null) {
            html.append("<input type='hidden' name='custom_2' value='").append(escapeHtml(custom_2)).append("'>");
        }
        html.append("</form>");
        html.append("<script>document.getElementById('payhere_form').submit();</script>");
        html.append("</body></html>");

        return ResponseEntity.ok(html.toString());
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
}
