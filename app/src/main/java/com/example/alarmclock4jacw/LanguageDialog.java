package com.example.alarmclock4jacw;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

/**
 * 语言选择弹窗。选中后立即写入存档并重建当前页面，
 * 其余页面下次进入时会在 {@link BaseActivity#attachBaseContext} 里用新语言创建。
 */
public final class LanguageDialog extends Dialog {

    private final Context host;

    public LanguageDialog(Context context) {
        super(context, R.style.Theme_AlarmClock4jACW_Dialog);
        host = context;
        // 弹窗有自己的主题副本，先把当前皮肤叠上，?attr/accent 之类的属性才对
        SkinStore.applyTheme(getContext());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.dialog_language);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.CENTER);
            int width = host.getResources().getDisplayMetrics().widthPixels;
            window.setLayout(Math.min(width - dp(48), dp(360)),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        bind(R.id.language_system, R.id.language_system_label,
                R.id.language_system_check, AppLanguage.SYSTEM);
        bind(R.id.language_chinese, R.id.language_chinese_label,
                R.id.language_chinese_check, AppLanguage.CHINESE);
        bind(R.id.language_english, R.id.language_english_label,
                R.id.language_english_check, AppLanguage.ENGLISH);
        bind(R.id.language_japanese, R.id.language_japanese_label,
                R.id.language_japanese_check, AppLanguage.JAPANESE);
        bind(R.id.language_french, R.id.language_french_label,
                R.id.language_french_check, AppLanguage.FRENCH);

        findViewById(R.id.language_cancel).setOnClickListener(v -> dismiss());
    }

    private void bind(int rowId, int labelId, int checkId, String tag) {
        View row = findViewById(rowId);
        TextView label = findViewById(labelId);
        View check = findViewById(checkId);

        boolean selected = AppLanguage.isSelected(host, tag);
        row.setBackgroundResource(selected ? R.drawable.bg_option_selected : R.drawable.bg_option);
        label.setTextColor(selected
                ? SkinStore.accent(host)
                : ContextCompat.getColor(host, R.color.text_primary));
        check.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);

        row.setOnClickListener(v -> choose(tag, selected));
    }

    private void choose(String tag, boolean alreadySelected) {
        if (alreadySelected) {
            dismiss();
            return;
        }
        AppLanguage.set(host, tag);
        dismiss();
        if (host instanceof Activity) ((Activity) host).recreate();
    }

    private int dp(int value) {
        return Math.round(value * host.getResources().getDisplayMetrics().density);
    }
}
