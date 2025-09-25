package com.example.koiyure;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements P2PWebsocket.Listener, WolfxWebsocket.Listener {

    private static final String TAG = "MainActivity";
    private WebView webView;
    private P2PWebsocket p2pWebsocket;
    private WolfxWebsocket wolfxWebsocket;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Queue<String> jsQueue = new LinkedList<>();
    private boolean isSending = false;

    // ★★★ 接続状態を保持するフィールドを追加 ★★★
    private boolean isP2PConnected = false;
    private boolean isWolfxConnected = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);

        // ローカルストレージやCookieを有効化
        webSettings.setDomStorageEnabled(true); // localStorage / sessionStorage
        webSettings.setDatabaseEnabled(true);   // Web SQL / IndexedDB
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        } else {
            CookieManager.getInstance().setAcceptCookie(true);
        }

        // WebViewのデバッグを有効にする (開発時のみ)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "WebView finished loading: " + url);
                // ページロード完了時に現在の接続状態をWebViewに通知する
                sendMessageToWebView("onP2PStatusChange", String.valueOf(isP2PConnected));
                sendMessageToWebView("onWolfxStatusChange", String.valueOf(isWolfxConnected));
            }
        });
        webView.loadUrl("file:///android_asset/MainIndex.html");

        // JavaScriptインターフェースを追加
        webView.addJavascriptInterface(new WebAppInterface(this), "Android");

        // WebSocketの初期化
        p2pWebsocket = new P2PWebsocket();
        p2pWebsocket.setListener(this);
        p2pWebsocket.start();

        wolfxWebsocket = new WolfxWebsocket();
        wolfxWebsocket.setListener(this);
        wolfxWebsocket.start();

        // WebSocketServiceの起動
        Intent serviceIntent = new Intent(this, WebSocketService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        // バッテリー最適化の無視をリクエスト
        requestIgnoreBatteryOptimizations();

        // WorkManagerでWebSocketServiceの監視をスケジュール
        scheduleWebSocketRestartWorker();
    }

    /**
     * WebSocketServiceが常に稼働していることを確認するためのWorkManagerをスケジュールします。
     */
    private void scheduleWebSocketRestartWorker() {
        Log.d(TAG, "Scheduling WebSocketRestartWorker...");
        // 最小実行間隔はPeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS (15分)
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                WebSocketRestartWorker.class,
                15, // 15分ごとにチェック
                TimeUnit.MINUTES
        )
                // アプリ起動後、初回は少し遅延させてから実行する
                .setInitialDelay(30, TimeUnit.SECONDS)
                .addTag("WebSocketRestartWorkerTag") // タグを追加してログなどで識別しやすくする
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "WebSocketServiceRestart", // このWorkを一意に識別する名前
                ExistingPeriodicWorkPolicy.UPDATE, // 既に同名のWorkが存在すれば更新
                workRequest
        );
        Log.d(TAG, "WebSocketRestartWorker scheduled successfully.");
    }

    private void requestIgnoreBatteryOptimizations() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            new AlertDialog.Builder(this)
                    .setTitle("バックグラウンド実行の許可")
                    .setMessage("このアプリをバックグラウンドで安定して動作させるため、バッテリー最適化を無効にしてください。\n「はい」を選択後、設定画面で本アプリの項目を「最適化しない」に設定してください。")
                    .setPositiveButton("はい", (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        try {
                            startActivity(intent);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to open battery optimization settings", e);
                            Toast.makeText(MainActivity.this, "バッテリー最適化の設定画面を開けませんでした。手動で設定してください。", Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton("いいえ", (dialog, which) -> {
                        Toast.makeText(MainActivity.this, "バッテリー最適化が有効な場合、アプリは予期せず停止する可能性があります。", Toast.LENGTH_LONG).show();
                    })
                    .show();
        }
    }

    @Override
    public void onP2PMessageReceived(String message) {
        Log.d(TAG, "P2P受信 (MainActivity): " + message);
        sendMessageToWebView("onP2PMessage", message);
    }

    @Override
    public void onP2PStatusChanged(boolean isConnected) {
        Log.d(TAG, "P2P WebSocket Status (MainActivity): " + (isConnected ? "Connected" : "Disconnected"));
        this.isP2PConnected = isConnected; // ★★★ 状態を更新 ★★★
        sendMessageToWebView("onP2PStatusChange", String.valueOf(isConnected));
    }

    @Override
    public void onWolfxMessageReceived(String message) {
        Log.d(TAG, "Wolfx受信 (MainActivity): " + message);
        sendMessageToWebView("onWolfxMessage", message);
    }

    @Override
    public void onWolfxStatusChanged(boolean isConnected) {
        Log.d(TAG, "Wolfx WebSocket Status (MainActivity): " + (isConnected ? "Connected" : "Disconnected"));
        this.isWolfxConnected = isConnected; // ★★★ 状態を更新 ★★★
        sendMessageToWebView("onWolfxStatusChange", String.valueOf(isConnected));
    }

    private void sendMessageToWebView(String jsFunction, String message) {
        if (message == null) message = "";
        try {
            // JavaScriptに渡す文字列はエスケープする必要がある
            String escapedMessage = JSONObject.quote(message);
            final String jsCode = "javascript:" + jsFunction + "(" + escapedMessage + ");";
            Log.d(TAG, "Queueing JS for WebView: " + jsCode);

            synchronized (jsQueue) {
                jsQueue.add(jsCode);
            }
            processQueue();
        } catch (Exception e) {
            Log.e(TAG, "Failed to queue JS message for WebView", e);
        }
    }

    private void processQueue() {
        if (isSending) return; // 既に送信中なら処理しない

        String js;
        synchronized (jsQueue) {
            js = jsQueue.poll(); // キューから最初の要素を取り出す
        }

        if (js != null) {
            isSending = true; // 送信中フラグを立てる
            final String finalJs = js;
            mainHandler.post(() -> {
                if (webView != null) {
                    // evaluateJavascriptはAndroid 4.4 (API 19) 以降で利用可能
                    webView.evaluateJavascript(finalJs, value -> {
                        isSending = false; // 送信完了
                        processQueue(); // 次の要素を処理
                    });
                } else {
                    Log.w(TAG, "WebView is null, cannot execute JS: " + finalJs);
                    isSending = false; // WebViewがnullでもフラグをリセット
                    processQueue(); // 次の要素を処理
                }
            });
        } else {
            isSending = false; // キューが空になったらフラグをリセット
        }
    }

    private String loadJsonFromInternalStorage() {
        String fileName = "all_received_data.json";
        try (FileInputStream fis = openFileInput(fileName);
             InputStreamReader isr = new InputStreamReader(fis);
             BufferedReader reader = new BufferedReader(isr)) {
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
            Log.d(TAG, "JSONデータを内部ストレージから読み込みました: " + fileName);
            return stringBuilder.toString();
        } catch (IOException e) {
            Log.e(TAG, "JSONデータの読み込みに失敗しました", e);
            return null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "MainActivity onDestroy");

        // WorkManagerはMainActivityがdestroyされても動作を継続するため、ここでは停止しない

        if (p2pWebsocket != null) {
            p2pWebsocket.stop();
            p2pWebsocket = null;
        }
        if (wolfxWebsocket != null) {
            wolfxWebsocket.stop();
            wolfxWebsocket = null;
        }
        if (webView != null) {
            webView.destroy(); // WebViewを破棄してメモリリークを防ぐ
            webView = null;
        }
        // Handlerのコールバックを全て削除
        mainHandler.removeCallbacksAndMessages(null);
    }

    /**
     * WebViewからJavaScript経由で呼び出されるインターフェース。
     */
    public class WebAppInterface {
        MainActivity mActivity;

        WebAppInterface(MainActivity activity) {
            mActivity = activity;
        }

        @android.webkit.JavascriptInterface
        public String getLocalData() {
            Log.d(TAG, "getLocalData() called from WebView.");
            // メインスレッドで実行する必要がある場合は、Handler.post()を使う
            // しかし、ここではファイル読み込みなので特に問題なし
            String data = mActivity.loadJsonFromInternalStorage();
            return data != null ? data : "データがありません";
        }

        // ★★★ P2P WebSocketの接続状態を取得するメソッドを追加 ★★★
        @android.webkit.JavascriptInterface
        public boolean isP2PWebSocketConnected() {
            Log.d(TAG, "isP2PWebSocketConnected() called from WebView. Status: " + mActivity.isP2PConnected);
            return mActivity.isP2PConnected;
        }

        // ★★★ Wolfx WebSocketの接続状態を取得するメソッドを追加 ★★★
        @android.webkit.JavascriptInterface
        public boolean isWolfxWebSocketConnected() {
            Log.d(TAG, "isWolfxWebSocketConnected() called from WebView. Status: " + mActivity.isWolfxConnected);
            return mActivity.isWolfxConnected;
        }
    }
}