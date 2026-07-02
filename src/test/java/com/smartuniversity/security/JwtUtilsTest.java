package com.smartuniversity.security;

import com.smartuniversity.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtUtilsTest {

    private static final String SECRET = "test-secret-key-that-is-long-enough-for-hs256-signing!!";

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = buildJwtUtils(SECRET, 3600L);
    }

    private JwtUtils buildJwtUtils(String secret, long expirationSeconds) {
        JwtUtils utils = new JwtUtils();
        ReflectionTestUtils.setField(utils, "jwtSecret", secret);
        ReflectionTestUtils.setField(utils, "jwtExpirationMs", expirationSeconds);
        return utils;
    }

    @Test
    void generateJwtToken_fromUsername_roundTripsSubject() {
        String token = jwtUtils.generateJwtToken("alice");

        assertNotNull(token);
        assertEquals("alice", jwtUtils.getUserNameFromJwtToken(token));
    }

    @Test
    void generateJwtToken_fromAuthentication_usesPrincipalUsername() {
        User user = new User("Bob", "Silva", "bob@uni.edu", "bob", "password");
        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(user);

        String token = jwtUtils.generateJwtToken(authentication);

        assertEquals("bob", jwtUtils.getUserNameFromJwtToken(token));
    }

    @Test
    void validateJwtToken_acceptsFreshToken() {
        String token = jwtUtils.generateJwtToken("alice");

        assertTrue(jwtUtils.validateJwtToken(token));
    }

    @Test
    void validateJwtToken_rejectsExpiredToken() {
        JwtUtils expiredIssuer = buildJwtUtils(SECRET, -60L);
        String expiredToken = expiredIssuer.generateJwtToken("alice");

        assertFalse(jwtUtils.validateJwtToken(expiredToken));
    }

    @Test
    void validateJwtToken_rejectsMalformedToken() {
        assertFalse(jwtUtils.validateJwtToken("not-a-valid-jwt"));
    }

    @Test
    void validateJwtToken_rejectsEmptyToken() {
        assertFalse(jwtUtils.validateJwtToken(""));
    }

    @Test
    void validateJwtToken_rejectsTokenSignedWithDifferentKey() {
        JwtUtils otherIssuer = buildJwtUtils(
                "a-completely-different-secret-key-also-long-enough!!", 3600L);
        String foreignToken = otherIssuer.generateJwtToken("alice");

        assertFalse(jwtUtils.validateJwtToken(foreignToken));
    }
}
