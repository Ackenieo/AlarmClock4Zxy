package com.example.alarmclock4jacw;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

/**
 * 特效 UI：霓虹呼吸。
 *
 * 铺在主按钮上面（不接管触摸），沿着按钮轮廓画一圈「灯管」：
 * 几层由宽到窄的描边叠出光往外溢的感觉，亮度跟着呼吸曲线缓慢明灭，
 * 顺带把按钮本体也提亮一点点，看着像灯管在给按钮打光。
 *
 * 几何直接用自己的 bounds —— 外层容器的 padding 已经让出了光晕的空间，
 * 描边有一半会画到 bounds 外面去，正好落在那圈留白里。
 * 这里千万别再额外 inset：容器 padding 已经把子视图的 match_parent 压成内容区了，
 * 再内缩一圈，光圈就跑到按钮里面去了。
 */
public class NeonPulseView extends View {

    /** 一次「明」或一次「灭」的时长，来回一趟 = 3 秒 */
    private static final long HALF_CYCLE_MS = 1500L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int accent;

    private final float corner;

    /** 0 = 最暗，1 = 最亮 */
    private float pulse = 0.5f;
    private ValueAnimator animator;

    public NeonPulseView(Context context) {
        this(context, null);
    }

    public NeonPulseView(Context context, AttributeSet attrs) {
        super(context, attrs);
        accent = SkinStore.accent(context);
        corner = dp(20f);       // 与 Widget.PrimaryButton 的 cornerRadius 对齐
        paint.setStyle(Paint.Style.STROKE);
        setClickable(false);
        setFocusable(false);
        setWillNotDraw(false);
    }

    /**
     * 兜底：放进 wrap_content 的容器时，match_parent 会拿到 AT_MOST(整屏)，
     * 光框就会被拉成一整屏那么长。这里把上限收到按钮高度。
     */
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int h = MeasureSpec.getSize(heightMeasureSpec);
        if (MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
            h = Math.min(h, Math.round(dp(64f)));
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        rect.set(0f, 0f, getWidth(), getHeight());
        if (rect.width() <= 0f || rect.height() <= 0f) return;

        // 1. 外溢的光：由宽到窄四层，越宽越淡，模拟灯管周围的辉光
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        for (int i = 3; i >= 0; i--) {
            paint.setStrokeWidth(dp(2f + i * 2.6f));
            paint.setColor(alpha(accent, (0.16f - i * 0.032f) * (0.55f + 0.8f * pulse)));
            canvas.drawRoundRect(rect, corner, corner, paint);
        }

        // 2. 灯管本体：常亮，亮度是呼吸的主线
        paint.setStrokeWidth(dp(1.5f));
        paint.setColor(alpha(accent, 0.45f + 0.55f * pulse));
        canvas.drawRoundRect(rect, corner, corner, paint);

        // 3. 呼吸时按钮被一起点亮，压一层几乎看不见的白
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(alpha(0xFFFFFFFF, 0.06f * pulse));
        canvas.drawRoundRect(rect, corner, corner, paint);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getWindowVisibility() == VISIBLE) start();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        // 跳到运动页 / 背屏时停掉，别在后台白烧帧
        if (visibility == VISIBLE) start();
        else stop();
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE) start();
        else stop();
    }

    private void start() {
        if (animator != null) return;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(HALF_CYCLE_MS);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(a -> {
            pulse = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private void stop() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
    }

    private static int alpha(int color, float a) {
        int v = Math.round(Math.max(0f, Math.min(1f, a)) * 255f);
        return (color & 0x00FFFFFF) | (v << 24);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
