package com.usbsuloader;

import static com.usbsuloader.ALogger.infoLog;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ServiceInfo;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;


import  com.usbsuloader.ServiceInterface.mServiceInterface;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.util.HashMap;

public class UsbBackgroundService extends Service implements mServiceInterface {

    // Qinheng Electronics Vendor ID
    private static final int USB_VENDOR_CH340 = 0x1A86;

    // Prod IDs for CH340 / CH341 series
    private static final int USB_PRODUCT_CH340 = 0x7523;
    private static final int USB_PRODUCT_CH341 = 0x5523;
    private static final int USB_PRODUCT_CH340K = 0x5512;
    private static final int NOTIFICATION_ID = 8;
    private static final String TAG = "UsbBackgroundService";
    private static final String CHANNEL_ID = "usb_service";
    public static ServiceInterface.mServiceInterface mService;
    private boolean isBound;
    private UsbManager usbManager;
    private BroadcastReceiver usbEvtReceiver;
    private static final String ACTION_USB_PERMISSION = "com.USB_PERMISSION";
    protected UsbDevice usbDevice;
    private Thread rndisWatcherThread;
    private volatile boolean isWatching;

    @Override
    public Context accessServiceContext() {
        return this;
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            UsbBackgroundService.mBinder binder = (UsbBackgroundService.mBinder) service;
            mService = binder.getService();
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            isBound = false;
        }
    };

    public native void registerEventListener(NativeEventListener listener);

    public class mBinder extends Binder {
        // to retrieve context only
        public ServiceInterface.mServiceInterface getService() {
            return UsbBackgroundService.this;
        }

        public UsbBackgroundService getUsbBackgroundService() {
            return UsbBackgroundService.this;
        }
    }

    public void initAll() {
        usbMon();
        startRndisWatcher();

        try {
            Process p = Runtime.getRuntime().exec(
                    new String[]{"su", "-c", "id"}
            );

            BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream())
            );

            String line;
            while ((line = r.readLine()) != null) {
                Log.i(TAG, "su: " + line);
            }

            int rc = p.waitFor();
            Log.i(TAG, "su exit: " + rc);

        } catch (Exception e) {
            Log.e(TAG, "su failed", e);
        }

    }

    @Override
    public void onCreate() {
        super.onCreate();

        Log.i(TAG, "Service onCreate");

        NotificationManager nm =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        NotificationChannel channel = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channel = new NotificationChannel(
                    CHANNEL_ID,
                    "USB Service",
                    NotificationManager.IMPORTANCE_LOW
            );
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(channel);


            Notification notification =
                    new Notification.Builder(this, CHANNEL_ID)
                            .setContentTitle("USB Service")
                            .setContentText("Running")
                            .setSmallIcon(R.drawable.notify_ic_foreground)
                            .build();

            startForeground(1, notification);

            infoLog(getClass(), "Service entered foreground",
                    false, false);
        }
        initAll();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isWatching = false;
        if (rndisWatcherThread != null) {
            rndisWatcherThread.interrupt();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service onStartCommand");
        Log.i(TAG, "ABOUT TO CALL startForegroundService");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification(),
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        } else {
            startForeground(NOTIFICATION_ID, buildNotification()); // fallback for Android 8/9
        }

        if (!isBound) {
            if (intent == null) {
                intent = new Intent(this, UsbBackgroundService.class);
            }
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification buildNotification() {
        NotificationCompat.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String channelId = "ForegroundServiceChannel";
            NotificationChannel channel = new NotificationChannel(channelId,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_HIGH);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
            builder = new NotificationCompat.Builder(this, channelId);
        } else {
            builder = new NotificationCompat.Builder(this, "");
        }

        builder.setSmallIcon(R.drawable.notify_ic_foreground)
                .setContentTitle("Priority Service")

                .setContentText("usbSuLoader is now running in the foreground.")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        return builder.build();
    }

    private boolean checkUsbRndisState() {

        try {
          // Read system property Android uses to track USB state
            Process p = Runtime.getRuntime().exec(new String[]{"getprop", "sys.usb.state"});
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String state = r.readLine();

            // Drain stderr
            BufferedReader e = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            while (e.readLine() != null) { /* discard */ }

            p.waitFor();

            Log.d(TAG, "Raw sys.usb.state: " + state);

            // When RNDIS is active, this property reads rndis (adb or rndis)
            return state != null && state.contains("rndis");

        } catch (Exception e) {
            Log.e(TAG, "State check failed", e);
            return false;
        }

    }

    public void startRndisWatcher() {
        isWatching = true;
        rndisWatcherThread = new Thread(() -> {
            while (isWatching && !Thread.currentThread().isInterrupted()) {

                //  Check if RNDIS is active
                boolean isRndisActive = checkUsbRndisState();

                if (!isRndisActive) {
                    Log.w(TAG, "RNDIS dropped! Re-asserting...");
                    _setUsbRndis(); // fire-and-forget
                } else {
                    Log.d(TAG, "RNDIS still active.");
                }

                //   check every [xx] seconds
                try {
                    Thread.sleep(30_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break; // shutting down
                }
            }
        });
        rndisWatcherThread.setName("RndisWatcher");
        rndisWatcherThread.start();
    }

    public void stopRndisWatcher() {
        isWatching = false;
        if (rndisWatcherThread != null) {
            rndisWatcherThread.interrupt();
        }
    }

    public void setUsbRndis() {
        infoLog(getClass(), "dummy call to RNDIS", false, false);
    }

    public void _setUsbRndis() {
        infoLog(getClass(), "Service will try switching USB to RNDIS", false, false);
            try {
                // Redirect both stdout and stderr to /dev/null inside the shell
                Process process = Runtime.getRuntime().exec("su");

                try (DataOutputStream os = new DataOutputStream(process.getOutputStream())) {
                    os.writeBytes("svc usb setFunctions rndis > /dev/null 2>&1\n");
                    os.writeBytes("exit\n");
                    os.flush();
                }

                Log.d("UsbCommand", "RNDIS command dispatched async");

            } catch (Exception e) {
                Log.e("UsbCommand", "Failed to dispatch RNDIS command", e);
            }

    }

    protected void usbMon() {
        usbEvtReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if ("android.hardware.usb.action.USB_STATE".equals(intent.getAction())) {
                    boolean connected = intent.getBooleanExtra("connected", false);

                    if (connected) {
                        infoLog(getClass(), "+++--- USB physically connected to PC/Host", false, false);
                        // Fire root command to force RNDIS
                        setUsbRndis();
                    } else {
                        infoLog(getClass(), "---+++ USB physically disconnected", false, false);
                        // > Cable was unplugged
                    }
                } else
                if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    infoLog(getClass(), "+++--- usb: " + device, false, false);
                    setUsbRndis();

                    if (null != device && isDesiredDevice(device)) {
                        usbDevice = device;
                    }

                } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (null != device)
                        infoLog(getClass(), "---+++ usb: " + device, false, false);


                    if(null != device && isDesiredDevice(device)) {
                        infoLog(getClass(), " usb: " + device, false, false);
                    }
                }
            }
        };

        usbDevice = scanUsbDevices();
        if (usbDevice != null) {
            infoLog(getClass(), "usbDevice: " + usbDevice, false, false);
        }

            IntentFilter filter = new IntentFilter();
            filter.addAction("android.hardware.usb.action.USB_STATE");
            filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
            filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbEvtReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(usbEvtReceiver, filter);
        }
    }

    private UsbDevice scanUsbDevices() {
        UsbDevice usbDevice = null;
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        for (UsbDevice device : deviceList.values()) {
            if (isDesiredDevice(device)) {
                usbDevice = device;
            }
        }
        return usbDevice;
    }


    private boolean isDesiredDevice(UsbDevice device) {
        if (device.getVendorId() == USB_VENDOR_CH340) {
            infoLog(getClass(), "Connecting to CH340 serial device.", false, false);
            return true;
        }
        return false;
    }


    public boolean isCH340Device(UsbDevice device) {
        int vendorId = device.getVendorId();
        int productId = device.getProductId();

        // vendor is Qinheng?
        if (vendorId == USB_VENDOR_CH340) {
            return productId == USB_PRODUCT_CH340 ||
                    productId == USB_PRODUCT_CH341 ||
                    productId == USB_PRODUCT_CH340K;
        }
        return false;
    }

    private boolean _isDesiredDevice(UsbDevice device) {
        boolean dvc = false;
        String deviceName = device.getManufacturerName();
            if (deviceName != null) {
                deviceName = deviceName.toLowerCase();
                if (device.getDeviceClass() == 0 && device.getDeviceSubclass() == 0 &&
                        device.getDeviceProtocol() == 0 && deviceName.contains("ftdi")) {
                    dvc = true;
                    infoLog(getClass(), "Connecting to serial: " + deviceName, false, false);
                }
            }
        return dvc;
    }
}
