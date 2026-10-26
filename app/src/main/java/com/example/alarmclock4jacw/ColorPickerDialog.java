package com.example.alarmclock4jacw;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

/**
 * 色盘取色对话框。同一个色盘调两种颜色：
 *   字体颜色（accent：强调色 / 文字 / 开关）与背景颜色（bg 及派生的卡片色）。
 * 点左右两个尖括号、或在空白处左右滑动，都能切换当前在调哪个。
 *
 * 取色结果是一组 (hueIndex, toneIndex) 坐标，对应预生成的主题覆盖层，见 SkinStore。
 */
public class ColorPickerDialog extends Dialog {

    public static final int TARGET_ACCENT = 0;
    public static final int TARGET_BG = 1;

    public interface OnPickListener {
        void onPick(int accentHue, int accentTone, int bgHue, int bgTone, boolean bgChanged);
    }

    private final SkinStore store;
    private final OnPickListener listener;

    private final int[] hue = new int[2];
    private final int[] tone = new int[2];
    private int target = TARGET_ACCENT;
    /** 用户是否真的动过背景色；没动过就保持原背景，不写存档 */
    private boolean bgChanged;

    private ColorWheelView wheel;
    private ToneSliderView toneSlider;
    private View preview;
    private TextView hex;
    private TextView subtitle;
    private MaterialButton applyBtn;

    private float downX;
    private float downY;
    private boolean swipeCandidate;

    public ColorPickerDialog(Context context,
                             int accentHue, int accentTone,
                             int bgHue, int bgTone,
                             OnPickListener l) {
        super(context, R.style.Theme_AlarmClock4jACW_Dialog);
        // 对话框有自己的主题副本，得单独叠一次当前皮肤，?attr/xxx 才对得上
        SkinStore.applyTheme(getContext());
        this.store = SkinStore.get(context);
        this.listener = l;
        hue[TARGET_ACCENT] = accentHue;
        tone[TARGET_ACCENT] = accentTone;
        hue[TARGET_BG] = bgHue;
        tone[TARGET_BG] = bgTone;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_color_picker);

        Window w = getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setGravity(Gravity.CENTER);
            int screen = getContext().getResources().getDisplayMetrics().widthPixels;
            w.setLayout(Math.min(screen - dp(48), dp(360)),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }

        wheel = findViewById(R.id.color_wheel);
        toneSlider = findViewById(R.id.tone_slider);
        preview = findViewById(R.id.color_preview);
        hex = findViewById(R.id.color_hex);
        subtitle = findViewById(R.id.color_subtitle);
        applyBtn = findViewById(R.id.btn_apply);

        wheel.setOnHueChangeListener(index -> {
            hue[target] = index;
            toneSlider.setHueIndex(index);      // 轨道渐变跟着色相重画
            if (target == TARGET_BG) bgChanged = true;
            refresh();
        });

        toneSlider.setOnToneChangeListener(index -> {
            tone[target] = index;
            if (target == TARGET_BG) bgChanged = true;
            refresh();
        });

        findViewById(R.id.chev_prev).setOnClickListener(v -> switchTarget(-1));
        findViewById(R.id.chev_next).setOnClickListener(v -> switchTarget(1));

        findViewById(R.id.btn_cancel).setOnClickListener(v -> dismiss());
        applyBtn.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPick(hue[TARGET_ACCENT], tone[TARGET_ACCENT],
                        hue[TARGET_BG], tone[TARGET_BG], bgChanged);
            }
            dismiss();
        });

        applyTargetToControls();
    }

    /** 切换「字体颜色 / 背景颜色」；dir=+1 下一个，-1 上一个。 */
    private void switchTarget(int dir) {
        int next = (target + (dir > 0 ? 1 : -1) + 2) % 2;
        if (next == target) return;
        target = next;
        if (target == TARGET_BG) bgChanged = true;
        applyTargetToControls();
    }

    private void applyTargetToControls() {
        boolean bg = target == TARGET_BG;
        subtitle.setText(bg ? R.string.color_target_bg : R.string.color_target_accent);

        toneSlider.setBackgroundMode(bg);        // 先切模式（档位数不同），再设档位
        wheel.setHueIndex(hue[target]);
        toneSlider.setHueIndex(hue[target]);
        toneSlider.setToneIndex(tone[target]);

        refresh();
    }

    private void refresh() {
        int color = target == TARGET_BG
                ? store.backdrop(hue[TARGET_BG], tone[TARGET_BG]).bg
                : store.custom(hue[TARGET_ACCENT], tone[TARGET_ACCENT]).accent;

        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(color);
        dot.setStroke(dp(1), 0x33FFFFFF);        // 深色背景下也能看清边界
        preview.setBackground(dot);

        hex.setText(String.format("#%06X", 0xFFFFFF & color));
        wheel.setCenterColor(color);

        // 「应用」按钮恒用强调色：调背景时它要是也染成近黑色，看着就像禁用了
        Skin accentSkin = store.custom(hue[TARGET_ACCENT], tone[TARGET_ACCENT]);
        applyBtn.setBackgroundTintList(ColorStateList.valueOf(accentSkin.accent));
        applyBtn.setTextColor(accentSkin.on);
    }

    // ---------------------------------------------------------- 左右滑动切换

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = ev.getRawX();
                downY = ev.getRawY();
                // 色盘和滑条自己要用横向手势，别抢
                swipeCandidate = !hits(wheel, ev) && !hits(toneSlider, ev);
                break;
            case MotionEvent.ACTION_UP:
                if (swipeCandidate) {
                    float dx = ev.getRawX() - downX;
                    float dy = ev.getRawY() - downY;
                    if (Math.abs(dx) > dp(56) && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                        switchTarget(dx < 0 ? 1 : -1);
                    }
                }
                swipeCandidate = false;
                break;
            case MotionEvent.ACTION_CANCEL:
                swipeCandidate = false;
                break;
            default:
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    private boolean hits(View view, MotionEvent ev) {
        if (view == null) return false;
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        float x = ev.getRawX();
        float y = ev.getRawY();
        return x >= loc[0] && x < loc[0] + view.getWidth()
                && y >= loc[1] && y < loc[1] + view.getHeight();
    }

    private int dp(int v) {
        return Math.round(v * getContext().getResources().getDisplayMetrics().density);
    }
}
