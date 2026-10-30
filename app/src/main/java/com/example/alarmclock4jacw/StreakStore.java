package com.example.alarmclock4jacw;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/**
 * 成功次数与连续成功天数。
 *
 * <p>“成功”指真正完成运动/单词任务并关掉闹钟。累计次数每次成功都 +1；
 * 连续天数同一天最多 +1，所以一天设多个闹钟不会把连续天数刷上去。</p>
 *
 * <p>中断判定：30 分钟超时会立刻清零；如果隔了一天以上才再次成功，
 * 也会从 1 天重新开始。没有设闹钟的日子不主动扣天数，等下一次成功时自然中断。</p>
 */
public final class StreakStore {

    private static final String PREF = "streak_store";
    private static final String KEY_TOTAL = "total_successes";
    private static final String KEY_STREAK = "streak_days";
    private static final String KEY_LAST_SUCCESS = "last_success_date";
    private static final String KEY_LAST_CELEBRATED = "last_celebrated_date";

    private StreakStore() {
    }

    public static final class Record {
        public final int streak;
        public final int total;
        public final boolean celebrated;
        public final int nextMilestone;

        public Record(int streak, int total, boolean celebrated, int nextMilestone) {
            this.streak = streak;
            this.total = total;
            this.celebrated = celebrated;
            this.nextMilestone = nextMilestone;
        }
    }

    public static final class Snapshot {
        public final int streak;
        public final int total;
        /** 最近一次成功是今天或昨天，连续记录还活着。 */
        public final boolean active;

        Snapshot(int streak, int total, boolean active) {
            this.streak = streak;
            this.total = total;
            this.active = active;
        }
    }

    public static Record recordSuccess(Context context) {
        SharedPreferences sp = prefs(context);
        LocalDate today = LocalDate.now();
        String todayText = today.toString();
        LocalDate last = parse(sp.getString(KEY_LAST_SUCCESS, ""));

        int total = sp.getInt(KEY_TOTAL, 0) + 1;
        int streak = sp.getInt(KEY_STREAK, 0);
        boolean firstToday = last == null || !last.equals(today);

        if (firstToday) {
            if (last != null && last.equals(today.minusDays(1))) {
                streak += 1;
            } else {
                streak = 1;
            }
        }

        boolean celebrated = firstToday && StreakRules.isMilestone(streak)
                && !todayText.equals(sp.getString(KEY_LAST_CELEBRATED, ""));

        SharedPreferences.Editor editor = sp.edit()
                .putInt(KEY_TOTAL, total)
                .putInt(KEY_STREAK, streak)
                .putString(KEY_LAST_SUCCESS, todayText);
        if (celebrated) editor.putString(KEY_LAST_CELEBRATED, todayText);
        editor.apply();

        return new Record(streak, total, celebrated, StreakRules.nextMilestone(streak));
    }

    /** 闹钟响满 30 分钟仍未完成：连续记录中断。 */
    public static void recordMissed(Context context) {
        prefs(context).edit()
                .putInt(KEY_STREAK, 0)
                .remove(KEY_LAST_SUCCESS)
                .apply();
    }

    public static Snapshot snapshot(Context context) {
        SharedPreferences sp = prefs(context);
        int total = sp.getInt(KEY_TOTAL, 0);
        int streak = sp.getInt(KEY_STREAK, 0);
        LocalDate last = parse(sp.getString(KEY_LAST_SUCCESS, ""));
        boolean active = false;
        if (total > 0 && streak > 0 && last != null) {
            long gap = ChronoUnit.DAYS.between(last, LocalDate.now());
            active = gap == 0 || gap == 1;
        }
        return new Snapshot(streak, total, active);
    }

    private static LocalDate parse(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
