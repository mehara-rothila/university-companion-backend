package com.smartcampus.repository;

import com.smartcampus.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Boolean existsByUsername(String username);
    Boolean existsByEmail(String email);
    Optional<User> findByStudentId(String studentId);
    
    // Admin functionality methods
    long countByRole(User.UserRole role);
    Page<User> findByRole(User.UserRole role, Pageable pageable);
    Page<User> findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
        String username, String email, Pageable pageable);
    Page<User> findByRoleAndUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
        User.UserRole role, String username, String email, Pageable pageable);
}