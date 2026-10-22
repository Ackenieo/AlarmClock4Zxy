package com.example.alarmclock4jacw;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.content.ContextCompat;

/**
 * 闹钟到点的广播入口：更新重复状态 -> 拉起响铃前台服务。
 */
public class AlarmReceiver extends BroadcastReceiver {

    public static final String ACTION_FIRE = "com.example.alarmclock4jacw.action.FIRE";
    public static final String ACTION_TEST = "com.example.alarmclock4jacw.action.TEST";
    public static final String EXTRA_ID = "alarm_id";
    public static final String EXTRA_REPS = "reps";
    public static final String EXTRA_TASK = "task";
    public static final String EXTRA_WORDS = "words";
    private static final String TAG = "AlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            AlarmScheduler.scheduleAll(context);
            return;
        }

        // 调试用：不落库，直接响一次
        if (ACTION_TEST.equals(intent.getAction())) {
            Alarm fake = new Alarm();
            fake.id = -1L;
            fake.targetReps = intent.getIntExtra(EXTRA_REPS, 10);
            fake.taskType = intent.getStringExtra(EXTRA_TASK);
            if (!Alarm.isWordTask(fake.taskType)) fake.taskType = Alarm.TASK_JUMPING_JACK;
            fake.targetWords = intent.getIntExtra(EXTRA_WORDS, 10);
            java.util.Calendar c = java.util.Calendar.getInstance();
            fake.hour = c.get(java.util.Calendar.HOUR_OF_DAY);
            fake.minute = c.get(java.util.Calendar.MINUTE);
            try {
                ContextCompat.startForegroundService(context, AlarmService.startIntent(context, fake));
            } catch (Exception e) {
                Log.e(TAG, "test alarm failed", e);
            }
            return;
        }

        if (!ACTION_FIRE.equals(intent.getAction())) return;

        long id = intent.getLongExtra(EXTRA_ID, -1L);
        AlarmStore store = AlarmStore.get(context);
        Alarm alarm = store.find(id);
        if (alarm == null) {
            Log.w(TAG, "alarm " + id + " no longer exists");
            return;
        }

        // 重复闹钟：排下一次；一次性闹钟：关掉开关
        if (alarm.repeats()) {
            AlarmScheduler.schedule(context, alarm);
        } else {
            alarm.enabled = false;
            store.save(alarm);
            AlarmScheduler.cancel(context, alarm);
        }

        Intent svc = AlarmService.startIntent(context, alarm);
        try {
            ContextCompat.startForegroundService(context, svc);
        } catch (Exception e) {
            Log.e(TAG, "cannot start alarm service", e);
        }
    }
}
