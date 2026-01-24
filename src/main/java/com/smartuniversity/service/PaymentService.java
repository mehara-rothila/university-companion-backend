package com.smartuniversity.service;

import com.smartuniversity.config.PaymentConfig;
import com.smartuniversity.dto.PaymentInitiateRequest;
import com.smartuniversity.dto.PaymentInitiateResponse;
import com.smartuniversity.model.FinancialAid;
import com.smartuniversity.model.FinancialAidDonation;
import com.smartuniversity.model.User;
import com.smartuniversity.repository.FinancialAidDonationRepository;
import com.smartuniversity.repository.FinancialAidRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class PaymentService {

    @Autowired
    private PaymentConfig paymentConfig;

    @Autowired
    private FinancialAidRepository financialAidRepository;

    @Autowired
    private FinancialAidDonationRepository donationRepository;

    private final RestTemplate restTemplate = new RestTemplate();

    public PaymentInitiateResponse initiatePayment(PaymentInitiateRequest request, User donor) {
        try {
            Optional<FinancialAid> aidOpt = financialAidRepository.findById(request.getFinancialAidId());
            if (aidOpt.isEmpty()) {
                return PaymentInitiateResponse.error("Financial aid application not found", "AID_NOT_FOUND");
            }

            FinancialAid financialAid = aidOpt.get();

            if (financialAid.getStatus() != FinancialAid.ApplicationStatus.APPROVED) {
                return PaymentInitiateResponse.error("Donations only allowed for approved applications", "NOT_APPROVED");
            }

            if (!financialAid.isDonationEligible()) {
                return PaymentInitiateResponse.error("This application is not eligible for donations", "NOT_ELIGIBLE");
            }

            // Generate unique order ID
            String orderId = generateOrderId();
            String transactionRef = "DON-" + financialAid.getId() + "-" + System.currentTimeMillis();

            // Create pending donation record
            FinancialAidDonation donation = new FinancialAidDonation(financialAid, donor, request.getAmount());
            donation.setAnonymous(request.isAnonymous());
            donation.setMessage(request.getMessage());
            donation.setStatus(FinancialAidDonation.DonationStatus.PENDING);
            donation.setTransactionId(transactionRef);
            donationRepository.save(donation);

            // Prepare payment request to Athena gateway
            Map<String, Object> paymentData = new LinkedHashMap<>();
            paymentData.put("merchant_id", paymentConfig.getMerchantId());
            paymentData.put("order_id", orderId);
            paymentData.put("amount", request.getAmount().setScale(2).toString());
            paymentData.put("currency", "LKR");
            paymentData.put("return_url", paymentConfig.getReturnUrl() + "?order_id=" + orderId);
            paymentData.put("cancel_url", paymentConfig.getCancelUrl() + "?order_id=" + orderId);
            paymentData.put("notify_url", paymentConfig.getNotifyUrl());
            paymentData.put("first_name", donor != null ? donor.getFirstName() : (request.getDonorName() != null ? request.getDonorName() : "Anonymous"));
            paymentData.put("last_name", donor != null ? donor.getLastName() : "Donor");
            paymentData.put("email", donor != null ? donor.getEmail() : (request.getDonorEmail() != null ? request.getDonorEmail() : "donor@university.edu"));
            paymentData.put("phone", "");
            paymentData.put("address", "University Campus");
            paymentData.put("city", "Colombo");
            paymentData.put("country", "Sri Lanka");
            paymentData.put("items", "Donation for Financial Aid #" + financialAid.getId());
            paymentData.put("custom_1", transactionRef);
            paymentData.put("custom_2", String.valueOf(donation.getId()));

            // Generate hash for security
            String hash = generateHash(paymentData);
            paymentData.put("hash", hash);

            // Call Athena payment gateway
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", "Bearer " + paymentConfig.getMerchantSecret());

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(paymentData, headers);

            try {
                ResponseEntity<Map> response = restTemplate.exchange(
                    paymentConfig.getBaseUrl() + "/payment/initiate",
                    HttpMethod.POST,
                    entity,
                    Map.class
                );

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    Map<String, Object> responseBody = response.getBody();
                    String paymentUrl = (String) responseBody.get("payment_url");
                    if (paymentUrl == null) {
                        paymentUrl = (String) responseBody.get("redirect_url");
                    }

                    if (paymentUrl != null) {
                        return PaymentInitiateResponse.success(paymentUrl, orderId, transactionRef);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error calling payment gateway: " + e.getMessage());
                // For sandbox/testing, return a simulated payment URL
                if (paymentConfig.isSandbox()) {
                    String sandboxUrl = "https://sandbox.athena.mehara.io/pay/" + orderId;
                    return PaymentInitiateResponse.success(sandboxUrl, orderId, transactionRef);
                }
            }

            return PaymentInitiateResponse.error("Failed to initiate payment", "GATEWAY_ERROR");

        } catch (Exception e) {
            System.err.println("Payment initiation error: " + e.getMessage());
            e.printStackTrace();
            return PaymentInitiateResponse.error("Internal error: " + e.getMessage(), "INTERNAL_ERROR");
        }
    }

    public boolean processWebhook(Map<String, Object> webhookData) {
        try {
            String orderId = (String) webhookData.get("order_id");
            String status = (String) webhookData.get("status");
            String transactionRef = (String) webhookData.get("custom_1");
            String donationIdStr = (String) webhookData.get("custom_2");
            String paymentId = (String) webhookData.get("payment_id");
            String receivedHash = (String) webhookData.get("hash");

            // Verify hash
            if (!verifyWebhookHash(webhookData, receivedHash)) {
                System.err.println("Invalid webhook hash for order: " + orderId);
                return false;
            }

            if (donationIdStr == null || donationIdStr.isEmpty()) {
                System.err.println("Missing donation ID in webhook");
                return false;
            }

            Long donationId = Long.parseLong(donationIdStr);
            Optional<FinancialAidDonation> donationOpt = donationRepository.findById(donationId);

            if (donationOpt.isEmpty()) {
                System.err.println("Donation not found: " + donationId);
                return false;
            }

            FinancialAidDonation donation = donationOpt.get();

            if ("SUCCESS".equalsIgnoreCase(status) || "COMPLETED".equalsIgnoreCase(status) || "2".equals(status)) {
                donation.setStatus(FinancialAidDonation.DonationStatus.COMPLETED);
                donation.setTransactionId(paymentId != null ? paymentId : transactionRef);
                donationRepository.save(donation);

                // Update financial aid raised amount
                FinancialAid financialAid = donation.getFinancialAid();
                BigDecimal newRaisedAmount = financialAid.getRaisedAmount().add(donation.getAmount());
                financialAid.setRaisedAmount(newRaisedAmount);
                financialAid.setSupporterCount(financialAid.getSupporterCount() + 1);
                financialAidRepository.save(financialAid);

                System.out.println("Payment completed for donation: " + donationId);
                return true;

            } else if ("FAILED".equalsIgnoreCase(status) || "CANCELLED".equalsIgnoreCase(status) || "-1".equals(status) || "0".equals(status)) {
                donation.setStatus(FinancialAidDonation.DonationStatus.FAILED);
                donationRepository.save(donation);
                System.out.println("Payment failed for donation: " + donationId);
                return true;
            }

            return false;

        } catch (Exception e) {
            System.err.println("Webhook processing error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public Map<String, Object> getPaymentStatus(String orderId) {
        Map<String, Object> result = new HashMap<>();

        try {
            // Find donation by transaction reference pattern
            List<FinancialAidDonation> donations = donationRepository.findByTransactionIdContaining(orderId);

            if (!donations.isEmpty()) {
                FinancialAidDonation donation = donations.get(0);
                result.put("status", donation.getStatus().toString());
                result.put("amount", donation.getAmount());
                result.put("donationId", donation.getId());
                result.put("financialAidId", donation.getFinancialAid().getId());
            } else {
                result.put("status", "NOT_FOUND");
            }
        } catch (Exception e) {
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
        }

        return result;
    }

    private String generateOrderId() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = String.valueOf((int) (Math.random() * 10000));
        return "ORD-" + timestamp + "-" + random;
    }

    private String generateHash(Map<String, Object> data) {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(data.get("merchant_id"));
            sb.append(data.get("order_id"));
            sb.append(data.get("amount"));
            sb.append(data.get("currency"));
            sb.append(paymentConfig.getMerchantSecret());

            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                paymentConfig.getMerchantSecret().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
            );
            sha256Hmac.init(secretKey);

            byte[] hashBytes = sha256Hmac.doFinal(sb.toString().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            System.err.println("Hash generation error: " + e.getMessage());
            return "";
        }
    }

    private boolean verifyWebhookHash(Map<String, Object> data, String receivedHash) {
        if (paymentConfig.isSandbox()) {
            return true; // Skip hash verification in sandbox mode
        }

        try {
            StringBuilder sb = new StringBuilder();
            sb.append(data.get("merchant_id"));
            sb.append(data.get("order_id"));
            sb.append(data.get("payment_id"));
            sb.append(data.get("status"));
            sb.append(paymentConfig.getMerchantSecret());

            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(
                paymentConfig.getMerchantSecret().getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
            );
            sha256Hmac.init(secretKey);

            byte[] hashBytes = sha256Hmac.doFinal(sb.toString().getBytes(StandardCharsets.UTF_8));
            String calculatedHash = Base64.getEncoder().encodeToString(hashBytes);

            return calculatedHash.equals(receivedHash);

        } catch (Exception e) {
            System.err.println("Hash verification error: " + e.getMessage());
            return false;
        }
    }
}
