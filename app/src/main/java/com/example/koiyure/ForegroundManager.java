package com.example.koiyure;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import androidx.core.app.NotificationCompat;

public class ForegroundManager {

    private Context context;
    private static final String CHANNEL_ID = "earthquake_service";
    private static final int NOTIFICATION_ID = 1001;

    public ForegroundManager(Context context) {
        this.context = context;
        createNotificationChannel();
    }

    public void start() {
        Intent serviceIntent = new Intent(context, EarthquakeService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }

    public void stop() {
        Intent serviceIntent = new Intent(context, EarthquakeService.class);
        context.stopService(serviceIntent);
    }

    public boolean isRunning() {
        // ServiceManagerで確認する方法もあるが、簡易的にStaticフラグを使用
        return EarthquakeService.isRunning;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "地震監視サービス",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("24時間地震情報を監視します");

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    public void requestIgnoreBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            String packageName = context.getPackageName();

            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName)) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + packageName));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
        }
    }

    // ========================
    // フォアグラウンドサービス本体
    // ========================
    public static class EarthquakeService extends Service {

        public static boolean isRunning = false;
        private PowerManager.WakeLock wakeLock;
        private P2PWebsocket p2pWebsocket;
        private WolfxWebsocket wolfxWebsocket;

        @Override
        public void onCreate() {
            super.onCreate();
            isRunning = true;

            // WakeLockで画面OFF時も動作継続
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(
                        PowerManager.PARTIAL_WAKE_LOCK,
                        "EarthquakeApp:WakeLock"
                );
                wakeLock.acquire();
            }

            // WebSocket接続開始
            p2pWebsocket = new P2PWebsocket();
            p2pWebsocket.setListener(message -> {
                // サービス内での処理（通知送信など）
                sendNotification("P2P地震情報", message);
            });
            p2pWebsocket.start();

            wolfxWebsocket = new WolfxWebsocket();
            wolfxWebsocket.setListener(message -> {
                sendNotification("Wolfx", message);
            });
            wolfxWebsocket.start();
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            // 通知を表示してフォアグラウンド化
            Notification notification = createNotification();
            startForeground(NOTIFICATION_ID, notification);

            // サービスが強制終了されても自動再起動
            return START_STICKY;
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            isRunning = false;

            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }

            if (p2pWebsocket != null) {
                p2pWebsocket.stop();
            }
            if (wolfxWebsocket != null) {
                wolfxWebsocket.stop();
            }
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        private Notification createNotification() {
            Intent notificationIntent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    this, 0, notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE
            );

            return new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("地震監視中")
                    .setContentText("P2P地震情報とWolfxに接続しています")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentIntent(pendingIntent)
                    .setOngoing(true)
                    .build();
        }

        private void sendNotification(String title, String message) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle(title)
                        .setContentText(message)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .build();

                manager.notify((int) System.currentTimeMillis(), notification);
            }
        }
    }
}