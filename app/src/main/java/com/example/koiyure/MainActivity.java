package com.example.koiyure;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity
        implements P2PWebsocket.Listener, WolfxWebsocket.Listener {

    private WebView webView;
    private P2PWebsocket p2pWebsocket;
    private WolfxWebsocket wolfxWebsocket;
    private ForegroundManager foregroundManager;
    private AlarmManagerController alarmmanagercontroller;
    private WorkManager workManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // システムバーのパディング調整
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // マネージャー初期化
        foregroundManager = new ForegroundManager(this);
        alarmmanagercontroller = new AlarmManagerController(this);
        workManager = new WorkManager(this);

        // WebSocket初期化
        p2pWebsocket = new P2PWebsocket();
        p2pWebsocket.setListener(this);

        wolfxWebsocket = new WolfxWebsocket();
        wolfxWebsocket.setListener(this);

        // WebView 設定
        webView = findViewById(R.id.webView);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);

        // JavaScriptインターフェース追加
        webView.addJavascriptInterface(new WebAppInterface(), "Android");

        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/MainIndex.html");
    }

    // ========================
    // WebSocket リスナー
    // ========================
    @Override
    public void onP2PMessageReceived(String message) {
        runOnUiThread(() -> {
            webView.evaluateJavascript(
                    "if(window.onP2PMessage) window.onP2PMessage(" +
                            escapeJson(message) + ");", null
            );
        });
    }

    @Override
    public void onWolfxMessageReceived(String message) {
        runOnUiThread(() -> {
            webView.evaluateJavascript(
                    "if(window.onWolfxMessage) window.onWolfxMessage(" +
                            escapeJson(message) + ");", null
            );
        });
    }

    // ========================
    // JavaScript Interface
    // ========================
    public class WebAppInterface {

        // --- Foreground Service ---
        @JavascriptInterface
        public void startForeground() {
            runOnUiThread(() -> {
                foregroundManager.start();
                Toast.makeText(MainActivity.this,
                        "フォアグラウンドサービス開始", Toast.LENGTH_SHORT).show();
            });
        }

        @JavascriptInterface
        public void stopForeground() {
            runOnUiThread(() -> {
                foregroundManager.stop();
                Toast.makeText(MainActivity.this,
                        "フォアグラウンドサービス停止", Toast.LENGTH_SHORT).show();
            });
        }

        @JavascriptInterface
        public boolean isForegroundRunning() {
            return foregroundManager.isRunning();
        }

        // --- Alarm Manager ---
        @JavascriptInterface
        public void startAlarm(int intervalMinutes) {
            runOnUiThread(() -> {
                alarmmanagercontroller.start(intervalMinutes);
                Toast.makeText(MainActivity.this,
                        "定期アラーム開始: " + intervalMinutes + "分間隔",
                        Toast.LENGTH_SHORT).show();
            });
        }

        @JavascriptInterface
        public void stopAlarm() {
            runOnUiThread(() -> {
                alarmmanagercontroller.stop();
                Toast.makeText(MainActivity.this,
                        "定期アラーム停止", Toast.LENGTH_SHORT).show();
            });
        }

        // --- Work Manager ---
        @JavascriptInterface
        public void startWork(int intervalMinutes) {
            runOnUiThread(() -> {
                workManager.start(intervalMinutes);
                Toast.makeText(MainActivity.this,
                        "WorkManager開始: " + intervalMinutes + "分間隔",
                        Toast.LENGTH_SHORT).show();
            });
        }

        @JavascriptInterface
        public void stopWork() {
            runOnUiThread(() -> {
                workManager.stop();
                Toast.makeText(MainActivity.this,
                        "WorkManager停止", Toast.LENGTH_SHORT).show();
            });
        }

        // --- WebSocket 制御 ---
        @JavascriptInterface
        public void startP2P() {
            runOnUiThread(() -> {
                p2pWebsocket.start();
                Toast.makeText(MainActivity.this,
                        "P2P地震情報 接続開始", Toast.LENGTH_SHORT).show();
            });
        }

        @JavascriptInterface
        public void stopP2P() {
            runOnUiThread(() -> {
                p2pWebsocket.stop();
                Toast.makeText(MainActivity.this,
                        "P2P地震情報 切断", Toast.LENGTH_SHORT).show();
            });
        }

        @JavascriptInterface
        public void startWolfx() {
            runOnUiThread(() -> {
                wolfxWebsocket.start();
                Toast.makeText(MainActivity.this,
                        "Wolfx緊急地震速報 接続開始", Toast.LENGTH_SHORT).show();
            });
        }

        @JavascriptInterface
        public void stopWolfx() {
            runOnUiThread(() -> {
                wolfxWebsocket.stop();
                Toast.makeText(MainActivity.this,
                        "Wolfx緊急地震速報 切断", Toast.LENGTH_SHORT).show();
            });
        }

        @JavascriptInterface
        public boolean isP2PConnected() {
            return p2pWebsocket.isConnected();
        }

        @JavascriptInterface
        public boolean isWolfxConnected() {
            return wolfxWebsocket.isConnected();
        }

        // --- 全機能一括制御 ---
        @JavascriptInterface
        public void startAll() {
            runOnUiThread(() -> {
                foregroundManager.start();
                alarmmanagercontroller.start(15); // 15分間隔
                workManager.start(15);
                p2pWebsocket.start();
                wolfxWebsocket.start();
                Toast.makeText(MainActivity.this,
                        "すべてのサービス開始", Toast.LENGTH_LONG).show();
            });
        }

        @JavascriptInterface
        public void stopAll() {
            runOnUiThread(() -> {
                foregroundManager.stop();
                alarmmanagercontroller.stop();
                workManager.stop();
                p2pWebsocket.stop();
                wolfxWebsocket.stop();
                Toast.makeText(MainActivity.this,
                        "すべてのサービス停止", Toast.LENGTH_LONG).show();
            });
        }

        // --- バッテリー最適化除外リクエスト ---
        @JavascriptInterface
        public void requestBatteryOptimization() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                runOnUiThread(() -> {
                    foregroundManager.requestIgnoreBatteryOptimization();
                });
            }
        }

        // --- システム情報 ---
        @JavascriptInterface
        public String getDeviceInfo() {
            return "Manufacturer: " + Build.MANUFACTURER +
                    ", Model: " + Build.MODEL +
                    ", SDK: " + Build.VERSION.SDK_INT;
        }
    }

    // ========================
    // ユーティリティ
    // ========================
    private String escapeJson(String json) {
        return "'" + json.replace("'", "\\'") + "'";
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // アクティビティ破棄時もサービスは継続
        // （完全停止はWebViewから明示的に指示）
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}