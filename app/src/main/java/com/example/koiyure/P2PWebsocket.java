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

import java.util.concurrent.TimeUnit;

public class P2PWebsocket {

    private static final String TAG = "P2PWebsocket";
    private static final String URL = "wss://api.p2pquake.net/v2/ws";

    private OkHttpClient client;
    private WebSocket webSocket;
    private Listener listener;

    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean shouldReconnect = true;
    private int reconnectDelay = 1000; // 初回1秒
    private final int MAX_DELAY = 16000;

    // ------------------------
    // P2P専用Listener
    // ------------------------
    public interface Listener {
        void onP2PMessageReceived(String message);
        void onP2PStatusChanged(boolean isConnected);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public boolean isConnected() {
        return webSocket != null;
    }

    public void start() {
        Log.d(TAG, "P2PWebsocket start() 呼び出し");
        shouldReconnect = true;
        reconnectDelay = 1000;
        connect();
    }

    private void connect() {
        if (!shouldReconnect) {
            Log.d(TAG, "再接続不要: P2PWebsocketは停止中");
            return;
        }

        if (client == null) {
            client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .build();
        }

        Request request = new Request.Builder()
                .url(URL)
                .build();

        Log.d(TAG, "P2P WebSocket に接続を試行: " + URL);
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                webSocket = ws;
                reconnectDelay = 1000;
                if (listener != null) listener.onP2PStatusChanged(true);  // ←P2P用
                Log.d(TAG, "P2P接続成功: " + response.message());
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                Log.d(TAG, "P2Pメッセージ受信: " + text);
                if (listener != null) listener.onP2PMessageReceived(text); // ←P2P用
            }

            @Override
            public void onMessage(WebSocket ws, ByteString bytes) {
                Log.d(TAG, "P2Pメッセージ受信(バイナリ): " + bytes.hex());
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                Log.d(TAG, "P2P切断中: " + reason);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                Log.d(TAG, "P2P切断完了: " + reason);
                if (listener != null) listener.onP2PStatusChanged(false);  // ←P2P用
                webSocket = null;
                attemptReconnect();
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                String msg = response != null ? response.message() : "No response";
                Log.e(TAG, "P2P接続エラー: " + t.getMessage() + " / Response: " + msg, t);
                if (listener != null) listener.onP2PStatusChanged(false);  // ←P2P用
                webSocket = null;
                attemptReconnect();
            }
        });
    }

    public void stop() {
        Log.d(TAG, "P2PWebsocket stop() 呼び出し");
        shouldReconnect = false;
        handler.removeCallbacksAndMessages(null);
        if (webSocket != null) {
            Log.d(TAG, "P2P WebSocketを閉じます");
            webSocket.close(1000, "アプリ終了");
            webSocket = null;
        }
        if (listener != null) listener.onP2PStatusChanged(false);  // ←P2P用
        if (client != null) {
            client.dispatcher().cancelAll();
            client = null;
        }
    }

    private void attemptReconnect() {
        if (!shouldReconnect) {
            Log.d(TAG, "再接続キャンセル: P2PWebsocketは停止中");
            return;
        }
        Log.d(TAG, "P2P WebSocket 再接続を " + reconnectDelay + "ms 後に試行");
        handler.postDelayed(() -> {
            if (shouldReconnect) {
                connect();
                reconnectDelay = Math.min(reconnectDelay * 2, MAX_DELAY);
            } else {
                Log.d(TAG, "再接続中止: shouldReconnect が false になりました");
            }
        }, reconnectDelay);
    }
}
