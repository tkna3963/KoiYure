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
    private boolean shouldReconnect = true; // start()でtrue、stop()でfalseに
    private int reconnectDelay = 1000; // 初回は1秒後に再接続
    private final int MAX_DELAY = 16000; // 最大16秒まで延ばす

    public interface Listener {
        void onP2PMessageReceived(String message);
        void onP2PStatusChanged(boolean isConnected); // 接続状態の変化を通知
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public boolean isConnected() {
        // WebSocketオブジェクトが存在し、かつオープン状態であればtrueと判断
        // OkHttpのWebSocketにはisConnecting/isClosingのような直接的なメソッドがないため、
        // 最後に呼ばれたライフサイクルメソッドの状態に依存する。
        // ここでは、webSocketがnullでなく、かつ明確にcloseされていない限り「接続を試みているか、接続済み」と仮定。
        // より厳密な状態管理が必要なら、内部でenumStateを持つべき。
        return webSocket != null; // 少なくともオブジェクトは存在している
    }

    public void start() {
        Log.d(TAG, "P2PWebsocket start() called.");
        shouldReconnect = true;
        reconnectDelay = 1000; // start時はリセット
        connect();
    }

    private void connect() {
        if (!shouldReconnect) {
            Log.d(TAG, "P2PWebsocket is stopped, not connecting.");
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

        Log.d(TAG, "Attempting to connect to P2P WebSocket: " + URL);
        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                webSocket = ws; // 実際にオープンしたWebSocketインスタンスを保持
                reconnectDelay = 1000; // 接続成功したら再接続遅延をリセット
                if (listener != null) {
                    listener.onP2PStatusChanged(true);
                }
                P2PonOpen(response);
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                P2PonMessage(text);
            }

            @Override
            public void onMessage(WebSocket ws, ByteString bytes) {
                P2PonMessageBinary(bytes);
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                P2PonClosing(code, reason);
                // 明示的にcloseを呼ばなくてもonClosedが呼ばれる
            }

            @Override
            public void onClosed(WebSocket ws, int code, String reason) {
                P2PonClosed(code, reason);
                if (listener != null) {
                    listener.onP2PStatusChanged(false);
                }
                webSocket = null; // 切断されたのでnullにする
                attemptReconnect();
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                P2PonFailure(t, response);
                if (listener != null) {
                    listener.onP2PStatusChanged(false);
                }
                webSocket = null; // エラーで切断されたのでnullにする
                attemptReconnect();
            }
        });
    }

    public void stop() {
        Log.d(TAG, "P2PWebsocket stop() called.");
        shouldReconnect = false;
        handler.removeCallbacksAndMessages(null); // 再接続キューをクリア
        if (webSocket != null) {
            Log.d(TAG, "Closing P2P WebSocket gracefully.");
            webSocket.close(1000, "アプリ終了");
            webSocket = null;
        }
        if (listener != null) {
            listener.onP2PStatusChanged(false);
        }
        if (client != null) {
            // クライアントのリソースを解放
            client.dispatcher().cancelAll();
            client = null;
        }
    }

    private void attemptReconnect() {
        if (!shouldReconnect) {
            Log.d(TAG, "P2PWebsocket shouldReconnect is false. Not attempting reconnect.");
            return;
        }
        Log.d(TAG, "Attempting P2P reconnect in " + reconnectDelay + "ms");

        handler.postDelayed(() -> {
            if (shouldReconnect) {
                connect();
                // 次回の遅延は倍にして最大値を超えないようにする
                reconnectDelay = Math.min(reconnectDelay * 2, MAX_DELAY);
            } else {
                Log.d(TAG, "Reconnect cancelled: shouldReconnect became false during delay.");
            }
        }, reconnectDelay);
    }

    // -----------------------
    // P2P専用メソッド
    // -----------------------
    private void P2PonOpen(Response response) {
        Log.d(TAG, "P2P接続成功: " + response.message());
    }

    private void P2PonMessage(String text) {
        // Log.d(TAG, "P2P受信: " + text); // 受信頻度が高い可能性があるので、本番では注意
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
        String msg = (response != null) ? response.message() : "No response";
        Log.e(TAG, "P2Pエラー: " + t.getMessage() + " / Response: " + msg, t);
    }
}