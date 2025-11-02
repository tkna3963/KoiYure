package com.example.koiyure;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import fi.iki.elonen.NanoHTTPD;

public class HttpServer extends NanoHTTPD {

    private static final String TAG = "HttpServer";
    private Context context;

    public HttpServer(Context context, int port) {
        super(port);
        this.context = context;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        if (uri.equals("/")) uri = "/MainIndex.html";

        try {
            AssetManager am = context.getAssets();
            InputStream is = am.open(uri.substring(1)); // "/" を削除して assets フォルダ内を参照
            String mime = getMimeTypeForFile(uri);
            String content;

            // 画像やバイナリは InputStream を直接返す
            if (mime.startsWith("image/") || mime.startsWith("audio/") || mime.startsWith("video/")) {
                return newChunkedResponse(Response.Status.OK, mime, is);
            } else {
                content = readStreamToString(is);
                return newFixedLengthResponse(Response.Status.OK, mime, content);
            }

        } catch (IOException e) {
            Log.e(TAG, "Asset読み込み失敗: " + uri, e);
            return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/html", "<h1>404 Not Found</h1>");
        }
    }

    private String readStreamToString(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }

    public static String getMimeTypeForFile(String uri) {
        if (uri.endsWith(".html") || uri.endsWith(".htm")) return "text/html";
        if (uri.endsWith(".js")) return "application/javascript";
        if (uri.endsWith(".css")) return "text/css";
        if (uri.endsWith(".json")) return "application/json";
        if (uri.endsWith(".png")) return "image/png";
        if (uri.endsWith(".jpg") || uri.endsWith(".jpeg")) return "image/jpeg";
        if (uri.endsWith(".gif")) return "image/gif";
        if (uri.endsWith(".bmp")) return "image/bmp";
        if (uri.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }

    public void startServer() {
        try {
            start(SOCKET_READ_TIMEOUT, false);
            Log.d(TAG, "HTTPサーバー起動: http://localhost:8080");
        } catch (IOException e) {
            Log.e(TAG, "HTTPサーバー起動失敗", e);
        }
    }

    public void stopServer() {
        stop();
        Log.d(TAG, "HTTPサーバー停止");
    }
}
