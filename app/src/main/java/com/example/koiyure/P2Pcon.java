package com.example.koiyure;

import android.content.Context; // Contextをインポート
import org.json.JSONObject;
import org.json.JSONArray;

import java.util.Iterator;

public class P2Pcon {
    private TTScon ttsCon;
    private Context context; // Contextを保持するためのフィールドを追加

    /**
     * P2Pconのコンストラクタ
     * @param context アプリケーションのContext
     */
    public P2Pcon(Context context) {
        this.context = context;
        // TTSconを初期化。P2Pconのインスタンスが生成されるときにTTSも準備される。
        ttsCon = new TTScon(this.context);
    }


    /**
     * JSONデータを解析してテロップに変換し、TTSで読み上げる
     * @param json P2P地震情報のJSONオブジェクト
     * @return テロップ文字列
     */
    public String convertToTelop(JSONObject json) { // staticを削除
        int code = json.optInt("code");
        String telop;

        switch (code) {
            case 551:
                telop = formatJMAQuakeToTelop(json);
                break;
            case 552:
                telop = formatJMATsunamisToTelop(json);
                break;
            case 5520:
                telop = formatJMATsunamis5520ToTelop(json);
                break;
            case 554:
                telop = formatEEWDetectionToTelop(json);
                break;
            case 555:
                telop = formatAreapeersToTelop(json);
                break;
            case 556:
                telop = formatEEWToTelop(json);
                break;
            case 561:
                telop = formatUserquakeToTelop(json);
                break;
            case 9611:
                telop = formatUserquakeEvaluationToTelop(json);
                break;
            default:
                telop = "不明なコード: " + code + "\n内容: " + json.toString();
                break;
        }

        // P2Pピア分布情報(555)以外は読み上げる
        // ttsConインスタンスのisInitializedメソッドで初期化状態を確認
        if (code != 555) {
            if (ttsCon.isInitialized()) { // TTSconのisInitializedメソッドを使用
                ttsCon.speak(telop);
            } else {
                System.out.println("TTS未初期化: " + telop);
                // TTSが未初期化の場合のハンドリング。
                // 例: ログ出力のみ、あるいは後で初期化されたときに読み上げるキューに保存するなど
            }
        }

        return telop;
    }

    /**
     * P2Pconで使用しているTTSconインスタンスを返します。
     * 必要に応じて、アクティビティのonDestroyなどでTTSをシャットダウンするために使用できます。
     * @return このP2Pconインスタンスが使用するTTSconオブジェクト
     */
    public TTScon getTtsCon() {
        return ttsCon;
    }

    // ---- 地震情報（551）----
    private String formatJMAQuakeToTelop(JSONObject jmaQuake) { // staticを削除
        StringBuilder sb = new StringBuilder();
        sb.append("【地震情報】\n");
        sb.append("受信日時: ").append(jmaQuake.optString("time", "不明")).append("\n");

        JSONObject issue = jmaQuake.optJSONObject("issue");
        if (issue != null) {
            sb.append("発表元: ").append(issue.optString("source", "不明")).append("\n");
            sb.append("発表日時: ").append(issue.optString("time", "不明")).append("\n");
            sb.append("発表種類: ").append(issueTypeToString(issue.optString("type", "不明"))).append("\n");
            sb.append("訂正: ").append(issueCorrectToString(issue.optString("correct", "不明"))).append("\n");
        }

        JSONObject eq = jmaQuake.optJSONObject("earthquake");
        if (eq != null) {
            sb.append("\n発生時刻: ").append(eq.optString("time", "不明")).append("\n");
            JSONObject hypo = eq.optJSONObject("hypocenter");
            if (hypo != null) {
                sb.append("震源: ").append(hypo.optString("name", "不明")).append("\n");
                sb.append("緯度: ").append(hypo.optDouble("latitude", -200)).append("\n");
                sb.append("経度: ").append(hypo.optDouble("longitude", -200)).append("\n");
                sb.append("深さ: ").append(depthToString(hypo.optInt("depth", -1))).append("\n");
                sb.append("マグニチュード: ").append(magToString(hypo.optDouble("magnitude", -1))).append("\n");
            }
            sb.append("最大震度: ").append(scaleToString(eq.optInt("maxScale", -1))).append("\n");
            sb.append("国内津波: ").append(domesticTsunamiToString(eq.optString("domesticTsunami", "不明"))).append("\n");
            sb.append("海外津波: ").append(foreignTsunamiToString(eq.optString("foreignTsunami", "不明"))).append("\n");
        }

        JSONArray points = jmaQuake.optJSONArray("points");
        if (points != null && points.length() > 0) {
            sb.append("\n【震度観測点】\n");
            for (int i = 0; i < points.length(); i++) {
                JSONObject p = points.optJSONObject(i);
                if (p == null) continue;
                sb.append(p.optString("pref", ""))
                        .append(" ")
                        .append(p.optString("addr", ""))
                        .append("：")
                        .append(scaleToString(p.optInt("scale", -1)))
                        .append(p.optBoolean("isArea", false) ? "（区域）" : "")
                        .append("\n");
            }
        }

        JSONObject comments = jmaQuake.optJSONObject("comments");
        if (comments != null) {
            String text = comments.optString("freeFormComment", "");
            if (!text.isEmpty()) sb.append("\n【付加文】\n").append(text).append("\n");
        }

        return sb.toString();
    }

    // ---- 津波予報（552）----
    private String formatJMATsunamisToTelop(JSONObject json) { // staticを削除
        StringBuilder sb = new StringBuilder();
        sb.append("【津波予報】\n");
        sb.append("受信日時: ").append(json.optString("time", "不明")).append("\n");
        sb.append("解除: ").append(json.optBoolean("cancelled", false) ? "はい" : "いいえ").append("\n");

        JSONObject issue = json.optJSONObject("issue");
        if (issue != null) {
            sb.append("発表元: ").append(issue.optString("source", "不明")).append("\n");
            sb.append("発表日時: ").append(issue.optString("time", "不明")).append("\n");
            sb.append("発表種類: ").append(issueTypeToString(issue.optString("type", "不明"))).append("\n");
        }

        JSONArray areas = json.optJSONArray("areas");
        if (areas != null && areas.length() > 0) {
            sb.append("\n【津波予報区域】\n");
            for (int i = 0; i < areas.length(); i++) {
                JSONObject a = areas.optJSONObject(i);
                if (a == null) continue;
                sb.append(a.optString("name", "不明")).append("：")
                        .append(tsunamiGradeToString(a.optString("grade", "不明")));
                if (a.optBoolean("immediate", false)) sb.append("（直ちに来襲）");
                JSONObject fh = a.optJSONObject("firstHeight");
                if (fh != null)
                    sb.append("\n  第1波到達予想: ").append(fh.optString("arrivalTime", "不明"))
                            .append(" / 状況: ").append(tsunamiConditionToString(fh.optString("condition", "不明")));
                JSONObject mh = a.optJSONObject("maxHeight");
                if (mh != null)
                    sb.append("\n  予想高さ: ").append(mh.optString("description", "不明"))
                            .append(" (").append(mh.optDouble("value", -1)).append("m)");
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    // ---- 津波解除（5520）----
    private String formatJMATsunamis5520ToTelop(JSONObject json) { // staticを削除
        return "【津波予報解除】\n受信日時: " + json.optString("time", "不明");
    }

    // ---- 緊急地震速報発表検出（554）----
    private String formatEEWDetectionToTelop(JSONObject json) { // staticを削除
        return "【緊急地震速報 発表検出】\n検出種類: " + eewDetectionTypeToString(json.optString("type", "不明")) +
                "\n受信日時: " + json.optString("time", "不明");
    }

    // ---- P2Pピア分布（555）----
    private String formatAreapeersToTelop(JSONObject json) { // staticを削除
        JSONArray areas = json.optJSONArray("areas");
        StringBuilder sb = new StringBuilder();
        sb.append("【P2P地震情報ネットワーク】\n受信日時: ").append(json.optString("time", "不明")).append("\n");
        if (areas != null) {
            sb.append("接続地域数: ").append(areas.length()).append("\n");
        }
        return sb.toString();
    }

    // ---- 緊急地震速報（556）----
    private String formatEEWToTelop(JSONObject json) { // staticを削除
        StringBuilder sb = new StringBuilder();
        sb.append("【緊急地震速報（警報）】\n");
        sb.append("受信日時: ").append(json.optString("time", "不明")).append("\n");
        sb.append("テスト配信: ").append(json.optBoolean("test", false) ? "はい" : "いいえ").append("\n");
        sb.append("取消: ").append(json.optBoolean("cancelled", false) ? "はい" : "いいえ").append("\n");

        JSONObject issue = json.optJSONObject("issue");
        if (issue != null) {
            sb.append("発表時刻: ").append(issue.optString("time", "不明")).append("\n");
            sb.append("イベントID: ").append(issue.optString("eventId", "不明")).append("\n");
            sb.append("情報番号: ").append(issue.optString("serial", "不明")).append("\n");
        }

        JSONObject eq = json.optJSONObject("earthquake");
        if (eq != null) {
            sb.append("\n【地震情報】\n");
            sb.append("発生時刻: ").append(eq.optString("originTime", "不明")).append("\n");
            sb.append("発現時刻: ").append(eq.optString("arrivalTime", "不明")).append("\n");
            sb.append("条件: ").append(eq.optString("condition", "不明")).append("\n");
            JSONObject hypo = eq.optJSONObject("hypocenter");
            if (hypo != null) {
                sb.append("震源: ").append(hypo.optString("name", "不明")).append("\n");
                sb.append("緯度: ").append(hypo.optDouble("latitude", -200)).append("\n");
                sb.append("経度: ").append(hypo.optDouble("longitude", -200)).append("\n");
                sb.append("深さ: ").append(depthToString((int) hypo.optDouble("depth", -1))).append("\n");
                sb.append("マグニチュード: ").append(magToString(hypo.optDouble("magnitude", -1))).append("\n");
            }
        }

        JSONArray areas = json.optJSONArray("areas");
        if (areas != null && areas.length() > 0) {
            sb.append("\n【予測区域】\n");
            for (int i = 0; i < areas.length(); i++) {
                JSONObject a = areas.optJSONObject(i);
                if (a == null) continue;
                sb.append(a.optString("pref", ""))
                        .append(" ").append(a.optString("name", ""))
                        .append("：")
                        .append(scaleToString(a.optInt("scaleFrom", -1)))
                        .append("〜")
                        .append(scaleToString(a.optInt("scaleTo", -1)))
                        .append("（主要動予測: ")
                        .append(a.optString("arrivalTime", "不明")).append("）\n");
            }
        }
        return sb.toString();
    }

    // ---- 地震感知情報（561）----
    private String formatUserquakeToTelop(JSONObject json) { // staticを削除
        return "【地震感知情報】\n地域コード: " + json.optInt("area", -1) +
                "\n受信日時: " + json.optString("time", "不明");
    }

    // ---- 感知評価（9611）----
    private String formatUserquakeEvaluationToTelop(JSONObject json) { // staticを削除
        StringBuilder sb = new StringBuilder();
        sb.append("【地震感知情報評価結果】\n");
        sb.append("評価日時: ").append(json.optString("time", "不明")).append("\n");
        sb.append("件数: ").append(json.optInt("count", -1)).append("\n");
        sb.append("信頼度: ").append(json.optDouble("confidence", 0)).append("\n");
        sb.append("開始日時: ").append(json.optString("started_at", "不明")).append("\n");
        sb.append("更新日時: ").append(json.optString("updated_at", "不明")).append("\n");

        JSONObject areaConfs = json.optJSONObject("area_confidences");
        if (areaConfs != null) {
            sb.append("\n【地域別信頼度】\n");
            Iterator<String> keys = areaConfs.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject a = areaConfs.optJSONObject(key);
                if (a == null) continue;
                sb.append("地域コード ").append(key)
                        .append("： 信頼度 ").append(a.optDouble("confidence", 0))
                        .append(" / 件数 ").append(a.optInt("count", 0))
                        .append(" / 表示 ").append(a.optString("display", "不明"))
                        .append("\n");
            }
        }
        return sb.toString();
    }

    // ---- 共通補助メソッド ----
    // これらはTTSConに直接依存しないため、もし外部からも利用する可能性があるならstaticに戻しても良いですが、
    // 現在の設計ではP2Pconインスタンスからのみ利用されるため、非staticのままでも問題ありません。
    private String scaleToString(int scale) {
        switch (scale) {
            case 10: return "震度1";
            case 20: return "震度2";
            case 30: return "震度3";
            case 40: return "震度4";
            case 45: return "震度5弱";
            case 50: return "震度5強";
            case 55: return "震度6弱";
            case 60: return "震度6強";
            case 70: return "震度7";
            default: return "不明";
        }
    }

    private String depthToString(int depth) {
        if (depth == 0) return "ごく浅い";
        if (depth == -1) return "不明";
        return depth + "km";
    }

    private String magToString(double mag) {
        if (mag < 0) return "不明";
        return "M" + String.format("%.1f", mag);
    }

    // ---- Enum変換メソッド ----
    private String issueTypeToString(String type) {
        switch (type) {
            case "ScalePrompt": return "震度速報";
            case "Destination": return "震源に関する情報";
            case "ScaleAndDestination": return "震度・震源に関する情報";
            case "DetailScale": return "各地の震度に関する情報";
            case "Foreign": return "遠地地震に関する情報";
            case "Other": return "その他の情報";
            default: return type;
        }
    }

    private String issueCorrectToString(String correct) {
        switch (correct) {
            case "None": return "なし";
            case "Unknown": return "不明";
            case "ScaleOnly": return "震度";
            case "DestinationOnly": return "震源";
            case "ScaleAndDestination": return "震度・震源";
            default: return correct;
        }
    }

    private String domesticTsunamiToString(String value) {
        switch (value) {
            case "None": return "なし";
            case "Unknown": return "不明";
            case "Checking": return "調査中";
            case "NonEffective": return "若干の海面変動が予想されるが、被害の心配なし";
            case "Watch": return "津波注意報";
            case "Warning": return "津波予報（種類不明）";
            default: return value;
        }
    }

    private String foreignTsunamiToString(String value) {
        switch (value) {
            case "None": return "なし";
            case "Unknown": return "不明";
            case "Checking": return "調査中";
            case "NonEffectiveNearby": return "震源近傍で小さな津波の可能性（被害の心配なし）";
            case "WarningNearby": return "震源近傍で津波の可能性";
            case "WarningPacific": return "太平洋で津波の可能性";
            case "WarningPacificWide": return "太平洋広域で津波の可能性";
            case "WarningIndian": return "インド洋で津波の可能性";
            case "WarningIndianWide": return "インド洋広域で津波の可能性";
            case "Potential": return "この規模では津波の可能性あり";
            default: return value;
        }
    }

    private String tsunamiGradeToString(String value) {
        switch (value) {
            case "MajorWarning": return "大津波警報";
            case "Warning": return "津波警報";
            case "Watch": return "津波注意報";
            case "Unknown": return "不明";
            default: return value;
        }
    }

    private String tsunamiConditionToString(String value) {
        switch (value) {
            case "ただちに津波来襲と予測": return "ただちに津波来襲と予測";
            case "津波到達中と推測": return "津波到達中と推測";
            case "第１波の到達を確認": return "第１波の到達を確認";
            default: return value;
        }
    }

    private String eewDetectionTypeToString(String type) {
        switch (type) {
            case "Full": return "チャイム＋音声";
            case "Chime": return "チャイム (未実装)";
            default: return type;
        }
    }
}