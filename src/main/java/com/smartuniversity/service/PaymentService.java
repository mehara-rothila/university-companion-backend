package com.smartuniversity.service;

import com.smartuniversity.config.PaymentConfig;
import com.smartuniversity.model.FinancialAid;
import com.smartuniversity.model.FinancialAidDonation;
import com.smartuniversity.repository.FinancialAidDonationRepository;
import com.smartuniversity.repository.FinancialAidRepository;
import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.model.Event;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.*;

@Service
public class PaymentService {

    @Autowired
    private PaymentConfig paymentConfig;

    @Autowired
    private FinancialAidRepository financialAidRepository;

    @Autowired
    private FinancialAidDonationRepository donationRepository;

    @PostConstruct
    public void init() {
        String secretKey = paymentConfig.getSecretKey();
        if (secretKey != null && !secretKey.isEmpty()) {
            Stripe.apiKey = secretKey;
            System.out.println("Stripe API initialized");
        } else {
            System.err.println("Warning: Stripe secret key not configured");
        }
    }

    /**
     * Confirm a Stripe payment session and update the donation record
     */
    @Transactional
    public Map<String, Object> confirmStripePayment(String sessionId) {
        Map<String, Object> result = new HashMap<>();

        try {
            System.out.println("=== CONFIRMING STRIPE PAYMENT ===");
            System.out.println("Session ID: " + sessionId);

            // Retrieve session from Stripe
            Session session = Session.retrieve(sessionId);

            System.out.println("Session status: " + session.getStatus());
            System.out.println("Payment status: " + session.getPaymentStatus());

            if (!"complete".equals(session.getStatus()) || !"paid".equals(session.getPaymentStatus())) {
                result.put("status", "PENDING");
                result.put("message", "Payment not yet completed");
                return result;
            }

            // Get metadata from session
            Map<String, String> metadata = session.getMetadata();
            String financialAidIdStr = metadata.get("financialAidId");
            String donorName = metadata.get("donorName");
            boolean isAnonymous = "true".equals(metadata.get("isAnonymous"));
            String message = metadata.get("message");

            if (financialAidIdStr == null) {
                result.put("status", "ERROR");
                result.put("message", "Missing financial aid ID in session metadata");
                return result;
            }

            Long financialAidId = Long.parseLong(financialAidIdStr);

            // Check if donation already exists for this session (exact match)
            FinancialAidDonation existingDonation = donationRepository.findByTransactionId(sessionId);
            if (existingDonation != null) {
                if (existingDonation.getStatus() == FinancialAidDonation.DonationStatus.COMPLETED) {
                    System.out.println("Donation already completed for session: " + sessionId);
                    result.put("status", "COMPLETED");
                    result.put("message", "Donation already recorded");
                    result.put("donationId", existingDonation.getId());
                    result.put("amount", existingDonation.getAmount());
                    return result;
                }
            }

            // Get financial aid
            Optional<FinancialAid> aidOpt = financialAidRepository.findById(financialAidId);
            if (aidOpt.isEmpty()) {
                result.put("status", "ERROR");
                result.put("message", "Financial aid not found");
                return result;
            }

            FinancialAid financialAid = aidOpt.get();

            // Get amount from session (Stripe uses cents)
            BigDecimal amount = BigDecimal.valueOf(session.getAmountTotal()).divide(BigDecimal.valueOf(100));

            // Create or update donation record
            FinancialAidDonation donation;
            if (existingDonation != null) {
                donation = existingDonation;
            } else {
                donation = new FinancialAidDonation(financialAid, null, amount);
            }

            donation.setAmount(amount);
            donation.setAnonymous(isAnonymous);
            donation.setMessage(message);
            donation.setStatus(FinancialAidDonation.DonationStatus.COMPLETED);
            donation.setTransactionId(sessionId);
            donationRepository.save(donation);

            // Update financial aid raised amount
            BigDecimal newRaisedAmount = financialAid.getRaisedAmount().add(amount);
            financialAid.setRaisedAmount(newRaisedAmount);
            financialAid.setSupporterCount(financialAid.getSupporterCount() + 1);
            financialAidRepository.save(financialAid);

            System.out.println("Payment confirmed! Donation ID: " + donation.getId() + ", Amount: " + amount);

            result.put("status", "COMPLETED");
            result.put("message", "Payment confirmed successfully");
            result.put("donationId", donation.getId());
            result.put("amount", amount);
            result.put("financialAidId", financialAidId);

            return result;

        } catch (Exception e) {
            System.err.println("Error confirming Stripe payment: " + e.getMessage());
            e.printStackTrace();
            result.put("status", "ERROR");
            result.put("message", "Failed to confirm payment: " + e.getMessage());
            return result;
        }
    }

    /**
     * Get payment status by session ID
     */
    public Map<String, Object> getPaymentStatus(String sessionId) {
        Map<String, Object> result = new HashMap<>();

        try {
            // First check our database (exact match)
            FinancialAidDonation donation = donationRepository.findByTransactionId(sessionId);

            if (donation != null) {
                result.put("status", donation.getStatus().toString());
                result.put("amount", donation.getAmount());
                result.put("donationId", donation.getId());
                result.put("financialAidId", donation.getFinancialAid().getId());
                return result;
            }

            // If not in database, check Stripe
            Session session = Session.retrieve(sessionId);

            if ("complete".equals(session.getStatus()) && "paid".equals(session.getPaymentStatus())) {
                result.put("status", "complete");
            } else if ("expired".equals(session.getStatus())) {
                result.put("status", "expired");
            } else {
                result.put("status", "open");
            }

            return result;

        } catch (Exception e) {
            System.err.println("Error getting payment status: " + e.getMessage());
            result.put("status", "ERROR");
            result.put("message", e.getMessage());
            return result;
        }
    }

    /**
     * Process Stripe webhook events with signature verification
     */
    public boolean processStripeWebhook(String payload, String sigHeader) {
        try {
            String webhookSecret = paymentConfig.getWebhookSecret();
            if (webhookSecret == null || webhookSecret.isEmpty()) {
                System.err.println("Stripe webhook secret not configured — rejecting webhook");
                return false;
            }

            // Verify webhook signature to prevent forged events
            Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
            System.out.println("Verified Stripe webhook event: " + event.getType());

            // Process relevant event types
            if ("checkout.session.completed".equals(event.getType())) {
                // Payment was successful — confirmation handled by confirmStripePayment
                System.out.println("Checkout session completed event received");
            }

            return true;
        } catch (com.stripe.exception.SignatureVerificationException e) {
            System.err.println("Webhook signature verification failed: " + e.getMessage());
            return false;
        } catch (Exception e) {
            System.err.println("Webhook processing error: " + e.getMessage());
            return false;
        }
    }
}
