package com.example.koiyure;

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
import java.util.LinkedList;
import java.util.Queue;

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
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        webView = findViewById(R.id.webView);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/MainIndex.html");
        p2pWebsocket = new P2PWebsocket();
        p2pWebsocket.setListener(this);
        p2pWebsocket.start();
        wolfxWebsocket = new WolfxWebsocket();
        wolfxWebsocket.setListener(this);
        wolfxWebsocket.start();
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
}
