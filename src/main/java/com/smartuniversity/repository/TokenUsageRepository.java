package com.smartuniversity.repository;

import com.smartuniversity.model.TokenUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TokenUsageRepository extends JpaRepository<TokenUsage, Long> {

    Optional<TokenUsage> findByUserIdAndUsageDate(Long userId, LocalDate usageDate);

    List<TokenUsage> findByUserId(Long userId);

    List<TokenUsage> findByUserIdOrderByUsageDateDesc(Long userId);

    Optional<TokenUsage> findFirstByUserIdOrderByUsageDateDesc(Long userId);
}
