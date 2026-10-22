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

    public static final String TASK_JUMPING_JACK = "JUMPING_JACK";
    public static final String TASK_WORD_SPELL = "WORD_SPELL";

    public long id;
    public int hour;
    public int minute;
    public boolean enabled = true;
    public int repeatMask = 0;
    public int targetReps = 20;
    public int targetWords = 10;
    public String taskType = TASK_JUMPING_JACK;

    public Alarm() {
        this.id = System.currentTimeMillis();
    }

    public boolean repeats() {
        return repeatMask != 0;
    }

    public boolean isWordTask() {
        return TASK_WORD_SPELL.equals(taskType);
    }

    /** 闹钟列表里的摘要：20 个开合跳 / 10 个单词 · 四级 */
    public String taskSummary(Context ctx) {
        if (isWordTask()) {
            return ctx.getString(R.string.word_summary_fmt, targetWords, WordStore.bookLabel(ctx));
        }
        return ctx.getString(R.string.reps_value_fmt, targetReps)
                + ctx.getString(R.string.exercise_jumping_jack);
    }

    /** 响铃页/通知里的目标文案 */
    public String ringingText(Context ctx) {
        return isWordTask()
                ? ctx.getString(R.string.ringing_words_fmt, targetWords)
                : ctx.getString(R.string.ringing_reps_fmt, targetReps);
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
            o.put("words", targetWords);
            o.put("task", taskType);
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
        a.targetWords = o.optInt("words", 10);
        // 旧版本字段名是 exercise，读不到 task 时按开合跳处理
        a.taskType = o.optString("task", o.optString("exercise", TASK_JUMPING_JACK));
        if (!TASK_WORD_SPELL.equals(a.taskType)) a.taskType = TASK_JUMPING_JACK;
        return a;
    }

    public Alarm copy() {
        return fromJson(toJson());
    }
}
