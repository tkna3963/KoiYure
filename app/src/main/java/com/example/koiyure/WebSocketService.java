package com.example.koiyure;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager; // onTaskRemovedで必要になる可能性 (今回は不要)
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
    private ConnectivityManager connectivityManager; // ConnectivityManagerを保持

    // サービスが生きているか、WebSocketが接続されているか定期的に確認するRunnable
    private static final long HEALTH_CHECK_INTERVAL_MS = 30 * 1000; // 30秒ごと
    private Handler healthCheckHandler = new Handler(Looper.getMainLooper());
    private Runnable healthCheckRunnable = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG, "Service health check running...");
            boolean p2pConnected = p2pWebsocket != null && p2pWebsocket.isConnected();
            boolean wolfxConnected = wolfxWebsocket != null && wolfxWebsocket.isConnected();

            if (!p2pConnected) {
                Log.w(TAG, "P2P WebSocket reported disconnected or null. Attempting to restart P2P.");
                if (p2pWebsocket != null) {
                    p2pWebsocket.stop(); // 古いインスタンスを停止し、リソース解放
                }
                p2pWebsocket = new P2PWebsocket(); // 新しいインスタンス
                p2pWebsocket.setListener(WebSocketService.this);
                p2pWebsocket.start(); // 再接続開始
            }

            if (!wolfxConnected) {
                Log.w(TAG, "Wolfx WebSocket reported disconnected or null. Attempting to restart Wolfx.");
                if (wolfxWebsocket != null) {
                    wolfxWebsocket.stop(); // 古いインスタンスを停止し、リソース解放
                }
                wolfxWebsocket = new WolfxWebsocket(); // 新しいインスタンス
                wolfxWebsocket.setListener(WebSocketService.this);
                wolfxWebsocket.start(); // 再接続開始
            }

            // 次のチェックをスケジュール
            healthCheckHandler.postDelayed(this, HEALTH_CHECK_INTERVAL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "WebSocketService onCreate");
        createNotificationChannel();
        startForeground(SERVICE_NOTIFICATION_ID, createNotification());

        // TTSの初期化
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.JAPANESE);
            } else {
                Log.e(TAG, "TTS初期化失敗");
            }
        });

        // WebSocketの初期化と接続 (ここが最初の接続試行)
        p2pWebsocket = new P2PWebsocket();
        p2pWebsocket.setListener(this);
        p2pWebsocket.start();

        wolfxWebsocket = new WolfxWebsocket();
        wolfxWebsocket.setListener(this);
        wolfxWebsocket.start();

        // ネットワーク監視の登録
        registerNetworkCallback();

        // 定期的なヘルスチェックを開始
        healthCheckHandler.postDelayed(healthCheckRunnable, HEALTH_CHECK_INTERVAL_MS);
    }

    private void registerNetworkCallback() {
        connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    Log.d(TAG, "Network is available! Checking WebSockets.");
                    // ネットワークが利用可能になったら、WebSocketの再接続を試みる
                    // WebSocketクラス内の再接続ロジックが自動的に機能するため、
                    // ここでは単にstart()を呼び出すことで、内部のshouldReconnectフラグをtrueにし、
                    // 再接続タイマーをリセットしてすぐに接続を試みさせる。
                    if (p2pWebsocket != null) {
                        Log.d(TAG, "P2P WebSocket detected network available. Calling start() to ensure connection.");
                        p2pWebsocket.start();
                    }
                    if (wolfxWebsocket != null) {
                        Log.d(TAG, "Wolfx WebSocket detected network available. Calling start() to ensure connection.");
                        wolfxWebsocket.start();
                    }
                }

                @Override
                public void onLost(Network network) {
                    Log.d(TAG, "Network lost. WebSockets might disconnect.");
                    // ネットワークが失われたら、WebSocketのlistenerに通知する（実装していれば）
                    // 各WebSocketの内部再接続ロジックが自動的に再接続を試みるはずだが、
                    // 明示的にstop()してからstart()することで、より確実にリソースを解放し再試行させることもできる。
                    // 今回は内部ロジックに任せるため、ここでは特に何もしない。
                }
            };
            // ネットワークリクエストを構築し、インターネット接続が必要であることを示す
            NetworkRequest networkRequest = new NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build();
            connectivityManager.registerNetworkCallback(networkRequest, networkCallback);
            Log.d(TAG, "NetworkCallback registered.");
        }
    }

    // P2PWebsocket.Listenerの実装
    @Override
    public void onP2PMessageReceived(String message) {
        Log.d(TAG, "Service - P2P受信: " + message);
        // TTSで読み上げ (コメントアウト解除で有効化)
        // if (tts != null) { tts.speak(message, TextToSpeech.QUEUE_ADD, null, null); }
        saveJsonToInternalStorage(message);
        updateWidget(message);
    }

    @Override
    public void onP2PStatusChanged(boolean isConnected) {
        Log.d(TAG, "Service - P2P WebSocket Status: " + (isConnected ? "Connected" : "Disconnected"));
        // 必要に応じて接続状態を通知バーなどに表示することも可能
    }

    // WolfxWebsocket.Listenerの実装
    @Override
    public void onWolfxMessageReceived(String message) {
        Log.d(TAG, "Service - Wolfx受信: " + message);
        // TTSで読み上げ (コメントアウト解除で有効化)
        // if (tts != null) { tts.speak(message, TextToSpeech.QUEUE_ADD, null, null); }
        saveJsonToInternalStorage(message);
        updateWidget(message);
    }

    @Override
    public void onWolfxStatusChanged(boolean isConnected) {
        Log.d(TAG, "Service - Wolfx WebSocket Status: " + (isConnected ? "Connected" : "Disconnected"));
        // 必要に応じて接続状態を通知バーなどに表示することも可能
    }

    private void saveJsonToInternalStorage(String json) {
        String fileName = "all_received_data.json"; // すべてのデータを保存するファイル名
        try (FileOutputStream fos = openFileOutput(fileName, Context.MODE_APPEND)) { // Context.MODE_APPENDで追記
            fos.write((json + "\n").getBytes()); // 改行を追加して追記
            Log.d(TAG, "JSONデータを内部ストレージに追記しました: " + fileName);
        } catch (IOException e) {
            Log.e(TAG, "JSONデータの追記に失敗しました", e);
        }
    }

    private String loadJsonFromInternalStorage() {
        String fileName = "all_received_data.json";
        try (FileInputStream fis = openFileInput(fileName);
             InputStreamReader isr = new InputStreamReader(fis);
             BufferedReader reader = new BufferedReader(isr)) {
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
            Log.d(TAG, "JSONデータを内部ストレージから読み込みました: " + fileName);
            return stringBuilder.toString();
        } catch (IOException e) {
            Log.e(TAG, "JSONデータの読み込みに失敗しました", e);
            return null;
        }
    }

    private void updateWidget(String data) {
        // ウィジェットにデータを反映
        WidgetProvider.setLastReceivedData(data);
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        ComponentName widgetComponent = new ComponentName(this, WidgetProvider.class);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(widgetComponent);
        if (appWidgetIds.length > 0) {
            WidgetProvider.updateWidget(this, appWidgetManager, appWidgetIds);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "WebSocketService onStartCommand");
        // Android 8.0 (APIレベル 26) 以降では、サービスが明示的に停止されるかシステムによって強制終了されない限り、
        // フォアグラウンドサービスは実行され続けるため、START_STICKYは推奨されない場合があるが、堅牢性のため維持。
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "onTaskRemoved: Restarting service to ensure continuous operation.");
        // アプリがタスクキルされてもサービスを再起動する
        Intent restartService = new Intent(getApplicationContext(), this.getClass());
        // Android 8.0 (APIレベル 26) 以降では、startService()の代わりにstartForegroundService()を使用
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartService);
        } else {
            startService(restartService);
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "WebSocketService onDestroy");

        // 定期的なヘルスチェックを停止
        healthCheckHandler.removeCallbacks(healthCheckRunnable);

        // ネットワーク監視を解除
        if (connectivityManager != null && networkCallback != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
            Log.d(TAG, "NetworkCallback unregistered.");
        }

        // WebSocketクライアントを停止
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

        // フォアグラウンドサービスを停止 (通知を消す)
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
                    NotificationManager.IMPORTANCE_LOW // ユーザーに邪魔にならない程度の重要度
            );
            channel.setDescription("WebSocket接続をバックグラウンドで維持します。");
            // 通知の可視性設定 (ロック画面での表示など)
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        // 通知タップ時にMainActivityを開くIntent
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setAction(Intent.ACTION_MAIN);
        notificationIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        // PendingIntent.FLAG_IMMUTABLEはAPI 23以降必須
        // PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
        //         Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT);
        // TODO: PendingIntentのFLAGを適切に設定する。今回は簡略化のためPendingIntentを省略。

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("WebSocket Service")
                .setContentText("WebSocket接続を維持しています")
                .setSmallIcon(R.drawable.koiyuresikakuicon) // アプリアイコンなどを指定
                // .setContentIntent(pendingIntent) // 通知をタップした際の挙動
                .setPriority(NotificationCompat.PRIORITY_LOW) // 重要度に合わせて優先度を設定
                .setOngoing(true) // ユーザーがスワイプで消せないようにする
                .build();
    }
}