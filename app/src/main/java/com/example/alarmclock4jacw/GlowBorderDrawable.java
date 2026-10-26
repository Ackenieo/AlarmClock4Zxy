package com.example.alarmclock4jacw;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 特效 UI：流光描边。
 *
 * 卡片边框上有一小段高亮弧绕着跑，底下压一层很淡的常亮描边，
 * 这样即使亮弧转到背面，卡片边缘也还在。
 *
 * 做成 Drawable 而不是 View，是因为可以直接当 {@code ViewGroup.setForeground()} 用，
 * 卡片布局一行都不用改。setVisible() 里启停动画，滑出屏幕就自动停。
 */
public class GlowBorderDrawable extends Drawable implements Animatable {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Matrix matrix = new Matrix();

    private final int accent;
    private final float radius;
    private final float stroke;

    private float angle;
    private ValueAnimator animator;

    public GlowBorderDrawable(Context context) {
        this(context, 24);
    }

    public GlowBorderDrawable(Context context, float cornerDp) {
        this(context, cornerDp, 0f, SkinStore.accent(context));
    }

    /** @param startDeg 起始角度：一屏里摆好几张卡时，给不同的初值才不会整齐划一地转 */
    public GlowBorderDrawable(Context context, float cornerDp, float startDeg) {
        this(context, cornerDp, startDeg, SkinStore.accent(context));
    }

    /**
     * @param color 光弧颜色。皮肤卡上要传卡片自己的强调色，
     *              否则六张卡会用同一个当前色，看着像没换皮肤。
     */
    public GlowBorderDrawable(Context context, float cornerDp, float startDeg, int color) {
        accent = color;
        radius = dp(context, cornerDp);
        stroke = dp(context, 1.6f);
        angle = startDeg;
        paint.setStyle(Paint.Style.STROKE);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty()) return;

        rect.set(bounds);
        rect.inset(stroke / 2f, stroke / 2f);

        // 常亮底边：很淡，保证亮弧转到背面时轮廓还在
        paint.setShader(null);
        paint.setStrokeWidth(stroke);
        paint.setColor(alpha(accent, 0.20f));
        canvas.drawRoundRect(rect, radius, radius, paint);

        // 亮弧：扫一圈，0..0.28 区间是高亮，其余透明
        float cx = rect.centerX();
        float cy = rect.centerY();
        SweepGradient sweep = new SweepGradient(cx, cy,
                new int[]{alpha(accent, 0f), alpha(accent, 0.95f),
                        alpha(accent, 0.25f), alpha(accent, 0f)},
                new float[]{0f, 0.05f, 0.16f, 0.30f});
        matrix.setRotate(angle, cx, cy);
        sweep.setLocalMatrix(matrix);

        paint.setShader(sweep);
        paint.setStrokeWidth(stroke * 3f);          // 先铺一层宽的当辉光
        canvas.drawRoundRect(rect, radius, radius, paint);
        paint.setStrokeWidth(stroke);               // 再压一层实的
        canvas.drawRoundRect(rect, radius, radius, paint);
    }

    @Override
    public void setAlpha(int a) {
        paint.setAlpha(a);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        if (visible) start();
        else stop();
        return changed;
    }

    @Override
    public void start() {
        if (animator != null && animator.isRunning()) return;
        // 从当前角度接着转，别把构造时给的相位抹掉
        animator = ValueAnimator.ofFloat(angle, angle + 360f);
        animator.setDuration(2800);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(a -> {
            angle = (float) a.getAnimatedValue() % 360f;
            invalidateSelf();
        });
        animator.start();
    }

    @Override
    public void stop() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    @Override
    public boolean isRunning() {
        return animator != null && animator.isRunning();
    }

    private static int alpha(int color, float a) {
        int v = Math.round(Math.max(0f, Math.min(1f, a)) * 255f);
        return (color & 0x00FFFFFF) | (v << 24);
    }

    private static float dp(Context c, float v) {
        return v * c.getResources().getDisplayMetrics().density;
    }
}
