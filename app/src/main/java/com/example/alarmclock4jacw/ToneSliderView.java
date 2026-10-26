package com.example.alarmclock4jacw;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * 明暗度滑条：轨道本身就是当前色相从「浅」到「深」的渐变，滑到哪儿一眼能看出深浅。
 *
 * 自绘而不是用 Material Slider，是因为要：
 *   1. 轨道画成渐变（系统 Slider 只能单色）；
 *   2. 拖动时拇指连续跟手，松手才吸附到最近的档位。
 * 颜色档位是 SkinStore.TONE_STEPS 个，档距 1/40，相邻两档 RGB 只差 2~4，看不出台阶。
 */
public class ToneSliderView extends View {

    public interface OnToneChangeListener {
        void onToneChanged(int toneIndex);
    }

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbShell = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thumbFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trackEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();

    private int hueIndex;
    private int toneIndex;
    /** 拇指的连续位置 0..1，拖动时跟手，松手吸附到档位 */
    private float pos;
    private OnToneChangeListener listener;

    private boolean bgMode;
    private float centerY;
    private float trackHeight;
    private float thumbRadius;
    private boolean tracking;

    public ToneSliderView(Context context) {
        this(context, null);
    }

    public ToneSliderView(Context context, AttributeSet attrs) {
        super(context, attrs);

        trackHeight = dp(10);
        thumbRadius = dp(11);

        thumbShell.setColor(0xFFFFFFFF);
        thumbEdge.setStyle(Paint.Style.STROKE);
        thumbEdge.setColor(0x40000000);
        thumbEdge.setStrokeWidth(dp(1));

        trackEdge.setStyle(Paint.Style.STROKE);
        trackEdge.setColor(0x33FFFFFF);
        trackEdge.setStrokeWidth(dp(1));
    }

    /** 切到背景色模式：档位更少（只在深色区间），渐变也换成背景色。 */
    public void setBackgroundMode(boolean background) {
        if (bgMode == background) return;
        bgMode = background;
        toneIndex = Math.min(toneIndex, steps() - 1);
        pos = toneIndex / (float) (steps() - 1);
        rebuildTrack();
        invalidate();
    }

    private int steps() {
        return bgMode ? SkinStore.BG_TONE_STEPS : SkinStore.TONE_STEPS;
    }

    private int colorAt(int index) {
        SkinStore store = SkinStore.get(getContext());
        return bgMode ? store.bgToneColor(hueIndex, index)
                : store.toneColor(hueIndex, index);
    }

    public void setOnToneChangeListener(OnToneChangeListener l) {
        listener = l;
    }

    public int getToneIndex() {
        return toneIndex;
    }

    /** 换色相：重画渐变轨道，档位不变。 */
    public void setHueIndex(int index) {
        hueIndex = index;
        rebuildTrack();
        invalidate();
    }

    /** 设置档位（不回调，避免初始化时触发一次假变更）。 */
    public void setToneIndex(int index) {
        int max = steps() - 1;
        toneIndex = Math.max(0, Math.min(max, index));
        pos = toneIndex / (float) max;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerY = h / 2f;
        float padX = thumbRadius + dp(1);
        trackRect.set(padX, centerY - trackHeight / 2f, w - padX, centerY + trackHeight / 2f);
        rebuildTrack();
    }

    private void rebuildTrack() {
        int n = steps();
        int[] colors = new int[n];
        for (int i = 0; i < n; i++) {
            colors[i] = colorAt(i);
        }
        trackPaint.setShader(new LinearGradient(
                trackRect.left, 0f, trackRect.right, 0f, colors, null, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float r = trackHeight / 2f;
        canvas.drawRoundRect(trackRect, r, r, trackPaint);
        canvas.drawRoundRect(trackRect, r, r, trackEdge);   // 细描边，深色轨道在黑底上也看得见

        float x = trackRect.left + pos * trackRect.width();
        canvas.drawCircle(x, centerY, thumbRadius, thumbShell);
        canvas.drawCircle(x, centerY, thumbRadius, thumbEdge);
        thumbFill.setColor(colorAt(toneIndex));
        canvas.drawCircle(x, centerY, thumbRadius * 0.62f, thumbFill);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                tracking = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                moveTo(event.getX());
                return true;
            case MotionEvent.ACTION_MOVE:
                if (tracking) moveTo(event.getX());
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                tracking = false;
                // 松手吸附到最近档位，拇指和颜色对齐
                pos = toneIndex / (float) (steps() - 1);
                invalidate();
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    private void moveTo(float x) {
        float t = (x - trackRect.left) / trackRect.width();
        t = Math.max(0f, Math.min(1f, t));
        pos = t;

        int index = Math.round(t * (steps() - 1));
        if (index != toneIndex) {
            toneIndex = index;
            if (listener != null) listener.onToneChanged(index);
        }
        invalidate();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
