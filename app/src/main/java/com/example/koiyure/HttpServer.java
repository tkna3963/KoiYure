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
        uri = uri.toLowerCase();

        // --- HTML, Script, Style ---
        if (uri.endsWith(".html") || uri.endsWith(".htm")) return "text/html";
        if (uri.endsWith(".js")) return "application/javascript";
        if (uri.endsWith(".css")) return "text/css";

        // --- JSON系 ---
        if (uri.endsWith(".json")) return "application/json";
        if (uri.endsWith(".geojson")) return "application/geo+json";

        // --- 画像 ---
        if (uri.endsWith(".png")) return "image/png";
        if (uri.endsWith(".jpg") || uri.endsWith(".jpeg")) return "image/jpeg";
        if (uri.endsWith(".gif")) return "image/gif";
        if (uri.endsWith(".bmp")) return "image/bmp";
        if (uri.endsWith(".webp")) return "image/webp";
        if (uri.endsWith(".svg")) return "image/svg+xml";
        if (uri.endsWith(".ico")) return "image/x-icon";

        // --- 音声 ---
        if (uri.endsWith(".mp3")) return "audio/mpeg";
        if (uri.endsWith(".wav")) return "audio/wav";
        if (uri.endsWith(".ogg")) return "audio/ogg";
        if (uri.endsWith(".m4a")) return "audio/mp4";
        if (uri.endsWith(".flac")) return "audio/flac";

        // --- 動画 ---
        if (uri.endsWith(".mp4")) return "video/mp4";
        if (uri.endsWith(".webm")) return "video/webm";
        if (uri.endsWith(".ogg")) return "video/ogg";
        if (uri.endsWith(".mov")) return "video/quicktime";
        if (uri.endsWith(".avi")) return "video/x-msvideo";
        if (uri.endsWith(".mkv")) return "video/x-matroska";

        // --- フォント ---
        if (uri.endsWith(".ttf")) return "font/ttf";
        if (uri.endsWith(".otf")) return "font/otf";
        if (uri.endsWith(".woff")) return "font/woff";
        if (uri.endsWith(".woff2")) return "font/woff2";

        // --- 文書 ---
        if (uri.endsWith(".txt")) return "text/plain";
        if (uri.endsWith(".xml")) return "application/xml";
        if (uri.endsWith(".pdf")) return "application/pdf";
        if (uri.endsWith(".csv")) return "text/csv";
        if (uri.endsWith(".md")) return "text/markdown";
        if (uri.endsWith(".rtf")) return "application/rtf";
        if (uri.endsWith(".doc") || uri.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (uri.endsWith(".xls") || uri.endsWith(".xlsx")) return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        if (uri.endsWith(".ppt") || uri.endsWith(".pptx")) return "application/vnd.openxmlformats-officedocument.presentationml.presentation";

        // --- 圧縮・アーカイブ ---
        if (uri.endsWith(".zip")) return "application/zip";
        if (uri.endsWith(".rar")) return "application/vnd.rar";
        if (uri.endsWith(".tar")) return "application/x-tar";
        if (uri.endsWith(".gz")) return "application/gzip";
        if (uri.endsWith(".7z")) return "application/x-7z-compressed";
        // --- デフォルト ---
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
