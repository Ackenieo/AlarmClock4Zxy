package com.example.alarmclock4jacw;

import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.slider.Slider;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class AlarmEditActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "alarm_id";

    private static final String[] DAY_LABELS = {"一", "二", "三", "四", "五", "六", "日"};

    private Alarm alarm;
    private boolean isNew;
    private AlarmStore store;

    private NumberPicker hourPicker;
    private NumberPicker minutePicker;
    private LinearLayout dayRow;
    private Slider repsSlider;
    private TextView repsValue;
    private final List<TextView> dayViews = new ArrayList<>();

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

        TextView title = findViewById(R.id.screen_title);
        title.setText(isNew ? R.string.new_alarm : R.string.edit_alarm);

        hourPicker = findViewById(R.id.picker_hour);
        minutePicker = findViewById(R.id.picker_minute);
        dayRow = findViewById(R.id.day_row);
        repsSlider = findViewById(R.id.reps_slider);
        repsValue = findViewById(R.id.reps_value);

        setupPickers();
        setupDayChips();
        setupSlider();

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_save).setOnClickListener(v -> save());

        if (!isNew) {
            findViewById(R.id.btn_delete).setVisibility(android.view.View.VISIBLE);
            findViewById(R.id.btn_delete).setOnClickListener(v -> confirmDelete());
        }
    }

    private void setupPickers() {
        hourPicker.setMinValue(0);
        hourPicker.setMaxValue(23);
        hourPicker.setFormatter(v -> String.format(java.util.Locale.getDefault(), "%02d", v));
        hourPicker.setValue(alarm.hour);
        hourPicker.setWrapSelectorWheel(true);

        minutePicker.setMinValue(0);
        minutePicker.setMaxValue(59);
        minutePicker.setFormatter(v -> String.format(java.util.Locale.getDefault(), "%02d", v));
        minutePicker.setValue(alarm.minute);
        minutePicker.setWrapSelectorWheel(true);

        if (Build.VERSION.SDK_INT >= 29) {
            int color = getColor(R.color.text_primary);
            hourPicker.setTextColor(color);
            minutePicker.setTextColor(color);
            hourPicker.setTextSize(30);
            minutePicker.setTextSize(30);
        }
    }

    private void setupDayChips() {
        int size = dp(42);
        for (int i = 0; i < 7; i++) {
            TextView tv = new TextView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, size, 1f);
            if (i > 0) lp.setMarginStart(dp(5));
            tv.setLayoutParams(lp);
            tv.setText(DAY_LABELS[i]);
            tv.setGravity(Gravity.CENTER);
            tv.setTextSize(13);
            tv.setBackgroundResource(R.drawable.bg_day);
            tv.setTextColor(getColorStateList(R.color.day_text));
            tv.setSelected((alarm.repeatMask & (1 << i)) != 0);
            tv.setOnClickListener(v -> v.setSelected(!v.isSelected()));
            dayRow.addView(tv);
            dayViews.add(tv);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int readRepeatMask() {
        int mask = 0;
        for (int i = 0; i < dayViews.size(); i++) {
            if (dayViews.get(i).isSelected()) mask |= (1 << i);
        }
        return mask;
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
        alarm.repeatMask = readRepeatMask();
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
