package com.example.alarmclock4jacw;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 闹钟持久化。数据量很小（几条到十几条），用 SharedPreferences + JSON 足够，避免引入数据库。
 */
public class AlarmStore {

    private static final String PREF = "alarms";
    private static final String KEY = "items_v1";

    private static AlarmStore instance;

    private final SharedPreferences prefs;
    private final List<Alarm> alarms = new ArrayList<>();

    private AlarmStore(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE);
        load();
    }

    public static synchronized AlarmStore get(Context ctx) {
        if (instance == null) instance = new AlarmStore(ctx);
        return instance;
    }

    private void load() {
        alarms.clear();
        String raw = prefs.getString(KEY, null);
        if (raw == null) return;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                alarms.add(Alarm.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception ignored) {
        }
        sort();
    }

    private void sort() {
        Collections.sort(alarms, new Comparator<Alarm>() {
            @Override
            public int compare(Alarm a, Alarm b) {
                int ka = a.hour * 60 + a.minute;
                int kb = b.hour * 60 + b.minute;
                return Integer.compare(ka, kb);
            }
        });
    }

    private void persist() {
        JSONArray arr = new JSONArray();
        for (Alarm a : alarms) arr.put(a.toJson());
        prefs.edit().putString(KEY, arr.toString()).apply();
    }

    public List<Alarm> all() {
        return new ArrayList<>(alarms);
    }

    public boolean isEmpty() {
        return alarms.isEmpty();
    }

    public Alarm find(long id) {
        for (Alarm a : alarms) {
            if (a.id == id) return a;
        }
        return null;
    }

    /** 新增或更新 */
    public void save(Alarm alarm) {
        for (int i = 0; i < alarms.size(); i++) {
            if (alarms.get(i).id == alarm.id) {
                alarms.set(i, alarm);
                sort();
                persist();
                return;
            }
        }
        alarms.add(alarm);
        sort();
        persist();
    }

    public void delete(long id) {
        for (int i = 0; i < alarms.size(); i++) {
            if (alarms.get(i).id == id) {
                alarms.remove(i);
                persist();
                return;
            }
        }
    }

    /** 找到最近一次将响铃的时间，没有则返回 -1 */
    public long nextTriggerMillis() {
        long now = System.currentTimeMillis();
        long best = -1;
        for (Alarm a : alarms) {
            if (!a.enabled) continue;
            long t = a.nextTriggerMillis(now);
            if (t > 0 && (best < 0 || t < best)) best = t;
        }
        return best;
    }
}
