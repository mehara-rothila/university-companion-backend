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
}