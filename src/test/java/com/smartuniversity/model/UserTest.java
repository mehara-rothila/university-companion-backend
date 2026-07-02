package com.smartuniversity.model;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void newUser_defaultsToStudentRoleAndLocalProvider() {
        User user = new User("Alice", "Perera", "alice@uni.edu", "alice", "password");

        assertEquals(User.UserRole.STUDENT, user.getRole());
        assertEquals("local", user.getProvider());
        assertTrue(user.isEnabled());
        assertFalse(user.isEmailVerified());
    }

    @Test
    void getAuthorities_reflectsAssignedRole() {
        User user = new User("Alice", "Perera", "alice@uni.edu", "alice", "password");

        assertEquals("ROLE_STUDENT", singleAuthority(user));

        user.setRole(User.UserRole.ADMIN);
        assertEquals("ROLE_ADMIN", singleAuthority(user));

        user.setRole(User.UserRole.FACULTY);
        assertEquals("ROLE_FACULTY", singleAuthority(user));
    }

    @Test
    void accountStatusFlags_areAlwaysActive() {
        User user = new User();

        assertTrue(user.isAccountNonExpired());
        assertTrue(user.isAccountNonLocked());
        assertTrue(user.isCredentialsNonExpired());
    }

    @Test
    void isEnabled_reflectsEnabledFlag() {
        User user = new User();

        user.setEnabled(false);
        assertFalse(user.isEnabled());

        user.setEnabled(true);
        assertTrue(user.isEnabled());
    }

    private String singleAuthority(User user) {
        return user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .findFirst()
                .orElseThrow();
    }
}
