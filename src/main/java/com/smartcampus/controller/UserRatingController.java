package com.smartcampus.controller;

import com.smartcampus.model.UserRating;
import com.smartcampus.repository.UserRatingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/api/ratings")
public class UserRatingController {

    @Autowired
    private UserRatingRepository userRatingRepository;

    // Create a new rating
    @PostMapping
    public ResponseEntity<?> createRating(@RequestBody UserRating rating) {
        try {
            // Check if user has already rated this transaction
            Optional<UserRating> existingRating = userRatingRepository
                .findByRatedUserIdAndRaterUserIdAndTransactionId(
                    rating.getRatedUserId(),
                    rating.getRaterUserId(),
                    rating.getTransactionId()
                );

            if (existingRating.isPresent()) {
                return ResponseEntity.badRequest().body("You have already rated this transaction");
            }

            // Validate rating is between 1-5
            if (rating.getRating() < 1 || rating.getRating() > 5) {
                return ResponseEntity.badRequest().body("Rating must be between 1 and 5");
            }

            UserRating savedRating = userRatingRepository.save(rating);
            return ResponseEntity.ok(savedRating);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to create rating: " + e.getMessage());
        }
    }

    // Get all ratings for a user
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<UserRating>> getRatingsForUser(@PathVariable Long userId) {
        List<UserRating> ratings = userRatingRepository.findByRatedUserId(userId);
        return ResponseEntity.ok(ratings);
    }

    // Get ratings given by a user
    @GetMapping("/by-user/{userId}")
    public ResponseEntity<List<UserRating>> getRatingsByUser(@PathVariable Long userId) {
        List<UserRating> ratings = userRatingRepository.findByRaterUserId(userId);
        return ResponseEntity.ok(ratings);
    }

    // Get average rating and count for a user
    @GetMapping("/user/{userId}/average")
    public ResponseEntity<Map<String, Object>> getUserAverageRating(@PathVariable Long userId) {
        Double averageRating = userRatingRepository.getAverageRatingForUser(userId);
        Long totalRatings = userRatingRepository.countByRatedUserId(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("averageRating", averageRating != null ? Math.round(averageRating * 10.0) / 10.0 : 0.0);
        response.put("totalRatings", totalRatings);

        return ResponseEntity.ok(response);
    }

    // Check if user has rated a specific transaction
    @GetMapping("/check")
    public ResponseEntity<Map<String, Boolean>> checkIfRated(
        @RequestParam Long ratedUserId,
        @RequestParam Long raterUserId,
        @RequestParam Long transactionId
    ) {
        Optional<UserRating> rating = userRatingRepository
            .findByRatedUserIdAndRaterUserIdAndTransactionId(ratedUserId, raterUserId, transactionId);

        Map<String, Boolean> response = new HashMap<>();
        response.put("hasRated", rating.isPresent());

        return ResponseEntity.ok(response);
    }

    // Get a specific rating
    @GetMapping("/{id}")
    public ResponseEntity<UserRating> getRatingById(@PathVariable Long id) {
        Optional<UserRating> rating = userRatingRepository.findById(id);
        return rating.map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // Update a rating (only comment can be updated)
    @PutMapping("/{id}")
    public ResponseEntity<?> updateRating(@PathVariable Long id, @RequestBody UserRating ratingDetails) {
        Optional<UserRating> ratingOptional = userRatingRepository.findById(id);
        if (ratingOptional.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        UserRating rating = ratingOptional.get();

        // Update rating and comment
        if (ratingDetails.getRating() != null) {
            if (ratingDetails.getRating() < 1 || ratingDetails.getRating() > 5) {
                return ResponseEntity.badRequest().body("Rating must be between 1 and 5");
            }
            rating.setRating(ratingDetails.getRating());
        }
        if (ratingDetails.getComment() != null) {
            rating.setComment(ratingDetails.getComment());
        }

        UserRating updatedRating = userRatingRepository.save(rating);
        return ResponseEntity.ok(updatedRating);
    }

    // Delete a rating
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRating(@PathVariable Long id) {
        try {
            if (!userRatingRepository.existsById(id)) {
                return ResponseEntity.notFound().build();
            }
            userRatingRepository.deleteById(id);
            return ResponseEntity.ok("Rating deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to delete rating: " + e.getMessage());
        }
    }
}
