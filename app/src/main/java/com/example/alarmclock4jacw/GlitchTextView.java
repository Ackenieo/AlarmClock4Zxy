package com.example.alarmclock4jacw;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.SystemClock;
import android.util.AttributeSet;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;

import java.util.Random;

/**
 * 特效 UI：故障抖色。
 *
 * 平时安安静静，隔几秒抽一下：整段字错位几像素，红/青两个通道往反方向再各画一遍，
 * 叠出老录像带那种色散。响铃页的大时钟用这个 —— 时间在跳，字也在抖。
 *
 * 抖动全部在 onDraw 里用 canvas 平移做，不改 View 的 translationX，
 * 所以不会影响布局和点击区域。
 */
public class GlitchTextView extends AppCompatTextView {

    private static final long FRAME_MS = 40L;
    /** 一次抽动的时长（再随机加 0~90ms，避免看起来像固定节拍） */
    private static final long BURST_MS = 210L;
    private static final long IDLE_MIN_MS = 2200L;
    private static final long IDLE_JITTER_MS = 1600L;

    /** 红色通道整体往右偏、青色往左偏，这是色散的方向 */
    private static final int SHIFT_R = 0xFFFF2E4A;
    private static final int SHIFT_C = 0xFF1FE3E3;

    private final Random random = new Random();
    private final PorterDuffColorFilter filterR =
            new PorterDuffColorFilter(SHIFT_R, PorterDuff.Mode.SRC_IN);
    private final PorterDuffColorFilter filterC =
            new PorterDuffColorFilter(SHIFT_C, PorterDuff.Mode.SRC_IN);

    private boolean enabled = true;
    private boolean glitching;
    private long burstEnd;
    private float dx;
    private float dy;

    private final Runnable frameTick = new Runnable() {
        @Override
        public void run() {
            if (!glitching) return;
            if (SystemClock.uptimeMillis() >= burstEnd) {
                glitching = false;
                dx = 0f;
                dy = 0f;
                invalidate();
                scheduleIdle();
                return;
            }
            dx = (random.nextFloat() - 0.5f) * 2f * dp(2.6f);
            dy = (random.nextFloat() - 0.5f) * dp(1.4f);
            invalidate();
            postDelayed(this, FRAME_MS);
        }
    };

    private final Runnable idleTick = new Runnable() {
        @Override
        public void run() {
            glitch();
        }
    };

    public GlitchTextView(Context context) {
        this(context, null);
    }

    public GlitchTextView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        // 抖动会画到字形外面一点，留点余量免得被裁
        setClipToOutline(false);
    }

    /** 开关特效。关掉时立刻停掉抖动和排期，不留后台回调。 */
    public void setGlitchEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (!enabled) {
            removeCallbacks(idleTick);
            removeCallbacks(frameTick);
            glitching = false;
            dx = 0f;
            dy = 0f;
            invalidate();
        } else if (isAttachedToWindow()) {
            scheduleIdle();
        }
    }

    /** 手动抽一下。响铃页进来先来一发，之后按空闲节奏自己重复。 */
    public void glitch() {
        if (!enabled || glitching || !isShown()) return;
        glitching = true;
        burstEnd = SystemClock.uptimeMillis() + BURST_MS + random.nextInt(90);
        removeCallbacks(frameTick);
        post(frameTick);
    }

    private void scheduleIdle() {
        removeCallbacks(idleTick);
        if (!enabled) return;
        postDelayed(idleTick, IDLE_MIN_MS + random.nextInt((int) IDLE_JITTER_MS));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!glitching) {
            super.onDraw(canvas);
            return;
        }

        final Paint p = getPaint();
        try {
            // 红通道：右偏 + 上偏
            int save = canvas.save();
            canvas.translate(dx + dp(1.2f), dy);
            p.setColorFilter(filterR);
            super.onDraw(canvas);
            canvas.restoreToCount(save);

            // 青通道：反方向
            save = canvas.save();
            canvas.translate(-dx - dp(1.2f), -dy);
            p.setColorFilter(filterC);
            super.onDraw(canvas);
            canvas.restoreToCount(save);

            // 正常的一层压在上面，只跟着抖一点点，保证字还是读得清
            save = canvas.save();
            canvas.translate(dx * 0.4f, 0f);
            super.onDraw(canvas);
            canvas.restoreToCount(save);
        } finally {
            // 色偏滤镜是挂在共用的 TextPaint 上的，不摘掉会污染后续所有绘制
            p.setColorFilter(null);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        removeCallbacks(idleTick);
        removeCallbacks(frameTick);
        glitching = false;
        // 进来先抽一下：闹钟一响，界面就该有点不对劲
        if (enabled) postDelayed(idleTick, 420L);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(idleTick);
        removeCallbacks(frameTick);
        glitching = false;
        super.onDetachedFromWindow();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
