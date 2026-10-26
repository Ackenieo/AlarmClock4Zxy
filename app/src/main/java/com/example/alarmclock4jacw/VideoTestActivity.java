package com.example.alarmclock4jacw;

import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.pose.Pose;
import com.google.mlkit.vision.pose.PoseDetection;
import com.google.mlkit.vision.pose.PoseDetector;
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions;

import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 开发用：把 assets 里的测试视频逐帧喂给 ML Kit，用同一套 JumpingJackCounter 统计，
 * 用来验证/回归识别算法（不依赖真人站在镜头前）。
 * 长按标题可进入。
 */
public class VideoTestActivity extends AppCompatActivity {

    private static final String TAG = "JJVIDEO";
    private static final String ASSET = "test_jumpingjack.mp4";
    /** 采样间隔，约 15fps，兼顾速度与真实性 */
    private static final long STEP_MS = 66L;

    private ImageView frameView;
    private PoseOverlayView overlay;
    private TextView stats;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final AtomicBoolean uiBusy = new AtomicBoolean(false);
    private volatile boolean stopped;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SkinStore.applyTheme(this);
        setContentView(R.layout.activity_video_test);

        frameView = findViewById(R.id.frame);
        overlay = findViewById(R.id.overlay);
        stats = findViewById(R.id.stats);
        overlay.setMirror(false);
        overlay.setFitCenter(true);

        Thread worker = new Thread(this::runVideo, "video-pose-test");
        worker.setDaemon(true);
        worker.start();
    }

    private void runVideo() {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        PoseDetector detector = PoseDetection.getClient(new PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
                .build());
        JumpingJackCounter counter = new JumpingJackCounter(999);
        try {
            AssetFileDescriptor afd = getAssets().openFd(ASSET);
            retriever.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            String durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long durationMs = durStr == null ? 0 : Long.parseLong(durStr);
            int totalFrames = (int) (durationMs / STEP_MS);

            int index = 0;
            for (long t = 0; t < durationMs && !stopped; t += STEP_MS) {
                Bitmap bmp = retriever.getFrameAtTime(t * 1000L,
                        MediaMetadataRetriever.OPTION_CLOSEST);
                if (bmp == null) continue;

                Pose pose = Tasks.await(detector.process(InputImage.fromBitmap(bmp, 0)),
                        10, TimeUnit.SECONDS);
                Landmarks lm = Landmarks.fromPose(pose, bmp.getWidth(), bmp.getHeight());
                JumpingJackCounter.Frame f = counter.update(lm, t);

                Log.d(TAG, String.format(Locale.US,
                        "t=%dms count=%d phase=%s valid=%b arm=%.2f leg=%.2f hint=%s",
                        t, f.count, f.phase, f.valid, f.armScore, f.legScore, f.hint));

                final int idx = ++index;
                final int fc = f.count;
                final float fa = f.armScore;
                final float fl = f.legScore;
                final boolean fv = f.valid;
                final String fphase = f.phase.name();
                final boolean open = f.phase == JumpingJackCounter.Phase.OPEN;
                if (uiBusy.compareAndSet(false, true)) {
                    ui.post(() -> {
                        frameView.setImageBitmap(bmp);
                        overlay.setPose(lm, bmp.getWidth(), bmp.getHeight(), open);
                        stats.setText(getString(R.string.video_stats_fmt,
                                idx, totalFrames, fc, fa, fl, fv ? fphase : "no-pose"));
                        uiBusy.set(false);
                    });
                }
            }
            Log.i(TAG, "FINISHED total reps = " + counter.getCount());
            ui.post(() -> stats.setText("识别结束 · 共 " + counter.getCount() + " 个开合跳"));
        } catch (Exception e) {
            Log.e(TAG, "video test failed", e);
            ui.post(() -> stats.setText("失败：" + e));
        } finally {
            detector.close();
            try {
                retriever.release();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onDestroy() {
        stopped = true;
        super.onDestroy();
    }
}
