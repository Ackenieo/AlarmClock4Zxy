package com.example.alarmclock4jacw;

import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * 系统栏间距。
 *
 * targetSdk 35 起 Android 15 强制 edge-to-edge：主题里的 {@code statusBarColor} 直接被忽略，
 * 窗口内容一路铺到状态栏底下。所以「铺满全屏的滚动容器」必须自己把状态栏高度让出来，
 * 否则滚动时标题、副标题会直接压在状态栏的时钟上。
 *
 * 老系统上 decor 已经把 inset 吃掉了，这里拿到的是 0，布局里写死的间距照旧生效。
 */
public final class SystemBars {

    private SystemBars() {
    }

    /**
     * 铺满全屏的滚动容器：顶部让出状态栏，并且把 {@code clipToPadding} 打开 ——
     * 只有裁剪到 padding 边界，滚上去的内容才会在状态栏下沿被切掉，而不是继续压在时钟上。
     */
    public static void insetTop(View scroll) {
        padTop(scroll, 0f);
        if (scroll instanceof ViewGroup) {
            ((ViewGroup) scroll).setClipToPadding(true);
        }
    }

    /**
     * 固定在顶部的标题栏：让出状态栏，并保证栏下至少留 {@code minGapDp}。
     *
     * 取 {@code max(布局里的 paddingTop, 状态栏 + minGap)}：本机 edge-to-edge 时走后者，
     * 老系统（inset = 0）走前者，两种情况下视觉间距都不会塌。
     */
    public static void addTop(View view, float minGapDp) {
        padTop(view, minGapDp);
    }

    private static void padTop(View view, float minGapDp) {
        if (view == null) return;
        final int base = view.getPaddingTop();
        final int gap = Math.round(minGapDp * view.getResources().getDisplayMetrics().density);

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(v.getPaddingLeft(), Math.max(base, bars.top + gap),
                    v.getPaddingRight(), v.getPaddingBottom());
            return insets;
        });
        ViewCompat.requestApplyInsets(view);
    }
}
