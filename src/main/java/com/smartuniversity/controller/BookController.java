package com.smartuniversity.controller;

import com.smartuniversity.model.Book;
import com.smartuniversity.model.BookRequest;
import com.smartuniversity.model.User;
import com.smartuniversity.repository.BookRepository;
import com.smartuniversity.repository.BookRequestRepository;
import com.smartuniversity.repository.UserRatingRepository;
import com.smartuniversity.util.AuthUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


@RestController
@RequestMapping("/api/books")
public class BookController {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private BookRequestRepository bookRequestRepository;

    @Autowired
    private UserRatingRepository userRatingRepository;

    @Autowired
    private AuthUtils authUtils;

    // Health check endpoint - verify deployed version
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Books API v3.0 - Admin approval system added - 2025-11-28");
    }

    // Get all approved books (for regular users)
    @GetMapping
    public ResponseEntity<List<Book>> getAllBooks() {
        System.out.println("=== getAllBooks() called ===");
        try {
            System.out.println("Attempting to fetch approved books from database...");
            // Use query that includes null status for backward compatibility
            List<Book> books = bookRepository.findApprovedOrNullStatusBooks();
            System.out.println("Successfully fetched " + books.size() + " approved books");
            // Enrich each book with owner's rating
            books.forEach(book -> {
                try {
                    if (book.getOwnerId() != null) {
                        Double rating = userRatingRepository.getAverageRatingForUser(book.getOwnerId());
                        if (rating != null) {
                            book.setOwnerRating(Math.round(rating * 10.0) / 10.0);
                        }
                    }
                } catch (Exception e) {
                    // Skip rating if there's an error, book will just have null rating
                    System.err.println("Error getting rating for book " + book.getId() + ": " + e.getMessage());
                }
            });
            return ResponseEntity.ok(books);
        } catch (Exception e) {
            System.err.println("Error fetching books: " + e.getMessage());
            e.printStackTrace();
            // Return empty list instead of error
            return ResponseEntity.ok(List.of());
        }
    }

    // --- Admin Endpoints ---

    // Get all books for admin (including pending and rejected)
    @GetMapping("/admin/all")
    public ResponseEntity<?> getAllBooksForAdmin(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!authUtils.isAdmin(authHeader)) {
            return ResponseEntity.status(403).body("Access denied. Admin privileges required.");
        }
        try {
            List<Book> books = bookRepository.findAllByOrderByUploadDateDesc();
            return ResponseEntity.ok(books);
        } catch (Exception e) {
            return ResponseEntity.ok(List.of());
        }
    }

    // Get pending books for admin approval
    @GetMapping("/admin/pending")
    public ResponseEntity<?> getPendingBooks(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!authUtils.isAdmin(authHeader)) {
            return ResponseEntity.status(403).body("Access denied. Admin privileges required.");
        }
        try {
            List<Book> books = bookRepository.findByStatusOrderByUploadDateDesc(Book.BookStatus.PENDING);
            return ResponseEntity.ok(books);
        } catch (Exception e) {
            return ResponseEntity.ok(List.of());
        }
    }

    // Get books by status for admin
    @GetMapping("/admin/status/{status}")
    public ResponseEntity<?> getBooksByStatus(
            @PathVariable String status,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!authUtils.isAdmin(authHeader)) {
            return ResponseEntity.status(403).body("Access denied. Admin privileges required.");
        }
        try {
            Book.BookStatus bookStatus = Book.BookStatus.valueOf(status.toUpperCase());
            List<Book> books;
            // For APPROVED status, also include books with null status (backward compatibility)
            if (bookStatus == Book.BookStatus.APPROVED) {
                books = bookRepository.findApprovedOrNullStatusBooks();
            } else {
                books = bookRepository.findByStatusOrderByUploadDateDesc(bookStatus);
            }
            return ResponseEntity.ok(books);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Approve book
    @PutMapping("/admin/{id}/approve")
    public ResponseEntity<?> approveBook(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!authUtils.isAdmin(authHeader)) {
            return ResponseEntity.status(403).body("Access denied. Admin privileges required.");
        }
        try {
            Optional<Book> bookOptional = bookRepository.findById(id);
            if (bookOptional.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            Book book = bookOptional.get();
            book.setStatus(Book.BookStatus.APPROVED);
            bookRepository.save(book);
            return ResponseEntity.ok(book);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to approve book: " + e.getMessage());
        }
    }

    // Reject book
    @PutMapping("/admin/{id}/reject")
    public ResponseEntity<?> rejectBook(
            @PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!authUtils.isAdmin(authHeader)) {
            return ResponseEntity.status(403).body("Access denied. Admin privileges required.");
        }
        try {
            Optional<Book> bookOptional = bookRepository.findById(id);
            if (bookOptional.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            Book book = bookOptional.get();
            book.setStatus(Book.BookStatus.REJECTED);
            bookRepository.save(book);
            return ResponseEntity.ok(book);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to reject book: " + e.getMessage());
        }
    }

    // Get book stats for admin
    @GetMapping("/admin/stats")
    public ResponseEntity<?> getBookStats(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!authUtils.isAdmin(authHeader)) {
            return ResponseEntity.status(403).body("Access denied. Admin privileges required.");
        }
        try {
            Long pending = bookRepository.countPending();
            Long approved = bookRepository.countApprovedOrNullStatus();
            Long rejected = bookRepository.countRejected();
            Long total = bookRepository.count();
            return ResponseEntity.ok(java.util.Map.of(
                "pending", pending,
                "approved", approved,
                "rejected", rejected,
                "total", total
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to get stats: " + e.getMessage());
        }
    }

    // Get books by type (PHYSICAL or DIGITAL)
    @GetMapping("/type/{bookType}")
    public ResponseEntity<List<Book>> getBooksByType(@PathVariable String bookType) {
        try {
            Book.BookType type = Book.BookType.valueOf(bookType.toUpperCase());
            List<Book> books = bookRepository.findByBookType(type);
            return ResponseEntity.ok(books);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Get books by owner
    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<List<Book>> getBooksByOwner(@PathVariable Long ownerId) {
        List<Book> books = bookRepository.findByOwnerId(ownerId);
        return ResponseEntity.ok(books);
    }

    // Get single book by ID
    @GetMapping("/{id}")
    public ResponseEntity<Book> getBookById(@PathVariable Long id) {
        Optional<Book> book = bookRepository.findById(id);
        return book.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Create new book (requires authentication)
    @PostMapping
    public ResponseEntity<?> createBook(@RequestBody Book book,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authUtils.getUserFromAuthHeader(authHeader) == null) {
            return ResponseEntity.status(401).body("Authentication required");
        }
        try {
            book.setUploadDate(LocalDateTime.now());
            if (book.getTotalRequests() == null) {
                book.setTotalRequests(0);
            }
            if (book.getDownloadCount() == null) {
                book.setDownloadCount(0);
            }
            if (book.getAvailableForLending() == null) {
                book.setAvailableForLending(true);
            }
            // Always set status to PENDING — cannot bypass admin approval
            book.setStatus(Book.BookStatus.PENDING);

            Book savedBook = bookRepository.save(book);
            return ResponseEntity.ok(savedBook);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to create book: " + e.getMessage());
        }
    }

    // Update book (requires authentication)
    @PutMapping("/{id}")
    public ResponseEntity<?> updateBook(@PathVariable Long id, @RequestBody Book bookDetails,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authUtils.getUserFromAuthHeader(authHeader) == null) {
            return ResponseEntity.status(401).body("Authentication required");
        }
        Optional<Book> bookOptional = bookRepository.findById(id);
        if (bookOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Book book = bookOptional.get();

        // Update fields
        if (bookDetails.getTitle() != null) book.setTitle(bookDetails.getTitle());
        if (bookDetails.getAuthor() != null) book.setAuthor(bookDetails.getAuthor());
        if (bookDetails.getIsbn() != null) book.setIsbn(bookDetails.getIsbn());
        if (bookDetails.getDescription() != null) book.setDescription(bookDetails.getDescription());
        if (bookDetails.getBookCondition() != null) book.setBookCondition(bookDetails.getBookCondition());
        if (bookDetails.getCategory() != null) book.setCategory(bookDetails.getCategory());
        if (bookDetails.getLendingType() != null) book.setLendingType(bookDetails.getLendingType());
        if (bookDetails.getPrice() != null) book.setPrice(bookDetails.getPrice());
        if (bookDetails.getAvailableForLending() != null) book.setAvailableForLending(bookDetails.getAvailableForLending());
        if (bookDetails.getPreferredPickupLocation() != null) book.setPreferredPickupLocation(bookDetails.getPreferredPickupLocation());
        
        // Update photo/PDF fields - always update these if they're in the request
        // Use empty string check to allow clearing values
        book.setPhotoUrl(bookDetails.getPhotoUrl());
        book.setPdfUrl(bookDetails.getPdfUrl());
        if (bookDetails.getFileSize() != null) book.setFileSize(bookDetails.getFileSize());

        System.out.println("=== Updating book ID: " + id + " ===");
        System.out.println("New photoUrl: " + bookDetails.getPhotoUrl());
        System.out.println("New pdfUrl: " + bookDetails.getPdfUrl());
        System.out.println("New fileSize: " + bookDetails.getFileSize());

        Book updatedBook = bookRepository.save(book);
        return ResponseEntity.ok(updatedBook);
    }

    // Update book image only
    @PutMapping("/{id}/image")
    public ResponseEntity<?> updateBookImage(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        try {
            Optional<Book> bookOptional = bookRepository.findById(id);
            if (bookOptional.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Book book = bookOptional.get();
            String photoUrl = body.get("photoUrl");

            if (photoUrl == null || photoUrl.trim().isEmpty()) {
                return ResponseEntity.badRequest().body("Photo URL is required");
            }

            book.setPhotoUrl(photoUrl);
            bookRepository.save(book);

            return ResponseEntity.ok(java.util.Map.of("message", "Book image updated successfully", "photoUrl", photoUrl));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to update book image: " + e.getMessage());
        }
    }

    // Delete book (requires admin)
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBook(@PathVariable Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!authUtils.isAdmin(authHeader)) {
            return ResponseEntity.status(403).body("Admin privileges required");
        }
        try {
            Optional<Book> book = bookRepository.findById(id);
            if (book.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            bookRepository.deleteById(id);
            return ResponseEntity.ok("Book deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to delete book: " + e.getMessage());
        }
    }

    // Increment download count for digital books
    @PostMapping("/{id}/download")
    public ResponseEntity<?> incrementDownloadCount(@PathVariable Long id) {
        Optional<Book> bookOptional = bookRepository.findById(id);
        if (bookOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Book book = bookOptional.get();
        if (book.getBookType() != Book.BookType.DIGITAL) {
            return ResponseEntity.badRequest().body("This endpoint is only for digital books");
        }

        book.setDownloadCount(book.getDownloadCount() + 1);
        bookRepository.save(book);

        return ResponseEntity.ok(book);
    }

    // Search books
    @GetMapping("/search")
    public ResponseEntity<List<Book>> searchBooks(@RequestParam String query) {
        List<Book> books = bookRepository.findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(query, query);
        return ResponseEntity.ok(books);
    }

    // --- Book Request Endpoints ---

    // Create book request (requires authentication)
    @PostMapping("/requests")
    public ResponseEntity<?> createBookRequest(
            @RequestBody BookRequest bookRequest,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            User requester = authUtils.getUserFromAuthHeader(authHeader);
            if (requester == null) {
                return ResponseEntity.status(401).body("Authentication required");
            }

            // Validate required fields
            if (bookRequest.getBookId() == null) {
                return ResponseEntity.badRequest().body("Book ID is required");
            }
            if (bookRequest.getMessage() != null && bookRequest.getMessage().length() > 2000) {
                return ResponseEntity.badRequest().body("Message must be under 2000 characters");
            }
            if (bookRequest.getRequestType() == null) {
                return ResponseEntity.badRequest().body("Request type is required");
            }

            Optional<Book> bookOptional = bookRepository.findById(bookRequest.getBookId());
            if (bookOptional.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            Book book = bookOptional.get();

            // Cannot request your own book
            if (book.getOwnerId() != null && book.getOwnerId().equals(requester.getId())) {
                return ResponseEntity.badRequest().body("Cannot request your own book");
            }

            // Set authenticated user info (don't trust client-supplied values)
            bookRequest.setRequesterId(requester.getId());
            bookRequest.setRequesterName(requester.getFirstName() + " " + requester.getLastName());
            bookRequest.setRequesterEmail(requester.getEmail());
            bookRequest.setRequestDate(LocalDateTime.now());
            bookRequest.setStatus(BookRequest.RequestStatus.PENDING);

            // Increment total requests for the book
            book.setTotalRequests(book.getTotalRequests() + 1);
            bookRepository.save(book);

            BookRequest savedRequest = bookRequestRepository.save(bookRequest);
            return ResponseEntity.ok(savedRequest);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to create book request: " + e.getMessage());
        }
    }

    // Get all requests for a book (only book owner can see)
    @GetMapping("/{bookId}/requests")
    public ResponseEntity<?> getRequestsForBook(
            @PathVariable Long bookId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        User user = authUtils.getUserFromAuthHeader(authHeader);
        if (user == null) {
            return ResponseEntity.status(401).body("Authentication required");
        }

        Optional<Book> bookOptional = bookRepository.findById(bookId);
        if (bookOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Only the book owner or admin can view requests for a book
        Book book = bookOptional.get();
        if (!user.getId().equals(book.getOwnerId()) && user.getRole() != com.smartuniversity.model.User.UserRole.ADMIN) {
            return ResponseEntity.status(403).body("Only the book owner can view requests");
        }

        List<BookRequest> requests = bookRequestRepository.findByBookId(bookId);
        return ResponseEntity.ok(requests);
    }

    // Get all requests by a user (only own requests)
    @GetMapping("/requests/user/{userId}")
    public ResponseEntity<?> getRequestsByUser(
            @PathVariable Long userId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        User user = authUtils.getUserFromAuthHeader(authHeader);
        if (user == null) {
            return ResponseEntity.status(401).body("Authentication required");
        }

        // Users can only view their own requests, admins can view any
        if (!user.getId().equals(userId) && user.getRole() != com.smartuniversity.model.User.UserRole.ADMIN) {
            return ResponseEntity.status(403).body("You can only view your own requests");
        }

        List<BookRequest> requests = bookRequestRepository.findByRequesterId(userId);
        return ResponseEntity.ok(requests);
    }

    // Get single request (only participants or admin)
    @GetMapping("/requests/{requestId}")
    public ResponseEntity<?> getRequestById(
            @PathVariable Long requestId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        User user = authUtils.getUserFromAuthHeader(authHeader);
        if (user == null) {
            return ResponseEntity.status(401).body("Authentication required");
        }

        Optional<BookRequest> requestOptional = bookRequestRepository.findById(requestId);
        if (requestOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        BookRequest request = requestOptional.get();

        // Only the requester, book owner, or admin can view the request
        boolean isRequester = user.getId().equals(request.getRequesterId());
        boolean isAdmin = user.getRole() == com.smartuniversity.model.User.UserRole.ADMIN;
        boolean isOwner = false;
        Optional<Book> bookOptional = bookRepository.findById(request.getBookId());
        if (bookOptional.isPresent()) {
            isOwner = user.getId().equals(bookOptional.get().getOwnerId());
        }

        if (!isRequester && !isOwner && !isAdmin) {
            return ResponseEntity.status(403).body("Access denied");
        }

        return ResponseEntity.ok(request);
    }

    // Update request status (only book owner or admin can approve/decline)
    @PutMapping("/requests/{requestId}/status")
    public ResponseEntity<?> updateRequestStatus(
            @PathVariable Long requestId,
            @RequestParam String status,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            User user = authUtils.getUserFromAuthHeader(authHeader);
            if (user == null) {
                return ResponseEntity.status(401).body("Authentication required");
            }

            Optional<BookRequest> requestOptional = bookRequestRepository.findById(requestId);
            if (requestOptional.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            BookRequest request = requestOptional.get();
            BookRequest.RequestStatus newStatus = BookRequest.RequestStatus.valueOf(status.toUpperCase());

            // Authorization: only book owner or admin can change status
            Optional<Book> bookOptional = bookRepository.findById(request.getBookId());
            if (bookOptional.isPresent()) {
                Book book = bookOptional.get();
                boolean isOwner = user.getId().equals(book.getOwnerId());
                boolean isAdmin = user.getRole() == com.smartuniversity.model.User.UserRole.ADMIN;

                if (!isOwner && !isAdmin) {
                    return ResponseEntity.status(403).body("Only the book owner can update request status");
                }
            }

            request.setStatus(newStatus);

            // If approved and it's a borrow request, mark book as unavailable
            if (newStatus == BookRequest.RequestStatus.APPROVED &&
                request.getRequestType() == BookRequest.RequestType.BORROW) {
                if (bookOptional.isPresent()) {
                    Book book = bookOptional.get();
                    book.setAvailableForLending(false);
                    book.setCurrentlyLentTo(request.getRequesterName());
                    if (request.getReturnDate() != null) {
                        book.setExpectedReturnDate(request.getReturnDate());
                    }
                    bookRepository.save(book);
                }
            }

            // If returned or completed, mark book as available again
            if ((newStatus == BookRequest.RequestStatus.RETURNED ||
                 newStatus == BookRequest.RequestStatus.COMPLETED) &&
                request.getRequestType() == BookRequest.RequestType.BORROW) {
                if (bookOptional.isPresent()) {
                    Book book = bookOptional.get();
                    book.setAvailableForLending(true);
                    book.setCurrentlyLentTo(null);
                    book.setExpectedReturnDate(null);
                    bookRepository.save(book);
                }
            }

            BookRequest updatedRequest = bookRequestRepository.save(request);
            return ResponseEntity.ok(updatedRequest);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid status: " + status);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to update request status: " + e.getMessage());
        }
    }

    // Delete request (only the requester or admin can delete)
    @DeleteMapping("/requests/{requestId}")
    public ResponseEntity<?> deleteRequest(
            @PathVariable Long requestId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            User user = authUtils.getUserFromAuthHeader(authHeader);
            if (user == null) {
                return ResponseEntity.status(401).body("Authentication required");
            }

            Optional<BookRequest> requestOptional = bookRequestRepository.findById(requestId);
            if (requestOptional.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            BookRequest request = requestOptional.get();

            // Only the requester or admin can delete a request
            boolean isRequester = user.getId().equals(request.getRequesterId());
            boolean isAdmin = user.getRole() == com.smartuniversity.model.User.UserRole.ADMIN;

            if (!isRequester && !isAdmin) {
                return ResponseEntity.status(403).body("You can only delete your own requests");
            }

            bookRequestRepository.deleteById(requestId);
            return ResponseEntity.ok("Request deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to delete request: " + e.getMessage());
        }
    }
}
