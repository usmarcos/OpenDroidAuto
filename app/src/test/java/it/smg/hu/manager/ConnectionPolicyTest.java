package it.smg.hu.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ConnectionPolicyTest {
    @Test
    public void retriesAreBoundedAndKeepTheConnectionMode() {
        ConnectionPolicy policy = new ConnectionPolicy();
        policy.setAutoStartEnabled(true);
        policy.transportAvailable("modeUSB");

        assertTrue(policy.failed());
        assertTrue(policy.failed());
        assertTrue(policy.failed());
        assertFalse(policy.failed());
        assertEquals("modeUSB", policy.mode());
        assertEquals(ConnectionState.ERROR, policy.state());
    }

    @Test
    public void userExitSuppressesRetriesUntilDetach() {
        ConnectionPolicy policy = new ConnectionPolicy();
        policy.setAutoStartEnabled(true);
        policy.transportAvailable("modeUSB");
        policy.userExited();

        assertFalse(policy.isAutoStartAllowed());
        assertFalse(policy.failed());

        policy.detached();
        assertTrue(policy.isAutoStartAllowed());
        assertEquals(ConnectionState.IDLE, policy.state());
    }

    @Test
    public void manualModeDoesNotScheduleAutomaticRetries() {
        ConnectionPolicy policy = new ConnectionPolicy();
        policy.transportAvailable("modeUSB");

        assertFalse(policy.failed());
        assertEquals(ConnectionState.ERROR, policy.state());
    }
}
