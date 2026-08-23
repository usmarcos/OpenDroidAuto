package it.smg.hu.manager;

/** Pure connection policy so retry and exit behavior can be tested without Android hardware. */
public final class ConnectionPolicy {
    public static final int MAX_RETRIES = 3;

    private ConnectionState state = ConnectionState.IDLE;
    private String mode;
    private int retryCount;
    private boolean autoStartSuppressed;

    public ConnectionState state() {
        return state;
    }

    public String mode() {
        return mode;
    }

    public int retryCount() {
        return retryCount;
    }

    public boolean isAutoStartAllowed() {
        return !autoStartSuppressed;
    }

    public void transportAvailable(String connectionMode) {
        if (state == ConnectionState.EXITED && connectionMode != null && connectionMode.equals(mode)) {
            return;
        }
        mode = connectionMode;
        retryCount = 0;
        autoStartSuppressed = false;
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
        retryCount = 0;
        state = ConnectionState.ACTIVE;
    }

    public void manualRetry() {
        if (!autoStartSuppressed) {
            retryCount = 0;
            state = ConnectionState.CONNECTING;
        }
    }

    public void userExited() {
        autoStartSuppressed = true;
        state = ConnectionState.EXITED;
    }

    public void detached() {
        mode = null;
        retryCount = 0;
        autoStartSuppressed = false;
        state = ConnectionState.IDLE;
    }

    public boolean failed() {
        state = ConnectionState.ERROR;
        if (autoStartSuppressed || mode == null || retryCount >= MAX_RETRIES) {
            return false;
        }
        retryCount++;
        return true;
    }
}
