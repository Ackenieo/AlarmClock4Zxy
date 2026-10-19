package com.example.alarmclock4jacw;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * 圆环进度：中间大字显示已完成次数，右侧小字显示目标次数。
 */
public class RingProgressView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint countPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint targetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF oval = new RectF();
    private float progress;
    private int count;
    private int target = 20;
    private int accent = Color.parseColor("#FF7A45");

    public RingProgressView(Context context) {
        this(context, null);
    }

    public RingProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(7f * density);
        trackPaint.setColor(0x33FFFFFF);

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(7f * density);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(accent);

        countPaint.setColor(Color.WHITE);
        countPaint.setTextAlign(Paint.Align.CENTER);
        countPaint.setFakeBoldText(true);

        targetPaint.setColor(0xAAFFFFFF);
        targetPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setProgress(int count, int target) {
        this.count = count;
        this.target = Math.max(1, target);
        this.progress = Math.min(1f, count / (float) this.target);
        this.accent = count >= this.target ? Color.parseColor("#35D0A5") : Color.parseColor("#FF7A45");
        progressPaint.setColor(accent);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float size = Math.min(w, h);
        float stroke = progressPaint.getStrokeWidth();
        float pad = stroke / 2f + 2f;

        oval.set((w - size) / 2f + pad, (h - size) / 2f + pad,
                (w + size) / 2f - pad, (h + size) / 2f - pad);

        canvas.drawArc(oval, -90f, 360f, false, trackPaint);
        if (progress > 0f) {
            canvas.drawArc(oval, -90f, 360f * progress, false, progressPaint);
        }

        float density = getResources().getDisplayMetrics().density;
        countPaint.setTextSize(size * 0.30f);
        targetPaint.setTextSize(size * 0.13f);

        float cx = w / 2f;
        float cy = h / 2f;
        canvas.drawText(String.valueOf(count), cx, cy + size * 0.07f, countPaint);
        canvas.drawText("/ " + target, cx, cy + size * 0.25f, targetPaint);
    }
}
