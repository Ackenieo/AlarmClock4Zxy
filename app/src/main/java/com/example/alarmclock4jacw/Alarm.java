package com.example.alarmclock4jacw;

import android.content.Context;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.Locale;

/**
 * 一条闹钟配置。
 * repeatMask 用 7 个 bit 表示周一~周日（bit0 = 周一 ... bit6 = 周日），0 表示只响一次。
 */
public class Alarm {

    public static final String EXERCISE_JUMPING_JACK = "JUMPING_JACK";

    public long id;
    public int hour;
    public int minute;
    public boolean enabled = true;
    public int repeatMask = 0;
    public int targetReps = 20;
    public String exercise = EXERCISE_JUMPING_JACK;

    public Alarm() {
        this.id = System.currentTimeMillis();
    }

    public boolean repeats() {
        return repeatMask != 0;
    }

    public String timeText() {
        return String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
    }

    /** Calendar.DAY_OF_WEEK（周日=1）转换成 bit 下标（周一=0） */
    public static int dayIndex(Calendar c) {
        return (c.get(Calendar.DAY_OF_WEEK) + 5) % 7;
    }

    /**
     * 计算下一次响铃时间戳。返回 -1 表示不会再响。
     * 只响一次的闹钟：今天的时刻已过就排到明天；重复闹钟则找下一个命中的星期。
     */
    public long nextTriggerMillis(long now) {
        Calendar base = Calendar.getInstance();
        base.setTimeInMillis(now);
        for (int i = 0; i < 8; i++) {
            Calendar t = (Calendar) base.clone();
            t.add(Calendar.DAY_OF_YEAR, i);
            t.set(Calendar.HOUR_OF_DAY, hour);
            t.set(Calendar.MINUTE, minute);
            t.set(Calendar.SECOND, 0);
            t.set(Calendar.MILLISECOND, 0);
            long millis = t.getTimeInMillis();
            if (millis <= now + 1000L) continue;
            if (repeatMask != 0 && (repeatMask & (1 << dayIndex(t))) == 0) continue;
            return millis;
        }
        return -1L;
    }

    public String repeatText(Context ctx) {
        if (repeatMask == 0) return ctx.getString(R.string.once);
        if (repeatMask == 0x7F) return ctx.getString(R.string.every_day);
        if (repeatMask == 0x1F) return ctx.getString(R.string.workdays);
        if (repeatMask == 0x60) return ctx.getString(R.string.weekend);
        String[] names = {"一", "二", "三", "四", "五", "六", "日"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            if ((repeatMask & (1 << i)) != 0) {
                if (sb.length() > 0) sb.append('、');
                sb.append('周').append(names[i]);
            }
        }
        return sb.toString();
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("id", id);
            o.put("hour", hour);
            o.put("minute", minute);
            o.put("enabled", enabled);
            o.put("repeat", repeatMask);
            o.put("reps", targetReps);
            o.put("exercise", exercise);
        } catch (JSONException ignored) {
        }
        return o;
    }

    public static Alarm fromJson(JSONObject o) {
        Alarm a = new Alarm();
        a.id = o.optLong("id", System.currentTimeMillis());
        a.hour = o.optInt("hour", 7);
        a.minute = o.optInt("minute", 0);
        a.enabled = o.optBoolean("enabled", true);
        a.repeatMask = o.optInt("repeat", 0);
        a.targetReps = o.optInt("reps", 20);
        a.exercise = o.optString("exercise", EXERCISE_JUMPING_JACK);
        return a;
    }

    public Alarm copy() {
        return fromJson(toJson());
    }
}
