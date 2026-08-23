package it.smg.hu.service;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.SurfaceView;
import android.widget.Toast;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.Keep;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import it.smg.hu.R;
import it.smg.hu.config.Settings;
import it.smg.hu.manager.HondaConnectManager;
import it.smg.hu.manager.ConnectionManager;
import it.smg.hu.manager.USBManager;
import it.smg.hu.manager.WIFIManager;
import it.smg.hu.projection.InputDevice;
import it.smg.hu.ui.notification.NotificationFactory;
import it.smg.libs.aasdk.tcp.TCPConnectException;
import it.smg.libs.common.Log;
import it.smg.libs.aasdk.service.AndroidAutoEntity;
import it.smg.libs.aasdk.service.IAndroidAutoEntityEventHandler;
import it.smg.libs.aasdk.tcp.TCPEndpoint;
import it.smg.libs.aasdk.usb.LibUsbDevice;

public class ODAService extends Service implements IAndroidAutoEntityEventHandler {

    public static final String START_ACTION = "it.smg.hu.service.ODAService.START_ACTION";
    public static final String STOP_ACTION = "it.smg.hu.service.ODAService.STOP_ACTION";
    public static final String STOP_VIDEO_INDICATION = "it.smg.hu.service.ODAService.STOP_VIDEO_INDICATION";
    public static final String FORCE_CLOSE_ACTION = "it.smg.hu.service.ODAService.FORCE_CLOSE_ACTION";

    public static final String EXTRA_START_MODE = "startMode";
    public static final String MODE_USB = "modeUSB";
    public static final String MODE_WIFI = "modeWifi";

    private static final String TAG = "ODAService";

    private final IBinder mBinder = new ServiceBinder();

    private LocalBroadcastManager localBroadcastManager_;
    private NotificationFactory notificationFactory_;
    private volatile AndroidAutoEntity androidAutoEntity_;
    private final Object entityLock_ = new Object();

    private  USBManager usbManager_;
    private  WIFIManager wifiManager_;

    private Thread startThread_;

    private Handler mainHandler_;

    private volatile boolean isRunning_;
    private volatile boolean stopRequested_;
    private final AtomicBoolean isStarting_ = new AtomicBoolean(false);
    private final AtomicBoolean stopInProgress_ = new AtomicBoolean(false);
    private volatile String currentMode_;

    public ODAService() {}

    @Override
    public void onCreate() {
        if (Log.isInfo()) Log.i(TAG, "create");
        super.onCreate();

        NotificationFactory.init(getApplicationContext());
        notificationFactory_ = NotificationFactory.instance();

        usbManager_ = USBManager.instance();
        wifiManager_ = WIFIManager.instance();
        mainHandler_ = new Handler(Looper.getMainLooper());

        localBroadcastManager_ = LocalBroadcastManager.getInstance(this);
    }

    public void startUsb(SurfaceView surfaceView, InputDevice.OnKeyHolder keyHolder){
        if (isRunning_ || !isStarting_.compareAndSet(false, true)) {
            return;
        }
        currentMode_ = MODE_USB;
        stopRequested_ = false;
        stopInProgress_.set(false);
        ConnectionManager.instance().connecting(MODE_USB, getString(R.string.connection_usb_connecting));
        startThread_ = new Thread(() -> {
            try {
                prepareLegacyLooper();
                startUsbInternal(surfaceView, keyHolder);
            } finally {
                isStarting_.set(false);
            }
        });
        startThread_.setName("ODA-USB-start");
        startThread_.start();
    }

    public void startWifi(SurfaceView surfaceView, InputDevice.OnKeyHolder keyHolder){
        if (isRunning_ || !isStarting_.compareAndSet(false, true)) {
            return;
        }
        currentMode_ = MODE_WIFI;
        stopRequested_ = false;
        stopInProgress_.set(false);
        ConnectionManager.instance().connecting(MODE_WIFI, getString(R.string.connection_wifi_connecting));
        startThread_ = new Thread(() -> {
            try {
                prepareLegacyLooper();
                startWifiInternal(surfaceView, keyHolder);
            } finally {
                isStarting_.set(false);
            }
        });
        startThread_.setName("ODA-WiFi-start");
        startThread_.start();
    }

    private void startUsbInternal(SurfaceView surfaceView, InputDevice.OnKeyHolder keyHolder) {
        LibUsbDevice device = usbManager_.openAoapDeviceWithRetry();
        if (device == null) {
            ConnectionManager.instance().failed(getString(R.string.connection_usb_unavailable));
            return;
        }
        try {
            AndroidAutoEntity entity = AndroidAutoEntityFactory.create(this, device, surfaceView, keyHolder);
            if (entity == null) {
                throw new IllegalStateException("Android Auto USB entity was not created");
            }
            synchronized (entityLock_) {
                if (stopRequested_) {
                    disposeEntity(entity);
                    return;
                }
                androidAutoEntity_ = entity;
                entity.start(this);
                if (stopRequested_ || androidAutoEntity_ != entity) {
                    return;
                }
                isRunning_ = true;
            }
            ConnectionManager.instance().active(getString(R.string.connection_usb_active));
        } catch (Throwable e) {
            if (stopRequested_) {
                return;
            }
            Log.e(TAG, "USB startup error", e);
            onAndroidAutoQuitOnError("USB GENERIC ERROR", -1);
        }
    }

    private void startWifiInternal(SurfaceView surfaceView, InputDevice.OnKeyHolder keyHolder) {
        String ipAddress = wifiManager_.getIpAddress();
        if (ipAddress == null) {
            ConnectionManager.instance().failed(getString(R.string.connection_wifi_gateway_error));
            return;
        }
        try {
            TCPEndpoint tcpEndpoint = new TCPEndpoint(ipAddress);
            AndroidAutoEntity entity = AndroidAutoEntityFactory.create(this, tcpEndpoint, surfaceView, keyHolder);
            if (entity == null) {
                throw new IllegalStateException("Android Auto Wi-Fi entity was not created");
            }
            synchronized (entityLock_) {
                if (stopRequested_) {
                    disposeEntity(entity);
                    return;
                }
                androidAutoEntity_ = entity;
                entity.start(this);
                if (stopRequested_ || androidAutoEntity_ != entity) {
                    return;
                }
                isRunning_ = true;
            }
            ConnectionManager.instance().active(getString(R.string.connection_wifi_active));
        } catch (TCPConnectException e) {
            Log.e(TAG, "TCP connection error", e);
            ConnectionManager.instance().failed(getString(R.string.connection_wifi_endpoint_error, ipAddress));
            stop();
        } catch (Throwable e) {
            if (stopRequested_) {
                return;
            }
            Log.e(TAG, "Wi-Fi startup error", e);
            onAndroidAutoQuitOnError("WIFI GENERIC ERROR", -1);
        }
    }

    public void shutdown(){
        synchronized (entityLock_) {
            if (androidAutoEntity_ != null) {
                androidAutoEntity_.shutdown();
            }
        }
    }

    public void releaseFocus(){
        synchronized (entityLock_) {
            if (androidAutoEntity_ != null) {
                androidAutoEntity_.releaseFocus();
            }
        }
    }

    public void gainFocus(){
        synchronized (entityLock_) {
            if (androidAutoEntity_ != null) {
                androidAutoEntity_.gainFocus();
            }
        }
    }

    public void stop(){
        stopRequested_ = true;
        if (!stopInProgress_.compareAndSet(false, true)) {
            return;
        }
        if (!isRunning_ && androidAutoEntity_ == null) {
            if (Log.isInfo()) Log.i(TAG, "service not running, already stopped?");
            localBroadcastManager_.sendBroadcast(new Intent(ODAService.STOP_ACTION));
            stopService(new Intent(this, ODAService.class));
            return;
        }

        isRunning_ = false;

        if (Log.isInfo()) Log.i(TAG, "Stop");

        if (Settings.instance().advanced.hondaIntegrationEnabled()
                && HondaConnectManager.instance() != null){
            HondaConnectManager.instance().endAudioBinding();
        }

        cleanupEntity();

        startThread_ = null;
        currentMode_ = null;

        Intent stopIntent = new Intent(ODAService.STOP_ACTION);
        localBroadcastManager_.sendBroadcast(stopIntent);

        Intent service = new Intent(this, ODAService.class);
        stopService(service);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public void onDestroy() {
        if (Log.isDebug()) Log.d(TAG, "onDestroy");
        stopRequested_ = true;
        stopInProgress_.set(true);
        isRunning_ = false;
        cleanupEntity();
        super.onDestroy();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (Log.isDebug()) Log.d(TAG, "onUnbind");
        return super.onUnbind(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Log.isDebug()) Log.d(TAG, "Received start id " + startId + ": " + intent);

        Notification notification = notificationFactory_.create();
        startForeground(123456, notification);

        return START_NOT_STICKY;
    }

    @Keep
    @Override
    public void onAndroidAutoQuit() {
        stopRequested_ = true;
        ConnectionManager.instance().detached(currentMode_, getString(R.string.connection_session_ended));
        mainHandler_.post(this::stop);
    }

    @Keep
    @Override
    public void onAndroidAutoQuitOnError(String error, int nativeErrorCode){
        stopRequested_ = true;
        Log.e(TAG, "closing with error " + error + "(" + nativeErrorCode + ")");
        final String details = nativeErrorCode == -1
                ? getString(R.string.connection_native_error, error)
                : getString(R.string.connection_native_error_code, error, nativeErrorCode);
        ConnectionManager.instance().failed(details);

        mainHandler_.post(() -> {
            Toast.makeText(this, details, Toast.LENGTH_LONG).show();
            stop();
        });
    }

    @Keep
    @Override
    public void onAVChannelStopIndication() {
        if (Log.isInfo()) Log.i(TAG, "stop video indication");
        Intent stopIntent = new Intent(ODAService.STOP_VIDEO_INDICATION);
        localBroadcastManager_.sendBroadcast(stopIntent);
    }

    public class ServiceBinder extends Binder {
        public ODAService getService() {
            return ODAService.this;
        }
    }

    private void cleanupEntity() {
        synchronized (entityLock_) {
            AndroidAutoEntity entity = androidAutoEntity_;
            androidAutoEntity_ = null;
            if (entity == null) {
                return;
            }
            disposeEntity(entity);
        }
    }

    private void disposeEntity(AndroidAutoEntity entity) {
        try {
            entity.stop();
        } catch (Throwable t) {
            Log.e(TAG, "Error stopping Android Auto entity", t);
        }
        try {
            entity.delete();
        } catch (Throwable t) {
            Log.e(TAG, "Error deleting Android Auto entity", t);
        }
    }

    private void prepareLegacyLooper() {
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
    }

}
