package com.smartuniversity.util;

import com.smartuniversity.model.User;
import com.smartuniversity.repository.UserRepository;
import com.smartuniversity.security.JwtUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthUtilsTest {

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AuthUtils authUtils;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private User user(long id, String username, User.UserRole role) {
        User user = new User("Test", "User", username + "@uni.edu", username, "password");
        user.setId(id);
        user.setRole(role);
        return user;
    }

    // ---- getUserFromAuthHeader ----

    @Test
    void getUserFromAuthHeader_nullHeader_returnsNull() {
        assertNull(authUtils.getUserFromAuthHeader(null));
        verifyNoInteractions(jwtUtils, userRepository);
    }

    @Test
    void getUserFromAuthHeader_headerWithoutBearerPrefix_returnsNull() {
        assertNull(authUtils.getUserFromAuthHeader("Basic dXNlcjpwYXNz"));
        verifyNoInteractions(jwtUtils, userRepository);
    }

    @Test
    void getUserFromAuthHeader_validToken_returnsUser() {
        User alice = user(1L, "alice", User.UserRole.STUDENT);
        when(jwtUtils.getUserNameFromJwtToken("valid-jwt")).thenReturn("alice");
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        User result = authUtils.getUserFromAuthHeader("Bearer valid-jwt");

        assertNotNull(result);
        assertEquals("alice", result.getUsername());
        assertEquals(1L, result.getId());
    }

    @Test
    void getUserFromAuthHeader_unknownUser_returnsNull() {
        when(jwtUtils.getUserNameFromJwtToken("valid-jwt")).thenReturn("ghost");
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertNull(authUtils.getUserFromAuthHeader("Bearer valid-jwt"));
    }

    @Test
    void getUserFromAuthHeader_tokenParsingFails_returnsNull() {
        when(jwtUtils.getUserNameFromJwtToken("bad-jwt"))
                .thenThrow(new RuntimeException("invalid token"));

        assertNull(authUtils.getUserFromAuthHeader("Bearer bad-jwt"));
        verifyNoInteractions(userRepository);
    }

    // ---- getAdminFromAuthHeader / isAdmin ----

    @Test
    void getAdminFromAuthHeader_adminUser_returnsUser() {
        User admin = user(2L, "admin", User.UserRole.ADMIN);
        when(jwtUtils.getUserNameFromJwtToken("admin-jwt")).thenReturn("admin");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));

        User result = authUtils.getAdminFromAuthHeader("Bearer admin-jwt");

        assertNotNull(result);
        assertEquals(User.UserRole.ADMIN, result.getRole());
    }

    @Test
    void getAdminFromAuthHeader_studentUser_returnsNull() {
        User student = user(3L, "student", User.UserRole.STUDENT);
        when(jwtUtils.getUserNameFromJwtToken("student-jwt")).thenReturn("student");
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(student));

        assertNull(authUtils.getAdminFromAuthHeader("Bearer student-jwt"));
    }

    @Test
    void getAdminFromAuthHeader_nullHeader_returnsNull() {
        assertNull(authUtils.getAdminFromAuthHeader(null));
        verifyNoInteractions(jwtUtils, userRepository);
    }

    @Test
    void isAdmin_trueForAdmin_falseForStudent() {
        User admin = user(2L, "admin", User.UserRole.ADMIN);
        User student = user(3L, "student", User.UserRole.STUDENT);
        when(jwtUtils.getUserNameFromJwtToken("admin-jwt")).thenReturn("admin");
        when(jwtUtils.getUserNameFromJwtToken("student-jwt")).thenReturn("student");
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(userRepository.findByUsername("student")).thenReturn(Optional.of(student));

        assertTrue(authUtils.isAdmin("Bearer admin-jwt"));
        assertFalse(authUtils.isAdmin("Bearer student-jwt"));
    }

    // ---- getCurrentUserId ----

    @Test
    void getCurrentUserId_authenticatedUser_returnsId() {
        User alice = user(42L, "alice", User.UserRole.STUDENT);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(alice, null, alice.getAuthorities()));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        assertEquals(42L, authUtils.getCurrentUserId());
    }

    @Test
    void getCurrentUserId_stringPrincipal_returnsNull() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null, List.of()));

        assertNull(authUtils.getCurrentUserId());
        verifyNoInteractions(userRepository);
    }

    @Test
    void getCurrentUserId_noAuthentication_returnsNull() {
        SecurityContextHolder.clearContext();

        assertNull(authUtils.getCurrentUserId());
        verifyNoInteractions(userRepository);
    }

    @Test
    void getCurrentUserId_userMissingFromDatabase_returnsNull() {
        User alice = user(42L, "alice", User.UserRole.STUDENT);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(alice, null, alice.getAuthorities()));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.empty());

        assertNull(authUtils.getCurrentUserId());
    }
}
