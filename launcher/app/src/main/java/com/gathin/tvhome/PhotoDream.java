package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextClock;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CleanTV fork. Screensaver: the pictures of the launcher's wallpaper collection (the theme's own plus the ones downloaded
 * from the web), each slowly zooming in and crossfading every SLIDE_MS, with a small clock in the corner. With no pictures
 * it is just the clock on black. Chosen on the TV, or over adb with:
 *   settings put secure screensaver_components com.gathin.tvhome/com.gathin.tvhome.PhotoDream
 *   settings put secure screensaver_enabled 1
 */
public class PhotoDream extends DreamService {

    private static final long SLIDE_MS = 20_000;
    private static final long FADE_MS = 2_500;
    private static final float ZOOM = 1.08f;   // reached over one slide, then the next photo takes over

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<File> order = new ArrayList<>();
    private ImageView front, back;
    private TextClock clock;
    private int next;

    private final Runnable advance = new Runnable() {
        @Override
        public void run() {
            showNext();
            handler.postDelayed(this, SLIDE_MS);
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false);   // any key wakes the TV back to where it was
        setFullscreen(true);
        setScreenBright(true);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        front = photoView();
        back = photoView();
        root.addView(back);
        root.addView(front);
        clock = clock();
        root.addView(clock);
        setContentView(root);

        File dir = Config.dir(this);
        order.addAll(WallpaperRotation.images(dir == null ? null : new File(dir, "wallpapers")));
        Collections.shuffle(order);
        front.setAlpha(0f);
        back.setAlpha(0f);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        if (!order.isEmpty()) handler.post(advance);
    }

    @Override
    public void onDreamingStopped() {
        handler.removeCallbacks(advance);
        super.onDreamingStopped();
    }

    /** Fade the next photo in over the current one, and start its slow zoom. */
    private void showNext() {
        Bitmap bitmap = decode(order.get(next));
        next = (next + 1) % order.size();
        if (bitmap == null) return;   // unreadable: the current one stays until the next slide
        ImageView incoming = back, outgoing = front;
        incoming.setImageBitmap(bitmap);
        incoming.animate().cancel();
        incoming.setScaleX(1f);
        incoming.setScaleY(1f);
        incoming.setAlpha(0f);
        incoming.bringToFront();
        incoming.animate().alpha(1f).setDuration(FADE_MS).start();
        incoming.animate().scaleX(ZOOM).scaleY(ZOOM).setDuration(SLIDE_MS + FADE_MS).start();
        outgoing.animate().alpha(0f).setStartDelay(FADE_MS).setDuration(1).start();
        front = incoming;
        back = outgoing;
        clock.bringToFront();
    }

    /** The picture no larger than the screen needs, in 16 bits: a photo has no transparency. */
    private Bitmap decode(File file) {
        try {
            DisplayMetrics m = getResources().getDisplayMetrics();
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), bounds);
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= m.widthPixels && bounds.outHeight / (sample * 2) >= m.heightPixels) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getPath(), opts);
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }

    private ImageView photoView() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        v.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        return v;
    }

    private TextClock clock() {
        TextClock c = new TextClock(this);
        c.setFormat12Hour("h:mm");
        c.setFormat24Hour("HH:mm");
        c.setTextColor(0xE6FFFFFF);
        c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
        c.setTypeface(getResources().getFont(R.font.outfit_light));
        c.setShadowLayer(12f, 0f, 2f, 0x99000000);
        float dp = getResources().getDisplayMetrics().density;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        lp.setMargins(0, 0, (int) (48 * dp), (int) (36 * dp));
        c.setLayoutParams(lp);
        return c;
    }
}
