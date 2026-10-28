package com.example.alarmclock4jacw;

/**
 * 连续成功天数的庆祝节奏。
 *
 * <p>前期频繁、后期拉远：1、3、6、10、15、20 天各庆祝一次，
 * 20 天之后每 5 天一次，保证最长间隔不超过 5 天。</p>
 */
public final class StreakRules {

    private static final int[] EARLY_MILESTONES = {1, 3, 6, 10, 15, 20};
    private static final int LATE_STEP = 5;

    private StreakRules() {
    }

    public static boolean isMilestone(int days) {
        if (days <= 0) return false;
        for (int m : EARLY_MILESTONES) {
            if (days == m) return true;
        }
        return days > 20 && (days - 20) % LATE_STEP == 0;
    }

    /** 严格大于当前天数的下一个庆祝节点。 */
    public static int nextMilestone(int days) {
        for (int m : EARLY_MILESTONES) {
            if (days < m) return m;
        }
        return 20 + ((days - 20) / LATE_STEP + 1) * LATE_STEP;
    }
}
