package com.example.koiyure;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;

public class TTScon {

    private TextToSpeech tts;
    private boolean isInitialized = false;

    // コンストラクタで初期化
    public TTScon(Context context) {
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.JAPANESE);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTScon", "日本語はサポートされていません");
                    isInitialized = false; // 初期化失敗
                } else {
                    isInitialized = true;
                    Log.d("TTScon", "TTS初期化成功");
                }
            } else {
                Log.e("TTScon", "TTSの初期化に失敗しました。Status: " + status);
                isInitialized = false; // 初期化失敗
            }
        });
    }

    /**
     * TTSが正常に初期化されたかどうかの状態を返します。
     * @return trueなら初期化済み、falseなら未初期化または失敗。
     */
    public boolean isInitialized() {
        return isInitialized;
    }

    /**
     * 指定されたテキストを読み上げます。
     * @param text 読み上げるテキスト
     */
    public void speak(String text) {
        if (isInitialized) {
            // TextToSpeech.QUEUE_FLUSH: 現在の読み上げを停止して新しいテキストを読み始める
            // TextToSpeech.QUEUE_ADD: 現在の読み上げが終了した後、新しいテキストをキューに追加する
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        } else {
            Log.e("TTScon", "TTSが初期化されていないため、読み上げできません: " + text);
        }
    }

    /**
     * TTSリソースを解放します。
     * アプリケーションの終了時や、このクラスのインスタンスが不要になったときに呼び出す必要があります。
     */
    public void shutdown() {
        if (tts != null) {
            tts.stop(); // 読み上げ中の場合は停止
            tts.shutdown(); // リソースを解放
            isInitialized = false; // 状態をリセット
            Log.d("TTScon", "TTSシャットダウン完了");
        }
    }
}