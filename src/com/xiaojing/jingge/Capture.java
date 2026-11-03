package com.xiaojing.jingge;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;

/**
 * 手机内录：抓「别的 App 正在放的声音」。
 * 只能从用户点同意后拿到的 MediaProjection 走（系统硬要求，绕不过去），
 * 而且目标 App 得允许被捕获（很多播放器关了这个开关）。
 */
final class Capture {
    private Capture() {
    }

    static MediaProjection projection = null;

    static boolean supported() {
        return Build.VERSION.SDK_INT >= 29;
    }

    static boolean ready() {
        return supported() && projection != null;
    }

    /** 让系统弹「允许录屏/录内部音频」的框 */
    static void ask(Activity a, int req) {
        if (!supported()) return;
        MediaProjectionManager mpm = (MediaProjectionManager)
                a.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (mpm == null) return;
        try {
            a.startActivityForResult(mpm.createScreenCaptureIntent(), req);
        } catch (Throwable ignored) {
        }
    }

    static boolean take(Context c, int resultCode, Intent data) {
        if (!supported() || data == null) return false;
        try {
            MediaProjectionManager mpm = (MediaProjectionManager)
                    c.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            if (mpm == null) return false;
            projection = mpm.getMediaProjection(resultCode, data);
            return projection != null;
        } catch (Throwable t) {
            projection = null;
            return false;
        }
    }

    static void release() {
        try {
            if (projection != null) projection.stop();
        } catch (Throwable ignored) {
        }
        projection = null;
    }

    /** 按内录配置开一路 44.1k 单声道 16bit 的录音 */
    static AudioRecord open(int sampleRate) {
        if (!ready()) return null;
        AudioPlaybackCaptureConfiguration cfg =
                new AudioPlaybackCaptureConfiguration.Builder(projection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .addMatchingUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .build();
        AudioFormat fmt = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build();
        int min = AudioRecord.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        try {
            AudioRecord r = new AudioRecord.Builder()
                    .setAudioFormat(fmt)
                    .setBufferSizeInBytes(Math.max(min, 4096) * 2)
                    .setAudioPlaybackCaptureConfig(cfg)
                    .build();
            return r.getState() == AudioRecord.STATE_INITIALIZED ? r : null;
        } catch (Throwable t) {
            return null;
        }
    }
}
