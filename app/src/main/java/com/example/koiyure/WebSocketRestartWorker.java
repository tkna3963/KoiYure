package com.example.koiyure;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/**
 * WorkManagerによって定期的に実行され、
 * WebSocketServiceが稼働しているかを確認し、
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
        Log.d(TAG, "doWork: WebSocketRestartWorkerが実行されました。WebSocketServiceの状態を確認中...");

        // ServiceUtils を使用して WebSocketService が実行中かを確認
        if (!ServiceUtils.isServiceRunning(getApplicationContext(), WebSocketService.class)) {
            Log.w(TAG, "WebSocketServiceは実行されていません。再起動を試みます。");

            Intent serviceIntent = new Intent(getApplicationContext(), WebSocketService.class);
            try {
                // Android O (API 26) 以降では startForegroundService を使用する必要がある
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    getApplicationContext().startForegroundService(serviceIntent);
                } else {
                    getApplicationContext().startService(serviceIntent);
                }
                Log.d(TAG, "WebSocketServiceの再起動コマンドを送信しました。");
                return Result.success(); // サービスの起動を試みたので成功
            } catch (Exception e) {
                Log.e(TAG, "WorkerからWebSocketServiceの起動に失敗しました。エラー: " + e.getMessage(), e);
                return Result.retry(); // サービス起動に失敗した場合はリトライ
            }
        } else {
            Log.d(TAG, "WebSocketServiceは既に実行中です。操作は不要です。");
            return Result.success(); // サービスが既に実行中なので成功
        }
    }
}
