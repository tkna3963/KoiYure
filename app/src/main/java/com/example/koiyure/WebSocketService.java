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

    private static final long HEALTH_CHECK_INTERVAL_MS = 30 * 1000; // 30秒ごと
    private Handler healthCheckHandler = new Handler(Looper.getMainLooper());
    private Runnable healthCheckRunnable = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "Service health check running...");
            boolean p2pConnected = p2pWebsocket != null && p2pWebsocket.isConnected();
            boolean wolfxConnected = wolfxWebsocket != null && wolfxWebsocket.isConnected();

            if (!p2pConnected) {
                Log.w(TAG, "P2P WebSocket disconnected or null. Restarting P2P...");
                if (p2pWebsocket != null) p2pWebsocket.stop();
                p2pWebsocket = new P2PWebsocket();
                p2pWebsocket.setListener(WebSocketService.this);
                p2pWebsocket.start();
            } else {
                Log.d(TAG, "P2P WebSocket is connected.");
            }

            if (!wolfxConnected) {
                Log.w(TAG, "Wolfx WebSocket disconnected or null. Restarting Wolfx...");
                if (wolfxWebsocket != null) wolfxWebsocket.stop();
                wolfxWebsocket = new WolfxWebsocket();
                wolfxWebsocket.setListener(WebSocketService.this);
                wolfxWebsocket.start();
            } else {
                Log.d(TAG, "Wolfx WebSocket is connected.");
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

        // WakeLockの取得は充電中に限定する
        // サービスが常にバックグラウンドで動き続ける場合は、このWakeLockは過剰になる可能性も考慮する
        // ただし、バックグラウンドでの安定性を重視する目的であれば維持
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

        // WebSocketクライアントを初期化
        initializeWebSockets();

        // ネットワーク状態の監視を登録
        registerNetworkCallback();
        // ヘルスチェックの定期実行を開始
        healthCheckHandler.postDelayed(healthCheckRunnable, HEALTH_CHECK_INTERVAL_MS);
    }

    /**
     * WebSocketクライアントを初期化し、接続を開始します。
     * onCreateとnetworkCallbackから呼び出される可能性があります。
     */
    private void initializeWebSockets() {
        if (p2pWebsocket == null) {
            p2pWebsocket = new P2PWebsocket();
            p2pWebsocket.setListener(this);
            p2pWebsocket.start();
            Log.d(TAG, "P2PWebsocket initialized and started.");
        } else if (!p2pWebsocket.isConnected()) {
            p2pWebsocket.start(); // 接続が切れている場合は再接続を試みる
            Log.d(TAG, "P2PWebsocket re-started (was disconnected).");
        }

        if (wolfxWebsocket == null) {
            wolfxWebsocket = new WolfxWebsocket();
            wolfxWebsocket.setListener(this);
            wolfxWebsocket.start();
            Log.d(TAG, "WolfxWebsocket initialized and started.");
        } else if (!wolfxWebsocket.isConnected()) {
            wolfxWebsocket.start(); // 接続が切れている場合は再接続を試みる
            Log.d(TAG, "WolfxWebsocket re-started (was disconnected).");
        }
    }

    private void registerNetworkCallback() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    Log.d(TAG, "Network available. Checking WebSockets.");
                    // ネットワークが利用可能になったら、WebSocketの再接続を試みる
                    // initializeWebSockets() が内部でisConnected()をチェックして再接続する
                    initializeWebSockets();
                }

                @Override
                public void onLost(Network network) {
                    Log.d(TAG, "Network lost. WebSockets might disconnect.");
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
        Log.d(TAG, "P2P受信 (Service): " + message);
        saveJsonToInternalStorage(message);
        updateWidget(message);
    }

    @Override
    public void onP2PStatusChanged(boolean isConnected) {
        Log.d(TAG, "P2P WebSocket Status (Service): " + (isConnected ? "Connected" : "Disconnected"));
        // 必要に応じて通知を更新するなどの処理を追加
    }

    @Override
    public void onWolfxMessageReceived(String message) {
        Log.d(TAG, "Wolfx受信 (Service): " + message);
        saveJsonToInternalStorage(message);
        updateWidget(message);
    }

    @Override
    public void onWolfxStatusChanged(boolean isConnected) {
        Log.d(TAG, "Wolfx WebSocket Status (Service): " + (isConnected ? "Connected" : "Disconnected"));
        // 必要に応じて通知を更新するなどの処理を追加
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
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
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
        // START_REDELIVER_INTENT: システムがサービスを強制終了した場合、
        // サービスを再作成し、最後にstartService()に渡されたIntentを再配信する。
        // これにより、サービスが意図せず停止した場合の回復力が向上する。
        return START_REDELIVER_INTENT; // ★★★ 変更点 ★★★
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "onTaskRemoved: scheduling restart.");
        // アプリがタスクから削除された場合、AlarmManagerでサービス再起動をスケジュール
        scheduleServiceRestart();
        super.onTaskRemoved(rootIntent);
    }

    /**
     * AlarmManagerを使用して、サービスを短時間後に再起動するようにスケジュールします。
     * これは onTaskRemoved や、サービスがシステムによって強制終了された場合に役立ちます。
     */
    private void scheduleServiceRestart() {
        Intent restartIntent = new Intent(getApplicationContext(), this.getClass());
        // Android O (API 26) 以降では、暗黙的なブロードキャストは制限されるため、
        // 明示的にパッケージ名を設定するのがベストプラクティス
        restartIntent.setPackage(getPackageName());

        PendingIntent restartPendingIntent = PendingIntent.getService(
                this,
                1, // リクエストコード
                restartIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT
                        : PendingIntent.FLAG_ONE_SHOT
        );

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager != null) {
            // ELAPSED_REALTIME_WAKEUP: デバイスがスリープ状態でもアラームを発火させる
            // 1秒後に起動するように設定
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 1000, // 現在時刻から1秒後
                    restartPendingIntent
            );
            Log.d(TAG, "Service restart scheduled via AlarmManager.");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        // WakeLockを解放
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            Log.d(TAG, "WakeLock released.");
        }

        // ヘルスチェックのRunnableを停止
        healthCheckHandler.removeCallbacks(healthCheckRunnable);

        // ネットワークコールバックの登録を解除
        if (connectivityManager != null && networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
            Log.d(TAG, "NetworkCallback unregistered.");
        }

        // WebSocket接続を停止
        if (p2pWebsocket != null) {
            p2pWebsocket.stop();
            p2pWebsocket = null;
        }
        if (wolfxWebsocket != null) {
            wolfxWebsocket.stop();
            wolfxWebsocket = null;
        }
        // TTSリソースを解放
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }

        // フォアグラウンドサービスを停止
        stopForeground(true);

        // サービスがdestroyされたときに、WorkManagerが動いている場合はそちらが再起動を試みる
        // 必要であれば、ここでscheduleServiceRestart()を再度呼び出してAlarmManagerによる再起動も試みるが、
        // onTaskRemoved()以外でのonDestroy()は明示的な停止の場合もあるため注意が必要
        // 現状のWorkManagerとonTaskRemovedのAlarmManagerで十分な堅牢性があるはず
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null; // このサービスはバインドされない
    }

    /**
     * フォアグラウンドサービス通知のためのチャンネルを作成します。
     * Android O (API 26) 以降で必要です。
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "WebSocket Service", // ユーザーに表示されるチャンネル名
                    NotificationManager.IMPORTANCE_LOW // 低優先度で通知音が鳴らないようにする
            );
            channel.setDescription("WebSocket接続をバックグラウンドで維持します。");
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC); // ロック画面での表示設定

            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    /**
     * フォアグラウンドサービスに表示される通知を作成します。
     * ユーザーがタップするとMainActivityが開くように設定します。
     * @return フォアグラウンドサービス通知オブジェクト。
     */
    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0, // リクエストコード
                notificationIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                        : PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("WebSocket Service")
                .setContentText("WebSocket接続を維持しています")
                .setSmallIcon(R.drawable.koiyuresikakuicon) // 通知アイコンはres/drawableに配置してください
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW) // 低優先度
                .setOngoing(true) // ユーザーがスワイプで消せない永続的な通知
                .build();
    }
}