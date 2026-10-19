package com.example.alarmclock4jacw;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/**
 * 把骨骼点画在预览画面上。
 * 坐标映射与 PreviewView 的 FILL_CENTER 保持一致（预览与分析都用 4:3，视野相同），
 * 前置摄像头再做一次水平镜像，这样骨骼才能贴合镜面预览。
 */
public class PoseOverlayView extends View {

    private static final int[][] BONES = {
            {11, 12},   // 双肩
            {11, 13}, {13, 15},   // 左臂
            {12, 14}, {14, 16},   // 右臂
            {11, 23}, {12, 24},   // 躯干两侧
            {23, 24},             // 髋
            {23, 25}, {25, 27},   // 左腿
            {24, 26}, {26, 28},   // 右腿
            {0, 11}, {0, 12},     // 头到双肩
    };

    private final Paint bonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint jointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final float[] xs = new float[Landmarks.COUNT];
    private final float[] ys = new float[Landmarks.COUNT];
    private final boolean[] visible = new boolean[Landmarks.COUNT];
    private boolean hasPose;

    private int imageWidth = 480;
    private int imageHeight = 640;
    private boolean mirror = true;
    private boolean openPhase;
    /** true = 与 ImageView 的 fitCenter 对齐（视频回放），false = 与 PreviewView 的 fillCenter 对齐 */
    private boolean fitCenter = false;

    public PoseOverlayView(Context context) {
        this(context, null);
    }

    public PoseOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float density = getResources().getDisplayMetrics().density;
        bonePaint.setStyle(Paint.Style.STROKE);
        bonePaint.setStrokeWidth(4f * density);
        bonePaint.setStrokeCap(Paint.Cap.ROUND);
        bonePaint.setColor(Color.parseColor("#FF7A45"));

        jointPaint.setStyle(Paint.Style.FILL);
        jointPaint.setColor(Color.WHITE);

        haloPaint.setStyle(Paint.Style.FILL);
        haloPaint.setColor(0x3326E0B8);
    }

    public void setMirror(boolean mirror) {
        this.mirror = mirror;
    }

    public void setFitCenter(boolean fitCenter) {
        this.fitCenter = fitCenter;
    }

    public void setPose(Landmarks lm, int imageWidth, int imageHeight, boolean openPhase) {
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.openPhase = openPhase;
        hasPose = lm != null && lm.any;
        if (lm != null) {
            for (int i = 0; i < Landmarks.COUNT; i++) {
                visible[i] = lm.visible[i];
                xs[i] = lm.x[i];
                ys[i] = lm.y[i];
            }
        }
        invalidate();
    }

    public void clear() {
        hasPose = false;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!hasPose) return;

        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0 || imageWidth <= 0 || imageHeight <= 0) return;

        // FILL_CENTER：等比放大后居中裁切；FIT_CENTER：等比缩放后留黑边
        float scale = fitCenter
                ? Math.min(w / (float) imageWidth, h / (float) imageHeight)
                : Math.max(w / (float) imageWidth, h / (float) imageHeight);
        float dw = imageWidth * scale;
        float dh = imageHeight * scale;
        float dx = (w - dw) / 2f;
        float dy = (h - dh) / 2f;

        float[] px = new float[Landmarks.COUNT];
        float[] py = new float[Landmarks.COUNT];
        for (int i = 0; i < Landmarks.COUNT; i++) {
            float nx = mirror ? (1f - xs[i]) : xs[i];
            px[i] = dx + nx * dw;
            py[i] = dy + ys[i] * dh;
        }

        bonePaint.setColor(openPhase ? Color.parseColor("#35D0A5") : Color.parseColor("#FF7A45"));
        for (int[] bone : BONES) {
            int a = bone[0];
            int b = bone[1];
            if (!visible[a] || !visible[b]) continue;
            canvas.drawLine(px[a], py[a], px[b], py[b], bonePaint);
        }

        float r = 5f * getResources().getDisplayMetrics().density;
        for (int i = 0; i < Landmarks.COUNT; i++) {
            if (!visible[i]) continue;
            canvas.drawCircle(px[i], py[i], r, jointPaint);
        }

        // 头部光晕，提示"人已被识别到"
        if (visible[0]) {
            float radius = 16f * getResources().getDisplayMetrics().density;
            canvas.drawCircle(px[0], py[0], radius, haloPaint);
        }
    }
}
