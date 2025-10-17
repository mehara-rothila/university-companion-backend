package com.smartuniversity.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_ratings")
public class UserRating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "rated_user_id")
    private Long ratedUserId; // The user being rated

    @NotNull
    @Column(name = "rater_user_id")
    private Long raterUserId; // The user giving the rating

    @NotNull
    @Min(1)
    @Max(5)
    private Integer rating; // 1-5 stars

    @Column(length = 500)
    private String comment;

    @Column(name = "transaction_type")
    private String transactionType; // "BOOK_LENDING", "BOOK_PURCHASE", etc.

    @Column(name = "transaction_id")
    private Long transactionId; // Reference to BookRequest or other transaction

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public UserRating() {}

    public UserRating(Long ratedUserId, Long raterUserId, Integer rating, String comment, String transactionType, Long transactionId) {
        this.ratedUserId = ratedUserId;
        this.raterUserId = raterUserId;
        this.rating = rating;
        this.comment = comment;
        this.transactionType = transactionType;
        this.transactionId = transactionId;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getRatedUserId() {
        return ratedUserId;
    }

    public void setRatedUserId(Long ratedUserId) {
        this.ratedUserId = ratedUserId;
    }

    public Long getRaterUserId() {
        return raterUserId;
    }

    public void setRaterUserId(Long raterUserId) {
        this.raterUserId = raterUserId;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(String transactionType) {
        this.transactionType = transactionType;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(Long transactionId) {
        this.transactionId = transactionId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
