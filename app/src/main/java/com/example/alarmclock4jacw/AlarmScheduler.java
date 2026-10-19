package com.example.alarmclock4jacw;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.List;

/**
 * 使用 AlarmManager.setAlarmClock 调度精确闹钟：
 * 该 API 不受 Doze 限制，且会在状态栏显示闹钟图标。
 */
public class AlarmScheduler {

    private static final String TAG = "AlarmScheduler";

    public static PendingIntent fireIntent(Context ctx, Alarm alarm) {
        Intent i = new Intent(ctx, AlarmReceiver.class);
        i.setAction(AlarmReceiver.ACTION_FIRE);
        i.putExtra(AlarmReceiver.EXTRA_ID, alarm.id);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getBroadcast(ctx, requestCode(alarm.id), i, flags);
    }

    private static int requestCode(long id) {
        return (int) (id & 0x7FFFFFFFL);
    }

    public static void schedule(Context ctx, Alarm alarm) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        // 先撤销旧的
        am.cancel(fireIntent(ctx, alarm));
        if (!alarm.enabled) return;

        long triggerAt = alarm.nextTriggerMillis(System.currentTimeMillis());
        if (triggerAt <= 0) return;

        PendingIntent pi = fireIntent(ctx, alarm);
        Intent show = new Intent(ctx, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent showPi = PendingIntent.getActivity(ctx, requestCode(alarm.id) + 7, show,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                // 系统未授予精确闹钟权限时降级，仍尽量保证能响
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(triggerAt, showPi), pi);
            }
            Log.d(TAG, "scheduled alarm " + alarm.timeText() + " at " + triggerAt);
        } catch (SecurityException e) {
            Log.w(TAG, "exact alarm denied, fallback", e);
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }
    }

    public static void cancel(Context ctx, Alarm alarm) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(fireIntent(ctx, alarm));
    }

    public static void scheduleAll(Context ctx) {
        List<Alarm> list = AlarmStore.get(ctx).all();
        for (Alarm a : list) schedule(ctx, a);
    }
}
