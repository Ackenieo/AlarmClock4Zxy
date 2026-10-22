package com.example.alarmclock4jacw;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

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

    /**
     * 生成选择题选项：正确答案 + 同词库中释义不重叠的干扰项，数量固定为 count。
     * 只要词库足够大，就能保证有且只有一个正确答案。
     */
    public static List<Word> options(List<Word> all, Word correct, int count, Random random) {
        List<Word> candidates = new ArrayList<>();
        Set<String> correctMeanings = new HashSet<>();
        for (String m : correct.meanings) {
            correctMeanings.add(normalizeAnswer(m));
        }
        for (Word w : all) {
            if (w == null || w.word.equalsIgnoreCase(correct.word)) continue;
            boolean overlap = false;
            for (String m : w.meanings) {
                if (correctMeanings.contains(normalizeAnswer(m))) {
                    overlap = true;
                    break;
                }
            }
            if (!overlap) candidates.add(w);
        }
        Collections.shuffle(candidates, random);

        List<Word> result = new ArrayList<>();
        result.add(correct);
        for (Word w : candidates) {
            if (result.size() >= count) break;
            result.add(w);
        }
        Collections.shuffle(result, random);
        return result;
    }

    public static List<Word> options(List<Word> all, Word correct, int count) {
        return options(all, correct, count, new Random());
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