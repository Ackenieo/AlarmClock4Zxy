package com.example.alarmclock4jacw;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

/**
 * 连续成功达到 1/3/6/10/15/20 天（之后每 5 天）时的庆祝弹窗。
 * 在闹钟已经停止后展示，点「继续」才关闭页面。
 */
public final class CelebrationDialog extends Dialog {

    public interface OnContinueListener {
        void onContinue();
    }

    private final StreakStore.Record record;
    private final OnContinueListener listener;

    public CelebrationDialog(Context context, StreakStore.Record record,
                             OnContinueListener listener) {
        super(context, R.style.Theme_AlarmClock4jACW_Dialog);
        this.record = record;
        this.listener = listener;
        SkinStore.applyTheme(getContext());
        setCancelable(false);
        setCanceledOnTouchOutside(false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_celebration);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.CENTER);
            int screenWidth = getContext().getResources().getDisplayMetrics().widthPixels;
            window.setLayout(Math.min(screenWidth - dp(48), dp(340)),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        String dayText = getContext().getResources().getQuantityString(
                R.plurals.streak_days, record.streak, record.streak);

        TextView title = findViewById(R.id.celebration_title);
        title.setText(getContext().getString(R.string.streak_celebration_title_fmt, dayText));
        ((TextView) findViewById(R.id.celebration_total)).setText(
                getContext().getString(R.string.streak_celebration_total_fmt, record.total));
        ((TextView) findViewById(R.id.celebration_next)).setText(
                getContext().getString(R.string.streak_next_fmt, record.nextMilestone));

        findViewById(R.id.celebration_continue).setOnClickListener(v -> {
            dismiss();
            if (listener != null) listener.onContinue();
        });

        View icon = findViewById(R.id.celebration_icon);
        icon.setScaleX(0.55f);
        icon.setScaleY(0.55f);
        icon.setAlpha(0f);
        icon.animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(360)
                .setInterpolator(new OvershootInterpolator(1.5f))
                .start();
    }

    private int dp(int value) {
        return Math.round(value * getContext().getResources().getDisplayMetrics().density);
    }
}
