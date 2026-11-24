package com.aldef.power;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.media.VolumeProvider;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.view.KeyEvent;

/**
 * Menyambungkan tombol volume fisik HP ke volume TV (seperti aplikasi Google TV):
 * MediaSession dengan "remote playback" membuat sistem Android meneruskan tombol volume
 * ke {@link VolumeProvider} ini, dan panel volume HP menampilkan level volume TV.
 */
final class TvVolume {

    private static final int MAX_STEPS = 25;

    private final Context ctx;
    private final TvRemote remote;
    private MediaSession session;
    private int max = 100, level = -1;

    TvVolume(Context c, TvRemote remote) {
        this.ctx = c.getApplicationContext();
        this.remote = remote;
    }

    boolean active() {
        return session != null;
    }

    /** Aktifkan saat remote tersambung. */
    void attach(Activity a) {
        if (session == null) {
            try {
                session = new MediaSession(ctx, "AldefTvRemote");
                // Wajib: tanpa Callback, perintah volume ke VolumeProvider dibuang oleh MediaSession.
                session.setCallback(new MediaSession.Callback() {}, new android.os.Handler(android.os.Looper.getMainLooper()));
                session.setPlaybackState(new PlaybackState.Builder()
                        .setState(PlaybackState.STATE_PLAYING, 0, 1f)
                        .setActions(0)
                        .build());
                session.setPlaybackToRemote(provider());
                session.setActive(true);
            } catch (Exception e) {
                session = null;
                return;
            }
        }
        if (a != null) a.setMediaController(session.getController());
    }

    /** Matikan saat remote terputus / layar remote ditutup. */
    void detach(Activity a) {
        if (a != null) a.setMediaController(null);
        if (session != null) {
            try {
                session.setActive(false);
                session.release();
            } catch (Exception ignored) {
            }
            session = null;
        }
    }

    /** Level volume yang dilaporkan TV. */
    void update(int newLevel, int newMax) {
        boolean maxChanged = newMax > 0 && newMax != max;
        if (newMax > 0) max = newMax;
        level = Math.max(0, Math.min(newLevel, max));
        if (session == null) return;
        if (maxChanged) {
            session.setPlaybackToRemote(provider());
        } else if (current != null) {
            current.setCurrentVolume(level);
        }
    }

    private VolumeProvider current;

    private VolumeProvider provider() {
        int start = level < 0 ? max / 2 : level;
        current = new VolumeProvider(VolumeProvider.VOLUME_CONTROL_ABSOLUTE, max, start) {
            @Override
            public void onAdjustVolume(int direction) {
                if (direction == AudioManager.ADJUST_RAISE) {
                    step(KeyEvent.KEYCODE_VOLUME_UP, 1);
                } else if (direction == AudioManager.ADJUST_LOWER) {
                    step(KeyEvent.KEYCODE_VOLUME_DOWN, 1);
                } else if (direction == AudioManager.ADJUST_TOGGLE_MUTE
                        || direction == AudioManager.ADJUST_MUTE || direction == AudioManager.ADJUST_UNMUTE) {
                    remote.sendKey(KeyEvent.KEYCODE_VOLUME_MUTE, TvRemote.DIR_SHORT);
                }
            }

            @Override
            public void onSetVolumeTo(int volume) {
                // slider di panel volume HP digeser: kirim tombol volume sebanyak selisihnya
                int diff = volume - getCurrentVolume();
                if (diff == 0) return;
                step(diff > 0 ? KeyEvent.KEYCODE_VOLUME_UP : KeyEvent.KEYCODE_VOLUME_DOWN,
                        Math.min(Math.abs(diff), MAX_STEPS));
            }

            private void step(int key, int times) {
                for (int i = 0; i < times; i++) remote.sendKey(key, TvRemote.DIR_SHORT);
                int delta = key == KeyEvent.KEYCODE_VOLUME_UP ? times : -times;
                setCurrentVolume(Math.max(0, Math.min(max, getCurrentVolume() + delta)));
            }
        };
        return current;
    }
}
