package com.example.koiyure;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * WorkManagerによって定期的に実行され、WebSocketServiceが稼働しているかを確認し、
 * 稼働していなければ再起動を試みるWorker。
 */
public class WebSocketRestartWorker extends Worker {

    private static final String TAG = "WebSocketRestartWorker";

    public WebSocketRestartWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "doWork: WebSocketRestartWorker triggered. Checking WebSocketService status...");

        // ServiceUtils を使用して WebSocketService が実行中かを確認
        if (!ServiceUtils.isServiceRunning(getApplicationContext(), WebSocketService.class)) {
            Log.w(TAG, "WebSocketService is NOT running. Attempting to restart it.");

            Intent serviceIntent = new Intent(getApplicationContext(), WebSocketService.class);
            try {
                // Android O (API 26) 以降では startForegroundService を使用する必要がある
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    getApplicationContext().startForegroundService(serviceIntent);
                } else {
                    getApplicationContext().startService(serviceIntent);
                }
                Log.d(TAG, "WebSocketService restart command sent from Worker.");
                return Result.success(); // サービスの起動を試みたので成功
            } catch (Exception e) {
                Log.e(TAG, "Failed to start WebSocketService from worker. Error: " + e.getMessage(), e);
                // サービス起動に失敗した場合はリトライを検討
                return Result.retry();
            }
        } else {
            Log.d(TAG, "WebSocketService is already running. No action needed.");
            return Result.success(); // サービスが既に実行中なので成功
        }
    }
}