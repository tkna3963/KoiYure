package com.example.koiyure;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.Queue;
import android.os.PowerManager;
import android.provider.Settings;

public class MainActivity extends AppCompatActivity implements P2PWebsocket.Listener, WolfxWebsocket.Listener {

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
        webView.setWebViewClient(new WebViewClient());
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

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    @Override
    public void onP2PMessageReceived(String message) {
        Log.d("MainActivity", "P2P受信: " + message);
        sendMessageToWebView("onP2PMessage", message);
    }

    @Override
    public void onWolfxMessageReceived(String message) {
        Log.d("MainActivity", "Wolfx受信: " + message);
        sendMessageToWebView("onWolfxMessage", message);
    }

    private void sendMessageToWebView(String jsFunction, String message) {
        try {
            String escapedMessage = JSONObject.quote(message);
            synchronized (jsQueue) {
                jsQueue.add(jsFunction + "(" + escapedMessage + ");");
            }
            processQueue();
        } catch (Exception e) {
            Log.e("MainActivity", "メッセージ送信失敗", e);
        }
    }

    private void processQueue() {
        if (isSending) return;
        isSending = true;
        mainHandler.post(() -> {
            String js;
            synchronized (jsQueue) {
                js = jsQueue.poll();
            }
            if (js != null) {
                webView.evaluateJavascript(js, value -> {
                    isSending = false;
                    processQueue();
                });
            } else {
                isSending = false;
            }
        });
    }

    private String loadJsonFromInternalStorage() {
        String fileName = "last_received_data.json";
        try (FileInputStream fis = openFileInput(fileName);
             InputStreamReader isr = new InputStreamReader(fis);
             BufferedReader reader = new BufferedReader(isr)) {
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                stringBuilder.append(line);
            }
            Log.d("MainActivity", "JSONデータを内部ストレージから読み込みました: " + fileName);
            return stringBuilder.toString();
        } catch (IOException e) {
            Log.e("MainActivity", "JSONデータの読み込みに失敗しました", e);
            return null;
        }
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
    }

    // JavaScriptインターフェースクラス
    public class WebAppInterface {
        MainActivity mActivity;

        WebAppInterface(MainActivity activity) {
            mActivity = activity;
        }

        @android.webkit.JavascriptInterface
        public String getLocalData() {
            // ローカルデータを読み込む
            String data = mActivity.loadJsonFromInternalStorage();
            return data != null ? data : "データがありません";
        }
    }
}
