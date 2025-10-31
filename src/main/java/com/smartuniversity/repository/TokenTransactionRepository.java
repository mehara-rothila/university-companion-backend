package com.smartuniversity.repository;

import com.smartuniversity.model.TokenTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TokenTransactionRepository extends JpaRepository<TokenTransaction, Long> {

    List<TokenTransaction> findByUserId(Long userId);

    List<TokenTransaction> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<TokenTransaction> findByUserIdAndType(Long userId, TokenTransaction.TransactionType type);

    List<TokenTransaction> findByUserIdAndCreatedAtBetween(Long userId, LocalDateTime startTime, LocalDateTime endTime);

    List<TokenTransaction> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(Long userId, LocalDateTime startTime, LocalDateTime endTime);
}
