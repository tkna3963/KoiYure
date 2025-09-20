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
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.Queue;

public class MainActivity extends AppCompatActivity implements P2PWebsocket.Listener, WolfxWebsocket.Listener {

    private static final String TAG = "MainActivity";
    private WebView webView;
    private P2PWebsocket p2pWebsocket;
    private WolfxWebsocket wolfxWebsocket;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Queue<String> jsQueue = new LinkedList<>();
    private boolean isSending = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        // WebViewのデバッグを有効にする (開発時のみ)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                Log.d(TAG, "WebView finished loading: " + url);
                // ページロード完了後にJavaScriptからデータを取得するトリガーなど
                // 例: webView.evaluateJavascript("window.onPageLoaded();", null);
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
        Log.d(TAG, "P2P受信: " + message);
        sendMessageToWebView("onP2PMessage", message);
    }

    @Override
    public void onP2PStatusChanged(boolean isConnected) {
        Log.d(TAG, "P2P WebSocket Status: " + (isConnected ? "Connected" : "Disconnected"));
        // WebViewのJavaScript関数を呼び出して接続状態を通知
        sendMessageToWebView("onP2PStatusChange", String.valueOf(isConnected));
    }

    @Override
    public void onWolfxMessageReceived(String message) {
        Log.d(TAG, "Wolfx受信: " + message);
        sendMessageToWebView("onWolfxMessage", message);
    }

    @Override
    public void onWolfxStatusChanged(boolean isConnected) {
        Log.d(TAG, "Wolfx WebSocket Status: " + (isConnected ? "Connected" : "Disconnected"));
        // WebViewのJavaScript関数を呼び出して接続状態を通知
        sendMessageToWebView("onWolfxStatusChange", String.valueOf(isConnected));
    }

    private void sendMessageToWebView(String jsFunction, String message) {
        // メッセージがnullでないことを確認
        if (message == null) {
            message = "";
        }
        try {
            // JavaScriptの文字列リテラルとして安全にエスケープ
            String escapedMessage = JSONObject.quote(message);
            // WebView.evaluateJavascript()は"javascript:"プレフィックスを必要としない
            final String jsCode = jsFunction + "(" + escapedMessage + ");";
            Log.d(TAG, "Queueing JS: " + jsCode);

            synchronized (jsQueue) {
                jsQueue.add(jsCode);
            }
            processQueue();
        } catch (Exception e) {
            Log.e(TAG, "Failed to queue JS message for WebView", e);
        }
    }

    private void processQueue() {
        if (isSending) return; // 既に送信中であれば何もしない

        String js;
        synchronized (jsQueue) {
            js = jsQueue.poll(); // キューから次のJSコードを取得
        }

        if (js != null) {
            isSending = true;
            final String finalJs = js;
            mainHandler.post(() -> {
                if (webView != null) {
                    webView.evaluateJavascript(finalJs, value -> {
                        // Log.d(TAG, "JS executed: " + finalJs + ", Result: " + value); // 頻繁なログは避ける
                        isSending = false;
                        processQueue(); // 完了したら次のJSを処理
                    });
                } else {
                    Log.w(TAG, "WebView is null, cannot execute JS: " + finalJs);
                    isSending = false;
                    processQueue(); // WebViewがない場合でもキューは進める
                }
            });
        } else {
            isSending = false; // キューが空になった
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
        // WebSocketServiceはMainActivityが閉じても生き続けるように設計されているため、
        // ここでstopServiceは呼ばない。サービスは自身のライフサイクルで管理する。
    }

    // JavaScriptインターフェースクラス
    public class WebAppInterface {
        MainActivity mActivity;

        WebAppInterface(MainActivity activity) {
            mActivity = activity;
        }

        @android.webkit.JavascriptInterface
        public String getLocalData() {
            Log.d(TAG, "getLocalData() called from WebView.");
            // ローカルデータを読み込む
            String data = mActivity.loadJsonFromInternalStorage();
            return data != null ? data : "データがありません";
        }
    }
}