package com.example.alarmclock4jacw;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

/**
 * 皮肤市场：6 套特效皮肤（各自一种流光）+ 顶部预览卡点开的色盘自定义 + 特效 UI 栏目。
 * 换装只写一条 SharedPreferences，再由各 Activity 的 SkinStore.applyTheme() 叠加主题覆盖层。
 */
public class SkinMarketActivity extends AppCompatActivity {

    /** 特效 UI 的规划清单：还没实现，只把栏目列出来 */
    private static final int[][] FX_UI = {
            {R.string.fx_ui_glow_border, R.string.fx_ui_glow_border_desc},
            {R.string.fx_ui_particle, R.string.fx_ui_particle_desc},
            {R.string.fx_ui_scanline, R.string.fx_ui_scanline_desc},
            {R.string.fx_ui_neon, R.string.fx_ui_neon_desc},
            {R.string.fx_ui_glitch, R.string.fx_ui_glitch_desc},
            {R.string.fx_ui_liquid, R.string.fx_ui_liquid_desc},
    };

    private GridLayout grid;
    private TextView currentLabel;
    private SkinStore store;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SkinStore.applyTheme(this);
        setContentView(R.layout.activity_skin_market);

        store = SkinStore.get(this);
        grid = findViewById(R.id.skin_grid);
        currentLabel = findViewById(R.id.preview_current);

        SkinStore.applyFx(this.findViewById(R.id.flow_bg));
        SkinStore.applyPixelTitle(this.findViewById(R.id.market_title));

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.skin_preview_card).setOnClickListener(v -> openColorPicker());

        buildGrid();
        buildFxUi();
    }

    private void buildGrid() {
        grid.removeAllViews();
        Skin current = store.current();

        currentLabel.setText(getString(R.string.skin_current_fmt, current.name));

        for (Skin skin : store.presets()) {
            View item = getLayoutInflater().inflate(R.layout.item_skin, grid, false);
            bind(item, skin, skin.id.equals(current.id));
            item.setOnClickListener(v -> choose(skin));
            grid.addView(item);
        }
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

        ((TextView) item.findViewById(R.id.skin_name)).setText(skin.name);

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

    /** 特效 UI 栏目：每条只是占位，点了没反应，所以不给点击。 */
    private void buildFxUi() {
        LinearLayout list = findViewById(R.id.fx_ui_list);
        list.removeAllViews();

        int accent = SkinStore.accent(this);
        for (int[] item : FX_UI) {
            View row = getLayoutInflater().inflate(R.layout.item_fx_ui, list, false);
            ((TextView) row.findViewById(R.id.fx_ui_name)).setText(item[0]);
            ((TextView) row.findViewById(R.id.fx_ui_desc)).setText(item[1]);

            View dot = row.findViewById(R.id.fx_ui_dot);
            dot.setBackground(circle(accent));
            dot.setAlpha(0.55f);

            list.addView(row);
        }
    }

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
        if (skin.id.equals(store.current().id)) return;
        store.select(skin);
        Toast.makeText(this, getString(R.string.skin_applied_fmt, skin.name),
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
