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
    private static final int PERSISTENT_NOTIFICATION_ID = 999;
    private static final String PREFS_NAME = "WidgetPrefs";
    private static final String SERVICE_STATE_KEY = "service_running";
    private static final String INTENTIONAL_STOP_KEY = "intentional_stop";

    private P2PWebsocket p2pWebsocket;
    private WolfxWebsocket wolfxWebsocket;
    private ConnectivityManager.NetworkCallback networkCallback;
    private ConnectivityManager connectivityManager;
    private PowerManager.WakeLock wakeLock;

    private P2Pcon p2pConverter;
    private WolfxCon wolfxConverter;
    private Cache cache = Cache.getInstance();

    private static final long HEALTH_CHECK_INTERVAL_MS = 33 * 1000;
    private static final long NOTIFICATION_UPDATE_INTERVAL_MS = 10 * 1000;
    private Handler healthCheckHandler = new Handler(Looper.getMainLooper());
    private Handler notificationUpdateHandler = new Handler(Looper.getMainLooper());

    private long serviceStartTime = 0;
    private int messageCount = 0;
    private boolean intentionalStop = false; // 意図的な停止かどうかのフラグ

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

            updateForegroundNotification();
            healthCheckHandler.postDelayed(this, HEALTH_CHECK_INTERVAL_MS);
        }
    };

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
        intentionalStop = false;

        NotiFunc.createForegroundNotificationChannel(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(SERVICE_NOTIFICATION_ID, buildForegroundNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(SERVICE_NOTIFICATION_ID, buildForegroundNotification());
        }

        setServiceState(true);
        clearIntentionalStopFlag(); // 起動時にフラグをクリア
        updateAllWidgets();

        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Koiyure::WebSocketServiceWakeLock"
            );
            if (wakeLock != null && !wakeLock.isHeld()) {
                wakeLock.acquire(10 * 60 * 60 * 1000L);
                Log.d(TAG, "WakeLockを取得しました。");
            }
        }

        p2pConverter = new P2Pcon(this);
        wolfxConverter = new WolfxCon(this);
        initializeWebSockets();
        registerNetworkCallback();

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
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setOnlyAlertOnce(true);

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
        updateForegroundNotification();

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
        updateForegroundNotification();

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

        // 意図的な停止フラグをクリア（再起動時）
        intentionalStop = false;
        clearIntentionalStopFlag();

        setServiceState(true);
        updateAllWidgets();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(SERVICE_NOTIFICATION_ID, buildForegroundNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(SERVICE_NOTIFICATION_ID, buildForegroundNotification());
        }

        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "タスク削除検知。");

        // 意図的な停止でない場合のみ再起動
        if (!intentionalStop && !getIntentionalStopFlag()) {
            Log.d(TAG, "予期しない終了のため、サービスを再起動スケジュールします。");
            scheduleServiceRestart();

            Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
            restartServiceIntent.setPackage(getPackageName());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                getApplicationContext().startForegroundService(restartServiceIntent);
            } else {
                getApplicationContext().startService(restartServiceIntent);
            }
        } else {
            Log.d(TAG, "意図的な停止のため、再起動しません。");
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
        Log.d(TAG, "WebSocketService onDestroy - intentionalStop: " + intentionalStop);

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

        // WebSocketを完全に停止
        if (p2pWebsocket != null) {
            p2pWebsocket.stop();
            p2pWebsocket = null;
            Log.d(TAG, "P2P WebSocketを停止しました。");
        }
        if (wolfxWebsocket != null) {
            wolfxWebsocket.stop();
            wolfxWebsocket = null;
            Log.d(TAG, "Wolfx WebSocketを停止しました。");
        }

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(PERSISTENT_NOTIFICATION_ID);
        }

        stopForeground(true);

        // 意図的な停止でない場合のみ再起動をスケジュール
        if (!intentionalStop && !getIntentionalStopFlag()) {
            Log.d(TAG, "予期しない終了のため、再起動をスケジュールします。");
            scheduleServiceRestart();
        } else {
            Log.d(TAG, "意図的な停止のため、再起動をスケジュールしません。");
        }

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

    // 意図的な停止フラグを設定
    public void setIntentionalStop() {
        intentionalStop = true;
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(INTENTIONAL_STOP_KEY, true).apply();
        Log.d(TAG, "意図的な停止フラグを設定しました。");
    }

    private boolean getIntentionalStopFlag() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(INTENTIONAL_STOP_KEY, false);
    }

    private void clearIntentionalStopFlag() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(INTENTIONAL_STOP_KEY, false).apply();
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