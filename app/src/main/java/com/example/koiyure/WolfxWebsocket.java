package com.example.koiyure;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class WolfxWebsocket {

    private static final String TAG = "WolfxWebsocket";
    private static final String URL = "wss://ws-api.wolfx.jp/jma_eew";

    private OkHttpClient client;
    private WebSocket webSocket;
    private Listener listener;

    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean shouldReconnect = true;
    private int reconnectDelay = 1000; // 初回1秒後に再接続
    private final int MAX_DELAY = 16000; // 最大16秒

    // ★ Listener インターフェース
    public interface Listener {
        void onWolfxMessageReceived(String message);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    // ------------------------
    // 外部から操作できるメソッド
    // ------------------------
    public void start() {
        shouldReconnect = true;
        connect();
    }

    public void stop() {
        shouldReconnect = false;
        if (webSocket != null) {
            webSocket.close(1000, "アプリ終了");
        }
    }

    public boolean isConnected() {
        return webSocket != null;
    }

    // ------------------------
    // 内部接続処理
    // ------------------------
    private void connect() {
        client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(URL)
                .build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                reconnectDelay = 1000; // 接続成功したら遅延リセット
                WolfxOnOpen(response);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                WolfxOnMessage(text);
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                WolfxOnMessageBinary(bytes);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                WolfxOnClosing(code, reason);
                webSocket.close(1000, null);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                WolfxOnClosed(code, reason);
                attemptReconnect();
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                WolfxOnFailure(t, response);
                attemptReconnect();
            }
        });
    }

    private void attemptReconnect() {
        if (!shouldReconnect) return;
        Log.d(TAG, "再接続を試みます " + reconnectDelay + "ms後");

        handler.postDelayed(() -> {
            if (shouldReconnect) {
                connect();
                reconnectDelay = Math.min(reconnectDelay * 2, MAX_DELAY);
            }
        }, reconnectDelay);
    }

    // ------------------------
    // Wolfx専用メソッド（ログ用）
    // ------------------------
    private void WolfxOnOpen(Response response) {
        Log.d(TAG, "Wolfx接続成功: " + response.message());
    }

    private void WolfxOnMessage(String text) {
        Log.d(TAG, "Wolfx受信: " + text);
        if (listener != null) {
            listener.onWolfxMessageReceived(text);
        }
    }

    private void WolfxOnMessageBinary(ByteString bytes) {
        Log.d(TAG, "Wolfx受信(バイナリ): " + bytes.hex());
    }

    private void WolfxOnClosing(int code, String reason) {
        Log.d(TAG, "Wolfx切断中: " + reason);
    }

    private void WolfxOnClosed(int code, String reason) {
        Log.d(TAG, "Wolfx切断完了: " + reason);
    }

    private void WolfxOnFailure(Throwable t, Response response) {
        Log.e(TAG, "Wolfxエラー: " + t.getMessage(), t);
    }
}
