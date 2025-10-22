package com.example.koiyure;

import org.json.JSONObject;
import org.json.JSONArray;


public class P2Pcon {

    public static String convertToTelop(JSONObject json) {
        int code = json.optInt("code");

        switch (code) {
            case 551:
                return "\n古明地こいし: 地震情報が来たよ！\n\n" + formatJMAQuakeToTelop(json);
            case 552:
                return "\n古明地こいし: 津波予報が発表されたよ！海の近くの人は気をつけてね！\n\n" + formatJMATsunamisToTelop(json);
            case 5520:
                return "\n古明地こいし: 津波情報が解除されたよ！やったね！\n\n" + formatJMATsunamis5520ToTelop(json);
            case 554:
                return "\n古明地こいし: 緊急地震速報を検出したよ！揺れに備えて！\n\n" + formatEEWDetectionToTelop(json);
            case 555:
                return "\n古明地こいし: 各地域の接続状況をチェック中だよ～\n\n" + formatAreapeersToTelop(json);
            case 556:
                return "\n古明地こいし: 緊急地震速報（警報）だよ！強い揺れが来るから身を守って！\n\n" + formatEEWToTelop(json);
            case 561:
                return "\n古明地こいし: 地震を感じた人がいるみたい！情報を集めてるよ！\n\n" + formatUserquakeToTelop(json);
            case 9611:
                return "\n古明地こいし: 地震感知情報の解析結果が出たよ！\n\n" + formatUserquakeEvaluationToTelop(json);
            default:
                return "不明なコード: " + code;
        }
    }

    private static String formatJMAQuakeToTelop(JSONObject jmaQuake) {
        JSONObject issue = jmaQuake.optJSONObject("issue");
        JSONObject earthquake = jmaQuake.optJSONObject("earthquake");

        String receivedTime = jmaQuake.optString("time", "不明");
        String issueSource = issue != null ? issue.optString("source", "不明") : "不明";
        String issueTime = issue != null ? issue.optString("time", "不明") : "不明";
        String quakeTime = earthquake != null ? earthquake.optString("time", "不明") : "不明";

        String hypoName = earthquake != null && earthquake.has("hypocenter") ?
                earthquake.optJSONObject("hypocenter").optString("name", "不明") : "不明";

        double mag = earthquake != null && earthquake.has("hypocenter") ?
                earthquake.optJSONObject("hypocenter").optDouble("magnitude", -1) : -1;

        String magnitude = (mag == -1) ? "不明" : "M" + String.format("%.1f", mag);

        return "【地震情報】\n" +
                "受信日時: " + receivedTime + "\n" +
                "発表元: " + issueSource + "\n" +
                "発表日時: " + issueTime + "\n\n" +
                "発生時刻: " + quakeTime + "\n" +
                "震源: " + hypoName + "\n" +
                "マグニチュード: " + magnitude;
    }

    private static String formatJMATsunamisToTelop(JSONObject json) {
        String receivedTime = json.optString("time", "不明");
        JSONObject issue = json.optJSONObject("issue");
        String issueSource = issue != null ? issue.optString("source", "不明") : "不明";
        String issueTime = issue != null ? issue.optString("time", "不明") : "不明";

        return "【津波予報】\n" +
                "受信日時: " + receivedTime + "\n" +
                "発表元: " + issueSource + "\n" +
                "発表日時: " + issueTime;
    }

    private static String formatJMATsunamis5520ToTelop(JSONObject json) {
        return "【津波予報解除】\n受信日時: " + json.optString("time", "不明");
    }

    private static String formatUserquakeToTelop(JSONObject json) {
        return "【地震感知情報】\n地域: " + json.optString("area", "不明") + "\n受信日時: " + json.optString("time", "不明");
    }

    private static String formatAreapeersToTelop(JSONObject json) {
        return "【P2P地震情報ネットワーク】\n受信日時: " + json.optString("time", "不明");
    }

    private static String formatEEWDetectionToTelop(JSONObject json) {
        return "【緊急地震速報 発表検出】\n検出種類: " + json.optString("type", "不明") + "\n受信日時: " + json.optString("time", "不明");
    }

    private static String formatEEWToTelop(JSONObject json) {
        return "【緊急地震速報】\n受信日時: " + json.optString("time", "不明");
    }

    private static String formatUserquakeEvaluationToTelop(JSONObject json) {
        return "【地震感知情報評価結果】\n評価日時: " + json.optString("time", "不明");
    }
}
