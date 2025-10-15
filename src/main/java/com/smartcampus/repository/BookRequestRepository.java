package com.smartcampus.repository;

import com.smartcampus.model.BookRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookRequestRepository extends JpaRepository<BookRequest, Long> {

    List<BookRequest> findByBookId(Long bookId);

    List<BookRequest> findByRequesterId(Long requesterId);

    List<BookRequest> findByStatus(BookRequest.RequestStatus status);

    List<BookRequest> findByBookIdAndStatus(Long bookId, BookRequest.RequestStatus status);
}
