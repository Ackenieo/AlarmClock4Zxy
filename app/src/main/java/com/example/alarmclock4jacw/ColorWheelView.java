package com.example.alarmclock4jacw;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * 色相环：外圈是色盘，中心是当前选中的颜色，拖动/点击外圈选色相。
 *
 * 色相被量化到 {@link SkinStore#HUE_STEPS} 格，和预生成的主题覆盖层一一对应
 * （运行时任意色值注不进主题，详见 SkinStore 的注释）。
 */
public class ColorWheelView extends View {

    public interface OnHueChangeListener {
        void onHueChanged(int hueIndex);
    }

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ringBounds = new RectF();

    private int hueIndex;
    private int centerColor = Color.WHITE;
    private OnHueChangeListener listener;

    private float cx, cy, radius, ringWidth;
    private boolean tracking;

    public ColorWheelView(Context context) {
        this(context, null);
    }

    public ColorWheelView(Context context, AttributeSet attrs) {
        super(context, attrs);

        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeCap(Paint.Cap.BUTT);

        centerPaint.setStyle(Paint.Style.FILL);

        thumbPaint.setStyle(Paint.Style.FILL);
        thumbPaint.setColor(Color.WHITE);

        thumbStroke.setStyle(Paint.Style.STROKE);
        thumbStroke.setColor(0x33000000);
        thumbStroke.setStrokeWidth(dp(1.5f));
    }

    public void setOnHueChangeListener(OnHueChangeListener l) {
        listener = l;
    }

    public int getHueIndex() {
        return hueIndex;
    }

    /** 设置色相并重画（不回调，避免初始化时触发一次假变更）。 */
    public void setHueIndex(int index) {
        int i = ((index % SkinStore.HUE_STEPS) + SkinStore.HUE_STEPS) % SkinStore.HUE_STEPS;
        if (i != hueIndex) {
            hueIndex = i;
            invalidate();
        }
    }

    public void setCenterColor(int color) {
        centerColor = color;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        cx = w / 2f;
        cy = h / 2f;
        float outer = Math.min(w, h) / 2f - dp(6);
        ringWidth = outer * 0.34f;
        radius = outer - ringWidth / 2f;

        ringBounds.set(cx - radius, cy - radius, cx + radius, cy + radius);
        ringPaint.setStrokeWidth(ringWidth);

        int steps = SkinStore.HUE_STEPS;
        int[] colors = new int[steps + 1];
        for (int i = 0; i <= steps; i++) {
            colors[i] = Color.HSVToColor(new float[]{i * 360f / steps, 0.72f, 1f});
        }
        ringPaint.setShader(new SweepGradient(cx, cy, colors, null));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        canvas.drawCircle(cx, cy, radius, ringPaint);

        // 中心：当前颜色
        float inner = radius - ringWidth / 2f - dp(8);
        centerPaint.setColor(centerColor);
        canvas.drawCircle(cx, cy, inner, centerPaint);

        // 选中位置的圆点
        double rad = Math.toRadians(SkinStore.hueDegrees(hueIndex));
        float tx = (float) (cx + Math.cos(rad) * radius);
        float ty = (float) (cy + Math.sin(rad) * radius);
        float r = ringWidth / 2f - dp(1);
        canvas.drawCircle(tx, ty, r, thumbPaint);
        canvas.drawCircle(tx, ty, r, thumbStroke);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                tracking = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                pick(event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (tracking) pick(event.getX(), event.getY());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                tracking = false;
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void pick(float x, float y) {
        double deg = Math.toDegrees(Math.atan2(y - cy, x - cx));
        if (deg < 0) deg += 360;

        int steps = SkinStore.HUE_STEPS;
        int index = (int) Math.round(deg / (360.0 / steps)) % steps;

        if (index != hueIndex) {
            hueIndex = index;
            invalidate();
            if (listener != null) listener.onHueChanged(index);
        }
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
