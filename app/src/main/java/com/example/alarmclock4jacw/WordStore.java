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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 词库读取、全局学习进度、错词队列与单次响铃会话。
 *
 * 顺序策略：错词优先，其余按词库固定顺序推进。
 * 会话把本轮单词和游标落库，进程被杀后可以从原题继续。
 */
public class WordStore {

    public static final String BOOK_CET4 = "cet4";
    public static final String BOOK_CET6 = "cet6";

    private static final Set<String> KNOWN_BOOKS =
            new HashSet<>(Arrays.asList(BOOK_CET4, BOOK_CET6));
    private static final String PREF = "word_store";
    private static final String KEY_BOOK = "book";
    private static final String KEY_INDEX = "index_";
    private static final String KEY_REVIEW = "review_";
    private static final String KEY_SESSION = "session_";
    private static final int MAX_REVIEW_WORDS = 200;

    private static final Map<String, List<Word>> CACHE = new HashMap<>();

    private WordStore() {
    }

    /** 一次响铃的答题会话。 */
    public static class Session {
        public final String id;
        public final String book;
        public final List<Word> words;
        public final List<Boolean> fromReview;
        public final int reviewCount;
        public final int fixedEnd;
        public int cursor;
        public final List<String> wrongWords = new ArrayList<>();

        Session(String id, String book, List<Word> words, List<Boolean> fromReview,
                int reviewCount, int fixedEnd, int cursor) {
            this.id = id;
            this.book = book;
            this.words = words;
            this.fromReview = fromReview;
            this.reviewCount = reviewCount;
            this.fixedEnd = fixedEnd;
            this.cursor = cursor;
        }

        public int total() {
            return words.size();
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static String normalizeBook(String book) {
        return book != null && KNOWN_BOOKS.contains(book) ? book : BOOK_CET4;
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

    /** 当前词库下一个固定顺序位置。 */
    public static int nextIndex(Context ctx, String book) {
        return prefs(ctx).getInt(KEY_INDEX + normalizeBook(book), 0);
    }

    /** 兼容旧调用：顺序前进一步。 */
    public static void advance(Context ctx, String book) {
        int size = words(ctx, book).size();
        if (size <= 0) return;
        int next = (nextIndex(ctx, book) + 1) % size;
        prefs(ctx).edit().putInt(KEY_INDEX + normalizeBook(book), next).apply();
    }

    public static List<Word> page(Context ctx, String book, int startIndex, int count) {
        return Word.page(words(ctx, book), startIndex, count);
    }

    private static String key(String word) {
        return word == null ? "" : word.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static boolean containsIgnoreCase(List<String> list, String word) {
        String k = key(word);
        for (String s : list) {
            if (key(s).equals(k)) return true;
        }
        return false;
    }

    private static List<String> readReviewWords(Context ctx, String book) {
        List<String> out = new ArrayList<>();
        String raw = prefs(ctx).getString(KEY_REVIEW + normalizeBook(book), null);
        if (raw == null) return out;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                String w = arr.optString(i, "").trim();
                if (!w.isEmpty() && !containsIgnoreCase(out, w)) out.add(w);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void saveReviewWords(Context ctx, String book, List<String> list) {
        JSONArray arr = new JSONArray();
        for (String w : list) arr.put(w);
        prefs(ctx).edit().putString(KEY_REVIEW + normalizeBook(book), arr.toString()).apply();
    }

    public static void addReviewWord(Context ctx, String book, String word) {
        if (word == null || word.trim().isEmpty()) return;
        List<String> list = readReviewWords(ctx, book);
        if (containsIgnoreCase(list, word)) return;
        list.add(word.trim());
        while (list.size() > MAX_REVIEW_WORDS) list.remove(0);
        saveReviewWords(ctx, book, list);
    }

    public static void removeReviewWord(Context ctx, String book, String word) {
        List<String> list = readReviewWords(ctx, book);
        String k = key(word);
        list.removeIf(s -> key(s).equals(k));
        saveReviewWords(ctx, book, list);
    }

    /**
     * 开始（或恢复）一次答题会话。
     * 错词优先，剩余位置按固定顺序取词；固定顺序进度只在会话完成时整体前移。
     */
    public static synchronized Session beginSession(Context ctx, String book, int count,
                                                    String sessionId) {
        String b = normalizeBook(book);
        String sid = sessionId == null || sessionId.isEmpty()
                ? String.valueOf(System.currentTimeMillis()) : sessionId;
        Session existing = readSession(ctx, b);
        if (existing != null && sid.equals(existing.id)) return existing;

        int total = Math.max(1, count);
        List<Word> all = words(ctx, b);
        List<Word> selected = new ArrayList<>();
        List<Boolean> fromReview = new ArrayList<>();
        Set<String> used = new HashSet<>();
        Map<String, Word> byKey = new HashMap<>();
        for (Word w : all) byKey.put(key(w.word), w);

        for (String s : readReviewWords(ctx, b)) {
            if (selected.size() >= total) break;
            Word w = byKey.get(key(s));
            if (w != null && used.add(key(w.word))) {
                selected.add(w);
                fromReview.add(true);
            }
        }

        int start = nextIndex(ctx, b);
        int scanned = 0;
        while (selected.size() < total && scanned < all.size() && !all.isEmpty()) {
            int idx = (start + scanned) % all.size();
            Word w = all.get(idx);
            scanned++;
            if (used.add(key(w.word))) {
                selected.add(w);
                fromReview.add(false);
            }
        }

        int fixedEnd = all.isEmpty() ? start : (start + scanned) % all.size();
        int reviewCount = 0;
        for (Boolean flag : fromReview) {
            if (Boolean.TRUE.equals(flag)) reviewCount++;
        }

        Session session = new Session(sid, b, selected, fromReview, reviewCount,
                fixedEnd, 0);
        saveSession(ctx, session);
        return session;
    }

    /** 答完一题：更新错词队列与会话游标。 */
    public static synchronized void completeSessionWord(Context ctx, Session session, boolean wrong) {
        if (session == null || session.cursor < 0 || session.cursor >= session.words.size()) return;
        Word word = session.words.get(session.cursor);
        boolean fromReview = Boolean.TRUE.equals(session.fromReview.get(session.cursor));

        if (wrong) {
            addReviewWord(ctx, session.book, word.word);
            if (!containsIgnoreCase(session.wrongWords, word.word)) {
                session.wrongWords.add(word.word);
            }
        } else if (fromReview) {
            removeReviewWord(ctx, session.book, word.word);
        }

        session.cursor++;
        saveSession(ctx, session);
    }

    /** 会话完成：固定顺序进度前移到本轮用到的最后位置，并清除会话。 */
    public static synchronized void finishSession(Context ctx, Session session) {
        if (session == null) return;
        prefs(ctx).edit()
                .putInt(KEY_INDEX + session.book, session.fixedEnd)
                .remove(KEY_SESSION + session.book)
                .apply();
    }

    private static void saveSession(Context ctx, Session session) {
        try {
            JSONObject o = new JSONObject();
            o.put("id", session.id);
            o.put("book", session.book);
            JSONArray words = new JSONArray();
            for (Word w : session.words) words.put(w.word);
            o.put("words", words);
            JSONArray flags = new JSONArray();
            for (Boolean b : session.fromReview) flags.put(Boolean.TRUE.equals(b));
            o.put("fromReview", flags);
            o.put("reviewCount", session.reviewCount);
            o.put("fixedEnd", session.fixedEnd);
            o.put("cursor", session.cursor);
            JSONArray wrong = new JSONArray();
            for (String w : session.wrongWords) wrong.put(w);
            o.put("wrong", wrong);
            prefs(ctx).edit().putString(KEY_SESSION + session.book, o.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private static Session readSession(Context ctx, String book) {
        String raw = prefs(ctx).getString(KEY_SESSION + book, null);
        if (raw == null) return null;
        try {
            JSONObject o = new JSONObject(raw);
            String id = o.optString("id", "");
            JSONArray wa = o.optJSONArray("words");
            JSONArray fa = o.optJSONArray("fromReview");
            if (wa == null) return null;
            Map<String, Word> byKey = new HashMap<>();
            for (Word w : words(ctx, book)) byKey.put(key(w.word), w);

            List<Word> words = new ArrayList<>();
            List<Boolean> flags = new ArrayList<>();
            for (int i = 0; i < wa.length(); i++) {
                Word w = byKey.get(key(wa.optString(i, "")));
                if (w == null) continue;
                words.add(w);
                flags.add(fa != null && fa.optBoolean(i, false));
            }
            int reviewCount = 0;
            for (Boolean flag : flags) {
                if (Boolean.TRUE.equals(flag)) reviewCount++;
            }
            Session session = new Session(id, book, words, flags, reviewCount,
                    o.optInt("fixedEnd", nextIndex(ctx, book)),
                    Math.max(0, o.optInt("cursor", 0)));
            if (session.cursor > session.words.size()) session.cursor = session.words.size();
            JSONArray wrong = o.optJSONArray("wrong");
            if (wrong != null) {
                for (int i = 0; i < wrong.length(); i++) {
                    String w = wrong.optString(i, "");
                    if (!w.isEmpty() && !containsIgnoreCase(session.wrongWords, w)) {
                        session.wrongWords.add(w);
                    }
                }
            }
            return session;
        } catch (Exception ignored) {
            return null;
        }
    }
}
