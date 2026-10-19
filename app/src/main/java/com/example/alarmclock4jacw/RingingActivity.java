package com.example.alarmclock4jacw;

import android.app.KeyguardManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.util.Calendar;
import java.util.Locale;

/**
 * 响铃页：锁屏上直接显示，声音不停；只有完成运动（或长按紧急关闭）才能停。
 */
public class RingingActivity extends AppCompatActivity {

    private TextView clock;
    private TextView repsText;
    private int reps = 20;
    private long alarmId = -1L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            handler.postDelayed(this, 1000);
        }
    };

    private ActivityResultLauncher<Intent> exerciseLauncher;

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

        setContentView(R.layout.activity_ringing);
        hideSystemBars();

        clock = findViewById(R.id.clock);
        repsText = findViewById(R.id.reps_text);

        exerciseLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) dismissAlarm();
                });

        findViewById(R.id.btn_start).setOnClickListener(v -> {
            Intent i = new Intent(this, ExerciseActivity.class);
            i.putExtra(AlarmService.EXTRA_REPS, reps);
            i.putExtra(AlarmService.EXTRA_ID, alarmId);
            exerciseLauncher.launch(i);
        });

        findViewById(R.id.btn_emergency).setOnLongClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setMessage(R.string.emergency_confirm)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.ringing_emergency, (d, w) -> {
                        Toast.makeText(this, R.string.ringing_emergency_done, Toast.LENGTH_SHORT).show();
                        dismissAlarm();
                    })
                    .show();
            return true;
        });

        getOnBackPressedDispatcher().addCallback(this,
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        Toast.makeText(RingingActivity.this, R.string.ringing_back_hint,
                                Toast.LENGTH_SHORT).show();
                    }
                });

        applyIntent(getIntent());
        updateClock();
    }

    private void applyIntent(Intent intent) {
        if (intent == null) return;
        reps = intent.getIntExtra(AlarmService.EXTRA_REPS, 20);
        alarmId = intent.getLongExtra(AlarmService.EXTRA_ID, -1L);
        repsText.setText(getString(R.string.ringing_reps_fmt, reps));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyIntent(intent);
    }

    private void dismissAlarm() {
        AlarmService.stop(this);
        finish();
    }

    private void updateClock() {
        Calendar c = Calendar.getInstance();
        clock.setText(String.format(Locale.getDefault(), "%02d:%02d",
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)));
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
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
    }
}
