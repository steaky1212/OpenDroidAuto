package it.smg.hu.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ReconnectPolicyTest {

    private static final long GIVE_UP = ReconnectPolicy.GIVE_UP;

    @Test
    public void transientSslErrorsReconnectAfterTheDelay() {
        for (String error : new String[]{"SSL_READ", "SSL_WRITE", "SSL_BIO_READ", "SSL_BIO_WRITE", "SSL_HANDSHAKE"}) {
            ReconnectPolicy policy = new ReconnectPolicy();
            policy.sessionStarted(0);
            assertEquals(error, ReconnectPolicy.DELAY_MS, policy.onError(error, 1000));
        }
    }

    @Test
    public void otherErrorsDoNotReconnect() {
        for (String error : new String[]{"SSL_READ_CERTIFICATE", "SSL_METHOD", "USB_TRANSFER", "USB OPEN DEVICE", "TCP_TRANSFER", "", null}) {
            ReconnectPolicy policy = new ReconnectPolicy();
            policy.sessionStarted(0);
            assertEquals(String.valueOf(error), GIVE_UP, policy.onError(error, 1000));
        }
    }

    @Test
    public void givesUpAfterThreeFailedAttempts() {
        ReconnectPolicy policy = new ReconnectPolicy();
        policy.sessionStarted(0);

        assertEquals(ReconnectPolicy.DELAY_MS, policy.onError("SSL_READ", 100));
        policy.sessionStarted(700);
        assertEquals(ReconnectPolicy.DELAY_MS, policy.onError("SSL_READ", 800));
        policy.sessionStarted(1300);
        assertEquals(ReconnectPolicy.DELAY_MS, policy.onError("SSL_READ", 1400));
        policy.sessionStarted(1900);
        assertEquals(GIVE_UP, policy.onError("SSL_READ", 2000));
    }

    @Test
    public void attemptThatCannotStartCountsAsFailedAttempt() {
        ReconnectPolicy policy = new ReconnectPolicy();
        policy.sessionStarted(0);

        assertEquals(ReconnectPolicy.DELAY_MS, policy.onError("SSL_READ", 100));
        assertEquals(ReconnectPolicy.DELAY_MS, policy.onStartFailed());
        assertEquals(ReconnectPolicy.DELAY_MS, policy.onStartFailed());
        assertEquals(GIVE_UP, policy.onStartFailed());
    }

    @Test
    public void attemptNumberIsReportedForTheFirstAttemptToast() {
        ReconnectPolicy policy = new ReconnectPolicy();
        policy.sessionStarted(0);

        policy.onError("SSL_READ", 100);
        assertEquals(1, policy.attempt());
        policy.onStartFailed();
        assertEquals(2, policy.attempt());
    }

    @Test
    public void stableSessionGivesTheNextDropFreshAttempts() {
        ReconnectPolicy policy = new ReconnectPolicy();
        policy.sessionStarted(0);
        policy.onError("SSL_READ", 100);
        policy.onStartFailed();

        policy.sessionStarted(2000);
        // session lasted exactly the stable time
        assertEquals(ReconnectPolicy.DELAY_MS, policy.onError("SSL_READ", 2000 + ReconnectPolicy.STABLE_MS));
        assertEquals(1, policy.attempt());
    }

    @Test
    public void shortSessionsDoNotResetTheAttempts() {
        ReconnectPolicy policy = new ReconnectPolicy();
        policy.sessionStarted(0);
        policy.onError("SSL_READ", 100);
        policy.sessionStarted(700);
        policy.onError("SSL_READ", 700 + ReconnectPolicy.STABLE_MS - 1);
        policy.sessionStarted(20_000);
        policy.onError("SSL_READ", 20_100);

        policy.sessionStarted(20_700);
        assertEquals(GIVE_UP, policy.onError("SSL_READ", 20_800));
    }

    @Test
    public void userStartResetsTheAttempts() {
        ReconnectPolicy policy = new ReconnectPolicy();
        policy.sessionStarted(0);
        policy.onError("SSL_READ", 100);
        policy.onStartFailed();
        policy.onStartFailed();

        policy.reset();
        policy.sessionStarted(5000);
        assertEquals(ReconnectPolicy.DELAY_MS, policy.onError("SSL_READ", 5100));
        assertTrue(policy.attempt() == 1);
    }
}
