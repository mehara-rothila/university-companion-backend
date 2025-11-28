package com.smartuniversity.repository;

import com.smartuniversity.model.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    List<Book> findByBookType(Book.BookType bookType);

    List<Book> findByOwnerId(Long ownerId);

    List<Book> findByCategory(Book.BookCategory category);

    List<Book> findByBookTypeAndAvailableForLending(Book.BookType bookType, Boolean availableForLending);

    List<Book> findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(String title, String author);

    // File management queries
    List<Book> findByOwnerIdAndPhotoUrlIsNotNull(Long ownerId);

    List<Book> findByOwnerIdAndPdfUrlIsNotNull(Long ownerId);

    Long countByOwnerIdAndPhotoUrlIsNotNull(Long ownerId);

    Long countByOwnerIdAndPdfUrlIsNotNull(Long ownerId);

    // Admin status queries
    List<Book> findByStatus(Book.BookStatus status);

    List<Book> findByStatusOrderByUploadDateDesc(Book.BookStatus status);

    List<Book> findAllByOrderByUploadDateDesc();

    Long countByStatus(Book.BookStatus status);

    // Query to get approved books OR books with null status (backward compatibility)
    @Query("SELECT b FROM Book b WHERE b.status = 'APPROVED' OR b.status IS NULL ORDER BY b.uploadDate DESC")
    List<Book> findApprovedOrNullStatusBooks();

    // Count books including null as approved
    @Query("SELECT COUNT(b) FROM Book b WHERE b.status = 'APPROVED' OR b.status IS NULL")
    Long countApprovedOrNullStatus();

    // Count pending (only explicit PENDING, not null)
    @Query("SELECT COUNT(b) FROM Book b WHERE b.status = 'PENDING'")
    Long countPending();

    // Count rejected
    @Query("SELECT COUNT(b) FROM Book b WHERE b.status = 'REJECTED'")
    Long countRejected();
}
