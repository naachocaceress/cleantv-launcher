package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The app tiles. Pinned apps come first, in the order the user set; the rest is alphabetical. The pinned list is
 * saved on the device and starts from res/values/config.xml (dock_apps).
 *
 * What a tile looks like depends on the {@link Theme}: in DOCK the first {@link #dockCount()} positions are the dock
 * (small icons) and the rest are banner cards; in CIRCLES every app is a round icon; in TILES every app is a wide tile
 * Only CIRCLES and TILES have no dock ({@link #dockCount()} is 0), so "pinned" just means "listed first".
 */
public class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {

    interface TileListener {
        void onTileFocused(int position, View card);

        /** Called for D-pad Up/Down on a tile in the DOCK theme. Return true when handled. */
        boolean onTileVerticalKey(int position, int keyCode);

        /** Hold OK or press Menu on a tile: move (dock only), pin, unpin, app info. */
        void onTileMenu(int position, View card);

        /** True while a dock app is being moved: the tiles then hand every key to {@link #onMoveKey}. */
        boolean isMoving();

        /** A key pressed on a tile while moving. Return true when handled (it always should be). */
        boolean onMoveKey(int position, int keyCode);
    }

    static final int TYPE_ICON = 0, TYPE_BANNER = 1, TYPE_ROUND = 2, TYPE_WIDE = 3;

    // Square dock art (res/drawable-nodpi/dock_*.png), drawn locally by tools/make_dock_icons.py and kept out of git
    // (the logos are trademarks). Looked up by name so the app builds without them; apps without art show their own icon.
    private static final Map<String, String> GLYPHS = new HashMap<>();
    static {
        GLYPHS.put("com.netflix.ninja", "dock_netflix");
        GLYPHS.put("com.amazon.amazonvideo.livingroom", "dock_prime");
        GLYPHS.put("com.amazon.firebat", "dock_prime");
        GLYPHS.put("com.google.android.youtube.tv", "dock_youtube");
        GLYPHS.put("com.amazon.firetv.youtube", "dock_youtube");
        GLYPHS.put("com.disney.disneyplus", "dock_disney");
        GLYPHS.put("com.crunchyroll.crunchyroid", "dock_crunchyroll");
        GLYPHS.put("com.stremio.one", "dock_stremio");
        GLYPHS.put("org.xbmc.kodi", "dock_kodi");
        GLYPHS.put("com.spotify.tv.android", "dock_spotify");
        GLYPHS.put("com.plexapp.android", "dock_plex");
        GLYPHS.put("org.jellyfin.androidtv", "dock_jellyfin");
        GLYPHS.put("tv.twitch.android.app", "dock_twitch");
        GLYPHS.put("com.wbd.stream", "dock_max");
        GLYPHS.put("com.apple.atve.androidtv.appletv", "dock_appletv");
        GLYPHS.put("com.cbs.ott", "dock_paramount");
    }

    static final class Entry {
        final String label;
        final ComponentName component;
        final String pkg;
        /** Square dock art, or null: then the app's own icon is shown. */
        final Drawable glyph;
        final Drawable icon;
        /** The app's own TV banner (16:9), or null. */
        final Drawable banner;
        int ambient;
        /** Colours at the top and bottom edge of the square art, for the wide tile behind it (0 = not computed yet). */
        int edgeTop, edgeBottom;

        Entry(String label, ComponentName component, Drawable glyph, Drawable icon, Drawable banner) {
            this.label = label;
            this.component = component;
            this.pkg = component.getPackageName();
            this.glyph = glyph;
            this.icon = icon;
            this.banner = banner;
        }
    }

    static final float FOCUS_SCALE = 1.10f;          // large icons
    static final float BANNER_FOCUS_SCALE = 1.06f;   // banner cards
    static final float WIDE_FOCUS_SCALE = 1.18f;     // tvOS tiles
    static final float CORNER_RATIO = 0.225f;

    private final Context ctx;
    private final TileListener listener;
    private static final String PREFS = "tvhome";
    private static final String PREF_DOCK = "dock";
    private final int maxDock;
    private Theme theme = Theme.DOCK;
    private int dockCount;
    /** Pinned packages in dock order, including any that are not installed right now. */
    private final List<String> pinned = new ArrayList<>();
    /** Never shown as tiles (res/values/config.xml, hidden_apps). */
    private final Set<String> hidden;
    private final List<Entry> items = new ArrayList<>();

    AppAdapter(Context ctx, TileListener listener, int maxDock) {
        this.ctx = ctx;
        this.listener = listener;
        this.maxDock = maxDock;
        String saved = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PREF_DOCK, null);
        pinned.addAll(saved != null
                ? (saved.isEmpty() ? Collections.emptyList() : Arrays.asList(saved.split(",")))
                : Arrays.asList(ctx.getResources().getStringArray(R.array.dock_apps)));
        this.hidden = new HashSet<>(Arrays.asList(ctx.getResources().getStringArray(R.array.hidden_apps)));
    }

    Theme theme() {
        return theme;
    }

    void setTheme(Theme theme) {
        this.theme = theme;
        reload();
    }

    void reload() {
        PackageManager pm = ctx.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(probe, 0);
        List<Entry> fresh = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || hidden.contains(pkg)) continue;
            // One tile per app: skip extra launcher entries (AirPlay registers three).
            Intent main = pm.getLeanbackLaunchIntentForPackage(pkg);
            ComponentName mainComponent = main != null ? main.getComponent() : null;
            if (mainComponent != null && !mainComponent.getClassName().equals(ri.activityInfo.name)) continue;
            if (!seen.add(pkg)) continue;
            Drawable icon = ri.loadIcon(pm);
            Drawable banner = ri.activityInfo.loadBanner(pm);
            if (banner == null && ri.activityInfo.applicationInfo != null) {
                banner = ri.activityInfo.applicationInfo.loadBanner(pm);
            }
            String label = String.valueOf(ri.loadLabel(pm));
            String glyphName = GLYPHS.get(pkg);
            int glyph = glyphName == null ? 0
                    : ctx.getResources().getIdentifier(glyphName, "drawable", ctx.getPackageName());
            fresh.add(new Entry(label, new ComponentName(pkg, ri.activityInfo.name),
                    glyph != 0 ? ctx.getDrawable(glyph) : null, icon, banner));
        }
        Collections.sort(fresh, (a, b) -> {
            int fa = pinned.indexOf(a.pkg), fb = pinned.indexOf(b.pkg);
            if (fa >= 0 || fb >= 0) {
                if (fa < 0) return 1;
                if (fb < 0) return -1;
                return Integer.compare(fa, fb);
            }
            return a.label.compareToIgnoreCase(b.label);
        });
        items.clear();
        items.addAll(fresh);
        int docked = 0;
        while (docked < items.size() && pinned.contains(items.get(docked).pkg)) docked++;
        dockCount = theme == Theme.DOCK ? Math.min(docked, maxDock) : 0;
        notifyDataSetChanged();
    }

    // ---- dock editing ----------------------------------------------------------------------------

    int dockCount() {
        return dockCount;
    }

    boolean isDocked(int position) {
        return position >= 0 && position < dockCount;
    }

    String pkgAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).pkg : "";
    }

    int positionOf(String pkg) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).pkg.equals(pkg)) return i;
        return -1;
    }

    boolean isPinned(String pkg) {
        return pinned.contains(pkg);
    }

    /** Pins the app (last in the dock). False when the dock is full. */
    boolean pin(String pkg) {
        if (pinned.contains(pkg)) return true;
        int installedPins = 0;
        for (Entry e : items) if (pinned.contains(e.pkg)) installedPins++;
        if (installedPins >= maxDock) return false;
        pinned.removeIf(p -> positionOf(p) < 0);   // forget uninstalled pins so they don't hold slots
        pinned.add(pkg);
        saveAndReload();
        return true;
    }

    void unpin(String pkg) {
        pinned.remove(pkg);
        saveAndReload();
    }

    /** Swaps the app with its dock neighbour on the left (-1) or right (+1), skipping pins that are not installed. */
    void move(String pkg, int delta) {
        List<String> dock = new ArrayList<>();
        for (int i = 0; i < dockCount; i++) dock.add(items.get(i).pkg);
        int from = dock.indexOf(pkg), to = from + delta;
        if (from < 0 || to < 0 || to >= dock.size()) return;
        Collections.swap(dock, from, to);
        List<String> notInstalled = new ArrayList<>();
        for (String p : pinned) if (!dock.contains(p)) notInstalled.add(p);
        pinned.clear();
        pinned.addAll(dock);
        pinned.addAll(notInstalled);
        saveAndReload();
    }

    private void saveAndReload() {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(PREF_DOCK, String.join(",", pinned)).apply();
        reload();
    }

    // ---- views -----------------------------------------------------------------------------------

    @Override
    public int getItemViewType(int position) {
        if (theme == Theme.TILES) return TYPE_WIDE;
        if (theme == Theme.CIRCLES) return TYPE_ROUND;
        return isDocked(position) ? TYPE_ICON : TYPE_BANNER;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == TYPE_BANNER ? R.layout.item_banner
                : viewType == TYPE_WIDE ? R.layout.item_wide : R.layout.item_app;
        View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
        return new Holder(v, listener, this, viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Entry e = items.get(position);
        float d = ctx.getResources().getDisplayMetrics().density;
        h.card.setScaleX(1f);
        h.card.setScaleY(1f);
        h.card.setTranslationY(0f);
        // A round icon sits in a fixed 112dp cell of the horizontal row; every other item fills its grid cell.
        ViewGroup.LayoutParams ilp = h.itemView.getLayoutParams();
        ilp.width = h.type == TYPE_ROUND ? (int) (112 * d) : ViewGroup.LayoutParams.MATCH_PARENT;
        h.itemView.setLayoutParams(ilp);
        ViewGroup.LayoutParams lp = h.card.getLayoutParams();
        // Reset what the other item types set, since views are recycled between themes.
        h.card.setBackground(null);
        h.card.setForeground(null);
        h.card.setElevation(0f);
        h.icon.setPadding(0, 0, 0, 0);
        h.name.setText(e.label);
        h.name.setAlpha(0f);
        h.name.setVisibility(View.GONE);
        switch (h.type) {
            case TYPE_BANNER: {
                // 16:9 like the apps' own TV banners; the cells are 160dp wide in DOCK.
                lp.width = (int) (142 * d);
                lp.height = (int) (80 * d);
                h.card.setLayoutParams(lp);
                boolean hasBanner = e.banner != null;
                h.banner.setImageDrawable(e.banner);
                h.banner.setVisibility(hasBanner ? View.VISIBLE : View.GONE);
                h.icon.setImageDrawable(e.glyph != null ? e.glyph : e.icon);
                h.icon.setVisibility(hasBanner ? View.GONE : View.VISIBLE);
                h.label.setText(e.label);
                h.label.setVisibility(hasBanner ? View.GONE : View.VISIBLE);
                break;
            }
            case TYPE_ROUND: {   // CIRCLES: a round icon with a white ring when focused (the name is the big title)
                int size = (int) (88 * d);
                lp.width = size;
                lp.height = size;
                h.card.setLayoutParams(lp);
                h.card.setForeground(ctx.getDrawable(R.drawable.round_focus));
                if (e.glyph == null) h.card.setBackground(ctx.getDrawable(R.drawable.round_bg));
                h.icon.setPadding((int) (e.glyph == null ? 20 * d : 0), (int) (e.glyph == null ? 20 * d : 0),
                        (int) (e.glyph == null ? 20 * d : 0), (int) (e.glyph == null ? 20 * d : 0));
                h.banner.setVisibility(View.GONE);
                h.label.setVisibility(View.GONE);
                h.icon.setImageDrawable(e.glyph != null ? e.glyph : e.icon);
                h.icon.setVisibility(View.VISIBLE);
                h.shadow.setVisibility(View.GONE);
                break;
            }
            case TYPE_WIDE: {   // TILES: a wide tile; the colour comes from the art's own edges
                lp.width = (int) (196 * d);
                lp.height = (int) (131 * d);
                h.card.setLayoutParams(lp);
                int[] edges = edgeColors(e);
                GradientDrawable tile = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, edges);
                tile.setCornerRadius(18 * d);
                h.card.setBackground(tile);
                boolean hasBanner = e.banner != null && e.glyph == null;
                h.banner.setImageDrawable(e.banner);
                h.banner.setVisibility(hasBanner ? View.VISIBLE : View.GONE);
                h.icon.setImageDrawable(e.glyph != null ? e.glyph : e.icon);
                h.icon.setVisibility(hasBanner ? View.GONE : View.VISIBLE);
                if (e.glyph == null) h.icon.setPadding((int) (38 * d), (int) (28 * d), (int) (38 * d), (int) (28 * d));
                h.label.setVisibility(View.GONE);
                h.shadow.setVisibility(View.GONE);   // no name under the tile: the plate is all there is
                break;
            }
            default: {   // TYPE_ICON, the small dock icons of the DOCK theme
                int size = (int) (56 * d);
                lp.width = size;
                lp.height = size;
                h.card.setLayoutParams(lp);
                // Icon only: the dock art (or the app's own icon) fills the rounded square, nothing behind it.
                h.banner.setVisibility(View.GONE);
                h.label.setVisibility(View.GONE);
                h.icon.setImageDrawable(e.glyph != null ? e.glyph : e.icon);
                h.icon.setVisibility(View.VISIBLE);
                h.shadow.setVisibility(View.VISIBLE);
                h.shadow.setScaleX(1f);
                h.shadow.setScaleY(1f);
                h.shadow.setTranslationY(0f);
                h.name.setVisibility(View.INVISIBLE);   // the dock uses a tooltip instead
                FrameLayout.LayoutParams nlp = (FrameLayout.LayoutParams) h.name.getLayoutParams();
                nlp.width = (int) (100 * d);
                nlp.topMargin = (int) (63 * d);
                h.name.setLayoutParams(nlp);
                break;
            }
        }        h.card.setOnClickListener(v -> launch(e));
        h.card.setOnLongClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onTileMenu(pos, v);
            return true;
        });
    }

    /** Top and bottom colour of the tile behind a square logo: sampled from the logo's own edges so they match. */
    private int[] edgeColors(Entry e) {
        if (e.edgeTop == 0) {
            int top = 0xFF2A2A32, bottom = 0xFF2A2A32;   // graphite, for apps without brand art
            if (e.glyph != null && e.glyph.getConstantState() != null) {
                Drawable d = e.glyph.getConstantState().newDrawable().mutate();
                int n = 16;
                Bitmap bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
                d.setBounds(0, 0, n, n);
                d.draw(new Canvas(bmp));
                int t = bmp.getPixel(n / 2, 0), b = bmp.getPixel(n / 2, n - 1);
                if (Color.alpha(t) > 200) top = t | 0xFF000000;
                if (Color.alpha(b) > 200) bottom = b | 0xFF000000;
                bmp.recycle();
            }
            e.edgeTop = top;
            e.edgeBottom = bottom;
        }
        return new int[]{e.edgeTop, e.edgeBottom};
    }

    private void launch(Entry e) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(e.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            ctx.startActivity(i);
        } catch (Exception ex) {
            Toast.makeText(ctx, ctx.getString(R.string.cant_open, e.label), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    String labelAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).label : "";
    }

    /** A vivid colour sampled from the app's art (or icon), cached per entry. */
    int ambientColorAt(int position) {
        if (position < 0 || position >= items.size()) return 0xFF444444;
        Entry e = items.get(position);
        if (e.ambient == 0) e.ambient = vividColor(e.glyph != null ? e.glyph : e.icon);
        return e.ambient;
    }

    private static int vividColor(Drawable live) {
        if (live == null) return 0xFF444444;
        // Draw a private copy: changing the bounds of the drawable an ImageView shows would blank the tile.
        Drawable d = live.getConstantState() != null ? live.getConstantState().newDrawable().mutate() : live;
        Rect saved = new Rect(d.getBounds());
        int n = 12;
        Bitmap bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, n, n);
        d.draw(c);
        d.setBounds(saved);
        float[] hsv = new float[3];
        int best = 0xFF444444;
        float bestScore = -1f;
        long r = 0, g = 0, b = 0;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int p = bmp.getPixel(x, y);
                r += Color.red(p);
                g += Color.green(p);
                b += Color.blue(p);
                Color.colorToHSV(p, hsv);
                float score = hsv[1] * Math.min(1f, hsv[2] * 1.4f);   // saturated and not too dark
                if (score > bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
        }
        bmp.recycle();
        if (bestScore < 0.25f) {   // nearly monochrome art (e.g. white): use its average, lifted
            int avg = Color.rgb((int) (r / (n * n)), (int) (g / (n * n)), (int) (b / (n * n)));
            Color.colorToHSV(avg, hsv);
            hsv[2] = Math.max(hsv[2], 0.55f);
            return Color.HSVToColor(hsv);
        }
        Color.colorToHSV(best, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.1f);
        hsv[2] = Math.max(0.5f, hsv[2]);
        return Color.HSVToColor(hsv);
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final int type;
        final View card;
        final View shadow;
        final ImageView banner;
        final ImageView icon;
        final TextView label;
        final TextView name;

        Holder(View v, TileListener listener, AppAdapter owner, int type) {
            super(v);
            this.type = type;
            card = v.findViewById(R.id.card);
            shadow = v.findViewById(R.id.shadow);
            banner = v.findViewById(R.id.banner);
            icon = v.findViewById(R.id.icon);
            label = v.findViewById(R.id.label);
            name = v.findViewById(R.id.name);

            final float density = v.getResources().getDisplayMetrics().density;
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    // Banner cards: a fixed 12dp corner. Round icons: a circle. tvOS tiles: 18dp. Dock icons:
                    // squircle-ish, 22.5% of the short side.
                    if (Holder.this.type == TYPE_ROUND) {
                        outline.setOval(0, 0, view.getWidth(), view.getHeight());
                        return;
                    }
                    float radius = Holder.this.type == TYPE_BANNER ? 12 * density
                            : Holder.this.type == TYPE_WIDE ? 18 * density
                            : Math.min(view.getWidth(), view.getHeight()) * CORNER_RATIO;
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
                }
            });
            card.setClipToOutline(true);

            card.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                int pos = getBindingAdapterPosition();
                if (listener == null || pos == RecyclerView.NO_POSITION) return false;
                if (listener.isMoving()) return listener.onMoveKey(pos, keyCode);
                if (keyCode == KeyEvent.KEYCODE_MENU) {
                    listener.onTileMenu(pos, view);
                    return true;
                }
                if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN) return false;
                // The activity decides per theme (it returns false to leave it to Android's focus search).
                return listener.onTileVerticalKey(pos, keyCode);
            });

            card.setOnFocusChangeListener((view, focused) -> {
                // Dock icons are magnified as a group by MainActivity; every other tile scales itself, at once.
                boolean inDock = owner.isDocked(getBindingAdapterPosition());
                if (!inDock) {
                    float s = !focused ? 1.0f : Holder.this.type == TYPE_BANNER ? BANNER_FOCUS_SCALE
                            : Holder.this.type == TYPE_WIDE ? WIDE_FOCUS_SCALE : FOCUS_SCALE;
                    view.setScaleX(s);
                    view.setScaleY(s);
                    if (Holder.this.type == TYPE_WIDE) view.setElevation(focused ? 18 * density : 0f);
                }
                name.setAlpha(focused ? 1f : 0f);
                name.setTranslationY(focused ? density * 4f : 0f);
                int pos = getBindingAdapterPosition();
                if (focused && listener != null && pos != RecyclerView.NO_POSITION) {
                    listener.onTileFocused(pos, view);
                }
            });
        }
    }
}
