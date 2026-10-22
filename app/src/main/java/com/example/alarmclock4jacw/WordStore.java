package com.example.alarmclock4jacw;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 词库读取与学习进度。
 * 词库打包在 assets 里，离线可用；四级/六级各有一份全局顺序进度。
 */
public class WordStore {

    public static final String BOOK_CET4 = "cet4";
    public static final String BOOK_CET6 = "cet6";

    private static final String PREF = "word_store";
    private static final String KEY_BOOK = "book";
    private static final String KEY_INDEX = "index_";

    private static final Map<String, List<Word>> CACHE = new HashMap<>();

    private WordStore() {
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static String normalizeBook(String book) {
        return BOOK_CET6.equals(book) ? BOOK_CET6 : BOOK_CET4;
    }

    public static String getBook(Context ctx) {
        return normalizeBook(prefs(ctx).getString(KEY_BOOK, BOOK_CET4));
    }

    public static void setBook(Context ctx, String book) {
        prefs(ctx).edit().putString(KEY_BOOK, normalizeBook(book)).apply();
    }

    public static String bookLabel(Context ctx, String book) {
        return ctx.getString(BOOK_CET6.equals(normalizeBook(book))
                ? R.string.word_book_cet6
                : R.string.word_book_cet4);
    }

    public static String bookLabel(Context ctx) {
        return bookLabel(ctx, getBook(ctx));
    }

    private static String assetName(String book) {
        return BOOK_CET6.equals(normalizeBook(book)) ? "words_cet6.json" : "words_cet4.json";
    }

    /** 读取并缓存词库；失败时返回空列表（界面据此禁止选择背单词任务）。 */
    public static synchronized List<Word> words(Context ctx, String book) {
        String key = normalizeBook(book);
        List<Word> cached = CACHE.get(key);
        if (cached != null) return cached;

        List<Word> loaded = new ArrayList<>();
        try (InputStream in = ctx.getAssets().open(assetName(key));
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            JSONObject root = new JSONObject(sb.toString());
            JSONArray arr = root.optJSONArray("words");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    String word = o.optString("w", "").trim();
                    JSONArray mArr = o.optJSONArray("m");
                    List<String> meanings = new ArrayList<>();
                    if (mArr != null) {
                        for (int j = 0; j < mArr.length() && meanings.size() < 3; j++) {
                            String m = mArr.optString(j, "").trim();
                            if (!m.isEmpty()) meanings.add(m);
                        }
                    }
                    if (!word.isEmpty() && !meanings.isEmpty()) {
                        loaded.add(new Word(word, meanings));
                    }
                }
            }
        } catch (Exception ignored) {
            loaded.clear();
        }
        CACHE.put(key, loaded);
        return loaded;
    }

    public static List<Word> words(Context ctx) {
        return words(ctx, getBook(ctx));
    }

    /** 当前词库下一个要背的位置（全局顺序推进）。 */
    public static int nextIndex(Context ctx, String book) {
        return prefs(ctx).getInt(KEY_INDEX + normalizeBook(book), 0);
    }

    /** 背完一个词，全局进度前进一步；到末尾后回到开头。 */
    public static void advance(Context ctx, String book) {
        int size = words(ctx, book).size();
        if (size <= 0) return;
        int next = (nextIndex(ctx, book) + 1) % size;
        prefs(ctx).edit().putInt(KEY_INDEX + normalizeBook(book), next).apply();
    }

    public static List<Word> page(Context ctx, String book, int startIndex, int count) {
        return Word.page(words(ctx, book), startIndex, count);
    }
}
