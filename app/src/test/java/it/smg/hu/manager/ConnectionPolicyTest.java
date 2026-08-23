package it.smg.hu.manager;

import static org.junit.Assert.assertEquals;
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
    public void userExitKeepsManualRestartAvailable() {
        ConnectionPolicy policy = new ConnectionPolicy();
        policy.transportAvailable("modeUSB");
        policy.userExited();

        assertTrue(policy.isManualStartAllowed());
        policy.failed();

        policy.userExited();
        assertTrue(policy.isManualStartAllowed());
        assertEquals(ConnectionState.EXITED, policy.state());

        policy.detached();
        assertTrue(policy.isManualStartAllowed());
        assertEquals(ConnectionState.IDLE, policy.state());
    }
}
