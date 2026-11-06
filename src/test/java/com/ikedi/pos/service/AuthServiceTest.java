package com.ikedi.pos.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ikedi.pos.exception.AuthenticationException;
import com.ikedi.pos.exception.UnauthorizedActionException;
import com.ikedi.pos.exception.ValidationException;
import com.ikedi.pos.model.Role;
import com.ikedi.pos.model.User;
import com.ikedi.pos.repository.memory.InMemoryUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthServiceTest {

    private InMemoryUserRepository repo;
    private AuthService auth;
    private User manager;

    @BeforeEach
    void setUp() {
        repo = new InMemoryUserRepository();
        auth = new AuthService(repo);
        auth.seedDefaultManager();
        manager = auth.login(AuthService.DEFAULT_MANAGER, AuthService.DEFAULT_MANAGER_PASSWORD);
    }

    private void failLogin(String username, String password) {
        assertThrows(AuthenticationException.class, () -> auth.login(username, password));
    }

    @Test
    void seededManagerMustChangePasswordAndSeedingTwiceDoesNothing() {
        assertEquals(Role.MANAGER, manager.getRole());
        assertTrue(manager.isMustChangePassword());
        assertFalse(auth.seedDefaultManager());
        assertEquals(1, repo.findAll().size());
    }

    @Test
    void loginIgnoresUsernameCase() {
        User again = auth.login("  MANAGER ", AuthService.DEFAULT_MANAGER_PASSWORD);
        assertEquals("manager", again.getUsername());
    }

    @Test
    void wrongPasswordAndUnknownUserGiveTheSameMessage() {
        AuthenticationException wrong = assertThrows(AuthenticationException.class,
                () -> auth.login("manager", "nope"));
        AuthenticationException unknown = assertThrows(AuthenticationException.class,
                () -> auth.login("ghost", "nope"));
        assertEquals(wrong.getMessage(), unknown.getMessage());
    }

    @Test
    void thirdConsecutiveFailureLocksAndCorrectPasswordIsThenRejected() {
        auth.createUser(manager, "ada", "password1", Role.CASHIER);
        failLogin("ada", "x");
        failLogin("ada", "x");
        assertFalse(repo.findByUsername("ada").orElseThrow().isLocked());
        failLogin("ada", "x");
        assertTrue(repo.findByUsername("ada").orElseThrow().isLocked());
        AuthenticationException ex = assertThrows(AuthenticationException.class,
                () -> auth.login("ada", "password1"));
        assertTrue(ex.getMessage().contains("locked"));
    }

    @Test
    void successfulLoginResetsTheFailureCount() {
        auth.createUser(manager, "ada", "password1", Role.CASHIER);
        failLogin("ada", "x");
        failLogin("ada", "x");
        auth.login("ada", "password1");
        failLogin("ada", "x");
        failLogin("ada", "x");
        assertFalse(repo.findByUsername("ada").orElseThrow().isLocked());
    }

    @Test
    void managerCanUnlockAndUserCanThenLogIn() {
        auth.createUser(manager, "ada", "password1", Role.CASHIER);
        failLogin("ada", "x");
        failLogin("ada", "x");
        failLogin("ada", "x");
        auth.unlock(manager, "ada");
        assertEquals("ada", auth.login("ada", "password1").getUsername());
    }

    @Test
    void cashierCannotUnlockOrCreateUsers() {
        User cashier = auth.createUser(manager, "ada", "password1", Role.CASHIER);
        assertThrows(UnauthorizedActionException.class, () -> auth.unlock(cashier, "manager"));
        assertThrows(UnauthorizedActionException.class,
                () -> auth.createUser(cashier, "bob", "password1", Role.CASHIER));
    }

    @Test
    void samePasswordGivesDifferentSaltsAndHashes() {
        User a = auth.createUser(manager, "ada", "password1", Role.CASHIER);
        User b = auth.createUser(manager, "bob", "password1", Role.CASHIER);
        assertNotEquals(a.getSalt(), b.getSalt());
        assertNotEquals(a.getPasswordHash(), b.getPasswordHash());
        assertNotEquals("password1", a.getPasswordHash());
    }

    @Test
    void createUserRejectsDuplicatesAndShortPasswords() {
        auth.createUser(manager, "ada", "password1", Role.CASHIER);
        assertThrows(ValidationException.class, () -> auth.createUser(manager, "ADA", "password1", Role.CASHIER));
        assertThrows(ValidationException.class, () -> auth.createUser(manager, "bob", "short", Role.CASHIER));
        assertThrows(ValidationException.class, () -> auth.createUser(manager, " ", "password1", Role.CASHIER));
    }

    @Test
    void changePasswordClearsTheForcedChangeAndReplacesTheOldPassword() {
        auth.changePassword("manager", AuthService.DEFAULT_MANAGER_PASSWORD, "NewSecret99");
        User after = auth.login("manager", "NewSecret99");
        assertFalse(after.isMustChangePassword());
        failLogin("manager", AuthService.DEFAULT_MANAGER_PASSWORD);
    }

    @Test
    void changePasswordChecksTheOldPasswordAndTheNewRules() {
        assertThrows(AuthenticationException.class, () -> auth.changePassword("manager", "wrong", "NewSecret99"));
        assertThrows(ValidationException.class,
                () -> auth.changePassword("manager", AuthService.DEFAULT_MANAGER_PASSWORD, "short"));
        assertThrows(ValidationException.class, () -> auth.changePassword("manager",
                AuthService.DEFAULT_MANAGER_PASSWORD, AuthService.DEFAULT_MANAGER_PASSWORD));
    }

    @Test
    void resetDefaultManagerRecoversALockedSoleManager() {
        failLogin("manager", "x");
        failLogin("manager", "x");
        failLogin("manager", "x");
        assertTrue(repo.findByUsername("manager").orElseThrow().isLocked());
        auth.resetDefaultManager();
        User back = auth.login("manager", AuthService.DEFAULT_MANAGER_PASSWORD);
        assertTrue(back.isMustChangePassword());
    }
}

