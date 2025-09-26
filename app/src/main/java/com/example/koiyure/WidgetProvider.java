package com.example.koiyure;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.util.Log;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class WidgetProvider extends AppWidgetProvider {

    private static final String TAG = "WidgetProvider";
    private static String lastReceivedData = "データなし";

    public static void updateWidget(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.d(TAG, "ウィジェット更新開始: " + lastReceivedData);
        for (int appWidgetId : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);

            // 現在時刻を取得
            String currentTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                    .format(System.currentTimeMillis());
            views.setTextViewText(R.id.widget_time, "現在時刻: " + currentTime);

            // 最後に受信したデータを表示
            views.setTextViewText(R.id.widget_last_data, "最後のデータ: " + lastReceivedData);

            // ウィジェットを更新
            appWidgetManager.updateAppWidget(appWidgetId, views);
            Log.d(TAG, "ウィジェットID " + appWidgetId + " を更新しました。");
        }
        Log.d(TAG, "ウィジェット更新完了");
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.d(TAG, "onUpdate呼び出し: ウィジェットを更新します。");
        updateWidget(context, appWidgetManager, appWidgetIds);
    }

    public static void setLastReceivedData(String data) {
        lastReceivedData = data;
        Log.d(TAG, "最後に受信したデータを更新: " + data);
    }
}
