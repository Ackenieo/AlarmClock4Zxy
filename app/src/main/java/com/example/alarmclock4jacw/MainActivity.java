package com.example.alarmclock4jacw;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private LinearLayout container;
    private TextView subtitle;
    private AlarmStore store;

    private ActivityResultLauncher<Intent> editLauncher;
    private ActivityResultLauncher<String> notifPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        container = findViewById(R.id.alarm_container);
        subtitle = findViewById(R.id.subtitle);
        store = AlarmStore.get(this);

        editLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> render());

        notifPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (!granted) {
                        Toast.makeText(this, R.string.toast_perm_notification, Toast.LENGTH_LONG).show();
                    }
                });

        findViewById(R.id.fab_add).setOnClickListener(v ->
                editLauncher.launch(new Intent(this, AlarmEditActivity.class)));

        findViewById(R.id.title).setOnLongClickListener(v -> {
            showDebugMenu();
            return true;
        });

        // 列表一开始滚动，就把左滑展开的删除按钮收起来
        ScrollView scroll = findViewById(R.id.alarm_scroll);
        scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) ->
                SwipeRevealLayout.closeOpenRow());

        requestNotificationPermission();
        checkFullScreenIntent();
    }

    /**
     * Android 14+ 默认不给普通应用全屏通知权限，没有它锁屏时闹钟只会弹横幅。
     * 首次启动引导一次，用户拒绝过就不再打扰。
     */
    private void checkFullScreenIntent() {
        if (Build.VERSION.SDK_INT < 34) return;
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null || nm.canUseFullScreenIntent()) return;

        SharedPreferences prefs = getSharedPreferences("app", MODE_PRIVATE);
        if (prefs.getBoolean("fsi_prompted", false)) return;
        prefs.edit().putBoolean("fsi_prompted", true).apply();

        new AlertDialog.Builder(this)
                .setTitle(R.string.fsi_title)
                .setMessage(R.string.fsi_desc)
                .setNegativeButton(R.string.fsi_later, null)
                .setPositiveButton(R.string.fsi_open, (d, w) -> {
                    try {
                        startActivity(new Intent(
                                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                Uri.parse("package:" + getPackageName())));
                    } catch (Exception e) {
                        Toast.makeText(this, R.string.toast_exact_denied, Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        AlarmScheduler.scheduleAll(this);
        render();
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private void render() {
        List<Alarm> alarms = store.all();
        container.removeAllViews();

        if (alarms.isEmpty()) {
            View empty = LayoutInflater.from(this).inflate(R.layout.view_empty, container, false);
            container.addView(empty);
        } else {
            for (Alarm a : alarms) container.addView(buildItem(a));
        }
        updateSubtitle();
    }

    private void updateSubtitle() {
        long next = store.nextTriggerMillis();
        subtitle.setText(next > 0
                ? getString(R.string.next_alarm_fmt, describe(next))
                : getString(R.string.next_alarm_none));
    }

    private View buildItem(Alarm alarm) {
        SwipeRevealLayout row = (SwipeRevealLayout)
                LayoutInflater.from(this).inflate(R.layout.item_alarm, container, false);
        TextView time = row.findViewById(R.id.time);
        TextView detail = row.findViewById(R.id.detail);
        TextView reps = row.findViewById(R.id.reps);
        MaterialSwitch sw = row.findViewById(R.id.enabled_switch);

        time.setText(alarm.timeText());
        detail.setText(alarm.repeatText(this));
        reps.setText(alarm.taskSummary(this));

        row.setAlpha(alarm.enabled ? 1f : 0.45f);

        sw.setOnCheckedChangeListener(null);
        sw.setChecked(alarm.enabled);
        sw.setOnCheckedChangeListener((buttonView, checked) -> {
            alarm.enabled = checked;
            store.save(alarm);
            AlarmScheduler.schedule(this, alarm);
            row.setAlpha(checked ? 1f : 0.45f);
            updateSubtitle();
        });

        row.setOnContentClickListener(x -> {
            Intent i = new Intent(this, AlarmEditActivity.class);
            i.putExtra(AlarmEditActivity.EXTRA_ID, alarm.id);
            editLauncher.launch(i);
        });
        // 左滑露出垃圾桶，点一下直接删除
        row.setOnDeleteListener(() -> deleteAlarm(alarm, row));
        return row;
    }

    /** 左滑删除：不再二次确认，整行平滑收起后移除 */
    private void deleteAlarm(Alarm alarm, View row) {
        store.delete(alarm.id);
        AlarmScheduler.cancel(this, alarm);

        int height = row.getHeight();
        float startAlpha = row.getAlpha();
        if (height <= 0) {
            container.removeView(row);
            afterDelete();
            return;
        }

        ValueAnimator animator = ValueAnimator.ofFloat(1f, 0f);
        animator.setDuration(220);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            float f = (float) a.getAnimatedValue();
            ViewGroup.LayoutParams lp = row.getLayoutParams();
            lp.height = Math.round(height * f);
            row.setLayoutParams(lp);
            row.setAlpha(startAlpha * f);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                container.removeView(row);
                afterDelete();
            }
        });
        animator.start();
    }

    private void afterDelete() {
        Toast.makeText(this, R.string.toast_alarm_deleted, Toast.LENGTH_SHORT).show();
        if (store.all().isEmpty()) {
            render();
        } else {
            updateSubtitle();
        }
    }

    /** 把时间戳说成"今天 07:00 / 明天 07:00 / 周三 07:00" */
    public static String describe(long millis) {
        Calendar now = Calendar.getInstance();
        Calendar t = Calendar.getInstance();
        t.setTimeInMillis(millis);
        String hm = String.format(Locale.getDefault(), "%02d:%02d",
                t.get(Calendar.HOUR_OF_DAY), t.get(Calendar.MINUTE));

        Calendar today = (Calendar) now.clone();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.set(Calendar.MILLISECOND, 0);
        long days = (millis - today.getTimeInMillis()) / (24L * 3600 * 1000);

        if (days <= 0) return "今天 " + hm;
        if (days == 1) return "明天 " + hm;
        String[] names = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
        return names[t.get(Calendar.DAY_OF_WEEK) - 1] + " " + hm;
    }

    private void showDebugMenu() {
        String[] items = {getString(R.string.debug_test_10s),
                getString(R.string.debug_word_test), getString(R.string.debug_video)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.debug_menu)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        scheduleTestAlarm();
                    } else if (which == 1) {
                        scheduleWordTestAlarm();
                    } else {
                        startActivity(new Intent(this, VideoTestActivity.class));
                    }
                })
                .show();
    }

    /** 10 秒后触发一次测试响铃，方便验证整条链路 */
    private void scheduleTestAlarm() {
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent i = new Intent(this, AlarmReceiver.class);
        i.setAction(AlarmReceiver.ACTION_TEST);
        i.putExtra(AlarmReceiver.EXTRA_REPS, 10);
        PendingIntent pi = PendingIntent.getBroadcast(this, 9527, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long at = System.currentTimeMillis() + 10_000L;
        if (am != null) {
            try {
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, null), pi);
            } catch (SecurityException e) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            }
        }
        Toast.makeText(this, R.string.debug_testing_scheduled, Toast.LENGTH_SHORT).show();
    }

    /** 10 秒后触发一次背单词测试响铃（3 个单词），便于端到端验证 */
    private void scheduleWordTestAlarm() {
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent i = new Intent(this, AlarmReceiver.class);
        i.setAction(AlarmReceiver.ACTION_TEST);
        i.putExtra(AlarmReceiver.EXTRA_REPS, 10);
        i.putExtra(AlarmReceiver.EXTRA_WORDS, 3);
        i.putExtra(AlarmReceiver.EXTRA_TASK, Alarm.TASK_WORD_SPELL);
        PendingIntent pi = PendingIntent.getBroadcast(this, 9528, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long at = System.currentTimeMillis() + 10_000L;
        if (am != null) {
            try {
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, null), pi);
            } catch (SecurityException e) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            }
        }
        Toast.makeText(this, R.string.debug_testing_scheduled, Toast.LENGTH_SHORT).show();
    }
}
