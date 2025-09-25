package com.example.koiyure;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.BatteryManager; // 追加
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Locale;

public class WebSocketService extends Service implements P2PWebsocket.Listener, WolfxWebsocket.Listener {

    private static final String CHANNEL_ID = "WebSocketServiceChannel";
    private static final String TAG = "WebSocketService";
    private static final int SERVICE_NOTIFICATION_ID = 1;

    private P2PWebsocket p2pWebsocket;
    private WolfxWebsocket wolfxWebsocket;
    private TextToSpeech tts;
    private ConnectivityManager.NetworkCallback networkCallback;
    private ConnectivityManager connectivityManager;

    // WakeLock
    private PowerManager.WakeLock wakeLock;

    private static final long HEALTH_CHECK_INTERVAL_MS = 30 * 1000;
    private Handler healthCheckHandler = new Handler(Looper.getMainLooper());
    private Runnable healthCheckRunnable = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "Service health check running...");
            boolean p2pConnected = p2pWebsocket != null && p2pWebsocket.isConnected();
            boolean wolfxConnected = wolfxWebsocket != null && wolfxWebsocket.isConnected();

            if (!p2pConnected) {
                Log.w(TAG, "P2P WebSocket disconnected. Restarting...");
                if (p2pWebsocket != null) p2pWebsocket.stop();
                p2pWebsocket = new P2PWebsocket();
                p2pWebsocket.setListener(WebSocketService.this);
                p2pWebsocket.start();
            }

            if (!wolfxConnected) {
                Log.w(TAG, "Wolfx WebSocket disconnected. Restarting...");
                if (wolfxWebsocket != null) wolfxWebsocket.stop();
                wolfxWebsocket = new WolfxWebsocket();
                wolfxWebsocket.setListener(WebSocketService.this);
                wolfxWebsocket.start();
            }

            healthCheckHandler.postDelayed(this, HEALTH_CHECK_INTERVAL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "WebSocketService onCreate");
        createNotificationChannel();
        startForeground(SERVICE_NOTIFICATION_ID, createNotification());

        // 充電中のみWakeLock取得
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            BatteryManager batteryManager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
            boolean isCharging = false;
            if (batteryManager != null) {
                isCharging = batteryManager.isCharging();
            }
            if (isCharging) {
                wakeLock = powerManager.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "Koiyure::WebSocketServiceWakeLock");
                if (wakeLock != null && !wakeLock.isHeld()) {
                    wakeLock.acquire();
                    Log.d(TAG, "WakeLock acquired (charging).");
                }
            } else {
                Log.d(TAG, "Not charging. WakeLock not acquired.");
            }
        }

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.JAPANESE);
            } else {
                Log.e(TAG, "TTS初期化失敗");
            }
        });

        p2pWebsocket = new P2PWebsocket();
        p2pWebsocket.setListener(this);
        p2pWebsocket.start();

        wolfxWebsocket = new WolfxWebsocket();
        wolfxWebsocket.setListener(this);
        wolfxWebsocket.start();

        registerNetworkCallback();
        healthCheckHandler.postDelayed(healthCheckRunnable, HEALTH_CHECK_INTERVAL_MS);
    }

    private void registerNetworkCallback() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    Log.d(TAG, "Network available. Checking WebSockets.");
                    if (p2pWebsocket != null) p2pWebsocket.start();
                    if (wolfxWebsocket != null) wolfxWebsocket.start();
                }

                @Override
                public void onLost(Network network) {
                    Log.d(TAG, "Network lost.");
                }
            };
            NetworkRequest networkRequest = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
            Log.d(TAG, "NetworkCallback registered.");
        }
    }

    @Override
    public void onP2PMessageReceived(String message) {
        Log.d(TAG, "P2P受信: " + message);
        saveJsonToInternalStorage(message);
        updateWidget(message);
    }

    @Override
    public void onP2PStatusChanged(boolean isConnected) {
        Log.d(TAG, "P2P WebSocket Status: " + (isConnected ? "Connected" : "Disconnected"));
    }

    @Override
    public void onWolfxMessageReceived(String message) {
        Log.d(TAG, "Wolfx受信: " + message);
        saveJsonToInternalStorage(message);
        updateWidget(message);
    }

    @Override
    public void onWolfxStatusChanged(boolean isConnected) {
        Log.d(TAG, "Wolfx WebSocket Status: " + (isConnected ? "Connected" : "Disconnected"));
    }

    private void saveJsonToInternalStorage(String json) {
        String fileName = "all_received_data.json";
        try (FileOutputStream fos = openFileOutput(fileName, Context.MODE_APPEND)) {
            fos.write((json + "\n").getBytes());
            Log.d(TAG, "JSONデータを内部ストレージに追記: " + fileName);
        } catch (IOException e) {
            Log.e(TAG, "JSON保存失敗", e);
        }
    }

    private String loadJsonFromInternalStorage() {
        String fileName = "all_received_data.json";
        try (FileInputStream fis = openFileInput(fileName);
             InputStreamReader isr = new InputStreamReader(fis);
             BufferedReader reader = new BufferedReader(isr)) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            Log.d(TAG, "JSONデータ読み込み成功: " + fileName);
            return sb.toString();
        } catch (IOException e) {
            Log.e(TAG, "JSON読み込み失敗", e);
            return null;
        }
    }

    private void updateWidget(String data) {
        WidgetProvider.setLastReceivedData(data);
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        ComponentName widget = new ComponentName(this, WidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(widget);
        if (ids.length > 0) WidgetProvider.updateWidget(this, manager, ids);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "onTaskRemoved: scheduling restart.");
        scheduleServiceRestart();
        super.onTaskRemoved(rootIntent);
    }

    private void scheduleServiceRestart() {
        Intent restartIntent = new Intent(getApplicationContext(), this.getClass());
        restartIntent.setPackage(getPackageName());

        PendingIntent restartPendingIntent = PendingIntent.getService(
                this,
                1,
                restartIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT
                        : PendingIntent.FLAG_ONE_SHOT
        );

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 1000,
                    restartPendingIntent
            );
            Log.d(TAG, "Service restart scheduled.");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            Log.d(TAG, "WakeLock released.");
        }

        healthCheckHandler.removeCallbacks(healthCheckRunnable);

        if (connectivityManager != null && networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
            Log.d(TAG, "NetworkCallback unregistered.");
        }

        if (p2pWebsocket != null) {
            p2pWebsocket.stop();
            p2pWebsocket = null;
        }
        if (wolfxWebsocket != null) {
            wolfxWebsocket.stop();
            wolfxWebsocket = null;
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }

        stopForeground(true);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "WebSocket Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("WebSocket接続をバックグラウンドで維持します。");
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                        : PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("WebSocket Service")
                .setContentText("WebSocket接続を維持しています")
                .setSmallIcon(R.drawable.koiyuresikakuicon)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }
}
