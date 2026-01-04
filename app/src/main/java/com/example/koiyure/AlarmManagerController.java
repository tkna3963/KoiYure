package com.example.koiyure;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class AlarmManagerController {

    private static final String TAG = "AlarmScheduler";
    private static final int REQUEST_CODE = 2001;

    private Context context;
    private AlarmManager alarmManager;

    public AlarmManagerController(Context context) {
        this.context = context.getApplicationContext();
        this.alarmManager =
                (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    // ========================
    // アラーム開始
    // ========================
    public void start(int intervalMinutes) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        long intervalMillis = intervalMinutes * 60 * 1000L;
        long triggerTime = System.currentTimeMillis() + intervalMillis;

        if (alarmManager == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                );
            } else {
                alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                );
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
            );
        } else {
            alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
            );
        }

        Log.d(TAG, "アラーム設定: " + intervalMinutes + "分後");
    }

    // ========================
    // アラーム停止
    // ========================
    public void stop() {
        Intent intent = new Intent(context, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
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
            Log.d(TAG, "アラーム受信 → サービス再起動");

            // Foregroundサービス起動
            Intent serviceIntent =
                    new Intent(context, ForegroundManager.EarthquakeService.class);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }

            // 次のアラームを再設定（15分後）
            AlarmManagerController scheduler = new AlarmManagerController(context);
            scheduler.start(15);
        }
    }
}
