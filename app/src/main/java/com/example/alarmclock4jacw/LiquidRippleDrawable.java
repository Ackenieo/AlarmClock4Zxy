package com.example.alarmclock4jacw;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 特效 UI：液态按钮。
 *
 * 按下时从按钮中心荡开一团水波：边缘按正弦起伏（不是正圆），
 * 后面还追着一个小一圈的慢波，看着像液体被挤出去。
 *
 * 做成 Drawable 是为了直接 {@code setForeground()}，按钮样式一行都不用动；
 * 它自己监听 state_pressed，不用 Activity 传触摸坐标。
 */
public class LiquidRippleDrawable extends Drawable {

    private static final long DURATION_MS = 620L;
    private static final int STEPS = 72;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final Path blob = new Path();
    private final RectF rect = new RectF();

    private final float corner;
    private final float strokeWidth;

    /** 0 = 刚按下，1 = 完全散开（静止态，什么都不画） */
    private float progress = 1f;
    /** Drawable 整体透明度系数，和每帧算出来的 alpha 相乘 */
    private float alphaScale = 1f;
    private ValueAnimator animator;

    public LiquidRippleDrawable(Context context) {
        this(context, 20f);
    }

    public LiquidRippleDrawable(Context context, float cornerDp) {
        corner = dp(context, cornerDp);
        strokeWidth = dp(context, 1.4f);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(strokeWidth);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (progress >= 1f) return;

        rect.set(getBounds());
        if (rect.width() <= 0f || rect.height() <= 0f) return;

        // 圆角裁一下，不然水波会从按钮的直角边上溢出去
        clip.reset();
        clip.addRoundRect(rect, corner, corner, Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(clip);

        float cx = rect.centerX();
        float cy = rect.centerY();
        float maxR = (float) Math.hypot(rect.width() * 0.5f, rect.height() * 0.5f) * 1.06f;

        // 主波
        float r = maxR * progress;
        buildBlob(blob, cx, cy, r, 5, 9f, progress);
        fill.setColor(alpha(0xFFFFFFFF, 0.26f * (1f - progress * progress) * alphaScale));
        canvas.drawPath(blob, fill);
        rim.setColor(alpha(0xFFFFFFFF, 0.55f * (1f - progress) * alphaScale));
        canvas.drawPath(blob, rim);

        // 内层慢波：晚 18% 起步、慢一点、小一圈，多一层水感
        if (progress > 0.18f) {
            float p2 = (progress - 0.18f) / 0.82f;
            buildBlob(blob, cx, cy, maxR * p2 * 0.72f, 4, 11f, p2);
            fill.setColor(alpha(0xFFFFFFFF, 0.15f * (1f - p2) * alphaScale));
            canvas.drawPath(blob, fill);
        }

        canvas.restoreToCount(save);
    }

    /** 把一团波形的轮廓写进 path：半径按正弦起伏，不是正圆。 */
    private static void buildBlob(Path out, float cx, float cy, float r,
                                  int lobes, float wobble, float phase) {
        out.reset();
        if (r <= 0.5f) return;

        for (int i = 0; i <= STEPS; i++) {
            double a = i * Math.PI * 2 / STEPS;
            float k = 1f + 0.085f * (float) Math.sin(a * lobes + phase * wobble);
            float rr = r * k;
            float x = cx + (float) Math.cos(a) * rr;
            float y = cy + (float) Math.sin(a) * rr;
            if (i == 0) out.moveTo(x, y);
            else out.lineTo(x, y);
        }
        out.close();
    }

    @Override
    public boolean isStateful() {
        return true;
    }

    @Override
    public boolean onStateChange(int[] state) {
        boolean pressed = false;
        for (int s : state) {
            if (s == android.R.attr.state_pressed) {
                pressed = true;
                break;
            }
        }
        // 松手不打断：让水波自己荡完，硬停会看着像卡住
        if (pressed) wave();
        return true;
    }

    /**
     * 手动荡一次。
     * 前景 Drawable 的 state_pressed 转发在部分机型上不生效，所以按钮那边
     * 会直接监听 ACTION_DOWN 调这个方法；onStateChange 只是兜底。
     */
    public void wave() {
        if (animator != null) animator.cancel();
        progress = 0f;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(DURATION_MS);
        animator.setInterpolator(new DecelerateInterpolator(1.6f));
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidateSelf();
        });
        animator.start();
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        // 界面藏起来就别继续算了
        if (!visible && animator != null) {
            animator.cancel();
            animator = null;
            progress = 1f;
        }
        return changed;
    }

    @Override
    public void setAlpha(int a) {
        alphaScale = Math.max(0f, Math.min(1f, a / 255f));
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        fill.setColorFilter(colorFilter);
        rim.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    private static int alpha(int color, float a) {
        int v = Math.round(Math.max(0f, Math.min(1f, a)) * 255f);
        return (color & 0x00FFFFFF) | (v << 24);
    }

    private static float dp(Context c, float v) {
        return v * c.getResources().getDisplayMetrics().density;
    }
}
