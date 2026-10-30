package com.example.alarmclock4jacw;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.util.TypedValue;

import androidx.annotation.StyleRes;
import androidx.core.content.ContextCompat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 皮肤的存取 + 应用到主题。
 *
 * 每个 Activity 在 super.onCreate() 之后、setContentView() 之前调用
 * {@link #applyTheme(Activity)}，当前皮肤的覆盖层就会叠到该页面的主题上。
 *
 * 色盘自定义色：离散成 {@link #HUE_STEPS} 个色相 × {@link #TONE_STEPS} 档明暗，
 * 每格在 res/values/skins.xml 里都有一个预生成的 style（见 tools/gen_skins.py），
 * 因为 Theme.applyStyle 只接受编译期资源，运行时色值是注不进主题的。
 */
public final class SkinStore {

    public static final int HUE_STEPS = 48;
    /** 字体色的明暗档位，0 = 最浅，TONE_STEPS-1 = 最深；档距很小，滑起来看不出台阶 */
    public static final int TONE_STEPS = 40;
    /** 背景色的明暗档位：只在深色区间里走，保证白字永远读得清 */
    public static final int BG_TONE_STEPS = 24;

    private static final String PREF = "skin_store";
    private static final String KEY_ID = "skin_id";
    private static final String KEY_BG = "bg_id";
    private static final String CUSTOM_PREFIX = "custom:";
    private static final String BG_CUSTOM_PREFIX = "bgcustom:";
    public static final String DEFAULT_ID = "obsidian";
    public static final String DEFAULT_BG_ID = "black";

    private static SkinStore instance;

    private final Context app;
    private final SharedPreferences sp;
    private final Map<String, Skin> customCache = new HashMap<>();
    private final Map<String, Backdrop> backdropCache = new HashMap<>();
    private List<Skin> presets;

    private SkinStore(Context context) {
        app = context.getApplicationContext();
        sp = app.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static synchronized SkinStore get(Context context) {
        if (instance == null) instance = new SkinStore(context);
        return instance;
    }

    // ---------------------------------------------------------------- 预设

    public List<Skin> presets() {
        if (presets == null) {
            presets = Arrays.asList(
                    preset("obsidian", R.string.skin_obsidian, R.style.Skin_Obsidian,
                            R.color.skin_orange, R.color.skin_orange_deep,
                            R.color.skin_orange_soft, R.color.skin_orange_on,
                            FlowLightView.STYLE_LAVA, R.string.fx_lava),
                    preset("mint", R.string.skin_mint, R.style.Skin_Mint,
                            R.color.skin_mint, R.color.skin_mint_deep,
                            R.color.skin_mint_soft, R.color.skin_mint_on,
                            FlowLightView.STYLE_AURORA, R.string.fx_aurora),
                    preset("ocean", R.string.skin_ocean, R.style.Skin_Ocean,
                            R.color.skin_ocean, R.color.skin_ocean_deep,
                            R.color.skin_ocean_soft, R.color.skin_ocean_on,
                            FlowLightView.STYLE_DEEP, R.string.fx_deep),
                    preset("violet", R.string.skin_violet, R.style.Skin_Violet,
                            R.color.skin_violet, R.color.skin_violet_deep,
                            R.color.skin_violet_soft, R.color.skin_violet_on,
                            FlowLightView.STYLE_NEBULA, R.string.fx_nebula),
                    preset("sakura", R.string.skin_sakura, R.style.Skin_Sakura,
                            R.color.skin_sakura, R.color.skin_sakura_deep,
                            R.color.skin_sakura_soft, R.color.skin_sakura_on,
                            FlowLightView.STYLE_SAKURA, R.string.fx_sakura),
                    preset("sunset", R.string.skin_sunset, R.style.Skin_Sunset,
                            R.color.skin_sunset, R.color.skin_sunset_deep,
                            R.color.skin_sunset_soft, R.color.skin_sunset_on,
                            FlowLightView.STYLE_SUNSET, R.string.fx_sunset));
        }
        return presets;
    }

    private Skin preset(String id, int nameRes, int styleRes,
                        int accentRes, int deepRes, int softRes, int onRes,
                        int fxStyle, int fxNameRes) {
        return new Skin(id, nameRes, styleRes,
                ContextCompat.getColor(app, accentRes),
                ContextCompat.getColor(app, deepRes),
                ContextCompat.getColor(app, softRes),
                ContextCompat.getColor(app, onRes), -1, -1, fxStyle, fxNameRes);
    }

    // ---------------------------------------------------------------- 色盘

    /** 色盘格子：第 hueIndex 个色相 × 第 toneIndex 档明暗。 */
    public Skin custom(int hueIndex, int toneIndex) {
        int h = ((hueIndex % HUE_STEPS) + HUE_STEPS) % HUE_STEPS;
        int t = Math.max(0, Math.min(TONE_STEPS - 1, toneIndex));
        String id = CUSTOM_PREFIX + h + ":" + t;

        Skin cached = customCache.get(id);
        if (cached != null) return cached;

        int styleRes = app.getResources().getIdentifier(
                String.format(Locale.US, "Skin_H%02d_T%d", h, t),
                "style", app.getPackageName());
        if (styleRes == 0) styleRes = R.style.Skin_Obsidian;

        float hue = h * 360f / HUE_STEPS;
        float[] sv = toneSV(t);

        int accent = Color.HSVToColor(new float[]{hue, sv[0], sv[1]});
        int deep = Color.HSVToColor(new float[]{hue, Math.min(1f, sv[0] + 0.15f), sv[1] * 0.68f});
        int soft = Color.rgb(
                Math.round(Color.red(accent) * 0.14f),
                Math.round(Color.green(accent) * 0.14f),
                Math.round(Color.blue(accent) * 0.14f));

        Skin skin = new Skin(id, R.string.skin_custom, styleRes,
                accent, deep, soft, onColor(accent), h, t,
                FlowLightView.STYLE_FLOW, R.string.fx_flow);
        customCache.put(id, skin);
        return skin;
    }

    /**
     * 明暗滑条第 t 档的 (饱和度, 明度)，必须与 tools/gen_skins.py 的 tone_sv() 完全一致。
     * u=0 最浅（低饱和粉彩）→ u=0.5 标准 → u=1 最深（高饱和深色）。
     */
    private static float[] toneSV(int tone) {
        int t = Math.max(0, Math.min(TONE_STEPS - 1, tone));
        float u = t / (float) (TONE_STEPS - 1);
        if (u <= 0.5f) {
            return new float[]{0.42f + 0.30f * (u / 0.5f), 1f};
        }
        float k = (u - 0.5f) / 0.5f;
        return new float[]{0.72f + 0.16f * k, 1f - 0.38f * k};
    }

    /** 主色上压什么颜色的字：黑/白里挑对比度更高的那个（与生成脚本同一套算法）。 */
    private static int onColor(int accent) {
        double l = luminance(accent);
        double withWhite = 1.05 / (l + 0.05);
        double withBlack = (l + 0.05) / 0.05;
        return withBlack >= withWhite ? 0xFF101010 : 0xFFFFFFFF;
    }

    private static double luminance(int color) {
        return 0.2126 * linear(Color.red(color))
                + 0.7152 * linear(Color.green(color))
                + 0.0722 * linear(Color.blue(color));
    }

    private static double linear(int c) {
        double v = c / 255.0;
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    // ---------------------------------------------------------------- 存取

    public Skin current() {
        String id = sp.getString(KEY_ID, DEFAULT_ID);
        if (id != null && id.startsWith(CUSTOM_PREFIX)) {
            String[] parts = id.substring(CUSTOM_PREFIX.length()).split(":");
            if (parts.length == 2) {
                try {
                    return custom(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                } catch (NumberFormatException ignored) {
                    // 存档坏了就回默认
                }
            }
        }
        for (Skin s : presets()) {
            if (s.id.equals(id)) return s;
        }
        return presets().get(0);
    }

    public void select(Skin skin) {
        sp.edit().putString(KEY_ID, skin.id).apply();
    }

    /** 当前选中的是不是这个色盘格子。 */
    public boolean isCurrentCustom(int hueIndex, int toneIndex) {
        Skin c = current();
        return c.hueIndex == hueIndex && c.toneIndex == toneIndex;
    }

    // ---------------------------------------------------------------- 背景色

    /** 背景色方案：底色 + 由它派生出来的卡片色。 */
    public static final class Backdrop {
        public final String id;
        public final int bg;
        public final int surface;
        public final int surfaceHigh;
        public final int stroke;
        @StyleRes
        public final int styleRes;
        /** 色盘格子坐标；默认黑背景为 -1 */
        public final int hueIndex;
        public final int toneIndex;

        Backdrop(String id, int bg, int surface, int surfaceHigh, int stroke,
                 int styleRes, int hueIndex, int toneIndex) {
            this.id = id;
            this.bg = bg;
            this.surface = surface;
            this.surfaceHigh = surfaceHigh;
            this.stroke = stroke;
            this.styleRes = styleRes;
            this.hueIndex = hueIndex;
            this.toneIndex = toneIndex;
        }

        public boolean isCustom() {
            return hueIndex >= 0;
        }
    }

    public Backdrop backdrop() {
        String id = sp.getString(KEY_BG, DEFAULT_BG_ID);
        if (id != null && id.startsWith(BG_CUSTOM_PREFIX)) {
            String[] parts = id.substring(BG_CUSTOM_PREFIX.length()).split(":");
            if (parts.length == 2) {
                try {
                    return backdrop(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                } catch (NumberFormatException ignored) {
                    // 存档坏了就回默认
                }
            }
        }
        return defaultBackdrop();
    }

    private Backdrop defaultBackdrop() {
        return new Backdrop(DEFAULT_BG_ID,
                ContextCompat.getColor(app, R.color.bg),
                ContextCompat.getColor(app, R.color.surface),
                ContextCompat.getColor(app, R.color.surface_high),
                ContextCompat.getColor(app, R.color.stroke),
                R.style.Bg_Black, -1, -1);
    }

    /** 背景色格子：第 hueIndex 个色相 × 第 toneIndex 档（深色区间）。 */
    public Backdrop backdrop(int hueIndex, int toneIndex) {
        int h = ((hueIndex % HUE_STEPS) + HUE_STEPS) % HUE_STEPS;
        int t = Math.max(0, Math.min(BG_TONE_STEPS - 1, toneIndex));
        String id = BG_CUSTOM_PREFIX + h + ":" + t;

        Backdrop cached = backdropCache.get(id);
        if (cached != null) return cached;

        int styleRes = app.getResources().getIdentifier(
                String.format(Locale.US, "Bg_H%02d_T%d", h, t),
                "style", app.getPackageName());
        if (styleRes == 0) styleRes = R.style.Bg_Black;

        float u = t / (float) (BG_TONE_STEPS - 1);
        int bg = Color.HSVToColor(new float[]{
                h * 360f / HUE_STEPS, 0.45f + 0.25f * u, 0.32f * (1f - u)});

        Backdrop backdrop = new Backdrop(id, bg,
                lift(bg, 0.075f), lift(bg, 0.13f), lift(bg, 0.18f),
                styleRes, h, t);
        backdropCache.put(id, backdrop);
        return backdrop;
    }

    /** 朝白色提亮 k，用来从底色派生卡片色（与生成脚本同一套算法）。 */
    private static int lift(int color, float k) {
        return Color.rgb(
                Math.round(Color.red(color) + (255 - Color.red(color)) * k),
                Math.round(Color.green(color) + (255 - Color.green(color)) * k),
                Math.round(Color.blue(color) + (255 - Color.blue(color)) * k));
    }

    public void selectBackdrop(Backdrop backdrop) {
        sp.edit().putString(KEY_BG, backdrop.id).apply();
    }

    /**
     * 当前主题的指纹：强调色 + 背景色两个存档值的组合。
     * 页面在 onCreate 时记一份，onResume 时比对，变了就 recreate() 让 ?attr 重新解析。
     * 强调色和背景色要一起比，只比其中一个会漏掉「只换了背景」的情况。
     */
    public String themeSignature() {
        return sp.getString(KEY_ID, DEFAULT_ID) + "|" + sp.getString(KEY_BG, DEFAULT_BG_ID);
    }

    /** 把当前皮肤 + 背景色叠加到 context 的主题上；Activity 里必须在 setContentView 之前调用。 */
    public static void applyTheme(Context context) {
        SkinStore store = get(context);
        context.getTheme().applyStyle(store.current().styleRes, true);
        context.getTheme().applyStyle(store.backdrop().styleRes, true);

        // 状态栏 / 导航栏不一定会跟着主题重读，手动设一次
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            int bg = store.backdrop().bg;
            activity.getWindow().setStatusBarColor(bg);
            activity.getWindow().setNavigationBarColor(bg);
        }
    }

    // ---------------------------------------------------------------- 特效层

    /**
     * 给页面里的流光背景上色、选流法。
     * 背景色（backdrop）只决定底色，光带的颜色永远跟着强调色走 ——
     * 否则深蓝底 + 橙光会脏掉。
     */
    public static void applyFx(FlowLightView view) {
        Skin skin = get(view.getContext()).current();
        view.setColors(skin.accent, skin.deep);
        view.setStyle(skin.fxStyle);
    }

    /** 给像素标题垫一层强调色影子，让字和光带有点呼应。 */
    public static void applyPixelTitle(PixelTextView view) {
        view.setPixelShadowColor(accent(view.getContext()));
    }

    /** 取当前主题的强调色，给代码里手动上色的地方用（布局里请直接写 ?attr/accent）。 */
    public static int accent(Context context) {
        return resolve(context, R.attr.accent, R.color.accent);
    }

    /** 当前主题的背景色。 */
    public static int bgColor(Context context) {
        return resolve(context, R.attr.bgColor, R.color.bg);
    }

    /** 当前主题的分隔线颜色。 */
    public static int strokeColor(Context context) {
        return resolve(context, R.attr.strokeColor, R.color.stroke);
    }

    private static int resolve(Context context, int attr, int fallbackRes) {
        TypedValue tv = new TypedValue();
        if (context.getTheme().resolveAttribute(attr, tv, true)) {
            if (tv.resourceId != 0) return ContextCompat.getColor(context, tv.resourceId);
            if (tv.type >= TypedValue.TYPE_FIRST_COLOR_INT
                    && tv.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return tv.data;
            }
        }
        return ContextCompat.getColor(context, fallbackRes);
    }

    /** 色相环上第 i 格的色相角（度）。 */
    public static float hueDegrees(int hueIndex) {
        return hueIndex * 360f / HUE_STEPS;
    }

    /** 某一格的主色，给明暗滑条画渐变用。 */
    public int toneColor(int hueIndex, int toneIndex) {
        return custom(hueIndex, toneIndex).accent;
    }

    /** 某一格的背景色，给明暗滑条画渐变用。 */
    public int bgToneColor(int hueIndex, int toneIndex) {
        return backdrop(hueIndex, toneIndex).bg;
    }
}
