package io.github.sakurainakeipoi.hapticboost;

/** Timings are off/on pairs. Amplitude is resolved by Android, never scaled to 96/255. */
final class PreviewPattern {
    static long[] notification(boolean full) {
        // Double pulse observed in WeChat notification playback on the reference device.
        // This is a reference pattern, not a claim that all notification channels use it.
        return full ? new long[]{300, 200, 300, 200} : new long[]{0, 200};
    }

    static long[] ringtone(boolean full, int duration, int gap) {
        if (duration < 100 || duration > 3000 || gap < 0 || gap > 3000) {
            throw new IllegalArgumentException("Invalid ringtone timing");
        }
        return full ? new long[]{0, duration, gap, duration, gap, duration} : new long[]{0, 200};
    }

    static long duration(long[] timings) {
        long sum = 0;
        for (long timing : timings) sum += timing;
        return sum;
    }
}
