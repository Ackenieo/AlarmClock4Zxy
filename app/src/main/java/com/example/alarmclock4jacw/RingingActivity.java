package com.example.alarmclock4jacw;

import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.Calendar;
import java.util.Locale;

/**
 * 响铃页：锁屏上直接显示，声音不停；只有做完运动才能停。
 * 没有任何「强制关闭」入口——音量键被消费掉，返回键被拦下，
 * 服务侧的看门狗还会把被划掉的界面重新拉回来。
 * 进入时尽可能把设备锁进锁定任务（Kiosk），阻止用户回到桌面或锁屏逃脱。
 */
public class RingingActivity extends AppCompatActivity {

    /**
     * 测试用：直接 am start 本页时带上这个 extra，页面就不会因为「服务没在响」而自动关闭。
     * 正常响铃链路不会传它，所以不影响线上行为。
     */
    public static final String EXTRA_DEBUG_KEEP = "debug_keep";

    private GlitchTextView clock;
    private TextView repsText;
    private TextView hint;
    private int reps = 20;
    private int targetWords = 10;
    private int wordStart = 0;
    private String wordBook = WordStore.BOOK_CET4;
    private String wordSessionId = "";
    private String taskType = Alarm.TASK_JUMPING_JACK;
    private long alarmId = -1L;
    private boolean debugKeep;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            updateHint();
            // 服务侧 30 分钟兜底停止后，界面自己收掉；锁定任务必须先退出，否则设备会留在锁死状态
            if (!AlarmService.ringing && !debugKeep) {
                if (AlarmService.timedOut) {
                    Toast.makeText(RingingActivity.this, R.string.ringing_timeout, Toast.LENGTH_LONG).show();
                }
                closeAndUnlock();
                return;
            }
            handler.postDelayed(this, 1000);
        }
    };

    private ActivityResultLauncher<Intent> exerciseLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SkinStore.applyTheme(this);
        debugKeep = getIntent().getBooleanExtra(EXTRA_DEBUG_KEEP, false);

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

        setContentView(R.layout.activity_ringing);
        hideSystemBars();

        SkinStore.applyFx(findViewById(R.id.flow_bg));

        clock = findViewById(R.id.clock);
        repsText = findViewById(R.id.reps_text);
        hint = findViewById(R.id.hint);

        // 特效 UI：霓虹呼吸 + 故障抖色，按皮肤市场里的开关决定开不开
        FxStore.applyFlowBg(this, findViewById(R.id.flow_bg));
        FxStore.applyVisibility(this, FxStore.NEON, findViewById(R.id.btn_start_neon));
        clock.setGlitchEnabled(FxStore.isEnabled(this, FxStore.GLITCH));

        exerciseLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) dismissAlarm();
                });

        findViewById(R.id.btn_start).setOnClickListener(v -> {
            Intent i;
            if (Alarm.isWordTask(taskType)) {
                i = new Intent(this, WordSpellActivity.class);
                i.putExtra(AlarmService.EXTRA_TASK, taskType);
                i.putExtra(AlarmService.EXTRA_WORDS, targetWords);
                i.putExtra(AlarmService.EXTRA_WORD_START, wordStart);
                i.putExtra(AlarmService.EXTRA_WORD_BOOK, wordBook);
                i.putExtra(AlarmService.EXTRA_WORD_SESSION, wordSessionId);
            } else {
                i = new Intent(this, ExerciseActivity.class);
                i.putExtra(AlarmService.EXTRA_REPS, reps);
            }
            i.putExtra(AlarmService.EXTRA_ID, alarmId);
            exerciseLauncher.launch(i);
        });

        // 特效 UI：液态按钮 —— 按下时从中心荡开水波。
        // 前景 Drawable 收 state_pressed 不一定灵，这里直接盯 ACTION_DOWN 更稳；
        // 返回 false 是故意的：事件继续交给按钮，点击照常触发。
        final LiquidRippleDrawable liquid = new LiquidRippleDrawable(this, 20f);
        View startButton = findViewById(R.id.btn_start);
        startButton.setForeground(liquid);
        startButton.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN
                    && FxStore.isEnabled(this, FxStore.LIQUID)) {
                liquid.wave();
            }
            return false;
        });

        // 进入锁定任务：设备所有者下真正锁死设备，非设备所有者静默降级
        // 调试直启时跳过，否则系统会盖一层「应用已固定」的弹窗，挡住要看的特效
        if (!debugKeep) {
            KioskController.allowLockTask(this);
            KioskController.enterLockTask(this);
        }

        getOnBackPressedDispatcher().addCallback(this,
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        Toast.makeText(RingingActivity.this,
                                Alarm.isWordTask(taskType)
                                        ? R.string.ringing_word_back_hint
                                        : R.string.ringing_back_hint,
                                Toast.LENGTH_SHORT).show();
                    }
                });

        applyIntent(getIntent());
        updateClock();
        updateHint();
    }

    private void applyIntent(Intent intent) {
        if (intent == null) return;
        debugKeep = intent.getBooleanExtra(EXTRA_DEBUG_KEEP, false);
        reps = intent.getIntExtra(AlarmService.EXTRA_REPS, 20);
        targetWords = intent.getIntExtra(AlarmService.EXTRA_WORDS, 10);
        wordStart = intent.getIntExtra(AlarmService.EXTRA_WORD_START, 0);
        wordBook = intent.getStringExtra(AlarmService.EXTRA_WORD_BOOK);
        if (wordBook == null || wordBook.isEmpty()) wordBook = WordStore.getBook(this);
        wordSessionId = intent.getStringExtra(AlarmService.EXTRA_WORD_SESSION);
        if (wordSessionId == null) wordSessionId = "";
        taskType = intent.getStringExtra(AlarmService.EXTRA_TASK);
        if (!Alarm.isWordTask(taskType)) taskType = Alarm.TASK_JUMPING_JACK;
        alarmId = intent.getLongExtra(AlarmService.EXTRA_ID, -1L);
        repsText.setText(Alarm.isWordTask(taskType)
                ? getString(R.string.ringing_words_fmt, targetWords)
                : getString(R.string.ringing_reps_fmt, reps));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyIntent(intent);
    }

    /**
     * 音量键在响铃期间是废键：服务每秒会把闹钟音量重新拉满，
     * 这里再消费掉按键，免得用户以为能调小声音。
     */
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

    private void dismissAlarm() {
        // 设备所有者锁定的任务中，finish 前必须先退出锁定，否则任务无法结束
        KioskController.exitLockTask(this);
        AlarmService.stop(this);
        finish();
    }

    private void closeAndUnlock() {
        KioskController.exitLockTask(this);
        finish();
    }

    private void updateClock() {
        Calendar c = Calendar.getInstance();
        clock.setText(String.format(Locale.getDefault(), "%02d:%02d",
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)));
    }

    /** 免打扰/静音命中时，把提示换成「已切强震动」，让用户知道响铃没坏 */
    private void updateHint() {
        if (AlarmService.vibrationBoost) {
            hint.setText(R.string.ringing_vibration_boost);
        } else {
            if (Alarm.isWordTask(taskType)) {
                hint.setText(Alarm.TASK_WORD_CHOICE.equals(taskType)
                        ? R.string.ringing_choice_hint
                        : R.string.ringing_word_hint);
            } else {
                hint.setText(R.string.ringing_hint);
            }
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
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        AlarmService.uiHidden();
        handler.removeCallbacks(tick);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // 重新获得焦点时（例如系统弹窗消失）再收一次状态栏，并尝试重新锁定
        if (hasFocus) {
            hideSystemBars();
            if (!debugKeep) KioskController.enterLockTask(this);
        }
    }
}
