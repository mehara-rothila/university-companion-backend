package com.smartcampus.repository;

import com.smartcampus.model.FinancialAid;
import com.smartcampus.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface FinancialAidRepository extends JpaRepository<FinancialAid, Long> {
    
    List<FinancialAid> findByApplicant(User applicant);
    
    List<FinancialAid> findByStatus(FinancialAid.ApplicationStatus status);
    
    List<FinancialAid> findByAidType(FinancialAid.AidType aidType);
    
    List<FinancialAid> findByCategory(String category);
    
    List<FinancialAid> findByUrgency(FinancialAid.Urgency urgency);
    
    List<FinancialAid> findByIsDonationEligible(boolean isDonationEligible);
    
    @Query("SELECT f FROM FinancialAid f WHERE f.status = :status AND f.isDonationEligible = true ORDER BY f.urgency DESC, f.createdAt ASC")
    List<FinancialAid> findActiveDonationEligibleApplications(@Param("status") FinancialAid.ApplicationStatus status);
    
    @Query("SELECT COUNT(f) FROM FinancialAid f WHERE f.status = :status")
    Long countByStatus(@Param("status") FinancialAid.ApplicationStatus status);
    
    @Query("SELECT SUM(f.approvedAmount) FROM FinancialAid f WHERE f.status = 'APPROVED'")
    BigDecimal getTotalApprovedAmount();
    
    @Query("SELECT SUM(f.raisedAmount) FROM FinancialAid f WHERE f.isDonationEligible = true")
    BigDecimal getTotalRaisedAmount();
    
    @Query("SELECT DISTINCT f.category FROM FinancialAid f WHERE f.status != 'DRAFT'")
    List<String> findDistinctCategories();
    
    @Query("SELECT f FROM FinancialAid f WHERE " +
           "(:status IS NULL OR f.status = :status) AND " +
           "(:aidType IS NULL OR f.aidType = :aidType) AND " +
           "(:category IS NULL OR f.category = :category) AND " +
           "(:urgency IS NULL OR f.urgency = :urgency) " +
           "ORDER BY f.createdAt DESC")
    List<FinancialAid> findByFilters(
        @Param("status") FinancialAid.ApplicationStatus status,
        @Param("aidType") FinancialAid.AidType aidType,
        @Param("category") String category,
        @Param("urgency") FinancialAid.Urgency urgency
    );
}