package com.ikedi.pos.service;

import com.ikedi.pos.exception.AuthenticationException;
import com.ikedi.pos.exception.UnauthorizedActionException;
import com.ikedi.pos.exception.ValidationException;
import com.ikedi.pos.model.Role;
import com.ikedi.pos.model.User;
import com.ikedi.pos.repository.UserRepository;
import com.ikedi.pos.util.PasswordHasher;
import java.util.List;

/**
 * Login, lockout and password rules. Methods are synchronized so two threads cannot
 * both read the same failure count and both skip the lock.
 */
public class AuthService {

    public static final int MAX_FAILED_ATTEMPTS = 3;
    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final String DEFAULT_MANAGER = "manager";
    public static final String DEFAULT_MANAGER_PASSWORD = "ChangeMe123";

    private static final String INVALID = "Invalid username or password";

    private final UserRepository users;

    public AuthService(UserRepository users) {
        this.users = users;
    }

    /** Creates the default manager if there is no manager at all. Returns true if it did. */
    public synchronized boolean seedDefaultManager() {
        boolean hasManager = users.findAll().stream().anyMatch(u -> u.getRole() == Role.MANAGER);
        if (hasManager) {
            return false;
        }
        users.save(newUser(DEFAULT_MANAGER, DEFAULT_MANAGER_PASSWORD, Role.MANAGER, true));
        return true;
    }

    /**
     * Recovery when the only manager is locked out. Called from a startup flag in Main, so it
     * needs access to the machine, which already means access to the data files.
     */
    public synchronized void resetDefaultManager() {
        User existing = users.findByUsername(DEFAULT_MANAGER).orElse(null);
        if (existing == null) {
            users.save(newUser(DEFAULT_MANAGER, DEFAULT_MANAGER_PASSWORD, Role.MANAGER, true));
            return;
        }
        String salt = PasswordHasher.newSalt();
        existing.setCredentials(PasswordHasher.hash(DEFAULT_MANAGER_PASSWORD, salt), salt, true);
        existing.unlock();
        users.save(existing);
    }

    public synchronized User login(String username, String password) {
        User user = users.findByUsername(key(username)).orElseThrow(() -> new AuthenticationException(INVALID));
        if (user.isLocked()) {
            throw new AuthenticationException("Account is locked. Ask a manager to unlock it");
        }
        String attempt = password == null ? "" : password;
        if (!PasswordHasher.matches(attempt, user.getSalt(), user.getPasswordHash())) {
            boolean nowLocked = user.registerFailure(MAX_FAILED_ATTEMPTS);
            users.save(user);
            throw new AuthenticationException(nowLocked
                    ? "Too many failed attempts. Account is locked. Ask a manager to unlock it"
                    : INVALID);
        }
        user.resetFailures();
        users.save(user);
        return user;
    }

    public synchronized void unlock(User actor, String username) {
        requireManager(actor);
        User target = users.findByUsername(key(username))
                .orElseThrow(() -> new ValidationException("User not found: " + username));
        target.unlock();
        users.save(target);
    }

    public synchronized User createUser(User actor, String username, String password, Role role) {
        requireManager(actor);
        String name = key(username);
        if (name.isEmpty()) {
            throw new ValidationException("Username is required");
        }
        if (role == null) {
            throw new ValidationException("Role is required");
        }
        checkPasswordRules(password);
        if (users.findByUsername(name).isPresent()) {
            throw new ValidationException("Username already exists: " + name);
        }
        User created = newUser(name, password, role, true);
        users.save(created);
        return created;
    }

    public synchronized void changePassword(String username, String oldPassword, String newPassword) {
        User user = users.findByUsername(key(username)).orElseThrow(() -> new AuthenticationException(INVALID));
        String old = oldPassword == null ? "" : oldPassword;
        if (!PasswordHasher.matches(old, user.getSalt(), user.getPasswordHash())) {
            throw new AuthenticationException("Current password is incorrect");
        }
        checkPasswordRules(newPassword);
        if (newPassword.equals(old)) {
            throw new ValidationException("New password must be different from the current one");
        }
        String salt = PasswordHasher.newSalt();
        user.setCredentials(PasswordHasher.hash(newPassword, salt), salt, false);
        users.save(user);
    }

    public List<User> listUsers(User actor) {
        requireManager(actor);
        return users.findAll();
    }

    private static void requireManager(User actor) {
        if (actor == null || actor.getRole() != Role.MANAGER) {
            throw new UnauthorizedActionException("Only a manager can do this");
        }
    }

    private static void checkPasswordRules(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new ValidationException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
    }

    private static User newUser(String username, String password, Role role, boolean mustChange) {
        String salt = PasswordHasher.newSalt();
        return new User(key(username), role, PasswordHasher.hash(password, salt), salt, mustChange);
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}

