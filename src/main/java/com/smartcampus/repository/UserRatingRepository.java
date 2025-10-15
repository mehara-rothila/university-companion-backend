package com.smartcampus.repository;

import com.smartcampus.model.UserRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRatingRepository extends JpaRepository<UserRating, Long> {

    // Find all ratings for a specific user
    List<UserRating> findByRatedUserId(Long ratedUserId);

    // Find all ratings given by a specific user
    List<UserRating> findByRaterUserId(Long raterUserId);

    // Check if a user has already rated another user for a specific transaction
    Optional<UserRating> findByRatedUserIdAndRaterUserIdAndTransactionId(
        Long ratedUserId, Long raterUserId, Long transactionId
    );

    // Calculate average rating for a user
    @Query("SELECT AVG(r.rating) FROM UserRating r WHERE r.ratedUserId = :userId")
    Double getAverageRatingForUser(@Param("userId") Long userId);

    // Count total ratings received by a user
    Long countByRatedUserId(Long ratedUserId);
}
