package com.smartuniversity.dto;

import java.util.Map;

public class PaymentInitiateResponse {

    private boolean success;
    private String paymentUrl;
    private String orderId;
    private String transactionRef;
    private String message;
    private String errorCode;
    private Map<String, String> formData;

    public PaymentInitiateResponse() {}

    public PaymentInitiateResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static PaymentInitiateResponse success(String paymentUrl, String orderId, String transactionRef) {
        PaymentInitiateResponse response = new PaymentInitiateResponse();
        response.setSuccess(true);
        response.setPaymentUrl(paymentUrl);
        response.setOrderId(orderId);
        response.setTransactionRef(transactionRef);
        response.setMessage("Payment initiated successfully");
        return response;
    }

    public static PaymentInitiateResponse successWithFormData(String paymentUrl, String orderId, String transactionRef, Map<String, String> formData) {
        PaymentInitiateResponse response = new PaymentInitiateResponse();
        response.setSuccess(true);
        response.setPaymentUrl(paymentUrl);
        response.setOrderId(orderId);
        response.setTransactionRef(transactionRef);
        response.setFormData(formData);
        response.setMessage("Payment initiated successfully");
        return response;
    }

    public static PaymentInitiateResponse error(String message, String errorCode) {
        PaymentInitiateResponse response = new PaymentInitiateResponse();
        response.setSuccess(false);
        response.setMessage(message);
        response.setErrorCode(errorCode);
        return response;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getPaymentUrl() {
        return paymentUrl;
    }

    public void setPaymentUrl(String paymentUrl) {
        this.paymentUrl = paymentUrl;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getTransactionRef() {
        return transactionRef;
    }

    public void setTransactionRef(String transactionRef) {
        this.transactionRef = transactionRef;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public Map<String, String> getFormData() {
        return formData;
    }

    public void setFormData(Map<String, String> formData) {
        this.formData = formData;
    }
}
