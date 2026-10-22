package com.example.alarmclock4jacw;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.button.MaterialButton;

import java.util.List;

/**
 * 背单词拼写页：看中文释义，输入英文单词。
 * 3 次错给首字母，5 次错给字母数，10 次错显示答案并跳到下一个。
 */
public class WordSpellActivity extends AppCompatActivity {

    private static final int HINT_FIRST_AT = 3;
    private static final int HINT_LENGTH_AT = 5;
    private static final int SKIP_AT = 10;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView progressText;
    private RingProgressView ring;
    private TextView meaningView;
    private EditText input;
    private TextView hintView;
    private TextView goodView;
    private MaterialButton submitButton;
    private View spellPanel;
    private View reviewPanel;
    private LinearLayout reviewList;
    private TextView reviewSub;

    private String book;
    private List<Word> words;
    private int targetCount;
    private int index;
    private int wrongCount;
    private boolean awaitingNext;
    private boolean reviewing;
    private boolean finished;

    private final Runnable timeoutCheck = new Runnable() {
        @Override
        public void run() {
            if (!AlarmService.ringing && !reviewing && !finished) {
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
        spellPanel = findViewById(R.id.spell_panel);
        reviewPanel = findViewById(R.id.review_panel);
        reviewList = findViewById(R.id.review_list);
        reviewSub = findViewById(R.id.review_sub);

        book = WordStore.getBook(this);
        targetCount = Math.max(1, getIntent().getIntExtra(AlarmService.EXTRA_WORDS, 10));
        int startIndex = getIntent().getIntExtra(AlarmService.EXTRA_WORD_START,
                WordStore.nextIndex(this, book));
        words = WordStore.page(this, book, startIndex, targetCount);

        if (words.isEmpty()) {
            // 理论上编辑页已经禁止选择；这里只做最后兜底，避免把人锁死在响铃里
            Toast.makeText(this, R.string.word_list_unavailable, Toast.LENGTH_LONG).show();
            setResult(RESULT_OK);
            finish();
            return;
        }

        int advanced = WordStore.nextIndex(this, book) - startIndex;
        int size = WordStore.words(this, book).size();
        if (advanced < 0 && size > 0) advanced += size;
        index = Math.max(0, Math.min(targetCount, advanced));

        submitButton.setOnClickListener(v -> submit());
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });

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
        input.setEnabled(true);
        submitButton.setEnabled(true);

        Word w = words.get(i);
        meaningView.setText(w.meaningText());
        progressText.setText(getString(R.string.word_page_fmt, i + 1, targetCount));
        ring.setProgress(i, targetCount);
        hintView.setText(R.string.word_hint_default);
        input.setText("");
        input.requestFocus();
        handler.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
        }, 250);
    }

    private void submit() {
        if (awaitingNext || reviewing || finished) return;
        String typed = input.getText() == null ? "" : input.getText().toString();
        if (TextUtils.isEmpty(typed.trim())) return;

        Word current = words.get(index);
        if (Word.matches(current.word, typed)) {
            onCorrect();
        } else {
            onWrong(current);
        }
    }

    private void onCorrect() {
        hideKeyboard();
        input.setEnabled(false);
        submitButton.setEnabled(false);
        awaitingNext = true;
        goodView.setVisibility(View.VISIBLE);
        goodView.setAlpha(0f);
        goodView.setScaleX(0.7f);
        goodView.setScaleY(0.7f);
        goodView.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start();

        WordStore.advance(this, book);
        handler.postDelayed(() -> {
            awaitingNext = false;
            if (index + 1 >= targetCount) {
                showReview();
            } else {
                showWord(index + 1);
            }
        }, 650);
    }

    private void onWrong(Word current) {
        wrongCount++;
        input.setText("");
        shake(input);

        if (wrongCount >= SKIP_AT) {
            // 第 10 次直接给答案，并跳到下一个
            hintView.setText(getString(R.string.word_answer_fmt, current.word));
            input.setEnabled(false);
            submitButton.setEnabled(false);
            awaitingNext = true;
            hideKeyboard();
            WordStore.advance(this, book);
            handler.postDelayed(() -> {
                awaitingNext = false;
                if (index + 1 >= targetCount) {
                    showReview();
                } else {
                    showWord(index + 1);
                }
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

    private void showReview() {
        reviewing = true;
        finished = true;
        hideKeyboard();
        spellPanel.setVisibility(View.GONE);
        reviewPanel.setVisibility(View.VISIBLE);
        reviewSub.setText(getString(R.string.word_review_sub_fmt, targetCount));
        reviewList.removeAllViews();

        for (Word w : words) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(20), dp(14), dp(20), dp(14));

            TextView word = new TextView(this);
            word.setText(w.word);
            word.setTextColor(getColor(R.color.text_primary));
            word.setTextSize(18);
            word.setTypeface(null, android.graphics.Typeface.BOLD);

            TextView meaning = new TextView(this);
            meaning.setText(w.meaningText());
            meaning.setTextColor(getColor(R.color.text_secondary));
            meaning.setTextSize(13);
            meaning.setPadding(0, dp(4), 0, 0);

            row.addView(word);
            row.addView(meaning);
            reviewList.addView(row);

            View divider = new View(this);
            divider.setBackgroundColor(getColor(R.color.stroke));
            reviewList.addView(divider, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        }

        findViewById(R.id.btn_review_done).setOnClickListener(v -> {
            setResult(RESULT_OK);
            finish();
        });

        // 任务完成，立即停止铃声；复习列表留在这里给用户看
        AlarmService.stop(this);
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
