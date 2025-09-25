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

public class WolfxWebsocket {

    private static final String TAG = "WolfxWebsocket";
    private static final String URL = "wss://ws-api.wolfx.jp/jma_eew";

    private OkHttpClient client;
    private WebSocket webSocket;
    private Listener listener;

    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean shouldReconnect = true; // start()でtrue、stop()でfalseに
    private int reconnectDelay = 1000; // 初回は1秒後に再接続
    private final int MAX_DELAY = 16000; // 最大16秒まで延ばす

    public interface Listener {
        void onWolfxMessageReceived(String message);
        void onWolfxStatusChanged(boolean isConnected); // 接続状態の変化を通知
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public boolean isConnected() {
        return webSocket != null; // P2PWebsocketと同様の理由
    }

    public void start() {
        Log.d(TAG, "WolfxWebsocket start() called.");
        shouldReconnect = true;
        reconnectDelay = 1000; // start時はリセット
        connect();
    }

    private void connect() {
        if (!shouldReconnect) {
            Log.d(TAG, "WolfxWebsocket is stopped, not connecting.");
            return;
        }
        // nullチェックを追加。すでにクライアントが破棄されている場合は再作成
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

        Log.d(TAG, "Attempting to connect to Wolfx WebSocket: " + URL);
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                webSocket = ws; // 実際にオープンしたWebSocketインスタンスを保持
                reconnectDelay = 1000; // 接続成功したら再接続遅延をリセット
                if (listener != null) {
                    listener.onWolfxStatusChanged(true);
                }
                WolfxOnOpen(response);
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                WolfxOnMessage(text);
            }

            @Override
            public void onMessage(WebSocket ws, ByteString bytes) {
                WolfxOnMessageBinary(bytes);
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                WolfxOnClosing(code, reason);
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                WolfxOnClosed(code, reason);
                if (listener != null) {
                    listener.onWolfxStatusChanged(false);
                }
                webSocket = null;
                attemptReconnect();
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                WolfxOnFailure(t, response);
                if (listener != null) {
                    listener.onWolfxStatusChanged(false);
                }
                webSocket = null;
                attemptReconnect();
            }
        });
    }

    public void stop() {
        Log.d(TAG, "WolfxWebsocket stop() called.");
        shouldReconnect = false;
        handler.removeCallbacksAndMessages(null); // 再接続キューをクリア
        if (webSocket != null) {
            Log.d(TAG, "Closing Wolfx WebSocket gracefully.");
            webSocket.close(1000, "アプリ終了");
            webSocket = null;
        }
        if (listener != null) {
            listener.onWolfxStatusChanged(false);
        }
        if (client != null) {
            client.dispatcher().cancelAll();
            client = null;
        }
    }

    private void attemptReconnect() {
        if (!shouldReconnect) {
            Log.d(TAG, "WolfxWebsocket shouldReconnect is false. Not attempting reconnect.");
            return;
        }
        Log.d(TAG, "Attempting Wolfx reconnect in " + reconnectDelay + "ms");

        handler.postDelayed(() -> {
            if (shouldReconnect) {
                connect();
                reconnectDelay = Math.min(reconnectDelay * 2, MAX_DELAY);
            } else {
                Log.d(TAG, "Reconnect cancelled: shouldReconnect became false during delay.");
            }
        }, reconnectDelay);
    }

    // -----------------------
    // Wolfx専用メソッド
    // -----------------------
    private void WolfxOnOpen(Response response) {
        Log.d(TAG, "Wolfx接続成功: " + response.message());
    }

    private void WolfxOnMessage(String text) {
        // Log.d(TAG, "Wolfx受信: " + text); // 受信頻度が高い可能性があるので、本番では注意
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
        String msg = (response != null) ? response.message() : "No response";
        Log.e(TAG, "Wolfxエラー: " + t.getMessage() + " / Response: " + msg, t);
    }
}