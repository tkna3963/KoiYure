package com.example.koiyure;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class ServiceTile extends TileService {

    private static final String PREFS_NAME = "WidgetPrefs";
    private static final String SERVICE_STATE_KEY = "service_running";

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
        Intent WebSocketServiceserviceIntent = new Intent(this, WebSocketService.class);

        if (isRunning) {
            // ---- サービスを完全停止 ----
            stopService(WebSocketServiceserviceIntent);
            WorkManager.getInstance(this).cancelAllWorkByTag("WebSocketRestartWorkerTag");
            WorkManager.getInstance(this).cancelUniqueWork("WebSocketServiceRestart");
            setServiceState(this, false);
        } else {
            // ---- サービスを起動 ----
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(WebSocketServiceserviceIntent);
            } else {
                startService(WebSocketServiceserviceIntent);
            }

            // ---- 再起動Workerを登録 ----
            PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                    WebSocketRestartWorker.class,
                    15,
                    TimeUnit.MINUTES
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

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;

        boolean isRunning = getServiceState(this);

        if (isRunning) {
            tile.setState(Tile.STATE_ACTIVE);
            tile.setLabel("アプリ起動中");
        } else {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setLabel("アプリ停止中");
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
}
