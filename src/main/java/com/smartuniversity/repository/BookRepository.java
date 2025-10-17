package com.smartuniversity.repository;

import com.smartuniversity.model.Book;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookRepository extends JpaRepository<Book, Long> {

    List<Book> findByBookType(Book.BookType bookType);

    List<Book> findByOwnerId(Long ownerId);

    List<Book> findByCategory(Book.BookCategory category);

    List<Book> findByBookTypeAndAvailableForLending(Book.BookType bookType, Boolean availableForLending);

    List<Book> findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(String title, String author);
}
