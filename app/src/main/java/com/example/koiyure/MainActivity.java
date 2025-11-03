package com.example.koiyure;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import android.webkit.WebChromeClient;
import android.webkit.GeolocationPermissions;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONObject;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements P2PWebsocket.Listener, WolfxWebsocket.Listener {

    private static final String TAG = "MainActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private WebView webView;
    private P2PWebsocket p2pWebsocket;
    private WolfxWebsocket wolfxWebsocket;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Queue<String> jsQueue = new LinkedList<>();
    private boolean isSending = false;

    private boolean isP2PConnected = false;
    private boolean isWolfxConnected = false;

    HttpServer httpServer;
    private Cache cache = Cache.getInstance();
    private com.example.koiyure.Settings settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        settings = new com.example.koiyure.Settings(this);
        setContentView(R.layout.activity_main);

        // -------- WebView設定 --------
        webView = findViewById(R.id.webView);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setGeolocationEnabled(true); // ← 位置情報APIを有効化

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        } else {
            CookieManager.getInstance().setAcceptCookie(true);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        // -------- 位置情報の許可を自動承認するWebChromeClient --------
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }
        });

        // -------- ページ読み込み完了時 --------
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "WebViewのページ読み込み完了: " + url);

                List<String> P2PCache = cache.getMessages("P2P");
                List<String> WolfxCache = cache.getMessages("Wolfx");

                for (String message : P2PCache) {
                    sendMessageToWebView("onP2PMessage", message);
                }
                for (String message : WolfxCache) {
                    sendMessageToWebView("onWolfxMessage", message);
                }
                cache.clearAll();
                sendMessageToWebView("onP2PStatusChange", String.valueOf(isP2PConnected));
                sendMessageToWebView("onWolfxStatusChange", String.valueOf(isWolfxConnected));
            }
        });

        // -------- WebView起動 --------
        httpServer = new HttpServer(this, 8080);
        httpServer.startServer();
        webView.loadUrl("http://localhost:8080");
        webView.addJavascriptInterface(new WebAppInterface(this), "Android");

        // -------- P2P / Wolfx WebSocket --------
        p2pWebsocket = new P2PWebsocket();
        p2pWebsocket.setListener(this);
        p2pWebsocket.start();

        wolfxWebsocket = new WolfxWebsocket();
        wolfxWebsocket.setListener(this);
        wolfxWebsocket.start();

        Intent serviceIntent = new Intent(this, WebSocketService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        requestIgnoreBatteryOptimizations();
        scheduleWebSocketRestartWorker();
        requestLocationPermission(); // ← 実行時位置情報許可
    }

    // ------------------------------
    // 実行時パーミッション確認
    // ------------------------------
    private void requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        LOCATION_PERMISSION_REQUEST_CODE
                );
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "位置情報が許可されました", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "位置情報が拒否されました", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ------------------------------
    // Listener実装
    // ------------------------------
    @Override
    public void onP2PMessageReceived(String message) {
        sendMessageToWebView("onP2PMessage", message);
    }

    @Override
    public void onP2PStatusChanged(boolean isConnected) {
        this.isP2PConnected = isConnected;
        sendMessageToWebView("onP2PStatusChange", String.valueOf(isConnected));
    }

    @Override
    public void onWolfxMessageReceived(String message) {
        sendMessageToWebView("onWolfxMessage", message);
    }

    @Override
    public void onWolfxStatusChanged(boolean isConnected) {
        this.isWolfxConnected = isConnected;
        sendMessageToWebView("onWolfxStatusChange", String.valueOf(isConnected));
    }

    // ------------------------------
    // WebView通信処理
    // ------------------------------
    private void sendMessageToWebView(String jsFunction, String message) {
        if (message == null) message = "";
        try {
            String escapedMessage = JSONObject.quote(message);
            final String jsCode = "javascript:" + jsFunction + "(" + escapedMessage + ");";
            synchronized (jsQueue) {
                jsQueue.add(jsCode);
            }
            processQueue();
        } catch (Exception e) {
            Log.e(TAG, "WebView送信失敗", e);
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
                    isSending = false;
                    processQueue();
                }
            });
        } else {
            isSending = false;
        }
    }

    // ------------------------------
    // バッテリー最適化無効化
    // ------------------------------
    private void requestIgnoreBatteryOptimizations() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            new AlertDialog.Builder(this)
                    .setTitle("バックグラウンド実行の許可")
                    .setMessage("このアプリを安定して動作させるため、バッテリー最適化を無効にしてください。")
                    .setPositiveButton("はい", (dialog, which) -> {
                        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        try {
                            startActivity(intent);
                        } catch (Exception e) {
                            Toast.makeText(MainActivity.this, "設定画面を開けませんでした。", Toast.LENGTH_LONG).show();
                        }
                    })
                    .setNegativeButton("いいえ", null)
                    .show();
        }
    }

    // ------------------------------
    // WorkManager再起動設定
    // ------------------------------
    private void scheduleWebSocketRestartWorker() {
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
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (p2pWebsocket != null) {
            p2pWebsocket.stop();
            p2pWebsocket = null;
        }
        if (wolfxWebsocket != null) {
            wolfxWebsocket.stop();
            wolfxWebsocket = null;
        }
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        if (httpServer != null) {
            httpServer.stopServer();
        }
        mainHandler.removeCallbacksAndMessages(null);
    }

    // ------------------------------
    // WebViewインターフェース
    // ------------------------------
    public class WebAppInterface {
        MainActivity mActivity;

        WebAppInterface(MainActivity activity) {
            mActivity = activity;
        }

        @android.webkit.JavascriptInterface
        public String CacheReturn() {
            List<String> types = cache.getAllMessages();
            return types.toString();
        }

        @android.webkit.JavascriptInterface
        public boolean isP2PWebSocketConnected() {
            return mActivity.isP2PConnected;
        }

        @android.webkit.JavascriptInterface
        public boolean isWolfxWebSocketConnected() {
            return mActivity.isWolfxConnected;
        }

        @android.webkit.JavascriptInterface
        public String getSetting(String key, String defaultValue) {
            return mActivity.settings.get(key, defaultValue);
        }

        @android.webkit.JavascriptInterface
        public void setSetting(String key, String value) {
            mActivity.settings.set(key, value);
        }

        @android.webkit.JavascriptInterface
        public boolean containsSetting(String key) {
            return mActivity.settings.contains(key);
        }

        @android.webkit.JavascriptInterface
        public void removeSetting(String key) {
            mActivity.settings.remove(key);
        }
    }
}
