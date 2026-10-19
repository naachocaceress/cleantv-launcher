package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Wallpapers downloaded on demand. The catalog (assets/wallpapers.json, built by tools/make_wallpaper_catalog.py,
 * plus an optional extra list at res/values/config.xml wallpaper_catalog_url) holds only links; a picture is fetched
 * when asked for, cropped to 1920x1080 and kept in the launcher's private storage with a small note (title, credit,
 * source) next to it. Nothing is bundled in the APK.
 */
final class WallpaperStore {

    /** file is the new wallpaper, or null when nothing was downloaded; message is for the user either way. */
    interface Done {
        void onDone(File file, String message);
    }

    private static final int W = 1920, H = 1080;
    private static final int MAX_IMAGE_BYTES = 40 << 20;
    private static final int MAX_CATALOG_BYTES = 4 << 20;
    private static final String USER_AGENT = "tvhome-launcher/2.0 (Android TV home screen; personal use)";

    private final Context ctx;
    private final File dir;
    // One download at a time, and the thread goes away after 5 s idle instead of staying alive for ever.
    private final ExecutorService io = new ThreadPoolExecutor(0, 1, 5, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
    private final Handler main = new Handler(Looper.getMainLooper());

    WallpaperStore(Context context) {
        ctx = context.getApplicationContext();
        dir = new File(ctx.getFilesDir(), "wallpapers");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
    }

    File dir() {
        return dir;
    }

    /** Downloaded wallpapers, oldest first (a new one lands at the end). */
    List<File> files() {
        File[] found = dir.listFiles((d, name) -> name.endsWith(".jpg"));
        if (found == null) return Collections.emptyList();
        List<File> out = new ArrayList<>(Arrays.asList(found));
        Collections.sort(out, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));
        return out;
    }

    /** "Title - Author, Licence" for a downloaded wallpaper. */
    String credit(File f) {
        List<String> n = note(f);
        return n.size() >= 2 ? n.get(0) + " — " + n.get(1) : f.getName();
    }

    void delete(File f) {
        //noinspection ResultOfMethodCallIgnored
        f.delete();
        //noinspection ResultOfMethodCallIgnored
        noteFile(f).delete();
    }

    /** Picks a catalog entry that is not downloaded yet, fetches it and reports back on the main thread. */
    void fetchNew(Done done) {
        io.execute(() -> {
            try {
                Set<String> have = new HashSet<>();
                for (File f : files()) {
                    List<String> n = note(f);
                    if (n.size() >= 3) have.add(n.get(2));
                }
                List<JSONObject> fresh = new ArrayList<>();
                JSONArray catalog = catalog();
                for (int i = 0; i < catalog.length(); i++) {
                    JSONObject o = catalog.optJSONObject(i);
                    if (o != null && o.optString("url").startsWith("https://") && !have.contains(o.getString("url"))) {
                        fresh.add(o);
                    }
                }
                if (fresh.isEmpty()) {
                    post(done, null, ctx.getString(R.string.wallpaper_have_all));
                    return;
                }
                File f = save(fresh.get(new Random().nextInt(fresh.size())));
                post(done, f, credit(f));
            } catch (Exception e) {
                post(done, null, ctx.getString(R.string.wallpaper_failed));
            }
        });
    }

    private void post(Done done, File file, String message) {
        main.post(() -> done.onDone(file, message));
    }

    private JSONArray catalog() throws Exception {
        JSONArray all = new JSONArray();
        try (InputStream in = ctx.getAssets().open("wallpapers.json")) {
            append(all, new JSONArray(new String(readAll(in, MAX_CATALOG_BYTES), StandardCharsets.UTF_8)));
        }
        String extra = ctx.getString(R.string.wallpaper_catalog_url).trim();
        if (extra.startsWith("https://")) {
            try {   // a broken extra list must not take the built-in one down with it
                append(all, new JSONArray(new String(get(extra, MAX_CATALOG_BYTES), StandardCharsets.UTF_8)));
            } catch (Exception ignored) {
            }
        }
        return all;
    }

    private static void append(JSONArray to, JSONArray from) throws Exception {
        for (int i = 0; i < from.length(); i++) to.put(from.get(i));
    }

    private File save(JSONObject entry) throws Exception {
        String url = entry.getString("url");
        byte[] data = get(url, MAX_IMAGE_BYTES);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new Exception("not an image");
        int sample = 1;   // decode no larger than needed so a big photo cannot run the TV out of memory
        while (bounds.outWidth / (sample * 2) >= W && bounds.outHeight / (sample * 2) >= H) sample *= 2;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = sample;
        Bitmap src = BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        if (src == null) throw new Exception("could not decode");

        Bitmap out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        float s = Math.max((float) W / src.getWidth(), (float) H / src.getHeight());   // cover-crop to 16:9
        Matrix m = new Matrix();
        m.postScale(s, s);
        m.postTranslate((W - src.getWidth() * s) / 2f, (H - src.getHeight() * s) / 2f);
        new Canvas(out).drawBitmap(src, m, new Paint(Paint.FILTER_BITMAP_FLAG));
        src.recycle();

        File jpg = new File(dir, Integer.toHexString(url.hashCode()) + ".jpg");
        try (FileOutputStream fo = new FileOutputStream(jpg)) {
            out.compress(Bitmap.CompressFormat.JPEG, 88, fo);
        }
        out.recycle();
        String text = entry.optString("title", "Wallpaper") + "\n" + entry.optString("credit", "") + "\n" + url + "\n";
        Files.write(noteFile(jpg).toPath(), text.getBytes(StandardCharsets.UTF_8));
        return jpg;
    }

    private static File noteFile(File jpg) {
        String n = jpg.getName();
        return new File(jpg.getParentFile(), n.substring(0, n.length() - 4) + ".txt");
    }

    private static List<String> note(File jpg) {
        try {
            return Files.readAllLines(noteFile(jpg).toPath(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private static byte[] get(String url, int maxBytes) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestProperty("User-Agent", USER_AGENT);   // Wikimedia asks clients to identify themselves
        c.setConnectTimeout(15_000);
        c.setReadTimeout(30_000);
        try {
            if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
                return readAll(in, maxBytes);
            }
        } finally {
            c.disconnect();
        }
    }

    private static byte[] readAll(InputStream in, int maxBytes) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16 * 1024];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
            if (out.size() > maxBytes) throw new Exception("too large");
        }
        return out.toByteArray();
    }
}
