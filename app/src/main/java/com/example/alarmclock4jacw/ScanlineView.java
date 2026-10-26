package com.example.alarmclock4jacw;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/**
 * 特效 UI：扫描线。
 *
 * 一层很淡的 CRT 横向细线（用 1×N 的位图平铺，整屏只要一次 drawRect），
 * 再叠一条慢慢往下扫的光带。
 *
 * 铺在内容之上、触摸之下（不接管触摸），所以文字会有一点点「屏幕感」，
 * 但透明度压得很低，不影响读。
 */
public class ScanlineView extends View {

    private static final int FRAME_MS = 40;

    private final Paint linePaint = new Paint();
    private final Paint bandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int accent;

    private BitmapShader lineShader;
    private int tileHeight;
    private long startMs;

    public ScanlineView(Context context) {
        this(context, null);
    }

    public ScanlineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        accent = SkinStore.accent(context);
        setClickable(false);
        setFocusable(false);
        setWillNotDraw(false);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        tileHeight = Math.max(3, Math.round(dp(3f)));
        Bitmap tile = Bitmap.createBitmap(1, tileHeight, Bitmap.Config.ARGB_8888);
        tile.eraseColor(0x00000000);
        // 纯黑底上任何叠加都会被放大，6% 白就已经能看出屏幕感了，再高就脏
        tile.setPixel(0, 0, 0x10FFFFFF);
        lineShader = new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
        linePaint.setShader(lineShader);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        if (startMs == 0L) startMs = SystemClock.uptimeMillis();
        float t = (SystemClock.uptimeMillis() - startMs) / 1000f;

        // 细横线：平铺一层就够，不用逐条画
        if (lineShader != null) {
            canvas.drawRect(0f, 0f, w, h, linePaint);
        }

        // 扫描光带：一条很淡的横向亮带往下走
        float bandH = h * 0.22f;
        float y = ((t * 0.10f) % 1.35f - 0.15f) * h;
        bandPaint.setShader(new LinearGradient(0f, y, 0f, y + bandH,
                new int[]{alpha(accent, 0f), alpha(accent, 0.07f), alpha(accent, 0f)},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0f, y, w, y + bandH, bandPaint);

        if (isShown()) postInvalidateDelayed(FRAME_MS);
    }

    private static int alpha(int color, float a) {
        int v = Math.round(Math.max(0f, Math.min(1f, a)) * 255f);
        return (color & 0x00FFFFFF) | (v << 24);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
