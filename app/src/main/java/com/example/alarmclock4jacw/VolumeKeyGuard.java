package com.example.alarmclock4jacw;

import android.app.Activity;
import android.view.KeyEvent;
import android.widget.Toast;

/**
 * 响铃 / 运动期间的音量键守卫：把音量键变成废键。
 * 服务侧每秒会把闹钟音量重新拉满，这里再消费掉按键，
 * 免得用户以为按了能调小声音。
 */
final class VolumeKeyGuard {

    private VolumeKeyGuard() {
    }

    /** 首次按下时提示音量已锁定，随后的长按重复事件只吞掉不刷屏。 */
    static boolean onKeyDown(Activity activity, int keyCode, KeyEvent event) {
        if (!swallow(keyCode)) {
            return false;
        }
        if (event.getRepeatCount() == 0) {
            Toast.makeText(activity, R.string.ringing_volume_locked, Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    /** 音量类按键一律吞掉。 */
    static boolean swallow(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_VOLUME_UP
                || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE;
    }
}
