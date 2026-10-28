package com.example.alarmclock4jacw;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * 应用内语言设置。
 *
 * <p>只保存用户显式选择过的语言：没有存档时跟随系统，系统语言是英文/日文/法文
 * 就自动走 {@code values-en / values-ja / values-fr}。所有 Activity/Service 在
 * {@code attachBaseContext} 里套一层，切换语言后重建页面即可，不需要杀进程。</p>
 */
public final class AppLanguage {

    public static final String SYSTEM = "system";
    public static final String CHINESE = "zh-CN";
    public static final String ENGLISH = "en";
    public static final String JAPANESE = "ja";
    public static final String FRENCH = "fr";

    private static final String PREF = "app_language";
    private static final String KEY_TAG = "tag";

    private AppLanguage() {
    }

    /** 用户显式选择过的语言；null 表示跟随系统。 */
    @Nullable
    public static String savedTag(Context context) {
        String tag = prefs(context).getString(KEY_TAG, null);
        if (CHINESE.equals(tag) || ENGLISH.equals(tag)
                || JAPANESE.equals(tag) || FRENCH.equals(tag)) return tag;
        return null;
    }

    /** 当前是否选中了某个选项（SYSTEM 判断的是「没有存档」而不是系统语言值）。 */
    public static boolean isSelected(Context context, String tag) {
        if (SYSTEM.equals(tag)) return savedTag(context) == null;
        return tag.equals(savedTag(context));
    }

    /** 保存选择。传 {@link #SYSTEM} 表示清除存档、重新跟随系统。 */
    public static void set(Context context, String tag) {
        SharedPreferences.Editor editor = prefs(context).edit();
        if (tag == null || SYSTEM.equals(tag)) {
            editor.remove(KEY_TAG);
        } else {
            editor.putString(KEY_TAG, tag);
        }
        editor.apply();
    }

    /** 在 Activity / Service 的 attachBaseContext 里调用。 */
    public static Context wrap(Context base) {
        String tag = savedTag(base);
        // 从英文切回「跟随系统」时，进程默认 Locale 还停在英文；这里一并恢复
        Locale locale = tag != null
                ? Locale.forLanguageTag(tag)
                : base.getResources().getConfiguration().getLocales().get(0);
        Locale.setDefault(locale);
        if (tag == null) return base;

        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(locale);
        config.setLayoutDirection(locale);
        return base.createConfigurationContext(config);
    }

    private static SharedPreferences prefs(Context context) {
        // Application.attachBaseContext 阶段 getApplicationContext() 还是 null
        Context app = context.getApplicationContext();
        if (app == null) app = context;
        return app.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
