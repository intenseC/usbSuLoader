package com.usbsuloader;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i("BootReceiver", "Boot completed, starting ForegroundService...");

        if(Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(intent.getAction()) ||
           Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
        Intent serviceIntent = new Intent(context, UsbBackgroundService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else context.startService(serviceIntent);
        }
    }
}
