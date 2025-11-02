package com.example.koiyure;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.RemoteViews;

public class WidgetsCon extends AppWidgetProvider {

    private static final String PREFS_NAME = "WidgetPrefs";
    private static final String SERVICE_STATE_KEY = "service_running";
    private static final String ACTION_TOGGLE_SERVICE = "com.example.koiyure.TOGGLE_SERVICE";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int widgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, widgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        if (ACTION_TOGGLE_SERVICE.equals(intent.getAction())) {
            toggleService(context);

            // すべてのウィジェットを更新
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int[] ids = appWidgetManager.getAppWidgetIds(
                    new ComponentName(context, WidgetsCon.class)
            );
            for (int id : ids) {
                updateWidget(context, appWidgetManager, id);
            }
        }
    }

    private void updateWidget(Context context, AppWidgetManager appWidgetManager, int widgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);

        // サービスの状態を取得
        boolean isRunning = getServiceState(context);

        // ボタンのテキストを更新
        views.setTextViewText(R.id.ssbutton,
                isRunning ? "アプリ起動中" : "アプリ停止中");

        // ボタンクリック時のインテントを設定
        Intent intent = new Intent(context, WidgetsCon.class);
        intent.setAction(ACTION_TOGGLE_SERVICE);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                        : PendingIntent.FLAG_UPDATE_CURRENT
        );
        views.setOnClickPendingIntent(R.id.ssbutton, pendingIntent);

        appWidgetManager.updateAppWidget(widgetId, views);
    }

    private void toggleService(Context context) {
        boolean isRunning = getServiceState(context);

        Intent serviceIntent = new Intent(context, WebSocketService.class);

        if (isRunning) {
            // サービスを停止
            context.stopService(serviceIntent);
            setServiceState(context, false);
        } else {
            // サービスを開始
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
            setServiceState(context, true);
        }
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