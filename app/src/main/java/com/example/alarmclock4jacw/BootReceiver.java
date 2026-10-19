package com.example.alarmclock4jacw;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 开机、应用升级、改时间/时区之后重新登记所有闹钟（AlarmManager 的闹钟在重启后会丢失）。
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        AlarmScheduler.scheduleAll(context);
    }
}
