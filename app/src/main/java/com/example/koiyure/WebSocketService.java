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
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.json.JSONObject;

import java.util.Locale;

public class WebSocketService extends Service implements P2PWebsocket.Listener, WolfxWebsocket.Listener {

    private static final String CHANNEL_ID = "WebSocketServiceChannel";
    private static final String TAG = "WebSocketService";
    private static final int SERVICE_NOTIFICATION_ID = 1;

    private P2PWebsocket p2pWebsocket;
    private WolfxWebsocket wolfxWebsocket;
    private ConnectivityManager.NetworkCallback networkCallback;
    private ConnectivityManager connectivityManager;
    private PowerManager.WakeLock wakeLock;

    private P2Pcon p2pConverter; // P2Pconのインスタンスを追加
    private WolfxCon wolfxConverter; // WolfxConのインスタンスを追加 (WolfxConもContextを必要とする想定)


    private static final long HEALTH_CHECK_INTERVAL_MS = 33 * 1000; // 33秒ごと (30秒から少しずらして安定性向上)
    private Handler healthCheckHandler = new Handler(Looper.getMainLooper());
    private Runnable healthCheckRunnable = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "サービスのヘルスチェック実行中...");
            boolean p2pConnected = p2pWebsocket != null && p2pWebsocket.isConnected();
            boolean wolfxConnected = wolfxWebsocket != null && wolfxWebsocket.isConnected();

            if (!p2pConnected) {
                Log.w(TAG, "P2P WebSocketが切断中またはnullです。再起動します...");
                if (p2pWebsocket != null) p2pWebsocket.stop();
                p2pWebsocket = new P2PWebsocket();
                p2pWebsocket.setListener(WebSocketService.this);
                p2pWebsocket.start();
            }

            if (!wolfxConnected) {
                Log.w(TAG, "Wolfx WebSocketが切断中またはnullです。再起動します...");
                if (wolfxWebsocket != null) wolfxWebsocket.stop();
                wolfxWebsocket = new WolfxWebsocket();
                wolfxWebsocket.setListener(WebSocketService.this);
                wolfxWebsocket.start();
            }

            // P2PconとWolfxConの初期化チェックは不要。onCreateで必ず初期化されるため。
            // 強いて言うなら、ここで p2pConverter や wolfxConverter がnullでないことを確認してもよいが、
            // onCreateで初期化されるので通常は問題ない。

            healthCheckHandler.postDelayed(this, HEALTH_CHECK_INTERVAL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "WebSocketService onCreate");

        createNotificationChannel();
        startForeground(SERVICE_NOTIFICATION_ID, createNotification());

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            BatteryManager batteryManager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
            boolean isCharging = batteryManager != null && batteryManager.isCharging();
            if (isCharging) {
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Koiyure::WebSocketServiceWakeLock");
                if (!wakeLock.isHeld()) wakeLock.acquire();
                Log.d(TAG, "WakeLockを取得しました。");
            }
        }

        // P2PconとWolfxConをここで初期化し、サービス自身のContextを渡す
        p2pConverter = new P2Pcon(this);
        // WolfxConもContextを必要とするコンストラクタを持つと仮定して初期化
        wolfxConverter = new WolfxCon(this);


        initializeWebSockets();
        registerNetworkCallback();
        healthCheckHandler.postDelayed(healthCheckRunnable, HEALTH_CHECK_INTERVAL_MS);
    }

    private void initializeWebSockets() {
        if (p2pWebsocket == null) {
            p2pWebsocket = new P2PWebsocket();
            p2pWebsocket.setListener(this);
            p2pWebsocket.start();
            Log.d(TAG, "P2P WebSocketを初期化して接続開始。");
        } else if (!p2pWebsocket.isConnected()) {
            p2pWebsocket.start();
            Log.d(TAG, "P2P WebSocketを再接続。");
        }

        if (wolfxWebsocket == null) {
            wolfxWebsocket = new WolfxWebsocket();
            wolfxWebsocket.setListener(this);
            wolfxWebsocket.start();
            Log.d(TAG, "Wolfx WebSocketを初期化して接続開始。");
        } else if (!wolfxWebsocket.isConnected()) {
            wolfxWebsocket.start();
            Log.d(TAG, "Wolfx WebSocketを再接続。");
        }
    }

    private void registerNetworkCallback() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    Log.d(TAG, "ネットワーク接続を検知。WebSocketを再初期化します。");
                    initializeWebSockets();
                }
            };
            NetworkRequest request = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            connectivityManager.registerNetworkCallback(request, networkCallback);
            Log.d(TAG, "ネットワークコールバックを登録しました。");
        }
    }

    @Override
    public void onP2PMessageReceived(String message) {
        Log.d(TAG, "P2Pメッセージ受信: " + message);
        try {
            // 既にonCreateでインスタンス化されているp2pConverterを使用
            String telopText = p2pConverter.convertToTelop(new JSONObject(message));
            NotiFunc.showNotification(this, "KoiYue", telopText, 1);
            updateWidget(message);
        } catch (Exception e) {
            Log.e(TAG, "P2P JSON変換に失敗しました: " + e.getMessage());
        }
    }

    @Override
    public void onP2PStatusChanged(boolean isConnected) {
        Log.d(TAG, "P2P接続状態: " + (isConnected ? "接続中" : "切断中"));
    }

    @Override
    public void onWolfxMessageReceived(String message) {
        Log.d(TAG, "Wolfxメッセージ受信: " + message);
        try {
            // 既にonCreateでインスタンス化されているwolfxConverterを使用
            String telopText = wolfxConverter.wolfxConverter(new JSONObject(message));
            NotiFunc.showNotification(this, "KoiYue", telopText, 1);
            updateWidget(message);
        } catch (Exception e) {
            Log.e(TAG, "Wolfx JSON変換に失敗しました: " + e.getMessage());
        }
    }

    @Override
    public void onWolfxStatusChanged(boolean isConnected) {
        Log.d(TAG, "Wolfx接続状態: " + (isConnected ? "接続中" : "切断中"));
    }

    private void updateWidget(String data) {
        WidgetProvider.setLastReceivedData(data);
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        ComponentName widget = new ComponentName(this, WidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(widget);
        if (ids.length > 0) {
            WidgetProvider.updateWidget(this, manager, ids);
            Log.d(TAG, "ウィジェットを更新しました。");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "WebSocketService onStartCommand");
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "タスク削除検知。サービスを再起動スケジュールします。");
        scheduleServiceRestart();
        super.onTaskRemoved(rootIntent);
    }

    private void scheduleServiceRestart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.w(TAG, "Android 12以降ではサービス自動再起動をスキップします。");
            return;
        }

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
            Log.d(TAG, "サービス再起動を1秒後にスケジュールしました。(Android 11以下)");
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "WebSocketService onDestroy");
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        healthCheckHandler.removeCallbacks(healthCheckRunnable);
        if (connectivityManager != null && networkCallback != null)
            connectivityManager.unregisterNetworkCallback(networkCallback);
        if (p2pWebsocket != null) p2pWebsocket.stop();
        if (wolfxWebsocket != null) wolfxWebsocket.stop();
        // TTSリソースを解放する

        stopForeground(true);
        super.onDestroy();
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
                    "WebSocketサービス",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification createNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                        : PendingIntent.FLAG_UPDATE_CURRENT
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("WebSocketサービス")
                .setContentText("WebSocket接続を維持しています")
                .setSmallIcon(R.drawable.koiyuresikakuicon)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }
}