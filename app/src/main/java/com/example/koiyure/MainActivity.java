package com.example.koiyure;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        } else {
            CookieManager.getInstance().setAcceptCookie(true);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "WebViewのページ読み込み完了: " + url);
                sendMessageToWebView("onP2PStatusChange", String.valueOf(isP2PConnected));
                sendMessageToWebView("onWolfxStatusChange", String.valueOf(isWolfxConnected));
            }
        });
        webView.loadUrl("file:///android_asset/MainIndex.html");
        webView.addJavascriptInterface(new WebAppInterface(this), "Android");

        // ----------------------------------------
        // インストール時のみ FCM トークン送信
        // ----------------------------------------
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        boolean isFcmSent = prefs.getBoolean("fcm_token_sent", false);

        if (!isFcmSent) {
            MyFirebaseMessagingService fcmService = new MyFirebaseMessagingService();
            fcmService.getTokenAndSendToDatabase();

            prefs.edit().putBoolean("fcm_token_sent", true).apply();
            Log.d(TAG, "FCMトークン送信済みフラグを保存しました。");
        }

        // ----------------------------------------
        // P2P WebSocket
        // ----------------------------------------
        p2pWebsocket = new P2PWebsocket();
        p2pWebsocket.setListener(this);
        p2pWebsocket.start();
        Log.d(TAG, "P2P WebSocketを起動しました。");

        // Wolfx WebSocket
        wolfxWebsocket = new WolfxWebsocket();
        wolfxWebsocket.setListener(this);
        wolfxWebsocket.start();
        Log.d(TAG, "Wolfx WebSocketを起動しました。");

        Intent serviceIntent = new Intent(this, WebSocketService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
            Log.d(TAG, "WebSocketServiceをフォアグラウンドサービスとして起動しました。");
        } else {
            startService(serviceIntent);
            Log.d(TAG, "WebSocketServiceをバックグラウンドサービスとして起動しました。");
        }

        requestIgnoreBatteryOptimizations();
        scheduleWebSocketRestartWorker();
    }

    // ------------------------------
    // Listener関連の実装
    // ------------------------------
    @Override
    public void onP2PMessageReceived(String message) {
        Log.d(TAG, "P2Pメッセージを受信しました: " + message);
        sendMessageToWebView("onP2PMessage", message);
    }

    @Override
    public void onP2PStatusChanged(boolean isConnected) {
        Log.d(TAG, "P2P接続状態が変更されました: " + (isConnected ? "接続中" : "未接続"));
        this.isP2PConnected = isConnected;
        sendMessageToWebView("onP2PStatusChange", String.valueOf(isConnected));
    }

    @Override
    public void onWolfxMessageReceived(String message) {
        Log.d(TAG, "Wolfxメッセージを受信しました: " + message);
        sendMessageToWebView("onWolfxMessage", message);
    }

    @Override
    public void onWolfxStatusChanged(boolean isConnected) {
        Log.d(TAG, "Wolfx接続状態が変更されました: " + (isConnected ? "接続中" : "未接続"));
        this.isWolfxConnected = isConnected;
        sendMessageToWebView("onWolfxStatusChange", String.valueOf(isConnected));
    }

    // ------------------------------
    // WebView用
    // ------------------------------
    private void sendMessageToWebView(String jsFunction, String message) {
        if (message == null) message = "";
        try {
            String escapedMessage = JSONObject.quote(message);
            final String jsCode = "javascript:" + jsFunction + "(" + escapedMessage + ");";
            Log.d(TAG, "WebViewに送信するJSをキューに追加: " + jsCode);

            synchronized (jsQueue) {
                jsQueue.add(jsCode);
            }
            processQueue();
        } catch (Exception e) {
            Log.e(TAG, "WebViewに送信するJSのキュー追加に失敗しました。", e);
        }
    }

    private void processQueue() {
        if (isSending) return;

        String js;
        synchronized (jsQueue) {
            js = jsQueue.poll();
        }

        if (js != null) {
            isSending = true;
            final String finalJs = js;
            mainHandler.post(() -> {
                if (webView != null) {
                    webView.evaluateJavascript(finalJs, value -> {
                        isSending = false;
                        processQueue();
                    });
                } else {
                    Log.w(TAG, "WebViewがnullのためJSを実行できません: " + finalJs);
                    isSending = false;
                    processQueue();
                }
            });
        } else {
            isSending = false;
        }
    }

    // ------------------------------
    // 内部ストレージ読み込み
    // ------------------------------
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
            Log.d(TAG, "内部ストレージからJSONデータを読み込みました: " + fileName);
            return stringBuilder.toString();
        } catch (IOException e) {
            Log.e(TAG, "JSONデータの読み込みに失敗しました。", e);
            return null;
        }
    }

    // ------------------------------
    // バッテリー最適化対応
    // ------------------------------
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
                            Log.d(TAG, "バッテリー最適化無効化画面を開きました。");
                        } catch (Exception e) {
                            Log.e(TAG, "バッテリー最適化設定画面を開けませんでした。", e);
                            Toast.makeText(MainActivity.this, "手動で設定してください。", Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton("いいえ", (dialog, which) -> {
                        Toast.makeText(MainActivity.this, "バッテリー最適化が有効だとアプリが停止する可能性があります。", Toast.LENGTH_LONG).show();
                    })
                    .show();
        }
    }

    // ------------------------------
    // WorkManager再起動用
    // ------------------------------
    private void scheduleWebSocketRestartWorker() {
        Log.d(TAG, "WebSocket再起動用WorkManagerをスケジュール中...");
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                WebSocketRestartWorker.class,
                15,
                TimeUnit.MINUTES
        )
                .setInitialDelay(30, TimeUnit.SECONDS)
                .addTag("WebSocketRestartWorkerTag")
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "WebSocketServiceRestart",
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
        );
        Log.d(TAG, "WebSocket再起動用WorkManagerを正常にスケジュールしました。");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "MainActivityを破棄します。");

        if (p2pWebsocket != null) {
            p2pWebsocket.stop();
            p2pWebsocket = null;
            Log.d(TAG, "P2P WebSocketを停止しました。");
        }
        if (wolfxWebsocket != null) {
            wolfxWebsocket.stop();
            wolfxWebsocket = null;
            Log.d(TAG, "Wolfx WebSocketを停止しました。");
        }
        if (webView != null) {
            webView.destroy();
            webView = null;
            Log.d(TAG, "WebViewを破棄しました。");
        }
        mainHandler.removeCallbacksAndMessages(null);
    }

    // ------------------------------
    // WebView用インターフェース
    // ------------------------------
    public class WebAppInterface {
        MainActivity mActivity;

        WebAppInterface(MainActivity activity) {
            mActivity = activity;
        }

        @android.webkit.JavascriptInterface
        public String getLocalData() {
            Log.d(TAG, "WebViewからgetLocalData()が呼ばれました。");
            String data = mActivity.loadJsonFromInternalStorage();
            return data != null ? data : "データがありません";
        }

        @android.webkit.JavascriptInterface
        public boolean isP2PWebSocketConnected() {
            Log.d(TAG, "WebViewからisP2PWebSocketConnected()が呼ばれました。接続状態: " + mActivity.isP2PConnected);
            return mActivity.isP2PConnected;
        }

        @android.webkit.JavascriptInterface
        public boolean isWolfxWebSocketConnected() {
            Log.d(TAG, "WebViewからisWolfxWebSocketConnected()が呼ばれました。接続状態: " + mActivity.isWolfxConnected);
            return mActivity.isWolfxConnected;
        }
    }
}
