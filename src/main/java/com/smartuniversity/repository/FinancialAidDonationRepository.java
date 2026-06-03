package com.smartuniversity.repository;

import com.smartuniversity.model.FinancialAidDonation;
import com.smartuniversity.model.FinancialAid;
import com.smartuniversity.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface FinancialAidDonationRepository extends JpaRepository<FinancialAidDonation, Long> {
    
    List<FinancialAidDonation> findByFinancialAid(FinancialAid financialAid);
    
    List<FinancialAidDonation> findByDonor(User donor);
    
    List<FinancialAidDonation> findByStatus(FinancialAidDonation.DonationStatus status);
    
    @Query("SELECT SUM(d.amount) FROM FinancialAidDonation d WHERE d.financialAid = :financialAid AND d.status = 'COMPLETED'")
    BigDecimal getTotalDonationsForFinancialAid(@Param("financialAid") FinancialAid financialAid);
    
    @Query("SELECT COUNT(d) FROM FinancialAidDonation d WHERE d.financialAid = :financialAid AND d.status = 'COMPLETED'")
    Integer getDonorCountForFinancialAid(@Param("financialAid") FinancialAid financialAid);
    
    @Query("SELECT d FROM FinancialAidDonation d WHERE d.financialAid = :financialAid AND d.status = 'COMPLETED' ORDER BY d.createdAt DESC")
    List<FinancialAidDonation> findCompletedDonationsForFinancialAid(@Param("financialAid") FinancialAid financialAid);

    List<FinancialAidDonation> findByTransactionIdContaining(String transactionId);

    FinancialAidDonation findByTransactionId(String transactionId);

    @Query("SELECT d FROM FinancialAidDonation d LEFT JOIN FETCH d.financialAid WHERE d.donor.id = :donorId ORDER BY d.createdAt DESC")
    List<FinancialAidDonation> findByDonorIdWithAid(@Param("donorId") Long donorId);

    // ---- Analytics queries ----

    @Query("SELECT SUM(d.amount) FROM FinancialAidDonation d WHERE d.status = 'COMPLETED'")
    BigDecimal getTotalCompletedDonationAmount();

    @Query("SELECT COUNT(d) FROM FinancialAidDonation d WHERE d.status = 'COMPLETED'")
    Long getCompletedDonationCount();

    @Query("SELECT COUNT(DISTINCT d.donor.id) FROM FinancialAidDonation d WHERE d.status = 'COMPLETED'")
    Long getUniqueDonorCount();

    @Query("SELECT AVG(d.amount) FROM FinancialAidDonation d WHERE d.status = 'COMPLETED'")
    BigDecimal getAverageDonationAmount();

    @Query("SELECT d.donor.id, d.donor.firstName, d.donor.lastName, d.donor.email, " +
           "SUM(d.amount), COUNT(d) " +
           "FROM FinancialAidDonation d WHERE d.status = 'COMPLETED' AND d.isAnonymous = false AND d.donor IS NOT NULL " +
           "GROUP BY d.donor.id, d.donor.firstName, d.donor.lastName, d.donor.email " +
           "ORDER BY SUM(d.amount) DESC")
    List<Object[]> findTopDonors();

    @Query("SELECT d FROM FinancialAidDonation d LEFT JOIN FETCH d.donor LEFT JOIN FETCH d.financialAid " +
           "WHERE d.status = 'COMPLETED' ORDER BY d.createdAt DESC")
    List<FinancialAidDonation> findRecentCompletedDonations();

    @Query("SELECT d.financialAid.category, SUM(d.amount), COUNT(d) " +
           "FROM FinancialAidDonation d WHERE d.status = 'COMPLETED' " +
           "GROUP BY d.financialAid.category ORDER BY SUM(d.amount) DESC")
    List<Object[]> getDonationsByCategory();

    @Query("SELECT d.financialAid.aidType, SUM(d.amount), COUNT(d) " +
           "FROM FinancialAidDonation d WHERE d.status = 'COMPLETED' " +
           "GROUP BY d.financialAid.aidType ORDER BY SUM(d.amount) DESC")
    List<Object[]> getDonationsByAidType();
}