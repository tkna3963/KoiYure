package com.example.koiyure;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class WidgetsCon extends AppWidgetProvider {

    private static final String TAG = "WidgetsCon";
    private static final String PREFS_NAME = "WidgetPrefs";
    private static final String SERVICE_STATE_KEY = "service_running";
    private static final String INTENTIONAL_STOP_KEY = "intentional_stop";
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

        boolean isRunning = getServiceState(context);

        views.setTextViewText(R.id.ssbutton,
                isRunning ? "KoiYue 起動中" : "KoiYue 停止中");

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
            Log.d(TAG, "サービスを停止します（意図的な停止）");

            // ==================== 完全停止 ====================
            // 意図的な停止フラグを設定
            setIntentionalStopFlag(context, true);

            context.stopService(serviceIntent);

            // WorkManager 停止
            WorkManager.getInstance(context).cancelAllWorkByTag("WebSocketRestartWorkerTag");
            WorkManager.getInstance(context).cancelUniqueWork("WebSocketServiceRestart");

            // AlarmManager 再起動アラームキャンセル（超重要！）
            cancelAlarmRestart(context);

            setServiceState(context, false);

        } else {
            Log.d(TAG, "サービスを起動します");

            // ==================== 起動 ====================
            // 意図的な停止フラグをクリア
            setIntentionalStopFlag(context, false);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }

            // WorkManager 再登録
            PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                    WebSocketRestartWorker.class,
                    15, TimeUnit.MINUTES
            )
                    .setInitialDelay(30, TimeUnit.SECONDS)
                    .addTag("WebSocketRestartWorkerTag")
                    .build();

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "WebSocketServiceRestart",
                    ExistingPeriodicWorkPolicy.UPDATE,
                    workRequest
            );

            setServiceState(context, true);
        }
    }

    // AlarmManager の再起動アラームをキャンセル
    private void cancelAlarmRestart(Context context) {
        Intent restartIntent = new Intent(context, WebSocketService.class);
        PendingIntent pendingIntent = PendingIntent.getService(
                context,
                1,
                restartIntent,
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        ? PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
                        : PendingIntent.FLAG_NO_CREATE
        );

        if (pendingIntent != null) {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                alarmManager.cancel(pendingIntent);
                Log.d(TAG, "AlarmManagerの再起動アラームをキャンセルしました");
            }
            pendingIntent.cancel();
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

    private void setIntentionalStopFlag(Context context, boolean intentional) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(INTENTIONAL_STOP_KEY, intentional).apply();
        Log.d(TAG, "意図的な停止フラグ: " + intentional);
    }
}