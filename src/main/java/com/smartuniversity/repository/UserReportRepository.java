package com.smartuniversity.repository;

import com.smartuniversity.model.UserReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserReportRepository extends JpaRepository<UserReport, Long> {

    @Query("SELECT ur FROM UserReport ur WHERE ur.reporter.id = :userId ORDER BY ur.createdAt DESC")
    List<UserReport> findAllByReporter(@Param("userId") Long userId);

    @Query("SELECT ur FROM UserReport ur WHERE ur.reportedUser.id = :userId ORDER BY ur.createdAt DESC")
    List<UserReport> findAllByReportedUser(@Param("userId") Long userId);

    @Query("SELECT ur FROM UserReport ur WHERE ur.status = :status ORDER BY ur.createdAt DESC")
    List<UserReport> findAllByStatus(@Param("status") UserReport.ReportStatus status);

    @Query("SELECT ur FROM UserReport ur ORDER BY ur.createdAt DESC")
    List<UserReport> findAllOrderByCreatedAtDesc();

    @Query("SELECT COUNT(ur) FROM UserReport ur WHERE ur.status = 'PENDING'")
    Long countPendingReports();

    @Query("SELECT CASE WHEN COUNT(ur) > 0 THEN true ELSE false END FROM UserReport ur " +
           "WHERE ur.reporter.id = :reporterId AND ur.reportedUser.id = :reportedId AND ur.status = 'PENDING'")
    boolean existsPendingReport(@Param("reporterId") Long reporterId, @Param("reportedId") Long reportedId);
}
