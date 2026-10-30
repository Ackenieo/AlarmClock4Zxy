package com.example.alarmclock4jacw;

import android.content.Context;
import android.content.res.Configuration;
import android.net.Uri;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 系统内置铃声的本地化显示名。
 *
 * <p>系统媒体库里的 {@code title} 是固定的英文名，换 App 语言不会改变。
 * 这里按标题查一份中/日/法显示名表；英文、未知的系统铃声以及用户自己导入的铃声
 * 都保持原名称。</p>
 */
public final class RingtoneNames {

    private static final String ASSET = "ringtone_names.json";

    private static JSONObject names;

    private RingtoneNames() {
    }

    /**
     * @param uri   实际铃声 Uri，用于区分系统内置铃声和用户导入的铃声
     * @param title MediaStore 里的原始标题
     */
    public static String localized(Context ctx, Uri uri, String title) {
        if (title == null || title.isEmpty()) return title;
        if (!isSystemTone(uri)) return title;

        String language = currentLanguage(ctx);
        if (!"zh".equals(language) && !"ja".equals(language) && !"fr".equals(language)) {
            return title;
        }

        String key = title.trim();
        JSONObject entry = map(ctx).optJSONObject(key);
        if (entry == null) return title;

        String localized = entry.optString(language, "");
        return localized.isEmpty() ? title : localized;
    }

    private static boolean isSystemTone(Uri uri) {
        if (uri == null) return false;
        // 系统内置铃声在 internal 卷；用户导入的铃声在 external 卷。
        return uri.toString().contains("/internal/");
    }

    private static String currentLanguage(Context ctx) {
        Configuration config = ctx.getResources().getConfiguration();
        Locale locale = config.getLocales().get(0);
        return locale.getLanguage();
    }

    private static synchronized JSONObject map(Context ctx) {
        if (names != null) return names;
        names = new JSONObject();
        try (InputStream in = ctx.getApplicationContext().getAssets().open(ASSET);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            names = new JSONObject(sb.toString());
        } catch (Exception ignored) {
            // 资源缺失或解析失败时退回原始标题
        }
        return names;
    }
}
