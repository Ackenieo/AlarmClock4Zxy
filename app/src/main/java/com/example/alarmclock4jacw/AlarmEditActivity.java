package com.example.alarmclock4jacw;

import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputFilter;
import android.text.Spanned;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.slider.Slider;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
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
    private TextView ringtoneValue;
    private EditText noteInput;
    private View noteRow;
    /** 当前选中的铃声 Uri；null 表示用 App 内置默认提示音。 */
    private Uri selectedRingtone;
    /** 试听用的临时播放器，随弹窗关闭释放。 */
    private Ringtone previewRingtone;
    private android.media.MediaPlayer previewBeep;

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
        ringtoneValue = findViewById(R.id.ringtone_value);
        noteInput = findViewById(R.id.note_input);
        noteRow = findViewById(R.id.note_row);
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
        setupNoteInput();
        setupRingtoneRow();
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

    // -------------------------------------------------------------- 备注
    private void setupNoteInput() {
        noteInput.setFilters(new InputFilter[]{new NoteInputFilter()});
        noteInput.setText(alarm.note == null ? "" : alarm.note);
        // 平时像其他行的灰色值，编辑时切成主文字色
        noteInput.setTextColor(getColor(R.color.text_secondary));
        noteInput.setOnFocusChangeListener((v, hasFocus) ->
                noteInput.setTextColor(getColor(hasFocus ? R.color.text_primary : R.color.text_secondary)));
        // 进页面不自动弹键盘：默认不可聚焦，第一次点击备注卡片才进入编辑
        noteRow.setOnClickListener(v -> {
            if (!noteInput.hasFocus()) focusNoteEditor();
        });
        noteInput.setOnClickListener(v -> {
            if (!noteInput.isFocusableInTouchMode()) focusNoteEditor();
        });
        // 输入框内部已经超过 4 行时，拖动只滚输入框；其余区域的拖动交给页面 ScrollView
        noteInput.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (noteContentOverflows()) {
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.getParent().requestDisallowInterceptTouchEvent(false);
                    break;
            }
            return false;
        });
    }

    /** 备注内容是否已经超过输入框 4 行的可视高度。 */
    private boolean noteContentOverflows() {
        if (noteInput.getLayout() == null) return false;
        int visibleHeight = noteInput.getHeight()
                - noteInput.getTotalPaddingTop()
                - noteInput.getTotalPaddingBottom();
        return noteInput.getLayout().getHeight() > visibleHeight;
    }

    private void focusNoteEditor() {
        noteInput.setFocusable(true);
        noteInput.setFocusableInTouchMode(true);
        noteInput.requestFocus();
        noteInput.setSelection(noteInput.getText().length());
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) noteInput.post(() ->
                imm.showSoftInput(noteInput, InputMethodManager.SHOW_IMPLICIT));
    }

    /**
     * 备注输入过滤器：最多 50 个非换行字符、最多 4 行。
     * 换行符不占字符额度，但仍然受行数上限约束。
     */
    private static class NoteInputFilter implements InputFilter {
        @Override
        public CharSequence filter(CharSequence source, int start, int end,
                                   Spanned dest, int dstart, int dend) {
            int chars = countChars(dest, 0, dstart) + countChars(dest, dend, dest.length());
            int newlines = countNewlines(dest, 0, dstart)
                    + countNewlines(dest, dend, dest.length());
            int roomChars = Alarm.MAX_NOTE_LENGTH - chars;
            int roomNewlines = Alarm.MAX_NOTE_LINES - 1 - newlines;

            StringBuilder accepted = new StringBuilder();
            int i = start;
            while (i < end) {
                char c = source.charAt(i);
                if (c == '\n' || c == '\r') {
                    if (roomNewlines <= 0) break;
                    roomNewlines--;
                    accepted.append(c);
                } else {
                    if (roomChars <= 0) break;
                    roomChars--;
                    accepted.append(c);
                }
                i++;
            }

            // 不要把 emoji 等代理对截成半个字符
            if (i < end && Character.isLowSurrogate(source.charAt(i))
                    && accepted.length() > 0
                    && Character.isHighSurrogate(accepted.charAt(accepted.length() - 1))) {
                accepted.deleteCharAt(accepted.length() - 1);
            }

            if (accepted.length() == end - start) return null;
            return accepted.toString();
        }

        private static int countChars(CharSequence text, int start, int end) {
            int count = 0;
            for (int i = start; i < end; i++) {
                char c = text.charAt(i);
                if (c != '\n' && c != '\r') count++;
            }
            return count;
        }

        private static int countNewlines(CharSequence text, int start, int end) {
            int count = 0;
            for (int i = start; i < end; i++) {
                char c = text.charAt(i);
                if (c == '\n' || c == '\r') count++;
            }
            return count;
        }
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

    // -------------------------------------------------------------- 选取铃声
    private void setupRingtoneRow() {
        if (alarm.ringtone != null && !alarm.ringtone.isEmpty()) {
            try {
                selectedRingtone = Uri.parse(alarm.ringtone);
            } catch (Exception ignored) {
                selectedRingtone = null;
            }
        }
        updateRingtoneValue();
        findViewById(R.id.ringtone_row).setOnClickListener(v -> showRingtoneDialog());
    }

    private void updateRingtoneValue() {
        if (selectedRingtone == null) {
            ringtoneValue.setText(R.string.ringtone_default);
            return;
        }
        Ringtone r = RingtoneManager.getRingtone(this, selectedRingtone);
        String name = r != null ? r.getTitle(this) : getString(R.string.ringtone_default);
        ringtoneValue.setText(name);
    }

    /** 类 Apple 的铃声选择页：单选 + 勾选 + 点击试听；返回即生效。 */
    private void showRingtoneDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_ringtone);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(SkinStore.bgColor(this)));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setStatusBarColor(SkinStore.bgColor(this));
            window.setNavigationBarColor(SkinStore.bgColor(this));
        }

        TextView title = dialog.findViewById(R.id.ringtone_title);
        if (title != null) title.setText(R.string.ringtone_dialog_title);

        LinearLayout list = dialog.findViewById(R.id.ringtone_list);

        // 选项：默认（内置提示音）+ 系统闹钟/来电铃声，按 Uri 去重
        ArrayList<RingtoneOption> options = new ArrayList<>();
        options.add(new RingtoneOption(null, getString(R.string.ringtone_default)));
        collectRingtones(options, RingtoneManager.TYPE_ALARM);
        collectRingtones(options, RingtoneManager.TYPE_RINGTONE);

        final Uri[] picked = {selectedRingtone};
        int accent = SkinStore.accent(this);
        LayoutInflater inflater = LayoutInflater.from(this);

        for (int i = 0; i < options.size(); i++) {
            final RingtoneOption opt = options.get(i);

            View row = inflater.inflate(R.layout.item_ringtone, list, false);
            TextView name = row.findViewById(R.id.ringtone_name);
            ImageView check = row.findViewById(R.id.ringtone_check);
            name.setText(opt.title);
            check.setColorFilter(accent);
            check.setVisibility(isSelected(opt.uri, picked[0]) ? View.VISIBLE : View.INVISIBLE);

            row.setOnClickListener(v -> {
                picked[0] = opt.uri;
                // 更新所有勾选标记
                for (int j = 0; j < list.getChildCount(); j += 2) {
                    View child = list.getChildAt(j);
                    ImageView c = child.findViewById(R.id.ringtone_check);
                    if (c != null) {
                        RingtoneOption o = options.get(j / 2);
                        c.setVisibility(isSelected(o.uri, picked[0]) ? View.VISIBLE : View.INVISIBLE);
                    }
                }
                previewRingtone(opt.uri);
            });
            list.addView(row);

            if (i < options.size() - 1) {
                View divider = new View(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
                lp.setMarginStart(dp(20));
                divider.setLayoutParams(lp);
                divider.setBackgroundColor(SkinStore.strokeColor(this));
                list.addView(divider);
            }
        }

        dialog.findViewById(R.id.btn_ringtone_back).setOnClickListener(v -> dialog.dismiss());
        dialog.setOnDismissListener(d -> {
            stopPreview();
            selectedRingtone = picked[0];
            updateRingtoneValue();
        });
        dialog.show();
    }

    private static boolean isSelected(Uri uri, Uri picked) {
        if (uri == null) return picked == null;
        return uri.equals(picked);
    }

    /** 收集指定类型的系统铃声，按 Uri 去重追加到 out。 */
    private void collectRingtones(ArrayList<RingtoneOption> out, int type) {
        HashSet<String> seen = new HashSet<>();
        for (RingtoneOption o : out) {
            if (o.uri != null) seen.add(o.uri.toString());
        }
        try {
            RingtoneManager rm = new RingtoneManager(this);
            rm.setType(type);
            android.database.Cursor cursor = rm.getCursor();
            int titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
            while (cursor.moveToNext()) {
                Uri uri = rm.getRingtoneUri(cursor.getPosition());
                if (uri == null) continue;
                String key = uri.toString();
                if (seen.contains(key)) continue;
                seen.add(key);
                String title = titleCol >= 0 ? cursor.getString(titleCol) : "";
                if (title == null || title.isEmpty()) {
                    Ringtone r = RingtoneManager.getRingtone(this, uri);
                    title = r != null ? r.getTitle(this) : "";
                }
                if (title == null || title.isEmpty()) title = getString(R.string.ringtone_default);
                out.add(new RingtoneOption(uri, title));
            }
            cursor.close();
        } catch (Exception ignored) {
            // 某些 ROM 拿不到铃声表：至少保留「默认」一项
        }
    }

    /** 选中后试听：默认项播内置提示音，其余播对应铃声（仅一次）。 */
    private void previewRingtone(Uri uri) {
        stopPreview();
        try {
            if (uri == null) {
                previewBeep = new android.media.MediaPlayer();
                previewBeep.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM).build());
                android.content.res.AssetFileDescriptor afd =
                        getResources().openRawResourceFd(R.raw.alarm_beep);
                previewBeep.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
                previewBeep.prepare();
                previewBeep.start();
            } else {
                previewRingtone = RingtoneManager.getRingtone(this, uri);
                if (previewRingtone != null) {
                    previewRingtone.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM).build());
                    previewRingtone.play();
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void stopPreview() {
        if (previewRingtone != null) {
            try {
                previewRingtone.stop();
            } catch (Exception ignored) {
            }
            previewRingtone = null;
        }
        if (previewBeep != null) {
            try {
                previewBeep.stop();
                previewBeep.release();
            } catch (Exception ignored) {
            }
            previewBeep = null;
        }
    }

    /** 简单容器：铃声 Uri（null = 默认内置提示音）+ 展示名。 */
    private static class RingtoneOption {
        final Uri uri;
        final String title;

        RingtoneOption(Uri uri, String title) {
            this.uri = uri;
            this.title = title;
        }
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
        alarm.ringtone = selectedRingtone != null ? selectedRingtone.toString() : null;
        alarm.note = Alarm.normalizeNote(noteInput.getText().toString());
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
