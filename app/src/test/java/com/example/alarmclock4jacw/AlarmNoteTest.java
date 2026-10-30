package com.example.alarmclock4jacw;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class AlarmNoteTest {

    @Test
    public void blankNoteBecomesNull() {
        assertNull(Alarm.normalizeNote(null));
        assertNull(Alarm.normalizeNote(""));
        assertNull(Alarm.normalizeNote("   \n\t \r\n "));
    }

    @Test
    public void trimsLinesAndKeepsAtMostFour() {
        assertEquals("吃药\n记得带伞", Alarm.normalizeNote(" 吃药 \r\n\r\n 记得带伞 \r\n"));
        assertEquals("一\n二\n三\n四",
                Alarm.normalizeNote(" 一 \r\n 二 \r\n 三 \r\n 四 \r\n 五 "));
    }

    @Test
    public void capsLengthAtLimit() {
        String raw = new String(new char[80]).replace('\0', 'a');
        assertEquals(Alarm.MAX_NOTE_LENGTH, Alarm.normalizeNote(raw).length());
    }

    @Test
    public void keepsFiftyChineseCharacters() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Alarm.MAX_NOTE_LENGTH; i++) sb.append('汉');
        String note = sb.toString();

        assertEquals(note, Alarm.normalizeNote(note));
        assertEquals(note, Alarm.normalizeNote(note + "字"));
    }

    @Test
    public void newlinesDoNotCountTowardLimit() {
        StringBuilder sb = new StringBuilder();
        for (int line = 0; line < Alarm.MAX_NOTE_LINES; line++) {
            if (line > 0) sb.append('\n');
            for (int i = 0; i < 12; i++) sb.append('汉');
        }
        String note48 = sb.toString();
        assertEquals(note48, Alarm.normalizeNote(note48));

        String note50 = note48 + "汉字";
        assertEquals(note50, Alarm.normalizeNote(note50));
        assertEquals(note50, Alarm.normalizeNote(note50 + "字"));
        assertEquals(Alarm.MAX_NOTE_LENGTH,
                Alarm.noteCharCount(Alarm.normalizeNote(note50 + "字")));
    }

    @Test
    public void doesNotSplitSurrogatePairWhenTruncating() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Alarm.MAX_NOTE_LENGTH - 1; i++) sb.append('a');
        sb.append("😀").append("tail");

        String result = Alarm.normalizeNote(sb.toString());
        assertNotNull(result);
        assertEquals(Alarm.MAX_NOTE_LENGTH - 1, result.length());
        assertEquals('a', result.charAt(result.length() - 1));
    }
}
