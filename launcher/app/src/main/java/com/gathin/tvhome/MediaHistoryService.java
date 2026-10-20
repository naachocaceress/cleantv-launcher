package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.SystemClock;
import android.service.notification.NotificationListenerService;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Watches the media sessions of the other apps and writes what they play into {@link HistoryStore}, which feeds the
 * Continue watching row. Android only lets an app read the sessions of others through a notification listener, so this
 * is one; it never looks at a notification. It is switched on over adb (see build-and-install.sh) because a TV box has
 * no settings screen for it.
 */
public class MediaHistoryService extends NotificationListenerService {

    private final ExecutorService io = new ThreadPoolExecutor(0, 1, 5, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    private final Map<MediaSession.Token, Watcher> watchers = new HashMap<>();
    private MediaSessionManager sessions;

    private final MediaSessionManager.OnActiveSessionsChangedListener onSessions = this::sync;

    @Override
    public void onListenerConnected() {
        sessions = getSystemService(MediaSessionManager.class);
        ComponentName self = new ComponentName(this, MediaHistoryService.class);
        try {
            sessions.addOnActiveSessionsChangedListener(onSessions, self);
            sync(sessions.getActiveSessions(self));
        } catch (SecurityException e) {
            // The access was switched off between the bind and now.
        }
    }

    @Override
    public void onListenerDisconnected() {
        if (sessions != null) sessions.removeOnActiveSessionsChangedListener(onSessions);
        for (Watcher w : watchers.values()) w.controller.unregisterCallback(w);
        watchers.clear();
    }

    private void sync(List<MediaController> controllers) {
        if (controllers == null) return;
        // Controllers are new objects on every call; the session token is the identity.
        Set<MediaSession.Token> live = new HashSet<>();
        for (MediaController c : controllers) live.add(c.getSessionToken());
        watchers.entrySet().removeIf(e -> {
            if (live.contains(e.getKey())) return false;
            e.getValue().controller.unregisterCallback(e.getValue());
            return true;
        });
        for (MediaController c : controllers) {
            if (watchers.containsKey(c.getSessionToken()) || getPackageName().equals(c.getPackageName())) continue;
            Watcher w = new Watcher(c);
            watchers.put(c.getSessionToken(), w);
            c.registerCallback(w);
            w.capture(w.state);
        }
    }

    /** Only apps that can be opened from the home screen count: not Bluetooth, the system UI and the like. */
    private boolean openable(String pkg) {
        PackageManager pm = getPackageManager();
        return pm.getLeanbackLaunchIntentForPackage(pkg) != null || pm.getLaunchIntentForPackage(pkg) != null;
    }

    private final class Watcher extends MediaController.Callback {
        final MediaController controller;
        PlaybackState state;
        MediaMetadata metadata;

        Watcher(MediaController controller) {
            this.controller = controller;
            this.state = controller.getPlaybackState();
            this.metadata = controller.getMetadata();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState s) {
            PlaybackState before = state;
            state = s;
            // Starting, and pausing or stopping (which is when the position is worth keeping).
            if (isActive(s) && (!isPlaying(before) || !isPlaying(s))) capture(s);
            else if (!isActive(s) && isPlaying(before)) capture(before);
        }

        @Override
        public void onMetadataChanged(MediaMetadata m) {
            metadata = m;
            capture(state);
        }

        @Override
        public void onSessionDestroyed() {
            // The session ends with the app or the video: keep how far it had got.
            if (isPlaying(state)) capture(state);
            controller.unregisterCallback(this);
            watchers.remove(controller.getSessionToken());
        }

        void capture(PlaybackState st) {
            if (st == null || !isActive(st)) return;
            final String pkg = controller.getPackageName();
            if (!openable(pkg)) return;

            String title = text(metadata, MediaMetadata.METADATA_KEY_DISPLAY_TITLE, MediaMetadata.METADATA_KEY_TITLE);
            String subtitle = text(metadata, MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE,
                    MediaMetadata.METADATA_KEY_ARTIST, MediaMetadata.METADATA_KEY_ALBUM);
            String mediaUri = text(metadata, MediaMetadata.METADATA_KEY_MEDIA_URI);
            String artUri = text(metadata, MediaMetadata.METADATA_KEY_ART_URI,
                    MediaMetadata.METADATA_KEY_ALBUM_ART_URI, MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI);
            Bitmap art = metadata == null ? null : firstBitmap(metadata, MediaMetadata.METADATA_KEY_ART,
                    MediaMetadata.METADATA_KEY_ALBUM_ART, MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            long duration = metadata == null ? 0 : metadata.getLong(MediaMetadata.METADATA_KEY_DURATION);
            float progress = duration > 0 ? Math.min(1f, Math.max(0f, position(st) / (float) duration)) : 0f;
            // The bitmap belongs to the session; copy it before leaving the main thread.
            final Bitmap copy = art == null ? null : art.copy(Bitmap.Config.RGB_565, false);
            final String t = title == null ? "" : title;
            final String s = subtitle == null ? "" : subtitle;
            io.execute(() -> HistoryStore.record(getApplicationContext(), pkg, t, s, copy, artUri, mediaUri, progress));
        }
    }

    private static boolean isPlaying(PlaybackState s) {
        return s != null && (s.getState() == PlaybackState.STATE_PLAYING || s.getState() == PlaybackState.STATE_BUFFERING);
    }

    private static boolean isActive(PlaybackState s) {
        return isPlaying(s) || s.getState() == PlaybackState.STATE_PAUSED;
    }

    /** Where the playback is now: the reported position plus the time that went by while it was playing. */
    private static long position(PlaybackState s) {
        long pos = s.getPosition();
        if (s.getState() == PlaybackState.STATE_PLAYING && s.getLastPositionUpdateTime() > 0) {
            pos += (long) ((SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime()) * s.getPlaybackSpeed());
        }
        return Math.max(0, pos);
    }

    private static String text(MediaMetadata m, String... keys) {
        if (m == null) return null;
        for (String k : keys) {
            CharSequence v = m.getText(k);
            if (v != null && v.toString().trim().length() > 0) return v.toString().trim();
        }
        return null;
    }

    private static Bitmap firstBitmap(MediaMetadata m, String... keys) {
        for (String k : keys) {
            Bitmap b = m.getBitmap(k);
            if (b != null) return b;
        }
        return null;
    }
}
