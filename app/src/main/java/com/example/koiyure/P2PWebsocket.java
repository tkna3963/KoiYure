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

public class P2PWebsocket {

    private static final String TAG = "P2PWebsocket";
    private static final String URL = "wss://api.p2pquake.net/v2/ws";

    private OkHttpClient client;
    private WebSocket webSocket;
    private Listener listener;

    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean shouldReconnect = true;
    private int reconnectDelay = 1000; // 初回は1秒後に再接続
    private final int MAX_DELAY = 16000; // 最大16秒まで延ばす

    // ------------------------
    // 外部クラス用インターフェース
    // ------------------------
    public interface Listener {
        void onP2PMessageReceived(String message);
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
    // 内部メソッド（private）
    // ------------------------
    private void connect() {
        client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(URL)
                .build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                reconnectDelay = 1000;
                P2PonOpen(response);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                P2PonMessage(text);
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                P2PonMessageBinary(bytes);
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                P2PonClosing(code, reason);
                webSocket.close(1000, null);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                P2PonClosed(code, reason);
                attemptReconnect();
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                P2PonFailure(t, response);
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
    // P2P専用メソッド（ログ用）
    // ------------------------
    private void P2PonOpen(Response response) {
        Log.d(TAG, "P2P接続成功: " + response.message());
    }

    private void P2PonMessage(String text) {
        Log.d(TAG, "P2P受信: " + text);
        if (listener != null) {
            listener.onP2PMessageReceived(text);
        }
    }

    private void P2PonMessageBinary(ByteString bytes) {
        Log.d(TAG, "P2P受信(バイナリ): " + bytes.hex());
    }

    private void P2PonClosing(int code, String reason) {
        Log.d(TAG, "P2P切断中: " + reason);
    }

    private void P2PonClosed(int code, String reason) {
        Log.d(TAG, "P2P切断完了: " + reason);
    }

    private void P2PonFailure(Throwable t, Response response) {
        Log.e(TAG, "P2Pエラー: " + t.getMessage(), t);
    }
}
