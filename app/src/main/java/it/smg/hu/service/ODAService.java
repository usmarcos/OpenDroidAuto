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
    private String currentMode_;

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
        ConnectionManager.instance().connecting(MODE_USB, "Connecting through USB");
        startThread_ = new Thread(() -> {
            try {
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
        ConnectionManager.instance().connecting(MODE_WIFI, "Connecting through phone hotspot");
        startThread_ = new Thread(() -> {
            try {
                startWifiInternal(surfaceView, keyHolder);
            } finally {
                isStarting_.set(false);
            }
        });
        startThread_.setName("ODA-WiFi-start");
        startThread_.start();
    }

    private void startUsbInternal(SurfaceView surfaceView, InputDevice.OnKeyHolder keyHolder) {
        LibUsbDevice device = usbManager_.aoapDevice();
        if (device == null) {
            ConnectionManager.instance().failed("USB accessory unavailable. Reconnect the cable.");
            return;
        }
        try {
            if (!device.open()) {
                onAndroidAutoQuitOnError("USB OPEN DEVICE", -1);
                return;
            }
            androidAutoEntity_ = AndroidAutoEntityFactory.create(this, device, surfaceView, keyHolder);
            if (stopRequested_) {
                cleanupEntity();
                return;
            }
            androidAutoEntity_.start(this);
            isRunning_ = true;
            ConnectionManager.instance().active("Android Auto connected through USB");
        } catch (Exception e) {
            Log.e(TAG, "USB startup error", e);
            onAndroidAutoQuitOnError("USB GENERIC ERROR", -1);
        }
    }

    private void startWifiInternal(SurfaceView surfaceView, InputDevice.OnKeyHolder keyHolder) {
        String ipAddress = wifiManager_.getIpAddress();
        if (ipAddress == null) {
            ConnectionManager.instance().failed("Phone hotspot gateway is not ready");
            return;
        }
        try {
            TCPEndpoint tcpEndpoint = new TCPEndpoint(ipAddress);
            androidAutoEntity_ = AndroidAutoEntityFactory.create(this, tcpEndpoint, surfaceView, keyHolder);
            if (stopRequested_) {
                cleanupEntity();
                return;
            }
            androidAutoEntity_.start(this);
            isRunning_ = true;
            ConnectionManager.instance().active("Android Auto connected through Wi-Fi");
        } catch (TCPConnectException e) {
            Log.e(TAG, "TCP connection error", e);
            ConnectionManager.instance().failed("Wi-Fi endpoint unreachable at " + ipAddress + ":5277");
            stop();
        }
    }

    public void shutdown(){
        if (androidAutoEntity_ != null) {
            androidAutoEntity_.shutdown();
        }
    }

    public void releaseFocus(){
        if (androidAutoEntity_ != null) {
            androidAutoEntity_.releaseFocus();
        }
    }

    public void gainFocus(){
        if (androidAutoEntity_ != null) {
            androidAutoEntity_.gainFocus();
        }
    }

    public void stop(){
        stopRequested_ = true;
        if (!isRunning_ && androidAutoEntity_ == null) {
            if (Log.isInfo()) Log.i(TAG, "service not running, already stopped?");
            localBroadcastManager_.sendBroadcast(new Intent(ODAService.STOP_ACTION));
            stopService(new Intent(this, ODAService.class));
            return;
        }

        isRunning_ = false;

        if (Log.isInfo()) Log.i(TAG, "Stop");

        if (Settings.instance().advanced.hondaIntegrationEnabled()){
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
        ConnectionManager.instance().detached(currentMode_, "Android Auto session ended");
        stop();
    }

    @Keep
    @Override
    public void onAndroidAutoQuitOnError(String error, int nativeErrorCode){
        Log.e(TAG, "closing with error " + error + "(" + nativeErrorCode + ")");
        final String details = nativeErrorCode == -1 ? error : error + " (code " + nativeErrorCode + ")";
        ConnectionManager.instance().failed(details);

        mainHandler_.post(() -> {
            Toast.makeText(this, details, Toast.LENGTH_LONG).show();
        });

        stop();
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
    }

}
