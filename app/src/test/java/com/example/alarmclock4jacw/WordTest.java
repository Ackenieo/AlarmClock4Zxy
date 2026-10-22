package com.example.alarmclock4jacw;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

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

    @Test
    public void optionsContainOneCorrectAndUniqueWords() {
        List<Word> all = new ArrayList<>();
        all.add(new Word("apple", Arrays.asList("苹果")));
        all.add(new Word("apple2", Arrays.asList("苹果")));
        all.add(new Word("banana", Arrays.asList("香蕉")));
        all.add(new Word("orange", Arrays.asList("橙子")));
        all.add(new Word("grape", Arrays.asList("葡萄")));
        all.add(new Word("peach", Arrays.asList("桃子")));
        all.add(new Word("lemon", Arrays.asList("柠檬")));
        all.add(new Word("melon", Arrays.asList("甜瓜")));
        all.add(new Word("cherry", Arrays.asList("樱桃")));
        all.add(new Word("berry", Arrays.asList("浆果")));

        Word correct = all.get(0);
        List<Word> options = Word.options(all, correct, 4, new Random(7));

        assertEquals(4, options.size());
        assertTrue(options.contains(correct));

        Set<String> words = new HashSet<>();
        for (Word w : options) {
            assertTrue(words.add(w.word));
            if (w != correct) {
                for (String meaning : w.meanings) {
                    assertFalse(correct.meanings.contains(meaning));
                }
            }
        }
    }

    @Test
    public void alarmsTreatBothWordModesAsWordTasks() {
        assertTrue(Alarm.isWordTask(Alarm.TASK_WORD_CHOICE));
        assertTrue(Alarm.isWordTask(Alarm.TASK_WORD_SPELL));
        assertFalse(Alarm.isWordTask(Alarm.TASK_JUMPING_JACK));
    }
}
