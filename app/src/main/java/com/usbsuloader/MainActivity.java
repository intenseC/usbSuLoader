package com.usbsuloader;

import static com.usbsuloader.ALogger.infoLog;
import static com.usbsuloader.CommonMethods.REQUEST_CODE;
import static com.usbsuloader.CommonMethods.parseException;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;

import androidx.core.app.ActivityCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.usbsuloader.databinding.ActivityMainBinding;

import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewTreeObserver;
import android.widget.Toast;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements NativeEventListener {
    private boolean permGranted;
    private Context context;
    public static ServiceInterface.mServiceInterface mService;
    private AppBarConfiguration appBarConfiguration;
    private ActivityMainBinding binding;
    private boolean isBound;
    protected UsbBackgroundService usbBackgroundService;
    private  static boolean mServiceStarted;

    public void onNativeEvent(String event) {
        infoLog(getClass(), "onNativeEvent: \n" + event,
                false, false);
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            UsbBackgroundService.mBinder binder = (UsbBackgroundService.mBinder)service;
            UsbBackgroundService usbBackgroundService = binder.getUsbBackgroundService();
            usbBackgroundService.registerEventListener(MainActivity.this);
            isBound = true;
        }
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            usbBackgroundService = null;
            isBound = false;
        }
    };

    protected void initSvc() {
        if(!mServiceStarted) {
            mServiceStarted = true;
            new Handler(Looper.getMainLooper()).post(() -> {
                Intent intent = new Intent(this, UsbBackgroundService.class);
                bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
                infoLog(getClass(), " >> mService IS ON ", false, false);
            });
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = this;

        permGranted = checkAndRequestPermissions();

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.toolbar);

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);


        infoLog(getClass(), " >>>>>>>>>> permGranted {} " + permGranted,
                false, false);

        initSvc();

        if(permGranted)
            try {
                Intent serviceIntent = new Intent(context, UsbBackgroundService.class);
                context.startService(serviceIntent);
            } catch (Exception e) {
                infoLog(getClass(), "Not allowed to start service Intent... " +
                        parseException(e), false, false);
            }

        try {
            Process p = Runtime.getRuntime().exec("su");
            DataOutputStream os = new DataOutputStream(p.getOutputStream());
            os.writeBytes("id > /data/local/tmp/su_test\n");
            os.writeBytes("exit\n");
            os.flush();
            p.waitFor();
        } catch (IOException|InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        //  add items to the action bar if present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as a parent activity specified  in AndroidManifest
        int id = item.getItemId();

        // noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if(result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show();
                permGranted = true;
                try {
                    initSvc();
                } catch (Exception e) {
                    infoLog(getClass(), "Not allowed to start service Intent... "
                            + parseException(e), false, false);
                }
            } else {
                // notify on leaving, then shut down
                Toast.makeText(this, "Required permissions denied. Exiting application.", Toast.LENGTH_LONG).show();
                // close the activity and remove the app from the recents
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    finishAndRemoveTask();
                } else {
                    finish();
                }
            }
        }
    }

    private boolean checkAndRequestPermissions() {
        boolean granted = false;
        List<String> permissionList = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            permissionList = new ArrayList<>(Arrays.asList(
//                    Manifest.permission.RECORD_AUDIO,
//                    Manifest.permission.ACCESS_WIFI_STATE,
                    Manifest.permission.CHANGE_NETWORK_STATE,
                    Manifest.permission.RECEIVE_BOOT_COMPLETED,
                    Manifest.permission.WAKE_LOCK,
                    Manifest.permission.FOREGROUND_SERVICE
            ));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionList.add(android.Manifest.permission.POST_NOTIFICATIONS);
        }

        String[] permissions = (permissionList != null) ?
                permissionList.toArray(new String[0]) : new String[0];

        List<String> permissionsToRequest = new ArrayList<>();
        for(String permission : permissions) {
            if(ActivityCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if(!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toArray(new String[0]), REQUEST_CODE);
        } else granted = true;

        return granted;
    }

}
