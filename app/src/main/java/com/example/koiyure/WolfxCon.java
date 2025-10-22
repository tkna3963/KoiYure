package com.example.koiyure;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class WolfxCon{

    // Wolfxデータ変換
    public static Object[] WolxCon(JSONObject data) {
        String type = data.optString("type", "");

        if ("jma_eew".equals(type)) {
            return new Object[]{
                    data.optString("type", ""),                      // 1
                    data.optString("Title", ""),                     // 2
                    data.optString("CodeType", ""),                  // 3
                    data.optJSONObject("Issue") != null ? data.optJSONObject("Issue").optString("Source", "") : "", // 4
                    data.optJSONObject("Issue") != null ? data.optJSONObject("Issue").optString("Status", "") : "", // 5
                    data.optString("EventID", ""),                   // 6
                    data.optString("AnnouncedTime", ""),              // 7
                    data.optString("OriginTime", ""),                 // 8
                    data.optString("Hypocenter", ""),                 // 9
                    data.optString("MaxIntensity", ""),               // 10
                    data.optJSONObject("Accuracy") != null ? data.optJSONObject("Accuracy").optString("Epicenter", "") : "", // 11
                    data.optJSONObject("Accuracy") != null ? data.optJSONObject("Accuracy").optString("Depth", "") : "",     // 12
                    data.optJSONObject("Accuracy") != null ? data.optJSONObject("Accuracy").optString("Magnitude", "") : "", // 13
                    data.optJSONObject("MaxIntChange") != null ? data.optJSONObject("MaxIntChange").optString("String", "") : "", // 14
                    data.optJSONObject("MaxIntChange") != null ? data.optJSONObject("MaxIntChange").optString("Reason", "") : "", // 15
                    data.optJSONObject("WarnArea") != null ? data.optJSONObject("WarnArea").optString("Chiiki", "") : "",    // 16
                    data.optJSONObject("WarnArea") != null ? data.optJSONObject("WarnArea").optString("Shindo1", "") : "",   // 17
                    data.optJSONObject("WarnArea") != null ? data.optJSONObject("WarnArea").optString("Shindo2", "") : "",   // 18
                    data.optJSONObject("WarnArea") != null ? data.optJSONObject("WarnArea").optString("Time", "") : "",      // 19
                    data.optJSONObject("WarnArea") != null ? data.optJSONObject("WarnArea").optString("Type", "") : "",      // 20
                    data.optInt("Serial", 0),                        // 21
                    data.optDouble("Latitude", 0),                   // 22
                    data.optDouble("Longitude", 0),                  // 23
                    data.optDouble("Magunitude", 0),                 // 24
                    data.optDouble("Depth", 0),                      // 25
                    data.optJSONObject("WarnArea") != null && data.optJSONObject("WarnArea").optBoolean("Arrive", false), // 26
                    data.optBoolean("isSea", false),                 // 27
                    data.optBoolean("isTraining", false),            // 28
                    data.optBoolean("isAssumption", false),          // 29
                    data.optBoolean("isWarn", false),                // 30
                    data.optBoolean("isFinal", false),               // 31
                    data.optBoolean("isCancel", false),              // 32
                    data.optString("OriginalText", "")               // 33
            };
        } else {
            return new Object[]{
                    data.optString("type", ""),      // 1
                    data.optString("id", ""),        // 2
                    data.optString("timestamp", ""), // 3
                    data.optString("ver", "")        // 4
            };
        }
    }

    // Wolfx文字列変換
    public static String wolfxConverter(JSONObject data) throws JSONException {
        String type = data.optString("type", "");

        // heartbeat
        if ("heartbeat".equals(type)) {
            return "【システムハートビート】\n" +
                    "ID: " + data.optString("id", "不明") + "\n" +
                    "メッセージ: " + data.optString("message", "（なし）") + "\n";
        }

        // jma_eew
        if ("jma_eew".equals(type)) {
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
                            msg.append("地域: ").append(area.optString("Chiiki", "不明")).append("\n")
                                    .append("最大震度: ").append(area.optString("Shindo1", "不明"))
                                    .append(", 最小震度: ").append(area.optString("Shindo2", "不明")).append("\n")
                                    .append("発表時刻: ").append(area.optString("Time", "不明"))
                                    .append(", 種別: ").append(area.optString("Type", "不明"))
                                    .append(", 地震波到達: ").append(area.optBoolean("Arrive", false) ? "はい" : "いいえ").append("\n")
                                    .append("-------------------\n");
                        }
                    } else {
                        msg.append("警報対象地域: なし\n");
                    }
                } else {
                    msg.append("警報対象地域: なし\n");
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

            return msg.toString();
        }

        // 未対応タイプ
        return "【未対応のデータ】\nタイプ: " + type + "\n内容:\n" + data.toString(2);
    }
}
