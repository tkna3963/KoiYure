package com.example.koiyure;

import android.content.Context;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class WolfxCon {

    private static final String TAG = "WolfxCon";
    private Context context;

    // ★ 追加: TTSconのインスタンスを保持
    private TTScon ttscon;

    /**
     * WolfxConのコンストラクタ
     * @param context アプリケーションのContext
     */
    public WolfxCon(Context context) {
        this.context = context;
        this.ttscon = new TTScon(context); // ★ 追加: TTS初期化
        Log.d(TAG, "WolfxCon initialized with Context and TTS.");
    }

    // Wolfxデータ変換
    private Object[] extractWolfxData(JSONObject data) {
        String type = data.optString("type", "");

        if ("jma_eew".equals(type)) {
            return new Object[]{
                    data.optString("type", ""),
                    data.optString("Title", ""),
                    data.optString("CodeType", ""),
                    data.optJSONObject("Issue") != null ? data.optJSONObject("Issue").optString("Source", "") : "",
                    data.optJSONObject("Issue") != null ? data.optJSONObject("Issue").optString("Status", "") : "",
                    data.optString("EventID", ""),
                    data.optString("AnnouncedTime", ""),
                    data.optString("OriginTime", ""),
                    data.optString("Hypocenter", ""),
                    data.optString("MaxIntensity", ""),
                    data.optJSONObject("Accuracy") != null ? data.optJSONObject("Accuracy").optString("Epicenter", "") : "",
                    data.optJSONObject("Accuracy") != null ? data.optJSONObject("Accuracy").optString("Depth", "") : "",
                    data.optJSONObject("Accuracy") != null ? data.optJSONObject("Accuracy").optString("Magnitude", "") : "",
                    data.optJSONObject("MaxIntChange") != null ? data.optJSONObject("MaxIntChange").optString("String", "") : "",
                    data.optJSONObject("MaxIntChange") != null ? data.optJSONObject("MaxIntChange").optString("Reason", "") : "",
                    data.opt("WarnArea") instanceof JSONArray ? ((JSONArray) data.opt("WarnArea")).optJSONObject(0) != null ? ((JSONArray) data.opt("WarnArea")).optJSONObject(0).optString("Chiiki", "") : "" : (data.optJSONObject("WarnArea") != null ? data.optJSONObject("WarnArea").optString("Chiiki", "") : ""),
                    data.opt("WarnArea") instanceof JSONArray ? ((JSONArray) data.opt("WarnArea")).optJSONObject(0) != null ? ((JSONArray) data.opt("WarnArea")).optJSONObject(0).optString("Shindo1", "") : "" : (data.optJSONObject("WarnArea") != null ? data.optJSONObject("WarnArea").optString("Shindo1", "") : ""),
                    data.opt("WarnArea") instanceof JSONArray ? ((JSONArray) data.opt("WarnArea")).optJSONObject(0) != null ? ((JSONArray) data.opt("WarnArea")).optJSONObject(0).optString("Shindo2", "") : "" : (data.optJSONObject("WarnArea") != null ? data.optJSONObject("WarnArea").optString("Shindo2", "") : ""),
                    data.opt("WarnArea") instanceof JSONArray ? ((JSONArray) data.opt("WarnArea")).optJSONObject(0) != null ? ((JSONArray) data.opt("WarnArea")).optJSONObject(0).optString("Time", "") : "" : (data.optJSONObject("WarnArea") != null ? data.optJSONObject("WarnArea").optString("Time", "") : ""),
                    data.opt("WarnArea") instanceof JSONArray ? ((JSONArray) data.opt("WarnArea")).optJSONObject(0) != null ? ((JSONArray) data.opt("WarnArea")).optJSONObject(0).optString("Type", "") : "" : (data.optJSONObject("WarnArea") != null ? data.optJSONObject("WarnArea").optString("Type", "") : ""),
                    data.optInt("Serial", 0),
                    data.optDouble("Latitude", 0),
                    data.optDouble("Longitude", 0),
                    data.optDouble("Magnitude", 0),
                    data.optDouble("Depth", 0),
                    data.opt("WarnArea") instanceof JSONArray ? ((JSONArray) data.opt("WarnArea")).optJSONObject(0) != null && ((JSONArray) data.opt("WarnArea")).optJSONObject(0).optBoolean("Arrive", false) : (data.optJSONObject("WarnArea") != null && data.optJSONObject("WarnArea").optBoolean("Arrive", false)),
                    data.optBoolean("isSea", false),
                    data.optBoolean("isTraining", false),
                    data.optBoolean("isAssumption", false),
                    data.optBoolean("isWarn", false),
                    data.optBoolean("isFinal", false),
                    data.optBoolean("isCancel", false),
                    data.optString("OriginalText", "")
            };
        } else {
            return new Object[]{
                    data.optString("type", ""),
                    data.optString("id", ""),
                    data.optString("timestamp", ""),
                    data.optString("ver", "")
            };
        }
    }

    // Wolfx文字列変換
    public String wolfxConverter(JSONObject data) throws JSONException {
        String type = data.optString("type", "");
        String telopText;

        // heartbeat
        if ("heartbeat".equals(type)) {
            telopText = "【システムハートビート】\n" +
                    "ID: " + data.optString("id", "不明") + "\n" +
                    "メッセージ: " + data.optString("message", "（なし）") + "\n";
        }

        // jma_eew
        else if ("jma_eew".equals(type)) {
            StringBuilder msg = new StringBuilder();
            msg.append("【").append(data.optString("Title", "緊急地震速報")).append("】\n");
            JSONObject issue = data.optJSONObject("Issue");
            msg.append("発表機関: ")
                    .append(issue != null ? issue.optString("Source", "不明") : "不明")
                    .append(" (")
                    .append(issue != null ? issue.optString("Status", "不明") : "不明")
                    .append(")\n");
            msg.append("発表ID: ").append(data.optString("EventID", "不明"))
                    .append(" / 発表回数: 第").append(data.optInt("Serial", 0)).append("報\n");
            msg.append("発表時刻: ").append(data.optString("AnnouncedTime", "不明")).append(" (JST)\n");
            msg.append("地震発生時刻: ").append(data.optString("OriginTime", "不明")).append(" (JST)\n\n");

            msg.append("震源地: ").append(data.optString("Hypocenter", "不明"))
                    .append(" (緯度: ").append(data.optDouble("Latitude", 0))
                    .append(", 経度: ").append(data.optDouble("Longitude", 0)).append(")\n");
            msg.append("マグニチュード: M").append(data.optDouble("Magunitude", 0))
                    .append(" 深さ: ").append(data.optDouble("Depth", 0)).append("km\n");
            msg.append("最大震度: ").append(data.optString("MaxIntensity", "不明")).append("\n");

            JSONObject accuracy = data.optJSONObject("Accuracy");
            if (accuracy != null) {
                msg.append("\n【精度情報】\n");
                msg.append("震源位置: ").append(accuracy.optString("Epicenter", "不明"))
                        .append(" / 深さ: ").append(accuracy.optString("Depth", "不明"))
                        .append(" / マグニチュード: ").append(accuracy.optString("Magnitude", "不明")).append("\n");
            }

            JSONObject maxIntChange = data.optJSONObject("MaxIntChange");
            if (maxIntChange != null && maxIntChange.has("String")) {
                msg.append("最大震度の変更: ").append(maxIntChange.optString("String", ""))
                        .append(" (理由: ").append(maxIntChange.optString("Reason", "不明")).append(")\n");
            }

            if (data.has("WarnArea")) {
                msg.append("\n【警報情報】\n");
                Object warnObj = data.opt("WarnArea");
                if (warnObj instanceof JSONArray) {
                    JSONArray warnAreas = (JSONArray) warnObj;
                    if (warnAreas.length() > 0) {
                        for (int i = 0; i < warnAreas.length(); i++) {
                            JSONObject area = warnAreas.optJSONObject(i);
                            if (area != null) {
                                msg.append("地域: ").append(area.optString("Chiiki", "不明")).append("\n")
                                        .append("予測震度: ").append(area.optString("Shindo1", "不明"))
                                        .append(" (最小: ").append(area.optString("Shindo2", "不明")).append(")\n")
                                        .append("発表時刻: ").append(area.optString("Time", "不明"))
                                        .append(", 種別: ").append(area.optString("Type", "不明"))
                                        .append(", 主要動到達: ").append(area.optBoolean("Arrive", false) ? "到達済" : "未到達").append("\n")
                                        .append("-------------------\n");
                            }
                        }
                    } else {
                        msg.append("警報対象地域: なし\n");
                    }
                } else if (warnObj instanceof JSONObject) {
                    JSONObject area = (JSONObject) warnObj;
                    msg.append("地域: ").append(area.optString("Chiiki", "不明")).append("\n")
                            .append("予測震度: ").append(area.optString("Shindo1", "不明"))
                            .append(" (最小: ").append(area.optString("Shindo2", "不明")).append(")\n")
                            .append("発表時刻: ").append(area.optString("Time", "不明"))
                            .append(", 種別: ").append(area.optString("Type", "不明"))
                            .append(", 主要動到達: ").append(area.optBoolean("Arrive", false) ? "到達済" : "未到達").append("\n");
                } else {
                    msg.append("警報対象地域: 不明な形式\n");
                }
            }

            msg.append("\n【その他情報】\n")
                    .append("海域の地震: ").append(data.optBoolean("isSea", false) ? "はい" : "いいえ").append("\n")
                    .append("訓練報: ").append(data.optBoolean("isTraining", false) ? "はい" : "いいえ").append("\n")
                    .append("推定震源: ").append(data.optBoolean("isAssumption", false) ? "はい" : "いいえ").append("\n")
                    .append("警報発表: ").append(data.optBoolean("isWarn", false) ? "はい" : "いいえ").append("\n")
                    .append("最終報: ").append(data.optBoolean("isFinal", false) ? "はい" : "いいえ").append("\n")
                    .append("キャンセル報: ").append(data.optBoolean("isCancel", false) ? "はい" : "いいえ").append("\n");

            if (data.has("OriginalText")) {
                msg.append("\n【原文】\n").append(data.optString("OriginalText", "")).append("\n");
            }

            telopText = msg.toString();

            // ★ 追加: TTSで読み上げ
            if (ttscon != null && ttscon.isReady()) {
                String readText = "緊急地震速報です。震源地は " +
                        data.optString("Hypocenter", "不明") +
                        "。最大震度は " + data.optString("MaxIntensity", "不明") +
                        "。マグニチュード " + data.optDouble("Magunitude", 0) + " です。";
                ttscon.speak(readText);
            } else {
                Log.w(TAG, "TTSが未準備のため、読み上げをスキップしました。");
            }

        } else {
            telopText = "【未対応のデータ】\nタイプ: " + type + "\n内容:\n" + data.toString(2);
        }
        return telopText;
    }
}
