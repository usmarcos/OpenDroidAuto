package it.smg.hu.ui.main;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import it.smg.hu.R;
import it.smg.hu.config.Settings;
import it.smg.hu.manager.ConnectionManager;
import it.smg.hu.manager.ConnectionState;
import it.smg.hu.manager.USBManager;
import it.smg.hu.manager.WIFIManager;
import it.smg.hu.service.ODAService;
import it.smg.hu.ui.MainActivity;
import it.smg.hu.ui.SettingsActivity;

/** The stationary connection dashboard. The normal USB flow never requires a Start button. */
public class HomeFragment extends Fragment {
    private TextView statusBadge_;
    private TextView statusTitle_;
    private TextView statusDetail_;
    private TextView usbStatus_;
    private TextView wifiStatus_;
    private ImageView indicator_;
    private Button retryButton_;
    private USBManager usbManager_;
    private WIFIManager wifiManager_;
    private LocalBroadcastManager broadcasts_;
    private ConnectionState renderedState_;

    private final BroadcastReceiver receiver_ = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ConnectionManager.ACTION_STATE_CHANGED.equals(action)) {
                String name = intent.getStringExtra(ConnectionManager.EXTRA_STATE);
                ConnectionState state = name == null ? ConnectionState.IDLE : ConnectionState.valueOf(name);
                renderState(state, intent.getStringExtra(ConnectionManager.EXTRA_MESSAGE),
                        intent.getStringExtra(ConnectionManager.EXTRA_MODE));
            } else if (USBManager.ATTACH_AOAP_DEVICE.equals(action)) {
                usbStatus_.setText(R.string.home_usb_ready);
            } else if (USBManager.DETACH_AOAP_DEVICE.equals(action)) {
                usbStatus_.setText(R.string.home_usb_waiting);
            } else if (WIFIManager.CONNECT_WIFI.equals(action)) {
                String ssid = intent.getStringExtra(WIFIManager.EXTRA_SSID);
                wifiStatus_.setText(getString(R.string.home_wifi_ready, ssid == null ? "" : ssid));
            } else if (WIFIManager.DISCONNECT_WIFI.equals(action)) {
                renderWifiAvailability();
            }
        }
    };

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.home_fragment, container, false);
        usbManager_ = USBManager.instance();
        wifiManager_ = WIFIManager.instance();
        broadcasts_ = LocalBroadcastManager.getInstance(requireContext());

        statusBadge_ = view.findViewById(R.id.statusBadge);
        statusTitle_ = view.findViewById(R.id.statusTitle);
        statusDetail_ = view.findViewById(R.id.statusDetail);
        usbStatus_ = view.findViewById(R.id.usbStatus);
        wifiStatus_ = view.findViewById(R.id.wifiStatus);
        indicator_ = view.findViewById(R.id.connectionIndicator);
        retryButton_ = view.findViewById(R.id.retryBtn);

        view.findViewById(R.id.settingsBtn).setOnClickListener(v ->
                startActivityForResult(new Intent(getContext(), SettingsActivity.class), MainActivity.SETTINGS_ACTIVITY_REQUEST));
        view.findViewById(R.id.exitBtn).setOnClickListener(v -> ((MainActivity) requireActivity()).exitSession());
        retryButton_.setOnClickListener(v -> retryConnection());

        renderWifiAvailability();
        renderState(ConnectionManager.instance().state(), null, ConnectionManager.instance().mode());
        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectionManager.ACTION_STATE_CHANGED);
        filter.addAction(USBManager.ATTACH_AOAP_DEVICE);
        filter.addAction(USBManager.DETACH_AOAP_DEVICE);
        filter.addAction(WIFIManager.CONNECT_WIFI);
        filter.addAction(WIFIManager.DISCONNECT_WIFI);
        broadcasts_.registerReceiver(receiver_, filter);
    }

    @Override
    public void onStop() {
        broadcasts_.unregisterReceiver(receiver_);
        super.onStop();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == MainActivity.SETTINGS_ACTIVITY_REQUEST) {
            renderWifiAvailability();
            if (wifiManager_ != null) {
                wifiManager_.checkNetwork();
            }
        }
    }

    private void retryConnection() {
        String mode = ConnectionManager.instance().mode();
        if (ODAService.MODE_USB.equals(mode) && usbManager_ != null) {
            usbManager_.retryLastDevice();
        } else {
            ConnectionManager.instance().retryNow();
        }
    }

    private void renderWifiAvailability() {
        if (Settings.instance().advanced.enableWiFi()) {
            wifiStatus_.setText(R.string.home_wifi_waiting);
        } else {
            wifiStatus_.setText(R.string.home_wifi_off);
        }
    }

    private void renderState(ConnectionState state, String message, String mode) {
        renderedState_ = state;
        retryButton_.setVisibility(state == ConnectionState.ERROR ? View.VISIBLE : View.GONE);
        String detail = message;
        int badge = R.drawable.status_badge_idle;
        int icon = R.drawable.usb_dis;

        switch (state) {
            case PERMISSION_PENDING:
                statusBadge_.setText(R.string.home_status_permission);
                statusTitle_.setText(R.string.home_permission_title);
                detail = fallback(detail, R.string.home_permission_detail);
                break;
            case AOAP_SWITCHING:
                statusBadge_.setText(R.string.home_status_connecting);
                statusTitle_.setText(R.string.home_connecting_title);
                detail = fallback(detail, R.string.home_aoap_detail);
                break;
            case CONNECTING:
                statusBadge_.setText(R.string.home_status_connecting);
                statusTitle_.setText(R.string.home_connecting_title);
                detail = fallback(detail, R.string.home_connecting_detail);
                break;
            case ACTIVE:
                badge = R.drawable.status_badge_active;
                icon = R.drawable.usb;
                statusBadge_.setText(R.string.home_status_connected);
                statusTitle_.setText(R.string.home_connected_title);
                detail = fallback(detail, R.string.home_connected_detail);
                break;
            case EXITED:
                statusBadge_.setText(R.string.home_status_stopped);
                statusTitle_.setText(R.string.home_stopped_title);
                detail = fallback(detail, R.string.home_stopped_detail);
                break;
            case ERROR:
                badge = R.drawable.status_badge_error;
                statusBadge_.setText(R.string.home_status_error);
                statusTitle_.setText(R.string.home_error_title);
                detail = fallback(detail, R.string.home_error_detail);
                break;
            case IDLE:
            default:
                statusBadge_.setText(R.string.home_status_waiting);
                statusTitle_.setText(R.string.home_waiting_title);
                detail = fallback(detail, R.string.home_waiting_detail);
                break;
        }
        statusBadge_.setBackgroundResource(badge);
        indicator_.setImageResource(icon);
        statusDetail_.setText(detail);
        if (ODAService.MODE_USB.equals(mode) && usbManager_ != null && usbManager_.aoapDevice() != null) {
            usbStatus_.setText(R.string.home_usb_ready);
        }
    }

    private String fallback(String detail, int fallbackResource) {
        return detail == null || detail.trim().isEmpty() ? getString(fallbackResource) : detail;
    }
}
