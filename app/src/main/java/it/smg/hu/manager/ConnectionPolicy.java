package it.smg.hu.manager;

/** Pure connection policy so retry and exit behavior can be tested without Android hardware. */
public final class ConnectionPolicy {
    private ConnectionState state = ConnectionState.IDLE;
    private String mode;
    private boolean startSuppressed;

    public ConnectionState state() {
        return state;
    }

    public String mode() {
        return mode;
    }

    public boolean isManualStartAllowed() {
        return !startSuppressed;
    }

    public void transportAvailable(String connectionMode) {
        if (state == ConnectionState.EXITED && connectionMode != null && connectionMode.equals(mode)) {
            return;
        }
        mode = connectionMode;
        startSuppressed = false;
        state = ConnectionState.IDLE;
    }

    public void permissionPending() {
        state = ConnectionState.PERMISSION_PENDING;
    }

    public void switchingToAoap() {
        state = ConnectionState.AOAP_SWITCHING;
    }

    public void connecting(String connectionMode) {
        if (connectionMode != null) {
            mode = connectionMode;
        }
        state = ConnectionState.CONNECTING;
    }

    public void active() {
        state = ConnectionState.ACTIVE;
    }

    public void userExited() {
        startSuppressed = true;
        state = ConnectionState.EXITED;
    }

    public void detached() {
        mode = null;
        startSuppressed = false;
        state = ConnectionState.IDLE;
    }

    public void failed() {
        state = ConnectionState.ERROR;
    }
}
