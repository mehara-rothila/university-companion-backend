package com.smartcampus.controller;

import com.smartcampus.model.Book;
import com.smartcampus.model.BookRequest;
import com.smartcampus.repository.BookRepository;
import com.smartcampus.repository.BookRequestRepository;
import com.smartcampus.repository.UserRatingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/books")
public class BookController {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private BookRequestRepository bookRequestRepository;

    @Autowired
    private UserRatingRepository userRatingRepository;

    // Get all books
    @GetMapping
    public ResponseEntity<List<Book>> getAllBooks() {
        List<Book> books = bookRepository.findAll();
        // Enrich each book with owner's rating
        books.forEach(book -> {
            Double rating = userRatingRepository.getAverageRatingForUser(book.getOwnerId());
            if (rating != null) {
                book.setOwnerRating(Math.round(rating * 10.0) / 10.0);
            }
        });
        return ResponseEntity.ok(books);
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

    // Create new book
    @PostMapping
    public ResponseEntity<?> createBook(@RequestBody Book book) {
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

            Book savedBook = bookRepository.save(book);
            return ResponseEntity.ok(savedBook);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to create book: " + e.getMessage());
        }
    }

    // Update book
    @PutMapping("/{id}")
    public ResponseEntity<?> updateBook(@PathVariable Long id, @RequestBody Book bookDetails) {
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

        Book updatedBook = bookRepository.save(book);
        return ResponseEntity.ok(updatedBook);
    }

    // Delete book
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteBook(@PathVariable Long id) {
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

    // Create book request
    @PostMapping("/requests")
    public ResponseEntity<?> createBookRequest(@RequestBody BookRequest bookRequest) {
        try {
            bookRequest.setRequestDate(LocalDateTime.now());
            bookRequest.setStatus(BookRequest.RequestStatus.PENDING);

            // Increment total requests for the book
            Optional<Book> bookOptional = bookRepository.findById(bookRequest.getBookId());
            if (bookOptional.isPresent()) {
                Book book = bookOptional.get();
                book.setTotalRequests(book.getTotalRequests() + 1);
                bookRepository.save(book);
            }

            BookRequest savedRequest = bookRequestRepository.save(bookRequest);
            return ResponseEntity.ok(savedRequest);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to create book request: " + e.getMessage());
        }
    }

    // Get all requests for a book
    @GetMapping("/{bookId}/requests")
    public ResponseEntity<List<BookRequest>> getRequestsForBook(@PathVariable Long bookId) {
        List<BookRequest> requests = bookRequestRepository.findByBookId(bookId);
        return ResponseEntity.ok(requests);
    }

    // Get all requests by a user
    @GetMapping("/requests/user/{userId}")
    public ResponseEntity<List<BookRequest>> getRequestsByUser(@PathVariable Long userId) {
        List<BookRequest> requests = bookRequestRepository.findByRequesterId(userId);
        return ResponseEntity.ok(requests);
    }

    // Get single request
    @GetMapping("/requests/{requestId}")
    public ResponseEntity<BookRequest> getRequestById(@PathVariable Long requestId) {
        Optional<BookRequest> request = bookRequestRepository.findById(requestId);
        return request.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Update request status
    @PutMapping("/requests/{requestId}/status")
    public ResponseEntity<?> updateRequestStatus(
            @PathVariable Long requestId,
            @RequestParam String status) {
        try {
            Optional<BookRequest> requestOptional = bookRequestRepository.findById(requestId);
            if (requestOptional.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            BookRequest request = requestOptional.get();
            BookRequest.RequestStatus newStatus = BookRequest.RequestStatus.valueOf(status.toUpperCase());
            request.setStatus(newStatus);

            // If approved and it's a borrow request, mark book as unavailable
            if (newStatus == BookRequest.RequestStatus.APPROVED &&
                request.getRequestType() == BookRequest.RequestType.BORROW) {
                Optional<Book> bookOptional = bookRepository.findById(request.getBookId());
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
                Optional<Book> bookOptional = bookRepository.findById(request.getBookId());
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

    // Delete request
    @DeleteMapping("/requests/{requestId}")
    public ResponseEntity<?> deleteRequest(@PathVariable Long requestId) {
        try {
            if (!bookRequestRepository.existsById(requestId)) {
                return ResponseEntity.notFound().build();
            }
            bookRequestRepository.deleteById(requestId);
            return ResponseEntity.ok("Request deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to delete request: " + e.getMessage());
        }
    }
}
