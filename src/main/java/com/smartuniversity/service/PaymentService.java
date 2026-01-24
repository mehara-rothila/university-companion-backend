package com.smartuniversity.service;

import com.smartuniversity.config.PaymentConfig;
import com.smartuniversity.model.FinancialAid;
import com.smartuniversity.model.FinancialAidDonation;
import com.smartuniversity.repository.FinancialAidDonationRepository;
import com.smartuniversity.repository.FinancialAidRepository;
import com.stripe.Stripe;
import com.stripe.model.checkout.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

            // Check if donation already exists for this session
            List<FinancialAidDonation> existingDonations = donationRepository.findByTransactionIdContaining(sessionId);
            if (!existingDonations.isEmpty()) {
                FinancialAidDonation existing = existingDonations.get(0);
                if (existing.getStatus() == FinancialAidDonation.DonationStatus.COMPLETED) {
                    System.out.println("Donation already completed for session: " + sessionId);
                    result.put("status", "COMPLETED");
                    result.put("message", "Donation already recorded");
                    result.put("donationId", existing.getId());
                    result.put("amount", existing.getAmount());
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
            if (!existingDonations.isEmpty()) {
                donation = existingDonations.get(0);
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
            // First check our database
            List<FinancialAidDonation> donations = donationRepository.findByTransactionIdContaining(sessionId);

            if (!donations.isEmpty()) {
                FinancialAidDonation donation = donations.get(0);
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
     * Process Stripe webhook events
     */
    public boolean processStripeWebhook(String payload, String sigHeader) {
        try {
            // For now, just log the webhook
            System.out.println("Received Stripe webhook");

            // In production, you would verify the signature and process events
            // Event event = Webhook.constructEvent(payload, sigHeader, paymentConfig.getWebhookSecret());

            return true;
        } catch (Exception e) {
            System.err.println("Webhook processing error: " + e.getMessage());
            return false;
        }
    }
}
