package com.example.koiyure;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.util.concurrent.TimeUnit;

public class WorkManager {

    private static final String TAG = "WorkManager";
    private static final String WORK_NAME = "earthquake_monitor_work";

    private Context context;
    private androidx.work.WorkManager workManager;

    public WorkManager(Context context) {
        this.context = context;
        this.workManager = androidx.work.WorkManager.getInstance(context);
    }

    public void start(int intervalMinutes) {
        // 最小間隔は15分
        long interval = Math.max(intervalMinutes, 15);

        // 制約設定（ネットワーク必須）
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(false) // バッテリー低下でも実行
                .build();

        // 定期実行ワーク作成
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                EarthquakeWorker.class,
                interval,
                TimeUnit.MINUTES,
                5, // フレックス期間（分）
                TimeUnit.MINUTES
        )
                .setConstraints(constraints)
                .addTag(WORK_NAME)
                .build();

        // ワークをスケジュール（既存があれば置き換え）
        workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
        );

        Log.d(TAG, "WorkManager開始: " + interval + "分間隔");
    }

    public void stop() {
        workManager.cancelUniqueWork(WORK_NAME);
        Log.d(TAG, "WorkManager停止");
    }

    // ========================
    // Worker実装
    // ========================
    public static class EarthquakeWorker extends Worker {

        public EarthquakeWorker(@NonNull Context context,
                                @NonNull WorkerParameters params) {
            super(context, params);
        }

        @NonNull
        @Override
        public Result doWork() {
            Log.d(TAG, "WorkManager実行 - サービス確認");

            Context context = getApplicationContext();

            // Foregroundサービスが動いているか確認
            if (!ForegroundManager.EarthquakeService.isRunning) {
                Log.d(TAG, "サービス停止検出 - 再起動");

                // サービス再起動
                Intent serviceIntent = new Intent(
                        context,
                        ForegroundManager.EarthquakeService.class
                );

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            } else {
                Log.d(TAG, "サービス正常稼働中");
            }

            // WebSocket接続確認（簡易的にPingなど実装可能）
            // ここでは再起動のみ

            return Result.success();
        }
    }
}