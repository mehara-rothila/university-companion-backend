package com.smartcampus.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String author;

    @Column
    private String isbn;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookType bookType; // PHYSICAL or DIGITAL

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookCondition bookCondition; // EXCELLENT, GOOD, FAIR, POOR (for physical books)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookCategory category; // TEXTBOOK, REFERENCE, PROGRAMMING, ENGINEERING, OTHER

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LendingType lendingType; // FREE, SELL, TRADE (for physical books)

    @Column
    private Integer price; // For selling books

    // For physical books
    @Column
    private String photoUrl;

    @Column
    private String preferredPickupLocation;

    @Column
    private Boolean availableForLending = true;

    @Column
    private String currentlyLentTo;

    @Column
    private LocalDateTime expectedReturnDate;

    // For digital books (PDFs)
    @Column
    private String pdfUrl;

    @Column
    private Long fileSize; // in bytes

    @Column
    private Integer downloadCount = 0;

    // Owner information
    @Column(nullable = false)
    private Long ownerId;

    @Column(nullable = false)
    private String ownerName;

    @Column(nullable = false)
    private String ownerEmail;

    @Column
    private String ownerPhone;

    @Column
    private Double ownerRating;

    @Column(nullable = false)
    private LocalDateTime uploadDate;

    @Column
    private Integer totalRequests = 0;

    public enum BookType {
        PHYSICAL,
        DIGITAL
    }

    public enum BookCondition {
        EXCELLENT,
        GOOD,
        FAIR,
        POOR
    }

    public enum BookCategory {
        TEXTBOOK,
        REFERENCE,
        PROGRAMMING,
        ENGINEERING,
        OTHER
    }

    public enum LendingType {
        FREE,
        SELL,
        TRADE
    }

    // Constructors
    public Book() {
        this.uploadDate = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BookType getBookType() {
        return bookType;
    }

    public void setBookType(BookType bookType) {
        this.bookType = bookType;
    }

    public BookCondition getBookCondition() {
        return bookCondition;
    }

    public void setBookCondition(BookCondition bookCondition) {
        this.bookCondition = bookCondition;
    }

    public BookCategory getCategory() {
        return category;
    }

    public void setCategory(BookCategory category) {
        this.category = category;
    }

    public LendingType getLendingType() {
        return lendingType;
    }

    public void setLendingType(LendingType lendingType) {
        this.lendingType = lendingType;
    }

    public Integer getPrice() {
        return price;
    }

    public void setPrice(Integer price) {
        this.price = price;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }

    public String getPreferredPickupLocation() {
        return preferredPickupLocation;
    }

    public void setPreferredPickupLocation(String preferredPickupLocation) {
        this.preferredPickupLocation = preferredPickupLocation;
    }

    public Boolean getAvailableForLending() {
        return availableForLending;
    }

    public void setAvailableForLending(Boolean availableForLending) {
        this.availableForLending = availableForLending;
    }

    public String getCurrentlyLentTo() {
        return currentlyLentTo;
    }

    public void setCurrentlyLentTo(String currentlyLentTo) {
        this.currentlyLentTo = currentlyLentTo;
    }

    public LocalDateTime getExpectedReturnDate() {
        return expectedReturnDate;
    }

    public void setExpectedReturnDate(LocalDateTime expectedReturnDate) {
        this.expectedReturnDate = expectedReturnDate;
    }

    public String getPdfUrl() {
        return pdfUrl;
    }

    public void setPdfUrl(String pdfUrl) {
        this.pdfUrl = pdfUrl;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public Integer getDownloadCount() {
        return downloadCount;
    }

    public void setDownloadCount(Integer downloadCount) {
        this.downloadCount = downloadCount;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getOwnerEmail() {
        return ownerEmail;
    }

    public void setOwnerEmail(String ownerEmail) {
        this.ownerEmail = ownerEmail;
    }

    public String getOwnerPhone() {
        return ownerPhone;
    }

    public void setOwnerPhone(String ownerPhone) {
        this.ownerPhone = ownerPhone;
    }

    public Double getOwnerRating() {
        return ownerRating;
    }

    public void setOwnerRating(Double ownerRating) {
        this.ownerRating = ownerRating;
    }

    public LocalDateTime getUploadDate() {
        return uploadDate;
    }

    public void setUploadDate(LocalDateTime uploadDate) {
        this.uploadDate = uploadDate;
    }

    public Integer getTotalRequests() {
        return totalRequests;
    }

    public void setTotalRequests(Integer totalRequests) {
        this.totalRequests = totalRequests;
    }
}
