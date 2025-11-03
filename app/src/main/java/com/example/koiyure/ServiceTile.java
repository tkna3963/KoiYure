package com.example.koiyure;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

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
        Intent serviceIntent = new Intent(this, WebSocketService.class);

        if (isRunning) {
            stopService(serviceIntent);
            setServiceState(this, false);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            setServiceState(this, true);
        }
    }

    private void updateTile() {
        Tile tile = getQsTile();
        if (tile == null) return;

        boolean isRunning = getServiceState(this);

        // 状態に応じて見た目を切り替える
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
