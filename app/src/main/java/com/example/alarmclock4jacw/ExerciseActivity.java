package com.example.alarmclock4jacw;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.util.Size;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;


/**
 * 运动挑战页：CameraX 实时取流 -> ML Kit 骨骼识别 -> 开合跳计数。
 * 达到目标次数后返回 RESULT_OK，响铃页据此关闭闹钟。
 *
 * 这一页没有退出按钮，也没有长按强关：做够次数是唯一出口，
 * 只剩 {@link AlarmService} 的 30 分钟兜底会自动收掉本页。
 */
public class ExerciseActivity extends BaseActivity {

    private static final String TAG = "ExerciseActivity";

    private PreviewView previewView;
    private PoseOverlayView overlay;
    private RingProgressView ring;
    private TextView hintView;
    private TextView countdownView;
    private View permissionPanel;
    private View successPanel;

    private PoseDetector detector;
    private JumpingJackCounter counter;
    private int target = 20;

    private ProcessCameraProvider cameraProvider;
    private ImageAnalysis analysis;
    private boolean useFront = true;
    private volatile boolean analyzing = false;
    private boolean counting = false;
    private boolean finished = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer successPlayer;
    private Vibrator vibrator;

    private ActivityResultLauncher<String> cameraPermissionLauncher;

    /**
     * 服务侧 30 分钟兜底停止后收掉本页。
     * 不返回 RESULT_OK：闹钟已经由服务自行停止，响铃页不该再发一次停止指令。
     */
    private final Runnable guard = new Runnable() {
        @Override
        public void run() {
            if (!AlarmService.ringing && !finished) {
                finished = true;
                if (analysis != null) analysis.clearAnalyzer();
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
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_exercise);
        hideSystemBars();

        target = getIntent().getIntExtra(AlarmService.EXTRA_REPS, 20);
        counter = new JumpingJackCounter(target);

        previewView = findViewById(R.id.preview);
        overlay = findViewById(R.id.overlay);
        ring = findViewById(R.id.ring);
        hintView = findViewById(R.id.hint);
        countdownView = findViewById(R.id.countdown);
        permissionPanel = findViewById(R.id.permission_panel);
        successPanel = findViewById(R.id.success_panel);

        ring.setProgress(0, target);
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

        detector = PoseDetection.getClient(new PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build());

        cameraPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        permissionPanel.setVisibility(View.GONE);
                        startCamera();
                    } else {
                        permissionPanel.setVisibility(View.VISIBLE);
                    }
                });

        findViewById(R.id.btn_switch_camera).setOnClickListener(v -> {
            useFront = !useFront;
            bindUseCases();
        });
        findViewById(R.id.btn_grant).setOnClickListener(v -> requestCameraPermission());

        // 调试包专用：长按进度环直接判定完成，方便在没有真人的环境（模拟器/演示）里跑通整条链路。
        // release 包里 BuildConfig.DEBUG 为 false，这个入口不存在。
        ring.setOnLongClickListener(v -> {
            if (!BuildConfig.DEBUG) return false;
            Toast.makeText(this, R.string.debug_complete, Toast.LENGTH_SHORT).show();
            finishSuccess();
            return true;
        });

        if (hasCameraPermission()) {
            permissionPanel.setVisibility(View.GONE);
            startCamera();
        } else {
            permissionPanel.setVisibility(View.VISIBLE);
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestCameraPermission() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            return;
        }
        // 已被永久拒绝，只能引导到系统设置
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null)));
        } catch (Exception e) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    // ---------------------------------------------------------------- camera

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindUseCases();
                startCountdown();
            } catch (Exception e) {
                Log.e(TAG, "camera init failed", e);
                hintView.setText(R.string.exercise_hint_no_person);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private ResolutionSelector selector43() {
        return new ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .setResolutionStrategy(new ResolutionStrategy(new Size(640, 480),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                .build();
    }

    private void bindUseCases() {
        if (cameraProvider == null) return;
        try {
            cameraProvider.unbindAll();

            Preview preview = new Preview.Builder()
                    .setResolutionSelector(selector43())
                    .build();
            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            analysis = new ImageAnalysis.Builder()
                    .setResolutionSelector(selector43())
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();
            analysis.setAnalyzer(ContextCompat.getMainExecutor(this), this::analyze);

            CameraSelector selector = useFront
                    ? CameraSelector.DEFAULT_FRONT_CAMERA
                    : CameraSelector.DEFAULT_BACK_CAMERA;
            cameraProvider.bindToLifecycle(this, selector, preview, analysis);
            overlay.setMirror(useFront);
        } catch (Exception e) {
            Log.e(TAG, "bind failed", e);
            // 前置摄像头不可用时退回后置
            if (useFront) {
                useFront = false;
                bindUseCases();
            }
        }
    }

    private void analyze(@NonNull ImageProxy proxy) {
        if (analyzing || finished) {
            proxy.close();
            return;
        }
        if (proxy.getImage() == null) {
            proxy.close();
            return;
        }
        analyzing = true;
        int rotation = proxy.getImageInfo().getRotationDegrees();
        InputImage image = InputImage.fromMediaImage(proxy.getImage(), rotation);

        detector.process(image)
                .addOnSuccessListener(pose -> onPose(pose, proxy, rotation))
                .addOnFailureListener(e -> Log.w(TAG, "pose detect failed", e))
                .addOnCompleteListener(task -> {
                    analyzing = false;
                    proxy.close();
                });
    }

    // ---------------------------------------------------------------- pose

    private void onPose(Pose pose, ImageProxy proxy, int rotation) {
        if (finished || isFinishing()) return;

        int w = proxy.getWidth();
        int h = proxy.getHeight();
        boolean rotated = rotation == 90 || rotation == 270;
        int uprightW = rotated ? h : w;
        int uprightH = rotated ? w : h;

        Landmarks lm = Landmarks.fromPose(pose, uprightW, uprightH);
        overlay.setPose(lm, uprightW, uprightH,
                counter.getPhase() == JumpingJackCounter.Phase.OPEN);

        if (!counting) return;

        JumpingJackCounter.Frame frame = counter.update(lm, SystemClock.elapsedRealtime());
        ring.setProgress(frame.count, target);

        if (frame.countedNow) {
            buzz(25);
        }
        updateHint(frame);

        if (frame.count >= target) {
            finishSuccess();
        }
    }

    private void updateHint(JumpingJackCounter.Frame frame) {
        if (!frame.valid) {
            switch (frame.hint) {
                case "feet":
                    hintView.setText(R.string.exercise_hint_feet);
                    break;
                case "arms":
                    hintView.setText(R.string.exercise_hint_arms);
                    break;
                case "no_person":
                    hintView.setText(R.string.exercise_hint_no_person);
                    break;
                default:
                    hintView.setText(R.string.exercise_hint_stand);
                    break;
            }
            return;
        }
        if (frame.tooClose) {
            hintView.setText(R.string.exercise_hint_too_close);
            return;
        }
        hintView.setText(frame.phase == JumpingJackCounter.Phase.OPEN
                ? R.string.exercise_hint_closed
                : R.string.exercise_hint_open);
    }

    // ---------------------------------------------------------------- flow

    private void startCountdown() {
        counting = false;
        countdownView.setVisibility(View.VISIBLE);
        countdown(3);
    }

    private void countdown(final int n) {
        if (finished) return;
        if (n <= 0) {
            countdownView.setVisibility(View.GONE);
            counting = true;
            hintView.setText(R.string.exercise_hint_stand);
            return;
        }
        countdownView.setText(String.valueOf(n));
        countdownView.setAlpha(1f);
        countdownView.animate().alpha(0.2f).setDuration(600).withEndAction(() ->
                handler.postDelayed(() -> countdown(n - 1), 100));
    }

    private void finishSuccess() {
        if (finished) return;
        finished = true;
        counting = false;
        if (analysis != null) analysis.clearAnalyzer();

        successPanel.setVisibility(View.VISIBLE);
        overlay.clear();
        playSuccessSound();
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 60, 60, 60}, -1));
        }

        handler.postDelayed(() -> {
            setResult(RESULT_OK);
            finish();
        }, 1600);
    }

    private void playSuccessSound() {
        try {
            successPlayer = new MediaPlayer();
            successPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            android.content.res.AssetFileDescriptor afd = getResources().openRawResourceFd(R.raw.success);
            successPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            successPlayer.setOnCompletionListener(mp -> {
                mp.release();
                successPlayer = null;
            });
            successPlayer.prepare();
            successPlayer.start();
        } catch (Exception e) {
            Log.w(TAG, "success sound failed", e);
        }
    }

    private void buzz(long ms) {
        if (vibrator == null || !vibrator.hasVibrator()) return;
        vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
    }

    @Override
    protected void onResume() {
        super.onResume();
        AlarmService.uiSeen();
        handler.removeCallbacks(guard);
        handler.postDelayed(guard, 1000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        AlarmService.uiHidden();
        handler.removeCallbacks(guard);
    }

    /** 运动时手会碰到音量键，同样不让它把声音调小 */
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

    private void hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        WindowInsetsControllerCompat c =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        c.hide(WindowInsetsCompat.Type.systemBars());
        c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // 通知横幅等系统界面会让状态栏重新出现，重新获得焦点时再收一次
        if (hasFocus) hideSystemBars();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (analysis != null) analysis.clearAnalyzer();
        if (cameraProvider != null) cameraProvider.unbindAll();
        if (detector != null) detector.close();
        if (successPlayer != null) {
            successPlayer.release();
            successPlayer = null;
        }
        super.onDestroy();
    }
}
