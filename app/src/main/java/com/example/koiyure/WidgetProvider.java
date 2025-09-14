package com.example.koiyure;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class WidgetProvider extends AppWidgetProvider {

    private static String lastReceivedData = "データなし";

    public static void updateWidget(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);

            // 現在時刻を取得
            String currentTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(System.currentTimeMillis());
            views.setTextViewText(R.id.widget_time, "現在時刻: " + currentTime);

            // 最後に受信したデータを表示
            views.setTextViewText(R.id.widget_last_data, "最後のデータ: " + lastReceivedData);

            // ウィジェットを更新
            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        updateWidget(context, appWidgetManager, appWidgetIds);
    }

    public static void setLastReceivedData(String data) {
        lastReceivedData = data;
    }
}