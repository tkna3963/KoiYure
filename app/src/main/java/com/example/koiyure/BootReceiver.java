package com.example.koiyure;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

/**
 * 端末再起動時にサービスを自動開始
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "端末起動検出 - サービス自動開始");

            // フォアグラウンドサービス起動
            Intent serviceIntent = new Intent(
                    context,
                    ForegroundManager.EarthquakeService.class
            );

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }

            // AlarmManager設定
            AlarmManagerController alarmmanagercontroller = new AlarmManagerController(context);
            alarmmanagercontroller.start(15);

            // WorkManager設定
            WorkManager workManager = new WorkManager(context);
            workManager.start(15);

            Log.d(TAG, "すべてのサービスを起動しました");
        }
    }
}