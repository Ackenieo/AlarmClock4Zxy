package com.example.alarmclock4jacw;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

/**
 * 响铃服务：前台服务持有 MediaPlayer + 振动器，并发出带 fullScreenIntent 的高优先级通知。
 *
 * <h3>本服务承担的「关不掉」职责</h3>
 * <ul>
 *   <li><b>音量锁定最大</b>：每秒把 {@code STREAM_ALARM} 重新拉满，用户按音量键、拉通知栏
 *       滑块都压不下去；响铃页另外消费音量键，避免误触。</li>
 *   <li><b>免打扰 / 静音兜底</b>：检测到 ringerMode 非 NORMAL 或通知被打断过滤（免打扰）时，
 *       切到更重的振动节拍，振动用途标成 {@code USAGE_ALARM}。实测铃声本身在免打扰与静音档
 *       都照常播放（{@code STREAM_ALARM} 不在铃声模式抑制的流里），振动则在免打扰下会被系统吞掉，
 *       想连振动一起保住需要「通知策略访问」权限，本项目没有申请。</li>
 *   <li><b>滑不掉</b>：{@link #onTaskRemoved(Intent)} + 每秒看门狗，只要响铃还在进行而界面
 *       不见了（从概览划掉、按 Home、被系统回收），就重新拉回响铃页；后台启动被系统拦截时
 *       退回「重发全屏通知」这条合法路径。</li>
 *   <li><b>杀不掉</b>：{@code START_STICKY} + 会话状态落库，进程被杀后服务重启会恢复同一个
 *       闹钟的响铃。</li>
 * </ul>
 *
 * 唯一合法出口是 {@link #ACTION_STOP}，只有响铃页在运动达标后才会发出。作为最后一道保险，
 * 连续响满 {@link #MAX_RING_MILLIS} 会自动停止——否则识别失败、相机被拒、半夜没人时，
 * 手机会变成一台只会响的砖头。
 */
public class AlarmService extends Service {

    private static final String TAG = "AlarmService";
    private static final String CHANNEL_ID = "alarm_ringing";
    private static final int NOTIF_ID = 1001;

    /** 响铃会话状态：进程被杀后靠它恢复 */
    private static final String PREFS = "ring_state";
    private static final String KEY_ID = "id";
    private static final String KEY_REPS = "reps";
    private static final String KEY_LABEL = "label";
    private static final String KEY_DEADLINE = "deadline";
    private static final String KEY_TASK = "task";
    private static final String KEY_WORDS = "words";
    private static final String KEY_WORD_START = "word_start";

    /** 硬兜底：连续响满 30 分钟自动停止 */
    private static final long MAX_RING_MILLIS = 30 * 60 * 1000L;
    /** 界面切换（响铃页 <-> 运动页）会有重叠窗口，宽限期内不重开 */
    private static final long UI_GRACE_MILLIS = 2000L;

    public static final String ACTION_START = "com.example.alarmclock4jacw.action.START_RING";
    public static final String ACTION_STOP = "com.example.alarmclock4jacw.action.STOP_RING";
    public static final String EXTRA_ID = "alarm_id";
    public static final String EXTRA_REPS = "reps";
    public static final String EXTRA_LABEL = "label";
    public static final String EXTRA_TASK = "task";
    public static final String EXTRA_WORDS = "words";
    public static final String EXTRA_WORD_START = "word_start";

    /** 供界面判断当前是否正在响铃 */
    public static volatile boolean ringing = false;
    /** 免打扰 / 静音命中，已切强震动，供界面提示 */
    public static volatile boolean vibrationBoost = false;
    /** 是否由 30 分钟硬兜底停止，供界面决定要不要提示超时 */
    public static volatile boolean timedOut = false;

    private static volatile boolean uiVisible = false;
    private static volatile long lastUiSeenAt = 0L;

    /** 由响铃页/运动页在 onResume 调用，告诉看门狗「界面还在」 */
    public static void uiSeen() {
        uiVisible = true;
        lastUiSeenAt = SystemClock.elapsedRealtime();
    }

    public static void uiHidden() {
        uiVisible = false;
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private Vibrator vibrator;

    private long alarmId = -1L;
    private int reps = 20;
    private String label = "";
    private String taskType = Alarm.TASK_JUMPING_JACK;
    private int targetWords = 10;
    private int wordStart = 0;
    /** 墙钟截止时间：SystemClock.elapsedRealtime 会随重启归零，用 currentTimeMillis 才扛得住进程重启 */
    private long deadlineAt = 0L;

    public static Intent startIntent(Context ctx, Alarm alarm) {
        Intent i = new Intent(ctx, AlarmService.class);
        i.setAction(ACTION_START);
        i.putExtra(EXTRA_ID, alarm.id);
        i.putExtra(EXTRA_REPS, alarm.targetReps);
        i.putExtra(EXTRA_LABEL, alarm.timeText());
        i.putExtra(EXTRA_TASK, alarm.taskType);
        i.putExtra(EXTRA_WORDS, alarm.targetWords);
        if (alarm.isWordTask()) {
            i.putExtra(EXTRA_WORD_START,
                    WordStore.nextIndex(ctx, WordStore.getBook(ctx)));
        }
        return i;
    }

    public static void stop(Context ctx) {
        Intent i = new Intent(ctx, AlarmService.class);
        i.setAction(ACTION_STOP);
        try {
            ctx.startService(i);
        } catch (Exception e) {
            Log.w(TAG, "stop service failed", e);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stopRinging();
            return START_NOT_STICKY;
        }

        if (intent != null && intent.hasExtra(EXTRA_ID)) {
            alarmId = intent.getLongExtra(EXTRA_ID, -1L);
            reps = intent.getIntExtra(EXTRA_REPS, 20);
            label = intent.getStringExtra(EXTRA_LABEL);
            if (label == null) label = "";
            taskType = intent.getStringExtra(EXTRA_TASK);
            if (!Alarm.TASK_WORD_SPELL.equals(taskType)) taskType = Alarm.TASK_JUMPING_JACK;
            targetWords = intent.getIntExtra(EXTRA_WORDS, 10);
            wordStart = intent.getIntExtra(EXTRA_WORD_START, 0);
            timedOut = false;
            deadlineAt = System.currentTimeMillis() + MAX_RING_MILLIS;
        } else if (alarmId < 0 && !restoreState()) {
            // START_STICKY 重启但找不到原始会话：不要凭空响一个不存在的闹钟
            Log.w(TAG, "restarted without saved session, giving up");
            stopSelf();
            return START_NOT_STICKY;
        }

        startRinging();
        return START_STICKY;
    }

    // ---------------------------------------------------------------- 生命周期

    private void startRinging() {
        ringing = true;
        saveState();
        createChannel();

        try {
            ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(),
                    Build.VERSION.SDK_INT >= 29 ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK : 0);
        } catch (Exception e) {
            Log.e(TAG, "startForeground failed", e);
        }

        lockMaxVolume();
        startSound();
        applyVibration();
        relaunchUi();

        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker, 1000);
    }

    private void stopRinging() {
        ringing = false;
        handler.removeCallbacks(ticker);
        releaseSound();
        releaseVibration();
        clearState();
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(NOTIF_ID);
        stopSelf();
    }

    /**
     * 每秒巡检：拉满音量、复核免打扰状态、确认界面还在、检查 30 分钟兜底。
     */
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!ringing) return;

            if (System.currentTimeMillis() >= deadlineAt) {
                Log.i(TAG, "ring deadline reached, auto-stop as the last resort");
                timedOut = true;
                stopRinging();
                return;
            }

            lockMaxVolume();
            refreshVibrationMode();
            ensureUiVisible();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        // 从概览里划掉应用：界面会消失，但声音和振动不能停 —— 立刻把响铃页拉回来
        Log.i(TAG, "task removed while ringing, re-launching UI");
        if (ringing) {
            handler.postDelayed(this::relaunchUi, 800);
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        ringing = false;
        handler.removeCallbacks(ticker);
        releaseSound();
        releaseVibration();
        super.onDestroy();
    }

    // ---------------------------------------------------------------- 声音 / 振动

    private void lockMaxVolume() {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am == null) return;
        try {
            int max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
            if (am.getStreamVolume(AudioManager.STREAM_ALARM) != max) {
                am.setStreamVolume(AudioManager.STREAM_ALARM, max, 0);
            }
        } catch (Exception e) {
            Log.w(TAG, "lock volume failed", e);
        }
    }

    private void startSound() {
        if (player != null) return;
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            android.content.res.AssetFileDescriptor afd =
                    getResources().openRawResourceFd(R.raw.alarm_beep);
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            player.setLooping(true);
            player.setVolume(1f, 1f);
            player.prepare();
            player.start();
        } catch (Exception e) {
            Log.e(TAG, "play alarm sound failed", e);
        }
    }

    /** 免打扰或静音：铃声会被系统吞掉，此时把振动切到更重的节拍 */
    private void refreshVibrationMode() {
        if (isDndOrSilent() != vibrationBoost) applyVibration();
    }

    private boolean isDndOrSilent() {
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (am != null && am.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) return true;
        NotificationManager nm = getSystemService(NotificationManager.class);
        return nm != null
                && nm.getCurrentInterruptionFilter() != NotificationManager.INTERRUPTION_FILTER_ALL;
    }

    private void applyVibration() {
        vibrationBoost = isDndOrSilent();
        if (vibrator == null) {
            vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        }
        if (vibrator == null || !vibrator.hasVibrator()) return;
        vibrator.cancel();

        // 免打扰/静音下用更长的脉冲 + 更短的间歇，保证躺床上也能被震醒
        long[] pattern = vibrationBoost
                ? new long[]{0, 900, 200, 900, 200, 1200, 360}
                : new long[]{0, 420, 260, 420, 260, 760, 500};
        VibrationEffect effect = VibrationEffect.createWaveform(pattern, 0);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // 标成闹钟用途，静音/振动档下不会被铃声模式压掉。
                // 免打扰开启时系统仍会用 app-ops 吞掉第三方振动，除非持有 ACCESS_NOTIFICATION_POLICY。
                VibrationAttributes attrs = new VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_ALARM)
                        .build();
                vibrator.vibrate(effect, attrs);
            } else {
                vibrator.vibrate(effect);
            }
        } catch (Exception e) {
            Log.w(TAG, "vibrate failed", e);
        }
    }

    private void releaseSound() {
        if (player == null) return;
        try {
            player.stop();
        } catch (Exception ignored) {
        }
        player.release();
        player = null;
    }

    private void releaseVibration() {
        if (vibrator == null) return;
        vibrator.cancel();
        vibrator = null;
        vibrationBoost = false;
    }

    // ---------------------------------------------------------------- 界面看门狗

    private void ensureUiVisible() {
        if (uiVisible) return;
        if (SystemClock.elapsedRealtime() - lastUiSeenAt < UI_GRACE_MILLIS) return;
        relaunchUi();
    }

    /**
     * 把响铃页拉回前台。Android 10+ 会拦后台 startActivity，
     * 失败时退回「重发带 fullScreenIntent 的通知」这条系统认可的通路（息屏/锁屏下会直接点亮屏幕）。
     */
    private void relaunchUi() {
        Intent i = new Intent(this, RingingActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_ID, alarmId)
                .putExtra(EXTRA_REPS, reps)
                .putExtra(EXTRA_LABEL, label)
                .putExtra(EXTRA_TASK, taskType)
                .putExtra(EXTRA_WORDS, targetWords)
                .putExtra(EXTRA_WORD_START, wordStart);
        try {
            startActivity(i);
        } catch (Exception e) {
            Log.w(TAG, "direct launch blocked, fall back to full screen notification", e);
            try {
                NotificationManager nm = getSystemService(NotificationManager.class);
                if (nm != null) nm.notify(NOTIF_ID, buildNotification());
            } catch (Exception ignored) {
            }
        }
    }

    // ---------------------------------------------------------------- 通知 / 状态

    private Notification buildNotification() {
        Intent full = new Intent(this, RingingActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_ID, alarmId)
                .putExtra(EXTRA_REPS, reps)
                .putExtra(EXTRA_LABEL, label)
                .putExtra(EXTRA_TASK, taskType)
                .putExtra(EXTRA_WORDS, targetWords)
                .putExtra(EXTRA_WORD_START, wordStart);
        PendingIntent fullPi = PendingIntent.getActivity(this, 1, full,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = getString(R.string.ringing_title) + (label.isEmpty() ? "" : " · " + label);
        String text = Alarm.TASK_WORD_SPELL.equals(taskType)
                ? getString(R.string.ringing_words_fmt, targetWords)
                : getString(R.string.ringing_reps_fmt, reps);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alarm)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setAutoCancel(false)
                .setOnlyAlertOnce(true)
                .setFullScreenIntent(fullPi, true)
                .setContentIntent(fullPi)
                .build();
    }

    private void createChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                getString(R.string.notif_channel_alarm), NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription(getString(R.string.notif_channel_alarm_desc));
        ch.setSound(null, null);          // 声音由 MediaPlayer 负责，避免双份
        ch.enableVibration(false);        // 振动由 Vibrator 负责
        ch.setBypassDnd(true);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(ch);
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void saveState() {
        prefs().edit()
                .putLong(KEY_ID, alarmId)
                .putInt(KEY_REPS, reps)
                .putString(KEY_LABEL, label)
                .putLong(KEY_DEADLINE, deadlineAt)
                .putString(KEY_TASK, taskType)
                .putInt(KEY_WORDS, targetWords)
                .putInt(KEY_WORD_START, wordStart)
                .apply();
    }

    private boolean restoreState() {
        SharedPreferences p = prefs();
        long id = p.getLong(KEY_ID, Long.MIN_VALUE);
        long deadline = p.getLong(KEY_DEADLINE, 0L);
        if (id == Long.MIN_VALUE || deadline <= System.currentTimeMillis()) {
            clearState();
            return false;
        }
        alarmId = id;
        reps = p.getInt(KEY_REPS, 20);
        label = p.getString(KEY_LABEL, "");
        if (label == null) label = "";
        taskType = p.getString(KEY_TASK, Alarm.TASK_JUMPING_JACK);
        if (!Alarm.TASK_WORD_SPELL.equals(taskType)) taskType = Alarm.TASK_JUMPING_JACK;
        targetWords = p.getInt(KEY_WORDS, 10);
        wordStart = p.getInt(KEY_WORD_START, 0);
        deadlineAt = deadline;
        return true;
    }

    private void clearState() {
        prefs().edit().clear().apply();
    }
}
