package com.example.alarmclock4jacw;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;

/**
 * 响铃服务：前台服务持有 MediaPlayer + 振动器，并发出带 fullScreenIntent 的高优先级通知。
 * 声音/振动只有收到 ACTION_STOP 才停——也就是必须做完运动。
 */
public class AlarmService extends Service {

    private static final String TAG = "AlarmService";
    private static final String CHANNEL_ID = "alarm_ringing";
    private static final int NOTIF_ID = 1001;

    public static final String ACTION_START = "com.example.alarmclock4jacw.action.START_RING";
    public static final String ACTION_STOP = "com.example.alarmclock4jacw.action.STOP_RING";
    public static final String EXTRA_ID = "alarm_id";
    public static final String EXTRA_REPS = "reps";
    public static final String EXTRA_LABEL = "label";

    /** 供界面判断当前是否正在响铃 */
    public static volatile boolean ringing = false;

    private MediaPlayer player;
    private Vibrator vibrator;

    public static Intent startIntent(Context ctx, Alarm alarm) {
        Intent i = new Intent(ctx, AlarmService.class);
        i.setAction(ACTION_START);
        i.putExtra(EXTRA_ID, alarm.id);
        i.putExtra(EXTRA_REPS, alarm.targetReps);
        i.putExtra(EXTRA_LABEL, alarm.timeText());
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

        long id = intent != null ? intent.getLongExtra(EXTRA_ID, -1L) : -1L;
        int reps = intent != null ? intent.getIntExtra(EXTRA_REPS, 20) : 20;
        String label = intent != null ? intent.getStringExtra(EXTRA_LABEL) : "";

        startRinging(id, reps, label);
        return START_STICKY;
    }

    private void startRinging(long alarmId, int reps, String label) {
        ringing = true;
        createChannel();

        Notification notification = buildNotification(alarmId, reps, label);
        try {
            ServiceCompat.startForeground(this, NOTIF_ID, notification,
                    Build.VERSION.SDK_INT >= 29 ? ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK : 0);
        } catch (Exception e) {
            Log.e(TAG, "startForeground failed", e);
        }

        startSound();
        startVibration();

        // 兜底：部分机型 fullScreenIntent 不生效时直接拉起响铃页
        try {
            Intent i = new Intent(this, RingingActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            i.putExtra(EXTRA_ID, alarmId);
            i.putExtra(EXTRA_REPS, reps);
            i.putExtra(EXTRA_LABEL, label);
            startActivity(i);
        } catch (Exception e) {
            Log.w(TAG, "direct activity launch blocked, rely on full screen intent", e);
        }
    }

    private Notification buildNotification(long alarmId, int reps, String label) {
        Intent full = new Intent(this, RingingActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_ID, alarmId)
                .putExtra(EXTRA_REPS, reps)
                .putExtra(EXTRA_LABEL, label);
        PendingIntent fullPi = PendingIntent.getActivity(this, 1, full,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String title = getString(R.string.ringing_title) + (label.isEmpty() ? "" : " · " + label);
        String text = getString(R.string.ringing_reps_fmt, reps);

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

    private void startSound() {
        if (player != null) return;
        try {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            if (am != null) {
                int max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                int cur = am.getStreamVolume(AudioManager.STREAM_ALARM);
                int want = Math.round(max * 0.85f);
                if (cur < want) am.setStreamVolume(AudioManager.STREAM_ALARM, want, 0);
            }

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
            player.prepare();
            player.start();
        } catch (Exception e) {
            Log.e(TAG, "play alarm sound failed", e);
        }
    }

    private void startVibration() {
        if (vibrator != null) return;
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        long[] pattern = {0, 420, 260, 420, 260, 760, 500};
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
    }

    private void stopRinging() {
        ringing = false;
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            player.release();
            player = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.cancel(NOTIF_ID);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        ringing = false;
        if (player != null) {
            try {
                player.stop();
            } catch (Exception ignored) {
            }
            player.release();
            player = null;
        }
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
        super.onDestroy();
    }
}
