package com.example.koiyure;

import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;

import java.util.List;

/**
 * サービスの状態を確認するためのユーティリティクラス。
 */
public class ServiceUtils {
    private static final String TAG = "ServiceUtils";

    /**
     * 指定されたサービスが現在実行中であるかどうかをチェックします。
     *
     * @param context アプリケーションのコンテキスト。
     * @param serviceClass チェックするサービスのClassオブジェクト。
     * @return サービスが実行中であればtrue、そうでなければfalse。
     */
    public static boolean isServiceRunning(Context context, Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) {
            Log.e(TAG, "ActivityManagerが取得できません。サービス状態を確認できません。");
            return false;
        }

        // 実行中のすべてのサービスを取得
        List<ActivityManager.RunningServiceInfo> services = manager.getRunningServices(Integer.MAX_VALUE);
        if (services == null) {
            Log.e(TAG, "実行中のサービスが見つかりません。");
            return false;
        }

        // 実行中のサービスリストを走査し、指定されたサービス名と一致するか確認
        for (ActivityManager.RunningServiceInfo service : services) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                Log.d(TAG, serviceClass.getSimpleName() + " は実行中です。");
                return true;
            }
        }
        Log.d(TAG, serviceClass.getSimpleName() + " は実行されていません。");
        return false;
    }
}
