package com.ikedi.pos.model;

/**
 * A login account. Only the salted hash is stored, never the password.
 * The failed-attempt counter and lock flag live here so they survive a restart.
 */
public class User {

    private final String username;
    private final Role role;
    private String passwordHash;
    private String salt;
    private int failedAttempts;
    private boolean locked;
    private boolean mustChangePassword;

    public User(String username, Role role, String passwordHash, String salt, boolean mustChangePassword) {
        this.username = username;
        this.role = role;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.mustChangePassword = mustChangePassword;
    }

    public String getUsername() { return username; }
    public Role getRole() { return role; }
    public String getPasswordHash() { return passwordHash; }
    public String getSalt() { return salt; }
    public int getFailedAttempts() { return failedAttempts; }
    public boolean isLocked() { return locked; }
    public boolean isMustChangePassword() { return mustChangePassword; }

    /** Counts one failed login. Returns true if this failure locked the account. */
    public boolean registerFailure(int maxAttempts) {
        failedAttempts++;
        if (failedAttempts >= maxAttempts) {
            locked = true;
        }
        return locked;
    }

    public void resetFailures() {
        failedAttempts = 0;
    }

    public void unlock() {
        locked = false;
        failedAttempts = 0;
    }

    public void setCredentials(String passwordHash, String salt, boolean mustChangePassword) {
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.mustChangePassword = mustChangePassword;
    }
}

