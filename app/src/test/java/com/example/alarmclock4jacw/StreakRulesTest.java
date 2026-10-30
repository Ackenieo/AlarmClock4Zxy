package com.example.alarmclock4jacw;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StreakRulesTest {

    @Test
    public void earlyMilestonesMatchRequestedCadence() {
        int[] expected = {1, 3, 6, 10, 15, 20};
        for (int day : expected) {
            assertTrue("day " + day, StreakRules.isMilestone(day));
        }
        assertFalse(StreakRules.isMilestone(2));
        assertFalse(StreakRules.isMilestone(4));
        assertFalse(StreakRules.isMilestone(21));
    }

    @Test
    public void laterMilestonesStayAtMostFiveDaysApart() {
        assertTrue(StreakRules.isMilestone(25));
        assertTrue(StreakRules.isMilestone(30));
        assertTrue(StreakRules.isMilestone(105));

        assertEquals(3, StreakRules.nextMilestone(1));
        assertEquals(6, StreakRules.nextMilestone(3));
        assertEquals(20, StreakRules.nextMilestone(15));
        assertEquals(25, StreakRules.nextMilestone(20));
        assertEquals(25, StreakRules.nextMilestone(21));
        assertEquals(30, StreakRules.nextMilestone(25));
        assertEquals(105, StreakRules.nextMilestone(100));
    }
}
