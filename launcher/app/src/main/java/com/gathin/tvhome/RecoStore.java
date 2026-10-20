package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The recommendations row: what the installed apps publish in Android TV's "Watch Next" list (continue watching, up
 * next, new episodes). It is read from the system's TV provider on the device, so there is no polling and no account:
 * the only network use is fetching the poster of an entry when it is shown, the same way a wallpaper is fetched.
 *
 * Reading other apps' entries needs {@link #PERMISSION}. Without it (or on a box with no TV provider) the list is
 * simply empty and the row stays hidden.
 */
final class RecoStore {

    static final String PERMISSION = "com.android.providers.tv.permission.READ_EPG_DATA";
    private static final int MAX_ITEMS = 12;
    /** Posters are shown on cards about 170dp wide; a bit more than twice that is plenty. */
    private static final int IMAGE_WIDTH = 400;
    private static final int MAX_IMAGE_BYTES = 3 * 1024 * 1024;
    private static final String USER_AGENT = "CleanTV-Launcher";

    static final class Item {
        final String title;
        /** A content:// or https:// image, 16:9 when the app provides one. */
        final String imageUrl;
        final Intent intent;
        /** How far into it you are, 0 when unknown or not started. */
        final float progress;

        Item(String title, String imageUrl, Intent intent, float progress) {
            this.title = title;
            this.imageUrl = imageUrl;
            this.intent = intent;
            this.progress = progress;
        }
    }

    interface ItemsListener {
        void onItems(List<Item> items);
    }

    interface ImageListener {
        void onImage(Bitmap bitmap);
    }

    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    // One worker that goes away on its own when idle, like the wallpaper downloads.
    private final ExecutorService io = new ThreadPoolExecutor(0, 1, 5, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    /** About a dozen RGB_565 posters; dropped by the system under memory pressure. */
    private final LruCache<String, Bitmap> images = new LruCache<String, Bitmap>(4 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return value.getByteCount();
        }
    };

    RecoStore(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    boolean hasPermission() {
        return ctx.checkSelfPermission(PERMISSION) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    /** Reads the list off the main thread and delivers it on the main thread. */
    void load(ItemsListener listener) {
        io.execute(() -> {
            List<Item> items = query();
            main.post(() -> listener.onItems(items));
        });
    }

    /** The cached poster right away, or null; then {@link #loadImage} fetches it. */
    Bitmap cachedImage(String url) {
        return url == null ? null : images.get(url);
    }

    void loadImage(String url, ImageListener listener) {
        io.execute(() -> {
            Bitmap b = images.get(url);
            if (b == null) {
                b = fetch(url);
                if (b != null) images.put(url, b);
            }
            Bitmap result = b;
            if (result != null) main.post(() -> listener.onImage(result));
        });
    }

    private List<Item> query() {
        List<Item> out = new ArrayList<>();
        if (!hasPermission()) return out;
        String[] columns = {
                TvContract.WatchNextPrograms.COLUMN_TITLE,
                TvContract.WatchNextPrograms.COLUMN_THUMBNAIL_URI,
                TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI,
                TvContract.WatchNextPrograms.COLUMN_INTENT_URI,
                TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS,
                TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS,
        };
        Set<String> seen = new HashSet<>();
        try (Cursor c = ctx.getContentResolver().query(TvContract.WatchNextPrograms.CONTENT_URI, columns, null, null,
                TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS + " DESC")) {
            while (c != null && c.moveToNext() && out.size() < MAX_ITEMS) {
                String title = c.getString(0);
                String image = firstNonEmpty(c.getString(1), c.getString(2));
                String intentUri = c.getString(3);
                if (title == null || title.isEmpty() || intentUri == null || !seen.add(title + "|" + intentUri)) continue;
                Intent intent;
                try {
                    intent = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME);
                } catch (Exception e) {
                    continue;
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                long duration = c.getLong(4), position = c.getLong(5);
                float progress = duration > 0 && position > 0 ? Math.min(1f, (float) position / duration) : 0f;
                out.add(new Item(title, image, intent, progress));
            }
        } catch (Exception e) {
            // No TV provider on this box, or the permission was revoked: nothing to show.
            out.clear();
        }
        return out;
    }

    private static String firstNonEmpty(String a, String b) {
        return a != null && !a.isEmpty() ? a : b != null && !b.isEmpty() ? b : null;
    }

    private Bitmap fetch(String url) {
        try {
            byte[] data;
            Uri uri = Uri.parse(url);
            if ("content".equals(uri.getScheme()) || "android.resource".equals(uri.getScheme())) {
                try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
                    if (in == null) return null;
                    data = readAll(in);
                }
            } else if ("https".equals(uri.getScheme())) {
                HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setRequestProperty("User-Agent", USER_AGENT);
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(15_000);
                try {
                    if (conn.getResponseCode() != 200) return null;
                    try (InputStream in = conn.getInputStream()) {
                        data = readAll(in);
                    }
                } finally {
                    conn.disconnect();
                }
            } else {
                return null;
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = Math.max(1, bounds.outWidth / IMAGE_WIDTH);
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > MAX_IMAGE_BYTES) throw new Exception("too large");
        }
        return out.toByteArray();
    }
}
