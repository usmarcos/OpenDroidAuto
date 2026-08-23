package it.smg.hu.manager;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

/** Coordinates USB and Wi-Fi session state for the activities and foreground service. */
public final class ConnectionManager {
    public static final String ACTION_STATE_CHANGED = "it.smg.hu.CONNECTION_STATE_CHANGED";
    public static final String ACTION_RETRY = "it.smg.hu.CONNECTION_RETRY";
    public static final String EXTRA_STATE = "state";
    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_ATTEMPT = "attempt";

    private static ConnectionManager instance_;

    private final ConnectionPolicy policy_ = new ConnectionPolicy();
    private final LocalBroadcastManager broadcastManager_;
    private final Handler handler_ = new Handler(Looper.getMainLooper());
    private Runnable pendingRetry_;
    private String lastMessage_;

    private ConnectionManager(Context context) {
        broadcastManager_ = LocalBroadcastManager.getInstance(context.getApplicationContext());
    }

    public static void init(Context context) {
        if (instance_ == null) {
            instance_ = new ConnectionManager(context);
        }
    }

    public static ConnectionManager instance() {
        return instance_;
    }

    public ConnectionState state() {
        return policy_.state();
    }

    public String mode() {
        return policy_.mode();
    }

    public boolean isAutoStartAllowed() {
        return policy_.isAutoStartAllowed();
    }

    public boolean isManualStartAllowed() {
        return policy_.isManualStartAllowed();
    }

    public boolean isAutoStartEnabled() {
        return policy_.isAutoStartEnabled();
    }

    public String lastMessage() {
        return lastMessage_;
    }

    public void setAutoStartEnabled(boolean enabled) {
        if (policy_.isAutoStartEnabled() == enabled) {
            return;
        }
        policy_.setAutoStartEnabled(enabled);
        cancelRetry();
        publish(enabled ? "Automatic Android Auto start enabled" : "Automatic Android Auto start disabled");
    }

    public void transportAvailable(String mode, String message) {
        cancelRetry();
        policy_.transportAvailable(mode);
        publish(message);
    }

    public void permissionPending(String message) {
        cancelRetry();
        policy_.permissionPending();
        publish(message);
    }

    public void switchingToAoap(String message) {
        cancelRetry();
        policy_.switchingToAoap();
        publish(message);
    }

    public void connecting(String mode, String message) {
        policy_.connecting(mode);
        publish(message);
    }

    public void active(String message) {
        cancelRetry();
        policy_.active();
        publish(message);
    }

    public void userExited(String message) {
        cancelRetry();
        policy_.userExited();
        publish(message);
    }

    public void detached(String message) {
        cancelRetry();
        policy_.detached();
        publish(message);
    }

    public void failed(final String message) {
        if (policy_.failed()) {
            publish(message + " Retrying…");
            final int attempt = policy_.retryCount();
            final long delayMs = 1000L << (attempt - 1);
            pendingRetry_ = new Runnable() {
                @Override
                public void run() {
                    pendingRetry_ = null;
                    if (!policy_.isAutoStartAllowed() || policy_.mode() == null) {
                        return;
                    }
                    Intent retry = new Intent(ACTION_RETRY);
                    retry.putExtra(EXTRA_MODE, policy_.mode());
                    retry.putExtra(EXTRA_ATTEMPT, attempt);
                    broadcastManager_.sendBroadcast(retry);
                }
            };
            handler_.postDelayed(pendingRetry_, delayMs);
        } else {
            publish(message);
        }
    }

    public void retryNow() {
        if (policy_.mode() == null || !policy_.isManualStartAllowed()) {
            return;
        }
        cancelRetry();
        policy_.manualRetry();
        publish("Retrying connection");
        Intent retry = new Intent(ACTION_RETRY);
        retry.putExtra(EXTRA_MODE, policy_.mode());
        retry.putExtra(EXTRA_ATTEMPT, 0);
        broadcastManager_.sendBroadcast(retry);
    }

    private void publish(String message) {
        lastMessage_ = message;
        Intent intent = new Intent(ACTION_STATE_CHANGED);
        intent.putExtra(EXTRA_STATE, policy_.state().name());
        intent.putExtra(EXTRA_MODE, policy_.mode());
        intent.putExtra(EXTRA_MESSAGE, message);
        intent.putExtra(EXTRA_ATTEMPT, policy_.retryCount());
        broadcastManager_.sendBroadcast(intent);
    }

    private void cancelRetry() {
        if (pendingRetry_ != null) {
            handler_.removeCallbacks(pendingRetry_);
            pendingRetry_ = null;
        }
    }
}
