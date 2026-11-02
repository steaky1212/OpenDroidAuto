package it.smg.hu.service;

/** Decides whether a failed Session should be followed by a Reconnect, and how long to wait. */
class ReconnectPolicy {
    static final int MAX_ATTEMPTS = 3;
    static final long DELAY_MS = 500;
    static final long STABLE_MS = 10_000;
    static final long GIVE_UP = -1;

    private int attempts_;
    private long sessionStartedAt_ = -1;

    /** A user-initiated start: the next drop gets fresh attempts. */
    void reset() {
        attempts_ = 0;
        sessionStartedAt_ = -1;
    }

    void sessionStarted(long nowMs) {
        sessionStartedAt_ = nowMs;
    }

    /** The running Session ended with this error: returns the delay before the next attempt, or GIVE_UP. */
    long onError(String error, long nowMs) {
        boolean wasStable = sessionStartedAt_ >= 0 && nowMs - sessionStartedAt_ >= STABLE_MS;
        sessionStartedAt_ = -1;

        if (!isTransient(error)) {
            return GIVE_UP;
        }
        if (wasStable) {
            attempts_ = 0;
        }
        return nextAttempt();
    }

    /** A Reconnect attempt could not even start (no device yet, connect failed). */
    long onStartFailed() {
        sessionStartedAt_ = -1;
        return nextAttempt();
    }

    /** Number of the current Reconnect attempt, starting at 1. */
    int attempt() {
        return attempts_;
    }

    private long nextAttempt() {
        if (attempts_ >= MAX_ATTEMPTS) {
            return GIVE_UP;
        }
        attempts_++;
        return DELAY_MS;
    }

    private static boolean isTransient(String error) {
        if (error == null) {
            return false;
        }
        switch (error) {
            case "SSL_READ":
            case "SSL_WRITE":
            case "SSL_BIO_READ":
            case "SSL_BIO_WRITE":
            case "SSL_HANDSHAKE":
                return true;
            default:
                return false;
        }
    }
}
