package com.example.koiyure;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import java.util.Locale;

public class TTScon {

    private static final String TAG = "TTScon";
    private TextToSpeech tts;
    private boolean isReady = false;
    private Context context;

    /**
     * TTSconのコンストラクタ
     * @param context アプリケーションのContext
     */
    public TTScon(Context context) {
        this.context = context;
        initializeTTS();
    }

    /**
     * TextToSpeechの初期化
     */
    private void initializeTTS() {
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.JAPANESE);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "日本語がサポートされていません");
                    isReady = false;
                } else {
                    Log.d(TAG, "TTS初期化成功");
                    isReady = true;
                    // デフォルト設定
                    tts.setPitch(1.0f);
                    tts.setSpeechRate(1.0f);
                }
            } else {
                Log.e(TAG, "TTS初期化失敗");
                isReady = false;
            }
        });

        // 読み上げ進行状況のリスナー（オプション）
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                Log.d(TAG, "読み上げ開始: " + utteranceId);
            }

            @Override
            public void onDone(String utteranceId) {
                Log.d(TAG, "読み上げ完了: " + utteranceId);
            }

            @Override
            public void onError(String utteranceId) {
                Log.e(TAG, "読み上げエラー: " + utteranceId);
            }
        });
    }

    /**
     * テキストを読み上げる
     * @param text 読み上げるテキスト
     * @return 成功したかどうか
     */
    public boolean speak(String text) {
        if (!isReady) {
            Log.w(TAG, "TTSが準備できていません");
            return false;
        }

        if (text == null || text.isEmpty()) {
            Log.w(TAG, "読み上げるテキストが空です");
            return false;
        }

        // 既存の読み上げをキャンセルして新しいテキストを読み上げ
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, String.valueOf(System.currentTimeMillis()));
        return true;
    }

    /**
     * テキストをキューに追加して読み上げる（既存の読み上げの後に続ける）
     * @param text 読み上げるテキスト
     * @return 成功したかどうか
     */
    public boolean speakQueue(String text) {
        if (!isReady) {
            Log.w(TAG, "TTSが準備できていません");
            return false;
        }

        if (text == null || text.isEmpty()) {
            Log.w(TAG, "読み上げるテキストが空です");
            return false;
        }

        tts.speak(text, TextToSpeech.QUEUE_ADD, null, String.valueOf(System.currentTimeMillis()));
        return true;
    }

    /**
     * 読み上げを停止
     */
    public void stop() {
        if (tts != null && isReady) {
            tts.stop();
            Log.d(TAG, "読み上げ停止");
        }
    }

    /**
     * 音声のピッチを設定
     * @param pitch ピッチ（0.5～2.0、デフォルト1.0）
     */
    public void setPitch(float pitch) {
        if (tts != null && isReady) {
            tts.setPitch(pitch);
        }
    }

    /**
     * 読み上げ速度を設定
     * @param rate 速度（0.5～2.0、デフォルト1.0）
     */
    public void setSpeechRate(float rate) {
        if (tts != null && isReady) {
            tts.setSpeechRate(rate);
        }
    }

    /**
     * TTSが準備できているか確認
     * @return 準備完了していればtrue
     */
    public boolean isReady() {
        return isReady;
    }

    /**
     * リソースの解放（Activityの終了時などに呼び出す）
     */
    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            Log.d(TAG, "TTS終了");
        }
    }
}