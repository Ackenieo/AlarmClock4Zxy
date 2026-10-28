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

import com.google.android.material.slider.Slider;

import java.util.Calendar;
import java.util.Locale;

public class AlarmEditActivity extends BaseActivity {

    public static final String EXTRA_ID = "alarm_id";

    private Alarm alarm;
    private boolean isNew;
    private AlarmStore store;

    private NumberPicker hourPicker;
    private NumberPicker minutePicker;
    private Slider repsSlider;
    private TextView repsValue;
    private Slider wordCountSlider;
    private TextView wordCountValue;
    private TextView wordBookValue;
    private View repsSection;
    private View wordSection;
    private View typeJack;
    private View typeWord;
    private TextView typeWordTag;
    private TextView wordModeValue;
    private String wordMode = Alarm.TASK_WORD_CHOICE;
    private boolean wordAvailable;
    private TextView repeatSummary;

    /** 重复掩码，bit0 = 周一 … bit6 = 周日，0 = 只响一次 */
    private int repeatMask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SkinStore.applyTheme(this);
        setContentView(R.layout.activity_edit_alarm);
        // 全屏滚动：顶部让出状态栏，滚上去的内容在状态栏下沿裁掉
        SystemBars.insetTop(findViewById(R.id.edit_scroll));

        SkinStore.applyFx(this.findViewById(R.id.flow_bg));
        FxStore.applyFlowBg(this, findViewById(R.id.flow_bg));

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
        wordMode = alarm.isWordTask() ? alarm.taskType : Alarm.TASK_WORD_CHOICE;

        TextView title = findViewById(R.id.screen_title);
        title.setText(isNew ? R.string.new_alarm : R.string.edit_alarm);

        hourPicker = findViewById(R.id.picker_hour);
        minutePicker = findViewById(R.id.picker_minute);
        repsSlider = findViewById(R.id.reps_slider);
        repsValue = findViewById(R.id.reps_value);
        repeatSummary = findViewById(R.id.repeat_summary);
        repsSection = findViewById(R.id.reps_section);
        wordSection = findViewById(R.id.word_section);
        wordCountSlider = findViewById(R.id.word_count_slider);
        wordCountValue = findViewById(R.id.word_count_value);
        wordBookValue = findViewById(R.id.word_book_value);
        typeJack = findViewById(R.id.type_jumping_jack);
        typeWord = findViewById(R.id.type_word);
        typeWordTag = findViewById(R.id.type_word_tag);
        wordModeValue = findViewById(R.id.word_mode_value);

        setupPickers();
        setupRepeatRow();
        setupSlider();
        setupTaskSelector();
        setupWordSlider();
        setupWordModeRow();
        setupWordBookRow();

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
        repeatSummary.setText(Alarm.repeatDays(this, repeatMask));
    }

    /** 全屏的星期选择页：点一行切换选中，返回即生效 */
    private void showRepeatDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_repeat);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(SkinStore.bgColor(this)));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setStatusBarColor(SkinStore.bgColor(this));
            window.setNavigationBarColor(SkinStore.bgColor(this));
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
            check.setColorFilter(SkinStore.accent(this));
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
                divider.setBackgroundColor(SkinStore.strokeColor(this));
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

    /** 任务类型：横向滚动，点击卡片才算选中；深蹲/俯卧撑不可选 */
    private void setupTaskSelector() {
        typeJack.setOnClickListener(v -> selectTask(Alarm.TASK_JUMPING_JACK));
        typeWord.setOnClickListener(v -> {
            if (!wordAvailable) {
                Toast.makeText(this, R.string.word_list_unavailable, Toast.LENGTH_SHORT).show();
                return;
            }
            selectTask(wordMode);
        });
        updateTaskUi();
    }

    private void selectTask(String task) {
        alarm.taskType = task;
        updateTaskUi();
    }

    private void updateTaskUi() {
        boolean word = alarm.isWordTask();
        wordAvailable = !WordStore.words(this, WordStore.getBook(this)).isEmpty();

        typeJack.setBackgroundResource(!word ? R.drawable.bg_option_selected : R.drawable.bg_option);
        typeWord.setBackgroundResource(word ? R.drawable.bg_option_selected : R.drawable.bg_option);
        typeJack.setAlpha(1f);
        typeWord.setAlpha(wordAvailable ? 1f : 0.5f);

        if (word) wordMode = alarm.taskType;
        typeWordTag.setText(Alarm.TASK_WORD_CHOICE.equals(wordMode)
                ? R.string.word_mode_choice
                : R.string.word_mode_spell);

        repsSection.setVisibility(word ? View.GONE : View.VISIBLE);
        wordSection.setVisibility(word ? View.VISIBLE : View.GONE);
        updateWordModeValue();
        updateWordBookValue();
    }

    private void setupWordSlider() {
        wordCountSlider.setValue(Math.max(5, Math.min(60, alarm.targetWords)));
        wordCountValue.setText(getString(R.string.word_count_fmt, (int) wordCountSlider.getValue()));
        wordCountSlider.addOnChangeListener((slider, value, fromUser) ->
                wordCountValue.setText(getString(R.string.word_count_fmt, (int) value)));
    }

    private void setupWordModeRow() {
        updateWordModeValue();
        findViewById(R.id.word_mode_row).setOnClickListener(v -> showWordModeDialog());
    }

    private void updateWordModeValue() {
        wordModeValue.setText(Alarm.TASK_WORD_CHOICE.equals(wordMode)
                ? R.string.word_mode_choice
                : R.string.word_mode_spell);
    }

    /** 背单词模式：只切换选择/拼写，任务类型仍归到同一张“背单词”卡片。 */
    private void showWordModeDialog() {
        final String[] values = {Alarm.TASK_WORD_CHOICE, Alarm.TASK_WORD_SPELL};
        final String[] labels = {getString(R.string.word_mode_choice), getString(R.string.word_mode_spell)};
        int checked = Alarm.TASK_WORD_SPELL.equals(wordMode) ? 1 : 0;

        new AlertDialog.Builder(this)
                .setTitle(R.string.word_mode_label)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    wordMode = values[which];
                    if (alarm.isWordTask()) alarm.taskType = wordMode;
                    d.dismiss();
                    updateTaskUi();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void setupWordBookRow() {
        updateWordBookValue();
        findViewById(R.id.word_book_row).setOnClickListener(v -> showWordBookDialog());
    }

    private void updateWordBookValue() {
        wordBookValue.setText(getString(R.string.word_book_global_fmt, WordStore.bookLabel(this)));
    }

    /** 词库是全局设置：切换后所有背单词闹钟都用这一本 */
    private void showWordBookDialog() {
        final String[] values = {WordStore.BOOK_CET4, WordStore.BOOK_CET6};
        final String[] labels = {getString(R.string.word_book_cet4), getString(R.string.word_book_cet6)};
        int checked = WordStore.BOOK_CET6.equals(WordStore.getBook(this)) ? 1 : 0;

        new AlertDialog.Builder(this)
                .setTitle(R.string.word_book_dialog_title)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    WordStore.setBook(this, values[which]);
                    d.dismiss();
                    wordAvailable = !WordStore.words(this, WordStore.getBook(this)).isEmpty();
                    if (alarm.isWordTask() && !wordAvailable) {
                        Toast.makeText(this, R.string.word_list_unavailable, Toast.LENGTH_SHORT).show();
                        alarm.taskType = Alarm.TASK_JUMPING_JACK;
                    }
                    updateTaskUi();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void save() {
        alarm.hour = hourPicker.getValue();
        alarm.minute = minutePicker.getValue();
        alarm.repeatMask = repeatMask;
        alarm.targetReps = (int) repsSlider.getValue();
        alarm.targetWords = (int) wordCountSlider.getValue();
        alarm.enabled = true;

        if (alarm.isWordTask() && WordStore.words(this, WordStore.getBook(this)).isEmpty()) {
            Toast.makeText(this, R.string.word_list_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

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
