package com.example.koiyure;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.util.Log;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class ServiceTile extends TileService {

    private static final String TAG = "ServiceTile";
    private static final String PREFS_NAME = "WidgetPrefs";
    private static final String SERVICE_STATE_KEY = "service_running";
    private static final String INTENTIONAL_STOP_KEY = "intentional_stop";

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        toggleService();
        updateTile();
    }

    private void toggleService() {
        boolean isRunning = getServiceState(this);
        Intent serviceIntent = new Intent(this, WebSocketService.class);

        if (isRunning) {
            Log.d(TAG, "サービスを停止します（意図的な停止）");

            // ==================== サービス完全停止 ====================
            // 意図的な停止フラグを設定
            setIntentionalStopFlag(true);

            stopService(serviceIntent);

            // WorkManager の定期再起動をキャンセル
            WorkManager.getInstance(this).cancelAllWorkByTag("WebSocketRestartWorkerTag");
            WorkManager.getInstance(this).cancelUniqueWork("WebSocketServiceRestart");

            // AlarmManager の再起動アラームをキャンセル（超重要！）
            cancelAlarmRestart();

            // 状態保存
            setServiceState(this, false);

        } else {
            Log.d(TAG, "サービスを起動します");

            // ==================== サービス起動 ====================
            // 意図的な停止フラグをクリア
            setIntentionalStopFlag(false);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }

            // 定期再起動（WorkManager）
            PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                    WebSocketRestartWorker.class,
                    15, TimeUnit.MINUTES
            )
                    .setInitialDelay(30, TimeUnit.SECONDS)
                    .addTag("WebSocketRestartWorkerTag")
                    .build();

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                    "WebSocketServiceRestart",
                    ExistingPeriodicWorkPolicy.UPDATE,
                    workRequest
            );

            setServiceState(this, true);
        }
    }

    // AlarmManager の再起動アラームをキャンセル
    private void cancelAlarmRestart() {
        Intent restartIntent = new Intent(this, WebSocketService.class);
        PendingIntent pendingIntent = PendingIntent.getService(
                this,
                1,
                restartIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_NO_CREATE
        );

        if (pendingIntent != null) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                alarmManager.cancel(pendingIntent);
                Log.d(TAG, "AlarmManagerの再起動アラームをキャンセルしました");
            }
            pendingIntent.cancel();
        }
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;

        boolean isRunning = getServiceState(this);

        if (isRunning) {
            tile.setState(Tile.STATE_ACTIVE);
            tile.setLabel("KoiYue 起動中");
            tile.setContentDescription("サービス実行中。タップで停止");
        } else {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setLabel("KoiYue 停止中");
            tile.setContentDescription("サービス停止中。タップで起動");
        }

        tile.updateTile();
    }

    private boolean getServiceState(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(SERVICE_STATE_KEY, false);
    }

    private void setServiceState(Context context, boolean isRunning) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(SERVICE_STATE_KEY, isRunning).apply();
    }

    private void setIntentionalStopFlag(boolean intentional) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(INTENTIONAL_STOP_KEY, intentional).apply();
        Log.d(TAG, "意図的な停止フラグ: " + intentional);
    }
}