package com.example.alarmclock4jacw;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 一个待拼写的单词及其常用中文释义。
 */
public class Word {

    public final String word;
    public final List<String> meanings;

    public Word(String word, List<String> meanings) {
        this.word = word;
        this.meanings = Collections.unmodifiableList(new ArrayList<>(meanings));
    }

    public String meaningText() {
        StringBuilder sb = new StringBuilder();
        for (String m : meanings) {
            if (sb.length() > 0) sb.append("；");
            sb.append(m);
        }
        return sb.toString();
    }

    /** 答案判定：忽略大小写和首尾空格，弯引号按直引号处理。 */
    public static String normalizeAnswer(String text) {
        if (text == null) return "";
        return text.trim().toLowerCase(Locale.ROOT).replace('’', '\'');
    }

    public static boolean matches(String word, String input) {
        return normalizeAnswer(word).equals(normalizeAnswer(input));
    }

    /** 从词表中按固定顺序取 count 个词，走到末尾后从头循环。 */
    public static List<Word> page(List<Word> all, int startIndex, int count) {
        List<Word> result = new ArrayList<>();
        if (all == null || all.isEmpty() || count <= 0) return result;
        int size = all.size();
        int start = ((startIndex % size) + size) % size;
        for (int i = 0; i < count; i++) {
            result.add(all.get((start + i) % size));
        }
        return result;
    }
}