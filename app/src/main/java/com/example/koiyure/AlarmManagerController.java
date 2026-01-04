package com.example.koiyure;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class AlarmManager {

    private static final String TAG = "AlarmManager";
    private static final int REQUEST_CODE = 2001;

    private Context context;
    private android.app.AlarmManager alarmManager;

    public AlarmManager(Context context) {
        this.context = context;
        this.alarmManager = (android.app.AlarmManager)
                context.getSystemService(Context.ALARM_SERVICE);
    }

    public void start(int intervalMinutes) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        long intervalMillis = intervalMinutes * 60 * 1000L;
        long triggerTime = System.currentTimeMillis() + intervalMillis;

        if (alarmManager != null) {
            // Android 12以降は正確なアラームに権限必要
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                            android.app.AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            pendingIntent
                    );
                } else {
                    // 権限がない場合は非正確なアラーム
                    alarmManager.setAndAllowWhileIdle(
                            android.app.AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            pendingIntent
                    );
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                        android.app.AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                );
            } else {
                alarmManager.setExact(
                        android.app.AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                );
            }
            Log.d(TAG, "アラーム設定: " + intervalMinutes + "分間隔");
        }
    }

    public void stop() {
        Intent intent = new Intent(context, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
            Log.d(TAG, "アラームキャンセル");
        }
    }

    // ========================
    // BroadcastReceiver
    // ========================
    public static class AlarmReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(TAG, "アラーム受信 - サービス再起動");

            // Foregroundサービスを再起動
            Intent serviceIntent = new Intent(context, ForegroundManager.EarthquakeService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }

            // 次のアラームを再設定
            AlarmManager manager = new AlarmManager(context);
            manager.start(15); // 15分後に再度
        }
    }
}