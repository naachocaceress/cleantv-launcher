package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * What was played on this box, kept by {@link MediaHistoryService} from the media sessions the apps publish (the
 * title, the channel or show, a picture and how far you got). It is the source of the Continue watching row for apps
 * that do not publish to Android TV's "Watch Next" list, which on a box without Google TV is nearly all of them.
 *
 * Stored as one small JSON file plus a JPEG per entry in the launcher's private storage; nothing leaves the device.
 */
final class HistoryStore {

    static final class Entry {
        final String pkg;
        /** Empty when the app published a session without a title: the entry then only says "you used this app". */
        final String title;
        final String subtitle;
        /** A file:// picture saved here, an https:// or content:// one the app published, or null. */
        final String image;
        /** What the app said it is playing (a link it can open), or null. */
        final String mediaUri;
        final long time;
        final float progress;

        Entry(String pkg, String title, String subtitle, String image, String mediaUri, long time, float progress) {
            this.pkg = pkg;
            this.title = title;
            this.subtitle = subtitle;
            this.image = image;
            this.mediaUri = mediaUri;
            this.time = time;
            this.progress = progress;
        }

        String key() {
            return pkg + "|" + title;
        }
    }

    private static final int MAX_ENTRIES = 24;
    private static final int IMAGE_WIDTH = 400;
    private static final String FILE = "history.json";
    private static final String IMAGES = "history";

    private HistoryStore() {
    }

    /** Newest first. */
    static synchronized List<Entry> load(Context ctx) {
        List<Entry> out = new ArrayList<>();
        File f = new File(ctx.getFilesDir(), FILE);
        if (!f.exists()) return out;
        try (FileInputStream in = new FileInputStream(f)) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) > 0) buf.write(chunk, 0, n);
            JSONArray arr = new JSONArray(buf.toString("UTF-8"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Entry(o.getString("pkg"), o.optString("title"), o.optString("subtitle"),
                        o.isNull("image") ? null : o.optString("image", null),
                        o.isNull("uri") ? null : o.optString("uri", null),
                        o.optLong("time"), (float) o.optDouble("progress", 0)));
            }
        } catch (Exception e) {
            out.clear();   // a damaged file just starts the history over
        }
        return out;
    }

    /**
     * Records (or refreshes) an entry and moves it to the front. {@code art} is saved as a small JPEG; when there is
     * none, {@code artUri} (a link the app published) is kept instead.
     */
    static synchronized void record(Context ctx, String pkg, String title, String subtitle, Bitmap art, String artUri,
                                    String mediaUri, float progress) {
        List<Entry> list = load(ctx);
        Entry fresh = new Entry(pkg, title, subtitle, null, mediaUri, System.currentTimeMillis(), progress);
        String image = artUri;
        Entry previous = null;
        for (Entry e : list) if (e.key().equals(fresh.key())) previous = e;
        if (art != null) {
            image = saveImage(ctx, fresh.key(), art);
        } else if (image == null && previous != null) {
            image = previous.image;   // the same title keeps the picture it already had
        }
        List<Entry> next = new ArrayList<>();
        next.add(new Entry(pkg, title, subtitle, image, mediaUri, fresh.time, progress));
        for (Entry e : list) {
            if (e.key().equals(fresh.key())) continue;
            // A session reports itself before it has a title; once it does, the bare "used this app" entry is redundant.
            if (!title.isEmpty() && e.pkg.equals(pkg) && e.title.isEmpty()) {
                deleteImage(e);
                continue;
            }
            next.add(e);
        }
        while (next.size() > MAX_ENTRIES) {
            Entry dropped = next.remove(next.size() - 1);
            deleteImage(dropped);
        }
        save(ctx, next);
    }

    private static void save(Context ctx, List<Entry> list) {
        try {
            JSONArray arr = new JSONArray();
            for (Entry e : list) {
                JSONObject o = new JSONObject();
                o.put("pkg", e.pkg);
                o.put("title", e.title);
                o.put("subtitle", e.subtitle);
                o.put("image", e.image == null ? JSONObject.NULL : e.image);
                o.put("uri", e.mediaUri == null ? JSONObject.NULL : e.mediaUri);
                o.put("time", e.time);
                o.put("progress", e.progress);
                arr.put(o);
            }
            File tmp = new File(ctx.getFilesDir(), FILE + ".tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(arr.toString().getBytes("UTF-8"));
            }
            if (!tmp.renameTo(new File(ctx.getFilesDir(), FILE))) tmp.delete();
        } catch (Exception ignored) {
            // Out of space or the like: the history simply is not updated this time.
        }
    }

    private static String saveImage(Context ctx, String key, Bitmap art) {
        try {
            File dir = new File(ctx.getFilesDir(), IMAGES);
            if (!dir.isDirectory() && !dir.mkdirs()) return null;
            File file = new File(dir, Integer.toHexString(key.hashCode()) + ".jpg");
            Bitmap scaled = art;
            if (art.getWidth() > IMAGE_WIDTH) {
                int h = Math.max(1, Math.round(art.getHeight() * (IMAGE_WIDTH / (float) art.getWidth())));
                scaled = Bitmap.createScaledBitmap(art, IMAGE_WIDTH, h, true);
            }
            try (FileOutputStream out = new FileOutputStream(file)) {
                scaled.compress(Bitmap.CompressFormat.JPEG, 82, out);
            }
            if (scaled != art) scaled.recycle();
            return "file://" + file.getAbsolutePath();
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    private static void deleteImage(Entry e) {
        if (e.image != null && e.image.startsWith("file://")) new File(e.image.substring(7)).delete();
    }

    /** Whether the system has been told to let the launcher read the media sessions. */
    static boolean listenerEnabled(Context ctx) {
        String enabled = android.provider.Settings.Secure.getString(ctx.getContentResolver(),
                "enabled_notification_listeners");
        return enabled != null && enabled.contains(ctx.getPackageName() + "/");
    }
}
