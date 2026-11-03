package com.example.uiautomation;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** تنبيه «طن طن طن»: 3 نغمات قصيرة، يُشغَّل بخيط منفصل حتى لا يؤخر الضغط */
public final class AlertSound {

    private static final int RATE = 22050;
    private static final Executor EXEC = Executors.newSingleThreadExecutor();
    private static short[] cache;

    private AlertSound() {}

    private static synchronized short[] buffer() {
        if (cache != null) {
            return cache;
        }
        int beep = (int) (RATE * 0.16);
        int gap = (int) (RATE * 0.12);
        short[] out = new short[(beep + gap) * 3];
        for (int k = 0; k < 3; k++) {
            int base = k * (beep + gap);
            for (int i = 0; i < beep; i++) {
                double t = i / (double) RATE;
                double env = Math.exp(-14.0 * t);
                double v = Math.sin(2 * Math.PI * 1100 * t)
                        + 0.5 * Math.sin(2 * Math.PI * 2200 * t);
                out[base + i] = (short) (v / 1.5 * env * 30000);
            }
        }
        cache = out;
        return out;
    }

    public static void play() {
        EXEC.execute(() -> {
            AudioTrack track = null;
            try {
                short[] buf = buffer();
                track = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(
                                        AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(RATE)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setBufferSizeInBytes(buf.length * 2)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build();
                track.write(buf, 0, buf.length);
                track.setVolume(1.0f);
                track.play();
                Thread.sleep(1000);
            } catch (Exception ignored) {
            } finally {
                if (track != null) {
                    try {
                        track.release();
                    } catch (Exception ignored) {
                    }
                }
            }
        });
    }
}
