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
import android.content.SharedPreferences;
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

    private static final String TAG = "WebSocketService";
    private static final int SERVICE_NOTIFICATION_ID = 1;
    private static final int PERSISTENT_NOTIFICATION_ID = 999; // 追加の永続通知
    private static final String PREFS_NAME = "WidgetPrefs";
    private static final String SERVICE_STATE_KEY = "service_running";

    private P2PWebsocket p2pWebsocket;
    private WolfxWebsocket wolfxWebsocket;
    private ConnectivityManager.NetworkCallback networkCallback;
    private ConnectivityManager connectivityManager;
    private PowerManager.WakeLock wakeLock;

    private P2Pcon p2pConverter;
    private WolfxCon wolfxConverter;
    private Cache cache = Cache.getInstance();

    private static final long HEALTH_CHECK_INTERVAL_MS = 33 * 1000;
    private static final long NOTIFICATION_UPDATE_INTERVAL_MS = 10 * 1000; // 10秒ごとに通知更新
    private Handler healthCheckHandler = new Handler(Looper.getMainLooper());
    private Handler notificationUpdateHandler = new Handler(Looper.getMainLooper());

    private long serviceStartTime = 0;
    private int messageCount = 0;

    private Runnable healthCheckRunnable = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "サービスのヘルスチェック実行中...");
            boolean p2pConnected = p2pWebsocket != null && p2pWebsocket.isConnected();
            boolean wolfxConnected = wolfxWebsocket != null && wolfxWebsocket.isConnected();

            if (!p2pConnected) {
                Log.w(TAG, "P2P WebSocketが切断中またはnullです。再初期化・再接続します...");
                if (p2pWebsocket != null) p2pWebsocket.stop();
                p2pWebsocket = new P2PWebsocket();
                p2pWebsocket.setListener(WebSocketService.this);
                p2pWebsocket.start();
            }

            if (!wolfxConnected) {
                Log.w(TAG, "Wolfx WebSocketが切断中またはnullです。再初期化・再接続します...");
                if (wolfxWebsocket != null) wolfxWebsocket.stop();
                wolfxWebsocket = new WolfxWebsocket();
                wolfxWebsocket.setListener(WebSocketService.this);
                wolfxWebsocket.start();
            }

            // 通知を更新してシステムにサービスがアクティブであることを示す
            updateForegroundNotification();

            healthCheckHandler.postDelayed(this, HEALTH_CHECK_INTERVAL_MS);
        }
    };

    // 定期的に通知を更新してプロセスキルを防ぐ
    private Runnable notificationUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            updateForegroundNotification();
            notificationUpdateHandler.postDelayed(this, NOTIFICATION_UPDATE_INTERVAL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "WebSocketService onCreate");

        serviceStartTime = System.currentTimeMillis();
        messageCount = 0;

        NotiFunc.createForegroundNotificationChannel(this);

        // 最高優先度でフォアグラウンドサービスを開始
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(SERVICE_NOTIFICATION_ID, buildForegroundNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(SERVICE_NOTIFICATION_ID, buildForegroundNotification());
        }


        setServiceState(true);
        updateAllWidgets();

        // WakeLockの取得（常に取得するように変更）
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Koiyure::WebSocketServiceWakeLock"
            );
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(10 * 60 * 60 * 1000L); // 10時間のタイムアウト
                Log.d(TAG, "WakeLockを取得しました。");
            }
        }

        p2pConverter = new P2Pcon(this);
        wolfxConverter = new WolfxCon(this);
        initializeWebSockets();
        registerNetworkCallback();

        // ヘルスチェックと通知更新を開始
        healthCheckHandler.postDelayed(healthCheckRunnable, HEALTH_CHECK_INTERVAL_MS);
        notificationUpdateHandler.postDelayed(notificationUpdateRunnable, NOTIFICATION_UPDATE_INTERVAL_MS);
    }

    private Notification buildForegroundNotification() {
        long uptime = System.currentTimeMillis() - serviceStartTime;
        String uptimeStr = formatUptime(uptime);

        String p2pStatus = (p2pWebsocket != null && p2pWebsocket.isConnected()) ? "接続中" : "切断中";
        String wolfxStatus = (wolfxWebsocket != null && wolfxWebsocket.isConnected()) ? "接続中" : "切断中";

        String message = String.format(Locale.getDefault(),
                "稼働時間: %s | メッセージ: %d件\nP2P: %s | Wolfx: %s",
                uptimeStr, messageCount, p2pStatus, wolfxStatus
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "websocket_service_channel")
                .setSmallIcon(R.drawable.koisifavicon)
                .setContentTitle("KoiYue サービス実行中")
                .setContentText("地震情報を監視しています")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setOngoing(true)
                .setAutoCancel(false)
                .setPriority(NotificationCompat.PRIORITY_MAX) // 最高優先度
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setShowWhen(true)
                .setUsesChronometer(true) // クロノメーター表示
                .setOnlyAlertOnce(true); // 通知音は最初だけ

        // Android 12以降: フォアグラウンドサービスであることを明示
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
        }

        return builder.build();
    }
    private void updateForegroundNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(SERVICE_NOTIFICATION_ID, buildForegroundNotification());
        }
    }

    private String formatUptime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return String.format(Locale.getDefault(), "%d日%d時間", days, hours % 24);
        } else if (hours > 0) {
            return String.format(Locale.getDefault(), "%d時間%d分", hours, minutes % 60);
        } else {
            return String.format(Locale.getDefault(), "%d分", minutes);
        }
    }

    private void initializeWebSockets() {
        Log.d(TAG, "WebSocketの初期化と接続チェックを開始します。");

        if (p2pWebsocket == null || !p2pWebsocket.isConnected()) {
            if (p2pWebsocket != null) p2pWebsocket.stop();
            p2pWebsocket = new P2PWebsocket();
            p2pWebsocket.setListener(this);
            p2pWebsocket.start();
            Log.d(TAG, "P2P WebSocketを初期化し、接続を開始しました。");
        }

        if (wolfxWebsocket == null || !wolfxWebsocket.isConnected()) {
            if (wolfxWebsocket != null) wolfxWebsocket.stop();
            wolfxWebsocket = new WolfxWebsocket();
            wolfxWebsocket.setListener(this);
            wolfxWebsocket.start();
            Log.d(TAG, "Wolfx WebSocketを初期化し、接続を開始しました。");
        }
    }

    private void registerNetworkCallback() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    super.onAvailable(network);
                    Log.d(TAG, "ネットワーク接続を検知しました。WebSocketを再初期化します。");
                    initializeWebSockets();
                }

                @Override
                public void onLost(Network network) {
                    super.onLost(network);
                    Log.w(TAG, "ネットワーク接続が失われました。");
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
        messageCount++;
        updateForegroundNotification(); // メッセージ受信時に通知を更新

        try {
            cache.add("P2P", message);
            String telopText = p2pConverter.convertToTelop(new JSONObject(message));
            NotiFunc.showNotification(this, "KoiYue (P2P)", telopText, 101);
        } catch (Exception e) {
            Log.e(TAG, "P2P JSON変換に失敗しました: " + e.getMessage(), e);
        }
    }

    @Override
    public void onP2PStatusChanged(boolean isConnected) {
        String status = isConnected ? "接続中" : "切断中";
        Log.d(TAG, "P2P接続状態: " + status);
        updateForegroundNotification();
    }

    @Override
    public void onWolfxMessageReceived(String message) {
        Log.d(TAG, "Wolfxメッセージ受信: " + message);
        messageCount++;
        updateForegroundNotification(); // メッセージ受信時に通知を更新

        try {
            cache.add("Wolfx", message);
            String telopText = wolfxConverter.wolfxConverter(new JSONObject(message));
            NotiFunc.showNotification(this, "KoiYue (Wolfx)", telopText, 201);
        } catch (Exception e) {
            Log.e(TAG, "Wolfx JSON変換に失敗しました: " + e.getMessage(), e);
        }
    }

    @Override
    public void onWolfxStatusChanged(boolean isConnected) {
        String status = isConnected ? "接続中" : "切断中";
        Log.d(TAG, "Wolfx接続状態: " + status);
        updateForegroundNotification();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "WebSocketService onStartCommand");
        setServiceState(true);
        updateAllWidgets();

        // フォアグラウンド通知を確実に表示
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(SERVICE_NOTIFICATION_ID, buildForegroundNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(SERVICE_NOTIFICATION_ID, buildForegroundNotification());
        }

        return START_STICKY; // プロセスキル後に自動再起動
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "タスク削除検知。サービスを再起動スケジュールします。");

        // Android 12以降でも可能な限り再起動を試みる
        scheduleServiceRestart();

        // サービスを再起動（これにより START_STICKY が効果を発揮）
        Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
        restartServiceIntent.setPackage(getPackageName());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getApplicationContext().startForegroundService(restartServiceIntent);
        } else {
            getApplicationContext().startService(restartServiceIntent);
        }

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
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Android 12以降でも可能な範囲で設定
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(
                                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                                SystemClock.elapsedRealtime() + 3000,
                                restartPendingIntent
                        );
                        Log.d(TAG, "サービス再起動を3秒後にスケジュールしました。(Android 12+)");
                    } else {
                        Log.w(TAG, "正確なアラームの権限がありません。");
                    }
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            SystemClock.elapsedRealtime() + 1000,
                            restartPendingIntent
                    );
                    Log.d(TAG, "サービス再起動を1秒後にスケジュールしました。");
                }
            } catch (Exception e) {
                Log.e(TAG, "アラームスケジュールに失敗: " + e.getMessage());
            }
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "WebSocketService onDestroy");

        setServiceState(false);
        updateAllWidgets();

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            Log.d(TAG, "WakeLockを解放しました。");
        }

        healthCheckHandler.removeCallbacks(healthCheckRunnable);
        notificationUpdateHandler.removeCallbacks(notificationUpdateRunnable);

        if (connectivityManager != null && networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
            Log.d(TAG, "ネットワークコールバックを解除しました。");
        }

        if (p2pWebsocket != null) {
            p2pWebsocket.stop();
            Log.d(TAG, "P2P WebSocketを停止しました。");
        }
        if (wolfxWebsocket != null) {
            wolfxWebsocket.stop();
            Log.d(TAG, "Wolfx WebSocketを停止しました。");
        }

        // 追加の永続通知を削除
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(PERSISTENT_NOTIFICATION_ID);
        }

        stopForeground(true);

        // サービスの再起動を試みる
        scheduleServiceRestart();

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void setServiceState(boolean isRunning) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(SERVICE_STATE_KEY, isRunning).apply();
        Log.d(TAG, "サービス状態を保存: " + (isRunning ? "実行中" : "停止中"));
    }

    private void updateAllWidgets() {
        Intent intent = new Intent(this, WidgetsCon.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        int[] ids = appWidgetManager.getAppWidgetIds(new ComponentName(this, WidgetsCon.class));
        if (ids != null && ids.length > 0) {
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            sendBroadcast(intent);
            Log.d(TAG, String.format(Locale.getDefault(), "%d個のウィジェットを更新するブロードキャストを送信しました。", ids.length));
        }
    }
}