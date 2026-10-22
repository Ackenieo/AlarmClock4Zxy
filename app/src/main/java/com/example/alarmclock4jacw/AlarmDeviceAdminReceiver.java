package com.example.alarmclock4jacw;

import android.app.admin.DeviceAdminReceiver;

/**
 * 设备管理员声明：仅用于通过 adb 换取 Device Owner 身份，
 * 从而在响铃期间进入真正的锁定任务（Kiosk）模式。
 */
public class AlarmDeviceAdminReceiver extends DeviceAdminReceiver {
}
