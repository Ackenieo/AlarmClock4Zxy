package com.example.alarmclock4jacw;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WordTest {

    private static List<Word> sample() {
        List<Word> list = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            list.add(new Word("word" + i, Arrays.asList("释义" + i)));
        }
        return list;
    }

    @Test
    public void matchesIgnoresCaseAndOuterSpaces() {
        assertTrue(Word.matches("Abandon", "  abandon "));
        assertTrue(Word.matches("abandon", "ABANDON"));
        assertFalse(Word.matches("abandon", "abandoned"));
    }

    @Test
    public void matchesNormalizesCurlyApostrophe() {
        assertTrue(Word.matches("don't", "don’t"));
    }

    @Test
    public void pageFollowsFixedOrderAndWraps() {
        List<Word> list = sample();
        List<Word> page = Word.page(list, 3, 4);
        assertEquals(4, page.size());
        assertEquals("word3", page.get(0).word);
        assertEquals("word4", page.get(1).word);
        assertEquals("word0", page.get(2).word);
        assertEquals("word1", page.get(3).word);
    }

    @Test
    public void pageReturnsEmptyWhenNoWords() {
        assertTrue(Word.page(new ArrayList<>(), 0, 10).isEmpty());
        assertTrue(Word.page(sample(), 0, 0).isEmpty());
    }

    @Test
    public void meaningTextJoinsWithSemicolon() {
        Word w = new Word("test", Arrays.asList("测试", "试验"));
        assertEquals("测试；试验", w.meaningText());
    }
}
