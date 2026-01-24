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

            // Build PayHere checkout URL with parameters
            String merchantId = paymentConfig.getMerchantId();
            String amountFormatted = request.getAmount().setScale(2, java.math.RoundingMode.HALF_UP).toString();
            String currency = "LKR";
            String returnUrl = paymentConfig.getReturnUrl() + "?order_id=" + orderId;
            String cancelUrl = paymentConfig.getCancelUrl() + "?order_id=" + orderId;
            String notifyUrl = paymentConfig.getNotifyUrl();
            String firstName = donor != null && donor.getFirstName() != null ? donor.getFirstName() : (request.getDonorName() != null ? request.getDonorName() : "Anonymous");
            String lastName = donor != null && donor.getLastName() != null ? donor.getLastName() : "Donor";
            String email = donor != null && donor.getEmail() != null ? donor.getEmail() : (request.getDonorEmail() != null ? request.getDonorEmail() : "donor@university.edu");
            String phone = "";
            String address = "University Campus";
            String city = "Colombo";
            String country = "Sri Lanka";
            String items = "Donation for Financial Aid #" + financialAid.getId();

            // Generate PayHere hash (MD5 format)
            String hash = generatePayHereHash(merchantId, orderId, amountFormatted, currency);

            // Build the PayHere checkout URL with all parameters
            StringBuilder paymentUrl = new StringBuilder(paymentConfig.getBaseUrl());
            paymentUrl.append("?merchant_id=").append(urlEncode(merchantId));
            paymentUrl.append("&return_url=").append(urlEncode(returnUrl));
            paymentUrl.append("&cancel_url=").append(urlEncode(cancelUrl));
            paymentUrl.append("&notify_url=").append(urlEncode(notifyUrl));
            paymentUrl.append("&order_id=").append(urlEncode(orderId));
            paymentUrl.append("&items=").append(urlEncode(items));
            paymentUrl.append("&currency=").append(urlEncode(currency));
            paymentUrl.append("&amount=").append(urlEncode(amountFormatted));
            paymentUrl.append("&first_name=").append(urlEncode(firstName));
            paymentUrl.append("&last_name=").append(urlEncode(lastName));
            paymentUrl.append("&email=").append(urlEncode(email));
            paymentUrl.append("&phone=").append(urlEncode(phone));
            paymentUrl.append("&address=").append(urlEncode(address));
            paymentUrl.append("&city=").append(urlEncode(city));
            paymentUrl.append("&country=").append(urlEncode(country));
            paymentUrl.append("&hash=").append(urlEncode(hash));
            paymentUrl.append("&custom_1=").append(urlEncode(transactionRef));
            paymentUrl.append("&custom_2=").append(urlEncode(String.valueOf(donation.getId())));

            return PaymentInitiateResponse.success(paymentUrl.toString(), orderId, transactionRef);

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

    private String generatePayHereHash(String merchantId, String orderId, String amount, String currency) {
        try {
            // PayHere hash format: MD5(merchant_id + order_id + amount + currency + MD5(merchant_secret))
            String merchantSecretHash = md5(paymentConfig.getMerchantSecret()).toUpperCase();
            String hashString = merchantId + orderId + amount + currency + merchantSecretHash;
            return md5(hashString).toUpperCase();
        } catch (Exception e) {
            System.err.println("PayHere hash generation error: " + e.getMessage());
            return "";
        }
    }

    private String md5(String input) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : messageDigest) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("MD5 hash error", e);
        }
    }

    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value != null ? value : "", StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            return value != null ? value : "";
        }
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
