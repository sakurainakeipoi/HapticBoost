package io.github.sakurainakeipoi.hapticboost;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;

import java.io.File;

/** Root app_process entry point; owns only its own, bounded ringtone pulse. */
public final class RingtonePulse {
    private RingtonePulse() {}

    public static void main(String[] args) {
        Vibrator vibrator = null;
        try {
            int duration = Integer.parseInt(args[0]);
            if (duration < 100 || duration > 3000) throw new IllegalArgumentException("duration");
            File active = new File("/data/local/tmp/vib_ring_active");
            if (!active.isFile()) return;
            Looper.prepareMainLooper();
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object thread = activityThread.getMethod("systemMain").invoke(null);
            Context context = (Context) activityThread.getMethod("getSystemContext").invoke(thread);
            vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator == null || !vibrator.hasVibrator()) throw new IllegalStateException("No vibrator");
            if (!active.isFile()) return;
            // Native Telecom ringtones repeat. Equal-priority one-shots are still
            // rejected, so submit a repeating ringtone, bounded by this process.
            if (Build.VERSION.SDK_INT >= 33) {
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{duration}, new int[]{255}, 0), new VibrationAttributes.Builder()
                        .setUsage(VibrationAttributes.USAGE_RINGTONE).build());
            } else if (Build.VERSION.SDK_INT >= 26) {
                vibrator.vibrate(VibrationEffect.createWaveform(new long[]{duration}, new int[]{255}, 0), new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build());
            } else {
                vibrator.vibrate(new long[]{0, duration}, 0, new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build());
            }
            long end = SystemClock.elapsedRealtime() + duration;
            while (active.isFile() && SystemClock.elapsedRealtime() < end) {
                SystemClock.sleep(25);
            }
        } catch (Throwable failure) {
            throw new IllegalStateException("Ringtone pulse failed", failure);
        } finally {
            // This Binder token cannot cancel an unrelated app's vibration.
            if (vibrator != null) vibrator.cancel();
        }
    }
}
