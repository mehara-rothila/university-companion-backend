package com.smartuniversity.repository;

import com.smartuniversity.model.FinancialAidDisbursement;
import com.smartuniversity.model.FinancialAidDisbursement.DisbursementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FinancialAidDisbursementRepository extends JpaRepository<FinancialAidDisbursement, Long> {

    // Find all disbursements for a financial aid application
    List<FinancialAidDisbursement> findByFinancialAidId(Long financialAidId);

    // Find disbursements by status
    List<FinancialAidDisbursement> findByStatus(DisbursementStatus status);

    // Find disbursements created by a specific admin
    List<FinancialAidDisbursement> findByDisbursedBy(Long disbursedBy);

    // Find disbursements scheduled between dates
    List<FinancialAidDisbursement> findByScheduledDateBetween(LocalDateTime start, LocalDateTime end);

    // Count disbursements for a financial aid
    long countByFinancialAidId(Long financialAidId);

    // Calculate total disbursed amount for a financial aid
    @Query("SELECT COALESCE(SUM(d.amount), 0) FROM FinancialAidDisbursement d WHERE d.financialAid.id = :financialAidId AND d.status = 'COMPLETED'")
    BigDecimal getTotalDisbursedAmount(@Param("financialAidId") Long financialAidId);

    // Get all pending disbursements
    List<FinancialAidDisbursement> findByStatusOrderByScheduledDateAsc(DisbursementStatus status);

    // Check if financial aid has completed disbursements
    boolean existsByFinancialAidIdAndStatus(Long financialAidId, DisbursementStatus status);
}
