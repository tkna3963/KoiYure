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
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
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

        // TTS初期化
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.JAPANESE);
            } else {
                Log.e(TAG, "TTS初期化失敗");
            }
        });

        // WebSocket初期化
        p2pWebsocket = new P2PWebsocket();
        wolfxWebsocket = new WolfxWebsocket();

        p2pWebsocket.setListener(this::handleMessage);
        wolfxWebsocket.setListener(this::handleMessage);

        p2pWebsocket.start();
        wolfxWebsocket.start();
    }

    private void handleMessage(String message) {
        Log.d(TAG, "受信: " + message);

        // TTS（必要なら有効化）
        // if (tts != null) {
        //     tts.speak(message, TextToSpeech.QUEUE_ADD, null, "msgId");
        // }

        // JSON累積保存
        saveJsonToInternalStorage(message);

        // ウィジェット更新
        WidgetProvider.setLastReceivedData(message);
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        WidgetProvider.updateWidget(
                this,
                appWidgetManager,
                appWidgetManager.getAppWidgetIds(new ComponentName(this, WidgetProvider.class))
        );
    }

    /**
     * JSON配列として内部ストレージに累積保存
     */
    private void saveJsonToInternalStorage(String json) {
        String fileName = "all_received_data.json";
        try {
            JSONArray array;

            // 既存ファイルを読み込む
            try (FileInputStream fis = openFileInput(fileName);
                 InputStreamReader isr = new InputStreamReader(fis);
                 BufferedReader reader = new BufferedReader(isr)) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                if (sb.length() > 0) {
                    array = new JSONArray(sb.toString());
                } else {
                    array = new JSONArray();
                }
            } catch (FileNotFoundException e) {
                // 初回はファイルが存在しないので新規作成
                array = new JSONArray();
            }

            // 新しいデータを追加
            array.put(new JSONObject(json));

            // ファイルに書き戻し
            try (FileOutputStream fos = openFileOutput(fileName, Context.MODE_PRIVATE)) {
                fos.write(array.toString().getBytes());
                Log.d(TAG, "JSONデータを内部ストレージに保存しました: " + fileName);
            }

        } catch (Exception e) {
            Log.e(TAG, "JSONデータ保存エラー", e);
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
        } catch (Exception e) {
            Log.e(TAG, "JSONデータの読み込みに失敗しました", e);
            return null;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // システム再起動後にもソケットを復帰させたいならここで再初期化しても良い
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
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
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
