package com.example.alarmclock4jacw;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 背单词页：支持“看中文选英文”（选择）和“看中文拼英文”（拼写）两种模式。
 * 选择模式答错只把该选项标红并禁用，必须选到正确项才算完成；
 * 拼写模式保留 3/5 次提示、10 次显示答案跳词。
 * 两种模式都支持 TTS 发音与复习页点读。
 */
public class WordSpellActivity extends BaseActivity {

    private static final int HINT_FIRST_AT = 3;
    private static final int HINT_LENGTH_AT = 5;
    private static final int SKIP_AT = 10;
    private static final int OPTION_COUNT = 4;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private TextView progressText;
    private RingProgressView ring;
    private TextView meaningView;
    private EditText input;
    private TextView hintView;
    private TextView goodView;
    private MaterialButton submitButton;
    private ImageButton speakButton;
    private View spellPanel;
    private View reviewPanel;
    private LinearLayout reviewList;
    private TextView reviewSub;
    private LinearLayout choiceSection;
    private LinearLayout spellSection;
    private final TextView[] options = new TextView[OPTION_COUNT];

    private boolean choiceMode;
    private String taskType;
    private String book;
    private String sessionId;
    private WordStore.Session session;
    private List<Word> words;
    private List<Word> currentOptions = new ArrayList<>();
    private int targetCount;
    private int index;
    private int wrongCount;
    private boolean awaitingNext;
    private boolean reviewing;
    private boolean finished;
    private boolean ttsPrompted;

    private TtsSpeaker tts;
    private ToneGenerator tone;

    private final Runnable timeoutCheck = new Runnable() {
        @Override
        public void run() {
            if (!AlarmService.ringing && !reviewing && !finished) {
                if (tts != null) tts.shutdown();
                Toast.makeText(WordSpellActivity.this, R.string.ringing_timeout, Toast.LENGTH_LONG).show();
                finished = true;
                finish();
                return;
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SkinStore.applyTheme(this);

        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (km != null) km.requestDismissKeyguard(this, null);

        setContentView(R.layout.activity_word_spell);
        hideSystemBars();

        progressText = findViewById(R.id.word_page);
        ring = findViewById(R.id.word_ring);
        meaningView = findViewById(R.id.word_meaning);
        input = findViewById(R.id.word_input);
        hintView = findViewById(R.id.word_hint);
        goodView = findViewById(R.id.word_good);
        submitButton = findViewById(R.id.btn_word_submit);
        speakButton = findViewById(R.id.btn_speak);
        spellPanel = findViewById(R.id.spell_panel);
        reviewPanel = findViewById(R.id.review_panel);
        reviewList = findViewById(R.id.review_list);
        reviewSub = findViewById(R.id.review_sub);
        choiceSection = findViewById(R.id.choice_section);
        spellSection = findViewById(R.id.spell_section);
        options[0] = findViewById(R.id.option_1);
        options[1] = findViewById(R.id.option_2);
        options[2] = findViewById(R.id.option_3);
        options[3] = findViewById(R.id.option_4);

        taskType = getIntent().getStringExtra(AlarmService.EXTRA_TASK);
        if (!Alarm.isWordTask(taskType)) taskType = Alarm.TASK_WORD_SPELL;
        choiceMode = Alarm.TASK_WORD_CHOICE.equals(taskType);

        book = getIntent().getStringExtra(AlarmService.EXTRA_WORD_BOOK);
        if (book == null || book.isEmpty()) book = WordStore.getBook(this);
        book = WordStore.normalizeBook(book);

        sessionId = getIntent().getStringExtra(AlarmService.EXTRA_WORD_SESSION);
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = String.valueOf(System.currentTimeMillis());
        }

        targetCount = Math.max(1, getIntent().getIntExtra(AlarmService.EXTRA_WORDS, 10));
        session = WordStore.beginSession(this, book, targetCount, sessionId);
        words = session.words;

        if (words.isEmpty()) {
            Toast.makeText(this, R.string.word_list_unavailable, Toast.LENGTH_LONG).show();
            setResult(RESULT_OK);
            finish();
            return;
        }
        targetCount = words.size();
        index = Math.max(0, Math.min(targetCount, session.cursor));

        try {
            tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 80);
        } catch (Exception ignored) {
        }
        tts = new TtsSpeaker(this, () -> runOnUiThread(this::showTtsMissingDialog));

        submitButton.setOnClickListener(v -> submit());
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });
        speakButton.setOnClickListener(v -> {
            Word w = words.get(index);
            if (w != null && tts != null) tts.speak(w.word);
        });
        for (int i = 0; i < OPTION_COUNT; i++) {
            final int which = i;
            options[i].setOnClickListener(v -> onOptionClick(which));
        }

        KioskController.allowLockTask(this);
        KioskController.enterLockTask(this);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Toast.makeText(WordSpellActivity.this, R.string.ringing_word_back_hint,
                        Toast.LENGTH_SHORT).show();
            }
        });

        if (index >= targetCount) {
            showReview();
        } else {
            showWord(index);
        }
    }

    private void showWord(int i) {
        index = i;
        wrongCount = 0;
        awaitingNext = false;
        reviewing = false;
        spellPanel.setVisibility(View.VISIBLE);
        reviewPanel.setVisibility(View.GONE);
        goodView.setVisibility(View.GONE);

        Word w = words.get(i);
        meaningView.setText(w.meaningText());
        progressText.setText(getString(R.string.word_page_fmt, i + 1, targetCount));
        ring.setProgress(i, targetCount);

        if (choiceMode) {
            choiceSection.setVisibility(View.VISIBLE);
            spellSection.setVisibility(View.GONE);
            speakButton.setVisibility(View.GONE);
            hintView.setText(R.string.word_choice_hint);
            hideKeyboard();
            buildOptions(w);
        } else {
            choiceSection.setVisibility(View.GONE);
            spellSection.setVisibility(View.VISIBLE);
            speakButton.setVisibility(View.VISIBLE);
            hintView.setText(R.string.word_hint_default);
            input.setEnabled(true);
            submitButton.setEnabled(true);
            input.setText("");
            input.requestFocus();
            handler.postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
            }, 250);
        }
    }

    private void buildOptions(Word correct) {
        currentOptions = Word.options(WordStore.words(this, book), correct, OPTION_COUNT, random);
        for (int i = 0; i < OPTION_COUNT; i++) {
            TextView view = options[i];
            if (i < currentOptions.size()) {
                view.setVisibility(View.VISIBLE);
                view.setText(currentOptions.get(i).word);
                view.setBackgroundResource(R.drawable.bg_option);
                view.setTextColor(getColor(R.color.text_primary));
                view.setEnabled(true);
            } else {
                view.setVisibility(View.GONE);
            }
        }
    }

    private void onOptionClick(int which) {
        if (awaitingNext || reviewing || finished) return;
        if (which < 0 || which >= currentOptions.size()) return;
        TextView view = options[which];
        if (!view.isEnabled()) return;

        Word correct = words.get(index);
        Word picked = currentOptions.get(which);
        if (picked.word.equalsIgnoreCase(correct.word)) {
            view.setBackgroundResource(R.drawable.bg_option_correct);
            view.setTextColor(getColor(R.color.on_accent));
            for (TextView option : options) option.setEnabled(false);
            playTone(true);
            speakWord(correct.word);
            completeQuestion(wrongCount > 0);
        } else {
            wrongCount++;
            view.setBackgroundResource(R.drawable.bg_option_wrong);
            view.setTextColor(getColor(R.color.white));
            view.setEnabled(false);
            playTone(false);
            speakWord(correct.word);
        }
    }

    private void submit() {
        if (choiceMode || awaitingNext || reviewing || finished) return;
        String typed = input.getText() == null ? "" : input.getText().toString();
        if (TextUtils.isEmpty(typed.trim())) return;

        Word current = words.get(index);
        if (Word.matches(current.word, typed)) {
            onCorrect(current);
        } else {
            onWrong(current);
        }
    }

    private void onCorrect(Word current) {
        hideKeyboard();
        input.setEnabled(false);
        submitButton.setEnabled(false);
        playTone(true);
        speakWord(current.word);
        completeQuestion(wrongCount > 0);
    }

    private void onWrong(Word current) {
        wrongCount++;
        input.setText("");
        shake(input);
        playTone(false);
        speakWord(current.word);

        if (wrongCount >= SKIP_AT) {
            hintView.setText(getString(R.string.word_answer_fmt, current.word));
            input.setEnabled(false);
            submitButton.setEnabled(false);
            awaitingNext = true;
            hideKeyboard();
            WordStore.completeSessionWord(this, session, true);
            index = session.cursor;
            handler.postDelayed(() -> {
                awaitingNext = false;
                if (index >= targetCount) showReview(); else showWord(index);
            }, 1200);
            return;
        }

        if (wrongCount >= HINT_LENGTH_AT) {
            hintView.setText(getString(R.string.word_hint_length_fmt,
                    current.word.substring(0, 1), current.word.length()));
        } else if (wrongCount >= HINT_FIRST_AT) {
            hintView.setText(getString(R.string.word_hint_first_fmt,
                    current.word.substring(0, 1)));
        } else {
            hintView.setText(R.string.word_hint_default);
        }
    }

    private void completeQuestion(boolean wrong) {
        awaitingNext = true;
        goodView.setVisibility(View.VISIBLE);
        goodView.setAlpha(0f);
        goodView.setScaleX(0.7f);
        goodView.setScaleY(0.7f);
        goodView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start();

        WordStore.completeSessionWord(this, session, wrong);
        index = session.cursor;
        handler.postDelayed(() -> {
            awaitingNext = false;
            if (index >= targetCount) {
                showReview();
            } else {
                showWord(index);
            }
        }, 625);
    }

    private void playTone(boolean correct) {
        if (tone == null) return;
        try {
            tone.startTone(correct ? ToneGenerator.TONE_PROP_ACK : ToneGenerator.TONE_PROP_NACK,
                    correct ? 150 : 220);
        } catch (Exception ignored) {
        }
    }

    private void speakWord(String word) {
        if (tts != null) tts.speak(word);
    }

    private void showReview() {
        reviewing = true;
        finished = true;
        hideKeyboard();
        WordStore.finishSession(this, session);

        spellPanel.setVisibility(View.GONE);
        reviewPanel.setVisibility(View.VISIBLE);
        reviewSub.setText(getString(R.string.word_review_sub_fmt, targetCount));
        reviewList.removeAllViews();

        for (Word w : words) {
            addReviewRow(w);
        }

        findViewById(R.id.btn_review_done).setOnClickListener(v -> {
            AlarmService.stop(this);
            setResult(RESULT_OK);
            finish();
        });

        // 铃声继续响，等用户点“完成”后再停止
    }

    private boolean isWrongWord(Word word) {
        for (String s : session.wrongWords) {
            if (s.equalsIgnoreCase(word.word)) return true;
        }
        return false;
    }

    private void addReviewRow(Word w) {
        boolean wrong = isWrongWord(w);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_row_ripple);
        row.setClickable(true);
        row.setFocusable(true);
        row.setPadding(dp(20), dp(12), dp(16), dp(12));

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView word = new TextView(this);
        word.setText(w.word);
        word.setTextColor(getColor(wrong ? R.color.danger : R.color.text_primary));
        word.setTextSize(18);
        word.setTypeface(null, android.graphics.Typeface.BOLD);
        titleRow.addView(word);

        if (wrong) {
            TextView tag = new TextView(this);
            tag.setText(R.string.word_review_wrong);
            tag.setTextColor(getColor(R.color.danger));
            tag.setTextSize(11);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginStart(dp(8));
            titleRow.addView(tag, lp);
        }

        TextView meaning = new TextView(this);
        meaning.setText(w.meaningText());
        meaning.setTextColor(getColor(R.color.text_secondary));
        meaning.setTextSize(13);
        meaning.setPadding(0, dp(4), 0, 0);

        textCol.addView(titleRow);
        textCol.addView(meaning);
        row.addView(textCol, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        ImageView speaker = new ImageView(this);
        speaker.setImageResource(R.drawable.ic_volume);
        speaker.setColorFilter(getColor(R.color.text_secondary));
        row.addView(speaker, new LinearLayout.LayoutParams(dp(22), dp(22)));

        row.setOnClickListener(v -> {
            if (tts != null) tts.speak(w.word);
        });
        reviewList.addView(row);

        View divider = new View(this);
        divider.setBackgroundColor(SkinStore.strokeColor(this));
        reviewList.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
    }

    private void showTtsMissingDialog() {
        if (ttsPrompted || isFinishing() || isDestroyed()) return;
        ttsPrompted = true;
        new AlertDialog.Builder(this)
                .setTitle(R.string.word_tts_missing_title)
                .setMessage(R.string.word_tts_missing_desc)
                .setPositiveButton(R.string.word_tts_settings, (d, w) -> {
                    try {
                        startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA));
                    } catch (Exception e) {
                        try {
                            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                        } catch (Exception ignored) {
                        }
                    }
                })
                .setNegativeButton(R.string.word_tts_later, null)
                .show();
    }

    private void shake(View view) {
        TranslateAnimation anim = new TranslateAnimation(0, dp(8), 0, 0);
        anim.setDuration(45);
        anim.setRepeatCount(3);
        anim.setRepeatMode(Animation.REVERSE);
        view.startAnimation(anim);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
    }

    private void hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat c =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.hide(WindowInsetsCompat.Type.systemBars());
        c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AlarmService.uiSeen();
        handler.removeCallbacks(timeoutCheck);
        handler.postDelayed(timeoutCheck, 1000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        AlarmService.uiHidden();
        handler.removeCallbacks(timeoutCheck);
        if (tts != null) tts.stop();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (tts != null) tts.shutdown();
        if (tone != null) {
            tone.release();
            tone = null;
        }
        super.onDestroy();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
            KioskController.enterLockTask(this);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return VolumeKeyGuard.onKeyDown(this, keyCode, event) || super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        return VolumeKeyGuard.swallow(keyCode) || super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return VolumeKeyGuard.swallow(keyCode) || super.onKeyUp(keyCode, event);
    }
}
