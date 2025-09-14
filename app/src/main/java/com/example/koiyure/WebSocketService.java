package com.example.koiyure;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;

public class WebSocketService extends Service {

    private static final String CHANNEL_ID = "WebSocketServiceChannel";
    private static final String TAG = "WebSocketService";

    private P2PWebsocket p2pWebsocket;
    private WolfxWebsocket wolfxWebsocket;
    private TextToSpeech tts;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, createNotification());

        // TTSの初期化
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.JAPANESE);
            } else {
                Log.e(TAG, "TTS初期化失敗");
            }
        });

        // WebSocketの初期化と接続
        p2pWebsocket = new P2PWebsocket();
        wolfxWebsocket = new WolfxWebsocket();

        p2pWebsocket.setListener(this::handleMessage);
        wolfxWebsocket.setListener(this::handleMessage);

        p2pWebsocket.start();
        wolfxWebsocket.start();
    }

    private void handleMessage(String message) {
        Log.d(TAG, "受信: " + message);

        // TTSで読み上げ
        if (tts != null) {
            tts.speak(message, TextToSpeech.QUEUE_ADD, null, null);
        }

        // JSONデータをローカルに保存
        saveJsonToFile(message);

        // ウィジェットにデータを反映
        WidgetProvider.setLastReceivedData(message);
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        WidgetProvider.updateWidget(this, appWidgetManager, appWidgetManager.getAppWidgetIds(new ComponentName(this, WidgetProvider.class)));
    }

    private void saveJsonToFile(String json) {
        File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "WebSocketLogs");
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "ディレクトリ作成失敗");
            return;
        }

        File file = new File(dir, "log.json");
        try (FileWriter writer = new FileWriter(file, true)) {
            writer.write(json + "\n");
            Log.d(TAG, "JSONデータを保存: " + file.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "JSON保存失敗", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (p2pWebsocket != null) {
            p2pWebsocket.stop();
        }
        if (wolfxWebsocket != null) {
            wolfxWebsocket.stop();
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
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
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("WebSocket Service")
                .setContentText("WebSocket接続を維持しています")
                .setSmallIcon(R.drawable.koiyuresikakuicon)
                .build();
    }
}