package com.example.alarmclock4jacw;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.util.Log;

/**
 * 锁定任务（Kiosk）控制。
 * 只有作为设备所有者（Device Owner）时才能把应用真正锁死；
 * 其他情况下静默降级，交给按键拦截 + 服务看门狗兜底。
 */
final class KioskController {

    private static final String TAG = "KioskController";

    private KioskController() {
    }

    /** 设备所有者下把本应用加入锁定任务白名单，并关掉 Home / 概览 / 通知栏等出入口。 */
    static void allowLockTask(Context context) {
        DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
        if (dpm == null || !dpm.isDeviceOwnerApp(context.getPackageName())) {
            return;
        }
        ComponentName admin = new ComponentName(context, AlarmDeviceAdminReceiver.class);
        try {
            dpm.setLockTaskPackages(admin, new String[]{context.getPackageName()});
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE);
            }
        } catch (SecurityException | IllegalArgumentException e) {
            Log.w(TAG, "无法配置锁定任务白名单，降级为普通拦截", e);
        }
    }

    /** 进入锁定任务；未获授权时可能抛异常，直接静默降级。 */
    static void enterLockTask(Activity activity) {
        ActivityManager am = activity.getSystemService(ActivityManager.class);
        if (am != null && am.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE) {
            return;
        }
        try {
            activity.startLockTask();
        } catch (SecurityException | IllegalArgumentException e) {
            Log.w(TAG, "进入锁定任务失败，降级为普通拦截", e);
        }
    }

    /** 退出锁定任务，否则任务无法结束。 */
    static void exitLockTask(Activity activity) {
        ActivityManager am = activity.getSystemService(ActivityManager.class);
        if (am == null || am.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_NONE) {
            return;
        }
        try {
            activity.stopLockTask();
        } catch (SecurityException | IllegalArgumentException e) {
            Log.w(TAG, "退出锁定任务失败", e);
        }
    }
}
