package com.example.koiyure;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Settings {
    private static final String TAG = "Settings";
    private static final String PREFS_NAME = "KoiyureSettings";

    private SharedPreferences prefs;
    private Context context;

    public Settings(Context context) {
        this.context = context;
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 任意の値を自動判別して保存
     * 文字列、数値、boolean、JSONオブジェクト、JSON配列すべて対応
     */
    public void set(String key, String value) {
        if (value == null) {
            prefs.edit().putString(key, null).apply();
            return;
        }

// JSON形式で保存（型情報を保持）
        try {
            JSONObject wrapper = new JSONObject();

// 値の型を自動判別
            if (value.equals("true") || value.equals("false")) {
// boolean
                wrapper.put("type", "boolean");
                wrapper.put("value", Boolean.parseBoolean(value));
            } else if (isInteger(value)) {
// 整数
                wrapper.put("type", "int");
                wrapper.put("value", Long.parseLong(value));
            } else if (isDecimal(value)) {
// 小数
                wrapper.put("type", "float");
                wrapper.put("value", Double.parseDouble(value));
            } else if (isJSONObject(value)) {
// JSONオブジェクト
                wrapper.put("type", "object");
                wrapper.put("value", new JSONObject(value));
            } else if (isJSONArray(value)) {
// JSON配列
                wrapper.put("type", "array");
                wrapper.put("value", new JSONArray(value));
            } else {
// 文字列
                wrapper.put("type", "string");
                wrapper.put("value", value);
            }

            prefs.edit().putString(key, wrapper.toString()).apply();
            Log.d(TAG, "保存成功: " + key + " = " + value + " (type: " + wrapper.getString("type") + ")");
        } catch (Exception e) {
// JSON化に失敗したら文字列として保存
            Log.w(TAG, "JSON化失敗、文字列として保存: " + key, e);
            prefs.edit().putString(key, value).apply();
        }
    }

    /**
     * 保存された値を元の型で取得
     */
    public String get(String key, String defaultValue) {
        String stored = prefs.getString(key, null);
        if (stored == null) {
            return defaultValue;
        }

        try {
            JSONObject wrapper = new JSONObject(stored);
            String type = wrapper.getString("type");

            switch (type) {
                case "boolean":
                    return String.valueOf(wrapper.getBoolean("value"));
                case "int":
                    return String.valueOf(wrapper.getLong("value"));
                case "float":
                    return String.valueOf(wrapper.getDouble("value"));
                case "object":
                    return wrapper.getJSONObject("value").toString();
                case "array":
                    return wrapper.getJSONArray("value").toString();
                case "string":
                default:
                    return wrapper.getString("value");
            }
        } catch (JSONException e) {
// JSON形式でない場合は文字列としてそのまま返す
            Log.d(TAG, "JSON形式でない値を取得: " + key);
            return stored;
        }
    }

    /**
     * キーが存在するか確認
     */
    public boolean contains(String key) {
        return prefs.contains(key);
    }

    /**
     * 特定のキーを削除
     */
    public void remove(String key) {
        prefs.edit().remove(key).apply();
        Log.d(TAG, "削除: " + key);
    }

    /**
     * すべての設定をクリア
     */
    public void clearAll() {
        prefs.edit().clear().apply();
        Log.d(TAG, "すべての設定をクリア");
    }

    // ヘルパーメソッド
    private boolean isInteger(String str) {
        try {
            Long.parseLong(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isDecimal(String str) {
        try {
            Double.parseDouble(str);
            return str.contains(".");
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isJSONObject(String str) {
        try {
            new JSONObject(str);
            return str.trim().startsWith("{");
        } catch (JSONException e) {
            return false;
        }
    }

    private boolean isJSONArray(String str) {
        try {
            new JSONArray(str);
            return str.trim().startsWith("[");
        } catch (JSONException e) {
            return false;
        }
    }
}