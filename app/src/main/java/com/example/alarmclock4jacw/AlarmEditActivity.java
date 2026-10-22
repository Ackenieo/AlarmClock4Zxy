package com.example.alarmclock4jacw;

import android.app.Dialog;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.slider.Slider;

import java.util.Calendar;
import java.util.Locale;

public class AlarmEditActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "alarm_id";

    private static final String[] DAY_LABELS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private Alarm alarm;
    private boolean isNew;
    private AlarmStore store;

    private NumberPicker hourPicker;
    private NumberPicker minutePicker;
    private Slider repsSlider;
    private TextView repsValue;
    private TextView repeatSummary;

    /** 重复掩码，bit0 = 周一 … bit6 = 周日，0 = 只响一次 */
    private int repeatMask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_alarm);

        store = AlarmStore.get(this);

        long id = getIntent().getLongExtra(EXTRA_ID, -1L);
        Alarm existing = id > 0 ? store.find(id) : null;
        if (existing != null) {
            alarm = existing.copy();
            isNew = false;
        } else {
            alarm = new Alarm();
            Calendar c = Calendar.getInstance();
            c.add(Calendar.HOUR_OF_DAY, 1);
            alarm.hour = c.get(Calendar.HOUR_OF_DAY);
            alarm.minute = 0;
            isNew = true;
        }
        repeatMask = alarm.repeatMask;

        TextView title = findViewById(R.id.screen_title);
        title.setText(isNew ? R.string.new_alarm : R.string.edit_alarm);

        hourPicker = findViewById(R.id.picker_hour);
        minutePicker = findViewById(R.id.picker_minute);
        repsSlider = findViewById(R.id.reps_slider);
        repsValue = findViewById(R.id.reps_value);
        repeatSummary = findViewById(R.id.repeat_summary);

        setupPickers();
        setupRepeatRow();
        setupSlider();

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_save).setOnClickListener(v -> save());

        if (!isNew) {
            findViewById(R.id.btn_delete).setVisibility(View.VISIBLE);
            findViewById(R.id.btn_delete).setOnClickListener(v -> confirmDelete());
        }
    }

    private void setupPickers() {
        hourPicker.setMinValue(0);
        hourPicker.setMaxValue(23);
        hourPicker.setFormatter(v -> String.format(Locale.getDefault(), "%02d", v));
        hourPicker.setValue(alarm.hour);
        hourPicker.setWrapSelectorWheel(true);

        minutePicker.setMinValue(0);
        minutePicker.setMaxValue(59);
        minutePicker.setFormatter(v -> String.format(Locale.getDefault(), "%02d", v));
        minutePicker.setValue(alarm.minute);
        minutePicker.setWrapSelectorWheel(true);

        // 选中高亮已经由布局里的圆角条负责，滚轮自带的上下两条分割线不需要
        hideDivider(hourPicker);
        hideDivider(minutePicker);

        if (Build.VERSION.SDK_INT >= 29) {
            int color = getColor(R.color.text_primary);
            hourPicker.setTextColor(color);
            minutePicker.setTextColor(color);
            hourPicker.setTextSize(44);
            minutePicker.setTextSize(44);
        }
    }

    private void hideDivider(NumberPicker picker) {
        // mSelectionDivider 是 final 字段，反射改不动；新版框架提供了公开的
        // setSelectionDividerHeight()，把高度设成 0 就不会再画上下两条线。
        try {
            java.lang.reflect.Method m =
                    NumberPicker.class.getMethod("setSelectionDividerHeight", int.class);
            m.invoke(picker, 0);
        } catch (Throwable ignored) {
            // 老系统没有这个方法：保留原生分割线，不影响功能
        }
    }

    private void setupRepeatRow() {
        updateRepeatSummary();
        findViewById(R.id.repeat_row).setOnClickListener(v -> showRepeatDialog());
    }

    private void updateRepeatSummary() {
        if (repeatMask == 0) {
            repeatSummary.setText(R.string.repeat_never);
            return;
        }
        if (repeatMask == 0x7F) {
            repeatSummary.setText(R.string.every_day);
            return;
        }
        if (repeatMask == 0x1F) {
            repeatSummary.setText(R.string.workdays);
            return;
        }
        if (repeatMask == 0x60) {
            repeatSummary.setText(R.string.weekend);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if ((repeatMask & (1 << i)) != 0) {
                if (sb.length() > 0) sb.append('、');
                sb.append(DAY_LABELS[i]);
            }
        }
        repeatSummary.setText(sb.toString());
    }

    /** 全屏的星期选择页：点一行切换选中，返回即生效 */
    private void showRepeatDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_repeat);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(getColor(R.color.bg)));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setStatusBarColor(getColor(R.color.bg));
            window.setNavigationBarColor(getColor(R.color.bg));
        }

        LinearLayout list = dialog.findViewById(R.id.repeat_list);
        String[] labels = getResources().getStringArray(R.array.weekday_full);

        for (int i = 0; i < 7; i++) {
            final int bit = 1 << i;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(20), 0, dp(20), 0);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

            TextView label = new TextView(this);
            label.setText(labels[i]);
            label.setTextSize(16);
            label.setTextColor(getColor(R.color.text_primary));
            label.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            ImageView check = new ImageView(this);
            check.setImageResource(R.drawable.ic_check);
            check.setColorFilter(getColor(R.color.accent));
            check.setLayoutParams(new LinearLayout.LayoutParams(dp(20), dp(20)));
            check.setVisibility((repeatMask & bit) != 0 ? View.VISIBLE : View.INVISIBLE);

            row.addView(label);
            row.addView(check);
            row.setOnClickListener(v -> {
                repeatMask ^= bit;
                check.setVisibility((repeatMask & bit) != 0 ? View.VISIBLE : View.INVISIBLE);
                updateRepeatSummary();
            });
            list.addView(row);

            if (i < 6) {
                View divider = new View(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
                lp.setMarginStart(dp(20));
                divider.setLayoutParams(lp);
                divider.setBackgroundColor(getColor(R.color.stroke));
                list.addView(divider);
            }
        }

        dialog.findViewById(R.id.btn_repeat_back).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void setupSlider() {
        repsSlider.setValue(Math.max(5, Math.min(60, alarm.targetReps)));
        repsValue.setText(getString(R.string.reps_value_fmt, (int) repsSlider.getValue()));
        repsSlider.addOnChangeListener((slider, value, fromUser) ->
                repsValue.setText(getString(R.string.reps_value_fmt, (int) value)));
    }

    private void save() {
        alarm.hour = hourPicker.getValue();
        alarm.minute = minutePicker.getValue();
        alarm.repeatMask = repeatMask;
        alarm.targetReps = (int) repsSlider.getValue();
        alarm.exercise = Alarm.EXERCISE_JUMPING_JACK;
        alarm.enabled = true;

        store.save(alarm);
        AlarmScheduler.schedule(this, alarm);
        Toast.makeText(this, R.string.toast_alarm_saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete)
                .setMessage(R.string.delete_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    store.delete(alarm.id);
                    AlarmScheduler.cancel(this, alarm);
                    Toast.makeText(this, R.string.toast_alarm_deleted, Toast.LENGTH_SHORT).show();
                    finish();
                })
                .show();
    }
}
