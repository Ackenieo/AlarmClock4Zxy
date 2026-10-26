package com.example.alarmclock4jacw;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * 特效皮肤的背景层：流光。
 *
 * 6 套皮肤各配一种流动方式（熔岩 / 极光 / 深海 / 星云 / 樱雪 / 夕照），
 * 色值全部取自当前强调色 accent + deep，所以色盘里调出来的自定义色也能用，
 * 只是落到 {@link #STYLE_FLOW} 这一种通用流法上。
 *
 * 画法只有一件事：在底色之上叠几层很淡的线性/径向渐变光带，让它们按不同速度平移、呼吸。
 * 所有光带的边缘都画到控件外，所以看不到硬边；透明度压在 0.2 以下，白字始终读得清。
 */
public class FlowLightView extends View {

    /** 通用流光：色盘自定义色用这一种 */
    public static final int STYLE_FLOW = 0;
    /** 曜石橙：熔岩斜向涌流 */
    public static final int STYLE_LAVA = 1;
    /** 薄荷绿：极光幕帘 */
    public static final int STYLE_AURORA = 2;
    /** 海光蓝：深海缓涌 */
    public static final int STYLE_DEEP = 3;
    /** 星夜紫：星云漫游 + 星点 */
    public static final int STYLE_NEBULA = 4;
    /** 樱粉：花瓣飘落 */
    public static final int STYLE_SAKURA = 5;
    /** 落日金：夕照光扇 */
    public static final int STYLE_SUNSET = 6;

    /** 24fps 足够顺，也能让 6 张卡片同时动起来不费电 */
    private static final long FRAME_MS = 1000L / 24;

    private int style = STYLE_FLOW;
    private int accent = 0xFFFF7A3D;
    private int deep = 0xFFB23A12;
    private boolean animating = true;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    /** 星点：x, y, 随机种子 三元一组（0..1 归一化坐标） */
    private final float[] stars = new float[96];
    /** 花瓣：x, 起始相位, 下落速度, 大小 四元一组 */
    private final float[] petals = new float[64];

    private long startMs;

    public FlowLightView(Context context) {
        this(context, null);
    }

    public FlowLightView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FlowLightView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);

        Random r = new Random(20261026L);
        for (int i = 0; i < stars.length; i++) stars[i] = r.nextFloat();
        for (int i = 0; i < petals.length; i++) petals[i] = r.nextFloat();
    }

    /** 切换流法；同一套配色换皮肤时立刻换一种流动方式。 */
    public void setStyle(int style) {
        if (this.style == style) return;
        this.style = style;
        invalidate();
    }

    public int getStyle() {
        return style;
    }

    /** 光带的颜色来源：主色 + 深色，都是当前皮肤算出来的。 */
    public void setColors(int accent, int deep) {
        if (this.accent == accent && this.deep == deep) return;
        this.accent = accent;
        this.deep = deep;
        invalidate();
    }

    /** 卡片预览之类的小控件可以关掉动画省电。 */
    public void setAnimating(boolean animating) {
        this.animating = animating;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        final int w = getWidth();
        final int h = getHeight();
        if (w <= 0 || h <= 0) return;

        if (startMs == 0L) startMs = SystemClock.uptimeMillis();
        final float t = (SystemClock.uptimeMillis() - startMs) / 1000f;

        switch (style) {
            case STYLE_LAVA:
                drawLava(canvas, w, h, t);
                break;
            case STYLE_AURORA:
                drawAurora(canvas, w, h, t);
                break;
            case STYLE_DEEP:
                drawDeep(canvas, w, h, t);
                break;
            case STYLE_NEBULA:
                drawNebula(canvas, w, h, t);
                break;
            case STYLE_SAKURA:
                drawSakura(canvas, w, h, t);
                break;
            case STYLE_SUNSET:
                drawSunset(canvas, w, h, t);
                break;
            default:
                drawFlow(canvas, w, h, t);
                break;
        }

        if (animating && isAttachedToWindow()) postInvalidateDelayed(FRAME_MS);
    }

    // ---------------------------------------------------------------- 各种流法

    /** 熔岩：三条粗光带斜着往右下扫，速度各不相同，明暗带轻微呼吸。 */
    private void drawLava(Canvas c, int w, int h, float t) {
        c.save();
        c.rotate(-20f, w / 2f, h / 2f);
        for (int i = 0; i < 3; i++) {
            // 相位按 1/3 均分，三条带子永远均匀铺在屏幕里，不会一起跑到屏幕外
            float phase = (t * (0.09f + i * 0.035f) + i / 3f) % 1f;
            float cx = w * (-0.5f + phase * 2f);
            float bw = w * (0.34f + i * 0.10f);
            float breathe = 0.75f + 0.25f * (float) Math.sin(t * 1.6f + i * 2.1f);
            paint.setShader(new LinearGradient(cx - bw, 0f, cx + bw, 0f,
                    new int[]{alpha(accent, 0f),
                            alpha(deep, 0.18f * breathe),
                            alpha(accent, 0.30f * breathe),
                            alpha(deep, 0.18f * breathe),
                            alpha(accent, 0f)},
                    new float[]{0f, 0.25f, 0.5f, 0.75f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(cx - bw, -h, cx + bw, h * 2f, paint);
        }
        c.restore();
    }

    /** 极光：几根竖着的光柱左右摇摆，亮度呼吸。 */
    private void drawAurora(Canvas c, int w, int h, float t) {
        for (int i = 0; i < 5; i++) {
            float sway = (float) Math.sin(t * (0.22f + i * 0.05f) + i * 1.6f);
            float cx = w * (0.06f + i * 0.23f) + sway * w * 0.045f;
            float bw = w * (0.075f + (i % 3) * 0.035f);
            float breathe = 0.55f + 0.45f * (float) Math.sin(t * 0.55f + i * 2.3f);
            paint.setShader(new LinearGradient(cx - bw, 0f, cx + bw, 0f,
                    new int[]{alpha(accent, 0f),
                            alpha(deep, 0.16f * breathe),
                            alpha(accent, 0.26f * breathe),
                            alpha(deep, 0.16f * breathe),
                            alpha(accent, 0f)},
                    new float[]{0f, 0.28f, 0.5f, 0.72f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(cx - bw, -h * 0.2f, cx + bw, h * 1.2f, paint);
        }
    }

    /** 深海：底部一团水光，几条横向光带慢慢上浮。 */
    private void drawDeep(Canvas c, int w, int h, float t) {
        paint.setShader(new RadialGradient(w * 0.5f, h * 1.02f, h * 0.62f,
                new int[]{alpha(accent, 0.30f), alpha(accent, 0.10f), alpha(accent, 0f)},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0f, 0f, w, h, paint);

        c.save();
        c.rotate(-6f, w / 2f, h / 2f);
        for (int i = 0; i < 3; i++) {
            float phase = (t * (0.05f + i * 0.018f) + i / 3f) % 1f;
            float cy = h * (1.15f - phase * 1.35f);
            float bh = h * (0.10f + i * 0.045f);
            float breathe = 0.6f + 0.4f * (float) Math.sin(t * 0.8f + i * 1.9f);
            paint.setShader(new LinearGradient(0f, cy - bh, 0f, cy + bh,
                    new int[]{alpha(accent, 0f),
                            alpha(accent, 0.20f * breathe),
                            alpha(deep, 0.16f * breathe),
                            alpha(accent, 0f)},
                    new float[]{0f, 0.34f, 0.66f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(-w * 0.3f, cy - bh, w * 1.3f, cy + bh, paint);
        }
        c.restore();
    }

    /** 星云：三团径向光雾各自走李萨如轨迹，底下撒一层会闪的星点。 */
    private void drawNebula(Canvas c, int w, int h, float t) {
        for (int i = 0; i < stars.length; i += 3) {
            float x = stars[i] * w;
            float y = stars[i + 1] * h;
            float seed = stars[i + 2];
            float twinkle = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(t * (0.6f + seed) + i));
            dotPaint.setColor(alpha(0xFFFFFFFF, 0.70f * twinkle));
            c.drawCircle(x, y, dp(0.7f + seed * 0.9f), dotPaint);
        }

        for (int i = 0; i < 3; i++) {
            float px = w * (0.5f + 0.32f * (float) Math.sin(t * (0.09f + i * 0.03f) + i * 2.1f));
            float py = h * (0.45f + 0.28f * (float) Math.cos(t * (0.07f + i * 0.025f) + i * 1.3f));
            float r = Math.max(w, h) * (0.32f + i * 0.06f);
            paint.setShader(new RadialGradient(px, py, r,
                    new int[]{alpha(accent, 0.26f - i * 0.05f),
                            alpha(deep, 0.11f),
                            alpha(accent, 0f)},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0f, 0f, w, h, paint);
        }
    }

    /** 樱雪：顶上一团柔光，花瓣一边落一边摇一边转。 */
    private void drawSakura(Canvas c, int w, int h, float t) {
        paint.setShader(new RadialGradient(w * 0.5f, -h * 0.15f, h * 0.8f,
                new int[]{alpha(accent, 0.28f), alpha(accent, 0.08f), alpha(accent, 0f)},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0f, 0f, w, h, paint);

        for (int i = 0; i < petals.length; i += 4) {
            float baseX = petals[i];
            float phase = petals[i + 1];
            float speed = 0.012f + petals[i + 2] * 0.02f;
            float size = petals[i + 3];

            float y = ((phase + t * speed) % 1.15f - 0.08f) * h;
            float x = (baseX + 0.035f * (float) Math.sin(t * (0.4f + size) + i)) * w;
            float r = dp(2.2f + size * 2.6f);

            c.save();
            c.rotate((t * 22f * (0.5f + size) + i * 37f) % 360f, x, y);
            dotPaint.setColor(alpha(accent, 0.45f + 0.30f * size));
            oval.set(x - r, y - r * 0.62f, x + r, y + r * 0.62f);
            c.drawOval(oval, dotPaint);
            c.restore();
        }
    }

    /** 夕照：右上角一轮光晕，扇形光线缓慢转。 */
    private void drawSunset(Canvas c, int w, int h, float t) {
        final float ox = w * 0.92f;
        final float oy = -h * 0.06f;

        paint.setShader(new RadialGradient(ox, oy, h * 0.9f,
                new int[]{alpha(accent, 0.34f), alpha(accent, 0.12f), alpha(accent, 0f)},
                new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0f, 0f, w, h, paint);

        final float len = (float) Math.hypot(w, h) * 1.15f;
        c.save();
        c.rotate(t * 2.5f, ox, oy);
        for (int i = 0; i < 9; i++) {
            c.save();
            c.rotate(-72f + i * 18f, ox, oy);
            float halfW = w * (0.010f + (i % 3) * 0.007f);
            paint.setShader(new LinearGradient(ox, oy, ox, oy + len,
                    new int[]{alpha(accent, 0.24f), alpha(accent, 0f)},
                    new float[]{0f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(ox - halfW, oy, ox + halfW, oy + len, paint);
            c.restore();
        }
        c.restore();
    }

    /** 通用流光：三条斜光带，中间那条反向走，两股光对流。色盘自定义色用这个。 */
    private void drawFlow(Canvas c, int w, int h, float t) {
        c.save();
        c.rotate(-16f, w / 2f, h / 2f);
        for (int i = 0; i < 3; i++) {
            float phase = (t * (0.08f + i * 0.025f) + i / 3f) % 1f;
            if (i == 1) phase = 1f - phase;       // 中间那条反向，和另两条对流
            float cx = w * (-0.5f + phase * 2f);
            float bw = w * (0.36f + i * 0.08f);
            float breathe = 0.7f + 0.3f * (float) Math.sin(t * 1.2f + i * 2.4f);
            paint.setShader(new LinearGradient(cx - bw, 0f, cx + bw, 0f,
                    new int[]{alpha(accent, 0f),
                            alpha(accent, 0.28f * breathe),
                            alpha(deep, 0.20f * breathe),
                            alpha(accent, 0f)},
                    new float[]{0f, 0.3f, 0.62f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(cx - bw, -h, cx + bw, h * 2f, paint);
        }
        c.restore();
    }

    // ---------------------------------------------------------------- 工具

    /** 给颜色换一个透明度，光带颜色都是这么算出来的。 */
    private static int alpha(int color, float a) {
        int v = Math.round(Math.max(0f, Math.min(1f, a)) * 255f);
        return (color & 0x00FFFFFF) | (v << 24);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
