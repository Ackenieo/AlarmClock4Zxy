package com.example.alarmclock4jacw;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

/**
 * 皮肤市场：6 套特效皮肤（各自一种流光）+ 第 7 格「无特效」（关掉流光）
 * + 顶部预览卡点开的色盘自定义 + 特效 UI 开关卡。
 * 换装只写一条 SharedPreferences，再由各 Activity 的 SkinStore.applyTheme() 叠加主题覆盖层；
 * 特效 UI 的开关同理，存在 {@link FxStore}，各页面 onResume 时读一次。
 */
public class SkinMarketActivity extends BaseActivity {

    /** 特效 UI 清单：显式绑 id，不再靠下标去和开关数组对齐 */
    private static final FxEntry[] FX_UI = {
            new FxEntry(FxStore.GLOW_BORDER, R.string.fx_ui_glow_border, R.string.fx_ui_glow_border_desc),
            new FxEntry(FxStore.PARTICLE, R.string.fx_ui_particle, R.string.fx_ui_particle_desc),
            new FxEntry(FxStore.SCANLINE, R.string.fx_ui_scanline, R.string.fx_ui_scanline_desc),
            new FxEntry(FxStore.NEON, R.string.fx_ui_neon, R.string.fx_ui_neon_desc),
            new FxEntry(FxStore.GLITCH, R.string.fx_ui_glitch, R.string.fx_ui_glitch_desc),
            new FxEntry(FxStore.LIQUID, R.string.fx_ui_liquid, R.string.fx_ui_liquid_desc),
    };

    private static final class FxEntry {
        final String id;
        final int name;
        final int desc;

        FxEntry(String id, int name, int desc) {
            this.id = id;
            this.name = name;
            this.desc = desc;
        }
    }

    /** 预览里需要「主动喂一下」才会动的两个：粒子要 burst、水波要 wave */
    private static final long FX_PREVIEW_INTERVAL_MS = 1400L;

    private GridLayout grid;
    private GridLayout fxGrid;
    private TextView currentLabel;
    private SkinStore store;

    /** 预览卡自带的那层点击涟漪，开关描边时要和它叠/拆，所以得留着原件 */
    private Drawable previewRipple;
    private ParticleLayer particlePreview;
    private View liquidPreviewHost;

    private final Handler fxHandler = new Handler(Looper.getMainLooper());
    private final Runnable fxLoop = new Runnable() {
        @Override
        public void run() {
            if (particlePreview != null && particlePreview.isShown()) {
                particlePreview.burstRandom();
            }
            if (liquidPreviewHost != null && liquidPreviewHost.isShown()
                    && liquidPreviewHost.getForeground() instanceof LiquidRippleDrawable) {
                ((LiquidRippleDrawable) liquidPreviewHost.getForeground()).wave();
            }
            fxHandler.postDelayed(this, FX_PREVIEW_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SkinStore.applyTheme(this);
        setContentView(R.layout.activity_skin_market);
        // 全屏滚动：顶部让出状态栏，滚上去的内容在状态栏下沿裁掉
        SystemBars.insetTop(findViewById(R.id.market_scroll));

        store = SkinStore.get(this);
        grid = findViewById(R.id.skin_grid);
        fxGrid = findViewById(R.id.fx_ui_grid);
        currentLabel = findViewById(R.id.preview_current);

        SkinStore.applyFx(this.findViewById(R.id.flow_bg));
        SkinStore.applyPixelTitle(this.findViewById(R.id.market_title));
        applyFxToggles();

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        View preview = findViewById(R.id.skin_preview_card);
        preview.setOnClickListener(v -> openColorPicker());
        previewRipple = preview.getForeground();
        applyPreviewGlow();

        buildGrid();
        buildFxUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        fxHandler.removeCallbacks(fxLoop);
        fxHandler.postDelayed(fxLoop, 500);
    }

    @Override
    protected void onPause() {
        super.onPause();
        fxHandler.removeCallbacks(fxLoop);
    }

    /** 本页自己的特效层：流光背景（「无特效背景」是反向开关）。 */
    private void applyFxToggles() {
        FxStore.applyFlowBg(this, findViewById(R.id.flow_bg));
    }

    // ---------------------------------------------------------------- 皮肤卡

    private void buildGrid() {
        grid.removeAllViews();
        Skin current = store.current();

        currentLabel.setText(getString(R.string.skin_current_fmt, current.name(this)));

        for (Skin skin : store.presets()) {
            View item = getLayoutInflater().inflate(R.layout.item_skin, grid, false);
            // 特效 UI：流光描边。每张卡给不同的起始角，一屏六道弧才不会像阅兵；
            // 颜色必须用卡片自己的强调色，不然六张卡会一起用当前皮肤色
            item.setForeground(FxStore.isEnabled(this, FxStore.GLOW_BORDER)
                    ? new GlowBorderDrawable(this, 24f,
                    store.presets().indexOf(skin) * 60f, skin.accent)
                    : null);
            bind(item, skin, skin.id.equals(current.id));
            item.setOnClickListener(v -> choose(skin));
            grid.addView(item);
        }

        grid.addView(buildPlainCard());
    }

    /**
     * 网格最后一格：「无特效」。
     *
     * 它不是一套皮肤，是「不要流光」那一档 —— 所以点它不换配色，只翻 {@link FxStore#PLAIN_BG}，
     * 整页流光立刻收掉（含本页背景）。放在这里是因为它和「每套配一种流光」是同一类选择。
     */
    private View buildPlainCard() {
        boolean plain = FxStore.isEnabled(this, FxStore.PLAIN_BG);

        View item = getLayoutInflater().inflate(R.layout.item_skin, grid, false);
        item.setForeground(FxStore.isEnabled(this, FxStore.GLOW_BORDER)
                ? new GlowBorderDrawable(this, 24f, 300f, store.current().accent)
                : null);

        // 预览：一块纯黑底 + 一个空心灰环，正好是「有框没光」的样子。
        // 底色和卡片玻璃色太接近，补一道细描边，不然看不出这是个预览框
        GradientDrawable blank = new GradientDrawable();
        blank.setShape(GradientDrawable.RECTANGLE);
        blank.setColor(0xFF0A0A0A);
        blank.setCornerRadius(18 * getResources().getDisplayMetrics().density);
        blank.setStroke(Math.round(1f * getResources().getDisplayMetrics().density), 0xFF2C2C2C);
        item.findViewById(R.id.skin_preview).setBackground(blank);
        item.findViewById(R.id.skin_flow).setVisibility(View.GONE);
        item.findViewById(R.id.skin_dot_small).setVisibility(View.GONE);
        item.findViewById(R.id.skin_badge).setVisibility(View.GONE);

        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(Color.TRANSPARENT);
        ring.setStroke(Math.round(1.5f * getResources().getDisplayMetrics().density), 0xFF3C3C3C);
        item.findViewById(R.id.skin_dot).setBackground(ring);

        ((TextView) item.findViewById(R.id.skin_name)).setText(R.string.fx_skin_plain);

        TextView status = item.findViewById(R.id.skin_status);
        if (plain) {
            status.setText(R.string.skin_in_use);
            status.setTextColor(SkinStore.accent(this));
            item.setBackgroundResource(R.drawable.bg_card_selected);
        } else {
            status.setText(R.string.fx_skin_plain_sub);
            status.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary));
            item.setBackgroundResource(R.drawable.bg_card_glass);
        }

        item.setOnClickListener(v -> {
            FxStore.toggle(this, FxStore.PLAIN_BG);
            applyFxToggles();   // 本页背景立刻跟着灭/亮
            buildGrid();        // 这张卡的「使用中」和描边要跟着变
        });
        return item;
    }

    private void bind(View item, Skin skin, boolean active) {
        View preview = item.findViewById(R.id.skin_preview);
        preview.setBackground(roundRect(skin.soft));

        // 预览里的流光和真实背景用同一套画法，只是尺寸小一点
        FlowLightView flow = item.findViewById(R.id.skin_flow);
        flow.setColors(skin.accent, skin.deep);
        flow.setStyle(skin.fxStyle);

        item.findViewById(R.id.skin_dot).setBackground(circle(skin.accent));
        item.findViewById(R.id.skin_dot_small).setBackground(circle(skin.deep));

        ((TextView) item.findViewById(R.id.skin_name)).setText(skin.name(this));

        TextView status = item.findViewById(R.id.skin_status);
        if (active) {
            status.setText(R.string.skin_in_use);
            status.setTextColor(SkinStore.accent(this));
            item.setBackgroundResource(R.drawable.bg_card_selected);
        } else {
            status.setText(skin.fxNameRes);         // 卡片第二行直接报特效名：熔岩流光 / 极光幕帘 …
            status.setTextColor(ContextCompat.getColor(this, R.color.text_tertiary));
            item.setBackgroundResource(R.drawable.bg_card_glass);
        }
    }

    /** 预览卡上的流光描边：关掉时只留原来的点击涟漪。 */
    private void applyPreviewGlow() {
        View preview = findViewById(R.id.skin_preview_card);
        if (!FxStore.isEnabled(this, FxStore.GLOW_BORDER)) {
            preview.setForeground(previewRipple);
            return;
        }
        GlowBorderDrawable glow = new GlowBorderDrawable(this, 24f, 180f);
        preview.setForeground(previewRipple == null
                ? glow
                : new LayerDrawable(new Drawable[]{previewRipple, glow}));
    }

    // ---------------------------------------------------------------- 特效 UI

    /** 六张开关卡：每张上面一块真动效预览，点整张卡切换开/关。 */
    private void buildFxUi() {
        fxGrid.removeAllViews();
        particlePreview = null;
        liquidPreviewHost = null;

        for (int i = 0; i < FX_UI.length; i++) {
            final String id = FX_UI[i].id;
            final boolean on = FxStore.isEnabled(this, id);

            View card = getLayoutInflater().inflate(R.layout.item_fx_ui, fxGrid, false);
            ((TextView) card.findViewById(R.id.fx_ui_name)).setText(FX_UI[i].name);
            ((TextView) card.findViewById(R.id.fx_ui_desc)).setText(FX_UI[i].desc);

            TextView state = card.findViewById(R.id.fx_ui_state);
            state.setText(on ? R.string.fx_state_on : R.string.fx_state_off);
            state.setTextColor(on
                    ? SkinStore.accent(this)
                    : ContextCompat.getColor(this, R.color.text_tertiary));

            FrameLayout host = card.findViewById(R.id.fx_preview);
            host.addView(buildPreview(id, on));

            card.setOnClickListener(v -> {
                FxStore.toggle(this, id);
                applyFxToggles();   // 本页背景也跟着开关走
                buildFxUi();        // 预览要跟着重建，关了就不该还在动
                buildGrid();        // 描边开关会直接改皮肤卡的边框
                applyPreviewGlow();
            });

            fxGrid.addView(card);
        }
    }

    /** 预览用的就是各页面上那套真组件，只是尺寸小一点。 */
    private View buildPreview(String id, boolean on) {
        FrameLayout host = new FrameLayout(this);
        host.setBackgroundResource(R.drawable.bg_fx_preview);
        host.setClipToOutline(true);

        switch (id) {
            case FxStore.GLOW_BORDER: {
                // 内层用接近纯黑的实心块：描边是 20%~95% 透明度的强调色，
                // 垫在玻璃色上几乎看不出来，垫黑才亮得起来
                View box = new View(this);
                GradientDrawable dark = new GradientDrawable();
                dark.setShape(GradientDrawable.RECTANGLE);
                dark.setColor(0xF0000000);
                dark.setCornerRadius(14 * getResources().getDisplayMetrics().density);
                box.setBackground(dark);
                if (on) {
                    box.setForeground(new GlowBorderDrawable(this, 14f, 0f, SkinStore.accent(this)));
                }
                host.addView(box, inset(10));
                break;
            }
            case FxStore.PARTICLE: {
                ParticleLayer layer = new ParticleLayer(this);
                if (on) particlePreview = layer;
                host.addView(layer);
                break;
            }
            case FxStore.SCANLINE: {
                ScanlineView scan = new ScanlineView(this);
                if (!on) scan.setVisibility(View.GONE);
                host.addView(scan);
                break;
            }
            case FxStore.NEON: {
                // 和响铃页主按钮一个结构：底下一条实心药丸，上面盖呼吸灯管
                View pill = new View(this);
                pill.setBackground(roundRect(SkinStore.accent(this)));
                host.addView(pill, inset(14));

                NeonPulseView neon = new NeonPulseView(this);
                if (!on) neon.setVisibility(View.GONE);
                host.addView(neon, inset(14));
                break;
            }
            case FxStore.GLITCH: {
                GlitchTextView clock = new GlitchTextView(this);
                clock.setText("07:00");
                clock.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
                clock.setTextSize(24f);
                clock.setGravity(Gravity.CENTER);
                clock.setGlitchEnabled(on);
                host.addView(clock);
                break;
            }
            case FxStore.LIQUID: {
                View pill = new View(this);
                pill.setBackground(roundRect(SkinStore.accent(this)));
                if (on) {
                    pill.setForeground(new LiquidRippleDrawable(this, 18f));
                    liquidPreviewHost = pill;
                }
                host.addView(pill, inset(14));
                break;
            }
            default:
                break;
        }
        return host;
    }

    private FrameLayout.LayoutParams inset(int dp) {
        int p = Math.round(dp * getResources().getDisplayMetrics().density);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        lp.setMargins(p, p, p, p);
        return lp;
    }

    // ---------------------------------------------------------------- 色盘

    /** 打开色盘：预设皮肤按它的主色反推色相，自定义皮肤直接用存下来的坐标。 */
    private void openColorPicker() {
        Skin current = store.current();
        SkinStore.Backdrop backdrop = store.backdrop();

        int accentHue;
        int accentTone;
        if (current.isCustom()) {
            accentHue = current.hueIndex;
            accentTone = current.toneIndex;
        } else {
            float[] hsv = new float[3];
            Color.colorToHSV(current.accent, hsv);
            accentHue = Math.round(hsv[0] / (360f / SkinStore.HUE_STEPS)) % SkinStore.HUE_STEPS;
            accentTone = SkinStore.TONE_STEPS / 2;      // 预设皮肤从中间那档（标准）起步
        }

        // 背景默认是纯黑，色相无意义，从最深处起步
        int bgHue = backdrop.isCustom() ? backdrop.hueIndex : 0;
        int bgTone = backdrop.isCustom() ? backdrop.toneIndex : SkinStore.BG_TONE_STEPS - 1;

        new ColorPickerDialog(this, accentHue, accentTone, bgHue, bgTone,
                (ah, at, bh, bt, bgChanged) -> {
                    store.select(store.custom(ah, at));
                    if (bgChanged) store.selectBackdrop(store.backdrop(bh, bt));
                    Toast.makeText(this, R.string.skin_applied_custom, Toast.LENGTH_SHORT).show();
                    recreate();
                }).show();
    }

    private void choose(Skin skin) {
        // 挑了一套皮肤 = 想看它的流光，顺手把「无特效」摘掉，不然换完还是一块纯黑
        boolean wasPlain = FxStore.isEnabled(this, FxStore.PLAIN_BG);
        if (wasPlain) FxStore.setEnabled(this, FxStore.PLAIN_BG, false);

        if (skin.id.equals(store.current().id)) {
            if (wasPlain) recreate();
            return;
        }
        store.select(skin);
        Toast.makeText(this, getString(R.string.skin_applied_fmt, skin.name(this)),
                Toast.LENGTH_SHORT).show();
        recreate();     // 本页也立刻换色
    }

    private GradientDrawable roundRect(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(color);
        d.setCornerRadius(18 * getResources().getDisplayMetrics().density);
        return d;
    }

    private GradientDrawable circle(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }
}
