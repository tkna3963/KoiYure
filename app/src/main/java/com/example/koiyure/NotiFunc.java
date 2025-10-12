package com.example.koiyure;

import android.app.NotificationManager;
import android.app.NotificationChannel;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.Build;

import androidx.core.app.NotificationCompat;

public class NotiFunc {

    private static final String CHANNEL_ID = "default_channel";
    private static final String CHANNEL_NAME = "Default Channel";

    /** 共通：通知チャンネル作成 */
    private static void createChannelIfNeeded(NotificationManager manager, int importance) {
        if (manager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel existing = manager.getNotificationChannel(CHANNEL_ID);
            if (existing == null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        CHANNEL_NAME,
                        importance
                );
                manager.createNotificationChannel(channel);
            }
        }
    }

    /** 通常通知 */
    public static void showNotification(Context context, String title, String message, int id) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        createChannelIfNeeded(nm, NotificationManager.IMPORTANCE_DEFAULT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.koiyuresikakuicon)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true);

        nm.notify(id, builder.build());
    }

    /** プログレス通知 */
    public static void showProgressNotification(Context context, String title, int progress, int id) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        createChannelIfNeeded(nm, NotificationManager.IMPORTANCE_LOW);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.koiyuresikakuicon)
                .setContentTitle(title)
                .setContentText("Downloading...")
                .setProgress(100, progress, false)
                .setAutoCancel(false);

        nm.notify(id, builder.build());
    }

    /** BigText通知 */
    public static void showBigTextNotification(Context context, String title, String bigMessage, int id) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        createChannelIfNeeded(nm, NotificationManager.IMPORTANCE_DEFAULT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.koiyuresikakuicon)
                .setContentTitle(title)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(bigMessage))
                .setAutoCancel(true);

        nm.notify(id, builder.build());
    }

    /** 画像付き通知 */
    public static void showBigPictureNotification(Context context, String title, String message, int drawableId, int id) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        createChannelIfNeeded(nm, NotificationManager.IMPORTANCE_DEFAULT);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigPictureStyle()
                        .bigPicture(BitmapFactory.decodeResource(context.getResources(), drawableId)))
                .setAutoCancel(true);

        nm.notify(id, builder.build());
    }

    /** 常時通知（消せない） */
    public static void showOngoingNotification(Context context, String title, String message, int id) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        createChannelIfNeeded(nm, NotificationManager.IMPORTANCE_LOW);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setOngoing(true)
                .setAutoCancel(false);

        nm.notify(id, builder.build());
    }

    /** 常時通知更新 */
    public static void updateOngoingNotification(Context context, String title, String message, int id) {
        showOngoingNotification(context, title, message, id);
    }
}
