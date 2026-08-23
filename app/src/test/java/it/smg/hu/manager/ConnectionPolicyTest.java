package it.smg.hu.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ConnectionPolicyTest {
    @Test
    public void errorsKeepTheConnectionModeForManualRetry() {
        ConnectionPolicy policy = new ConnectionPolicy();
        policy.transportAvailable("modeUSB");

        policy.failed();
        assertEquals("modeUSB", policy.mode());
        assertEquals(ConnectionState.ERROR, policy.state());
    }

    @Test
    public void userExitSuppressesRetriesUntilDetach() {
        ConnectionPolicy policy = new ConnectionPolicy();
        policy.transportAvailable("modeUSB");
        policy.userExited();

        assertFalse(policy.isManualStartAllowed());
        policy.failed();

        policy.detached();
        assertTrue(policy.isManualStartAllowed());
        assertEquals(ConnectionState.IDLE, policy.state());
    }
}
