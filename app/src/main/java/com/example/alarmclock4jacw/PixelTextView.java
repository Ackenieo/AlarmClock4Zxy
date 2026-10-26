package com.example.alarmclock4jacw;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatTextView;

/**
 * 特效文本：像素风格文字。
 *
 * 做法是先把字画进一张「缩小 {@link #PIXEL} 倍」的位图里（关掉抗锯齿，边缘变硬），
 * 再用关闭插值的方式放大回原尺寸 —— 每个像素变成 {@link #PIXEL}×{@link #PIXEL} 的方块，
 * 就得到真正的像素字，而不是简单换个像素字体（中文字体根本找不到像素版）。
 *
 * 副作用是字的笔锋会被磨平，所以只用在标题这种大字号上；正文别用。
 */
public class PixelTextView extends AppCompatTextView {

    /** 一个逻辑像素放大成几个物理像素。3 在 40sp 标题上还能看清笔画。 */
    private static final int PIXEL = 3;

    private Bitmap buf;
    private final Canvas bufCanvas = new Canvas();
    private final Paint blit = new Paint();
    private final Paint shadow = new Paint();
    private final Rect dst = new Rect();

    private int shadowColor;

    public PixelTextView(Context context) {
        this(context, null);
    }

    public PixelTextView(Context context, AttributeSet attrs) {
        this(context, attrs, android.R.attr.textViewStyle);
    }

    public PixelTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        // 放大时不插值，才能保住硬边；缩小时也不抗锯齿，落进缓冲的颜色才干净
        blit.setFilterBitmap(false);
        blit.setAntiAlias(false);
        shadow.setFilterBitmap(false);
        shadow.setAntiAlias(false);
        getPaint().setAntiAlias(false);
    }

    /** 像素字后面垫一层同色偏移的实心影子，颜色一般给强调色。 */
    public void setPixelShadowColor(int color) {
        shadowColor = color;
        shadow.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0) return;

        final int bw = Math.max(1, Math.round(w / (float) PIXEL));
        final int bh = Math.max(1, Math.round(h / (float) PIXEL));
        if (buf == null || buf.getWidth() != bw || buf.getHeight() != bh) {
            if (buf != null) buf.recycle();
            buf = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        }

        buf.eraseColor(Color.TRANSPARENT);
        bufCanvas.setBitmap(buf);
        bufCanvas.save();
        bufCanvas.scale(1f / PIXEL, 1f / PIXEL);
        super.onDraw(bufCanvas);          // 坐标还是 View 坐标，缩放交给 canvas
        bufCanvas.restore();

        dst.set(0, 0, w, h);
        if (shadowColor != 0) {
            shadow.setAlpha(70);
            dst.offset(PIXEL, PIXEL);
            canvas.drawBitmap(buf, null, dst, shadow);
            dst.offset(-PIXEL, -PIXEL);
        }
        canvas.drawBitmap(buf, null, dst, blit);
    }
}
