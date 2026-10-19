package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * tvOS style home: a pinned dock row (magnified icons, name tooltip, glow) and an app grid below it. The dock keeps
 * its animations; the grid has none: the highlight sits on the focused tile and focus changes and scrolling show at
 * once.
 */
public class MainActivity extends Activity implements AppAdapter.TileListener {

    /** Tiles per row and dock slots (res/values/config.xml, columns). Set in onCreate. */
    static int COLUMNS = 4;
    /** Most apps the dock can hold (res/values/config.xml, dock_max). Set in onCreate. */
    static int DOCK_MAX = 8;
    /** Dock cells across the screen; the dock sits centred in them (res/values/config.xml, dock_slots). */
    static int DOCK_SLOTS = 9;
    private static final long MINUTE_MS = 60_000L;
    private String liveTvPkg;

    private AppAdapter adapter;
    private RecyclerView grid;
    private FrameLayout root;
    private GlassView glass;
    private View ambient;
    private TextView heroName;
    private View dockDot;
    // macOS dock magnification: focused icon and its neighbours grow from their bottom edge and lift.
    private static final float DOCK_MAGNIFY = 1.30f;
    private static final float DOCK_NEIGHBOUR = 1.10f;
    private static final float DOCK_LIFT_DP = 10f;
    private static final float DOCK_NEIGHBOUR_LIFT_DP = 4f;
    private GradientDrawable ambientGlow;
    private int ambientColor;
    private boolean heroShown;
    private TextView greeting;
    private ImageView bgPhoto;
    private WallpaperStore wallpapers;
    /** What the background shows now: "res:<id>" (time of day) or "file:<name>" (a downloaded wallpaper). */
    private String bgKey = "";
    private static final String PREFS = "tvhome";   // the same file the dock saves into (AppAdapter)
    private static final String PREF_WALLPAPER = "wallpaper";   // "auto", or the file name of a downloaded wallpaper
    private static final String PREF_THEME = "theme";           // Theme.key()
    private static final int ANDROID_COLUMNS = 5;               // banner cards per row
    private static final int APPLE_COLUMNS = 6;                 // large icons per row
    private Theme theme = Theme.DOCK;
    private DockDecoration dockDecoration;
    private TextView btnWifi;
    private float glassTargetX, glassTargetY;
    private boolean glassShown;
    private int lastFocused = 0;
    private float density;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final int COLOR_WARN = 0xFFFFB340;   // connected, no internet
    private static final int COLOR_ERROR = 0xFFFF5A52;  // no network
    private static final int[] WIFI_ICONS = {R.drawable.ic_wifi_1, R.drawable.ic_wifi_2, R.drawable.ic_wifi};
    // Refresh the Wi-Fi pill as soon as the connection changes, not on the next minute tick.
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(@NonNull Network n) { handler.post(MainActivity.this::updateWifiLabel); }
        @Override public void onLost(@NonNull Network n) { handler.post(MainActivity.this::updateWifiLabel); }
        @Override public void onCapabilitiesChanged(@NonNull Network n, @NonNull NetworkCapabilities c) {
            handler.post(MainActivity.this::updateWifiLabel);
        }
        @Override public void onLinkPropertiesChanged(@NonNull Network n, @NonNull LinkProperties p) {
            handler.post(MainActivity.this::updateWifiLabel);
        }
    };
    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            updateGreeting();
            updateBackground();
            updateWifiLabel();
            handler.postDelayed(this, MINUTE_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        density = getResources().getDisplayMetrics().density;
        COLUMNS = Math.max(1, getResources().getInteger(R.integer.columns));
        DOCK_MAX = Math.min(12, Math.max(COLUMNS, getResources().getInteger(R.integer.dock_max)));
        DOCK_SLOTS = Math.min(30, Math.max(DOCK_MAX, getResources().getInteger(R.integer.dock_slots)));
        liveTvPkg = getString(R.string.live_tv_package).trim();
        wallpapers = new WallpaperStore(this);

        root = findViewById(R.id.root);
        glass = findViewById(R.id.glass);
        ambient = findViewById(R.id.ambient);
        heroName = findViewById(R.id.heroName);
        dockDot = findViewById(R.id.dockDot);
        ambientGlow = new GradientDrawable();
        ambientGlow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        ambientGlow.setGradientCenter(0.32f, 0.42f);
        ambient.setBackground(ambientGlow);
        ambient.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) ->
                ambientGlow.setGradientRadius((r - l) * 0.62f));
        greeting = findViewById(R.id.greeting);
        bgPhoto = findViewById(R.id.bgPhoto);
        btnWifi = findViewById(R.id.btnWifi);

        grid = findViewById(R.id.grid);
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);

        adapter = new AppAdapter(this, this, DOCK_MAX);
        dockDecoration = new DockDecoration(adapter, DOCK_SLOTS, density);
        theme = Theme.from(getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_THEME, Theme.DOCK.key()));
        adapter.setTheme(theme);
        grid.setAdapter(adapter);
        configureGrid();

        // Keep the grid's padding right for the theme (in DOCK it also pushes the dock row to the bottom).
        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> applyGridPadding());

        // The top bar has two buttons: the connection (Wi-Fi, Ethernet or offline, as it is right now) and Settings,
        // which opens the launcher's own panel (theme, wallpaper) with the Android settings inside it.
        View btnSettings = findViewById(R.id.btnSettings);
        btnWifi.setOnClickListener(v -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        btnSettings.setOnClickListener(v -> showConfigSidebar());

        // Keep focus on the top bar at its ends and at the top: Android's focus finder would otherwise
        // jump to a tile far below when there is nothing in that direction.
        View.OnKeyListener pillKeys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && v.getId() == R.id.btnWifi) return true;
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.getId() == R.id.btnSettings;
        };
        btnWifi.setOnKeyListener(pillKeys);
        btnSettings.setOnKeyListener(pillKeys);

        // Hide the glass when focus leaves the tiles.
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> {
            boolean onTile = newF != null && newF.getId() == R.id.card;
            if (!onTile) {
                hideGlass();
                hideHero();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        glassShown = false;
        glass.setAlpha(0f);
        heroShown = false;
        heroName.setAlpha(0f);
        dockDot.setAlpha(0f);
        ambient.setAlpha(0f);
        adapter.reload();
        updateGreeting();
        updateBackground();
        updateWifiLabel();
        handler.postDelayed(minuteTick, MINUTE_MS);
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.registerDefaultNetworkCallback(networkCallback);

        // Return to the tile we left from, or the first one.
        grid.post(() -> {
            View focused = getCurrentFocus();
            if (focused != null && grid.findContainingViewHolder(focused) != null) return;
            focusTile(lastFocused);
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(minuteTick);
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.unregisterNetworkCallback(networkCallback);
    }

    @Override
    public void onBackPressed() {
        // Home screen: Back goes nowhere.
    }

    // ---- themes and the grid ---------------------------------------------------------------------

    /** Switches the layout (DOCK, ANDROID or APPLE), remembers it, and starts again from the first app. */
    private void applyTheme(Theme t) {
        theme = t;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_THEME, t.key()).apply();
        movingPkg = null;
        hideHero();
        hideGlass();
        adapter.setTheme(t);
        configureGrid();
        lastFocused = 0;
        grid.scrollToPosition(0);
        grid.post(() -> focusTile(0));
    }

    /** Layout manager, spans and decoration for the current theme. */
    private void configureGrid() {
        if (dockDecoration != null) grid.removeItemDecoration(dockDecoration);
        GridLayoutManager glm;
        if (theme == Theme.DOCK) {
            // The row is 2 * DOCK_SLOTS units wide (half-icon units, so the dock stays exactly centred for any number
            // of icons). Each dock icon takes 2 units; the first and last also take the empty units on their side
            // (the decoration pushes the icon back into its own 2 units), which centres the dock and fills the row so
            // the cards start on the next one. Banner cards are COLUMNS per row.
            final int spans = lcm(COLUMNS, 2 * DOCK_SLOTS);
            glm = new AppGridLayoutManager(this, spans);
            glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    if (!adapter.isDocked(position)) return spans / COLUMNS;
                    int n = adapter.dockCount();
                    int pad = dockLead(n);   // empty units on each side
                    return spans / (2 * DOCK_SLOTS) * (2 + (position == 0 || position == n - 1 ? pad : 0));
                }
            });
            grid.addItemDecoration(dockDecoration);
        } else {
            glm = new AppGridLayoutManager(this, theme == Theme.ANDROID ? ANDROID_COLUMNS : APPLE_COLUMNS);
        }
        grid.setLayoutManager(glm);
        applyGridPadding();
    }

    /**
     * Side margins per theme, and the top padding: in DOCK it pushes the dock row to the bottom of the screen (the
     * cards below hide until you press Down); in the other themes the grid simply starts under the top bar.
     */
    private void applyGridPadding() {
        int side = (int) ((theme == Theme.DOCK ? 264 : 0) * density);   // the screen already has a 56dp margin
        int bottom = theme == Theme.DOCK ? (int) (40 * density) : (int) (32 * density);
        int top;
        if (theme == Theme.DOCK) {
            top = Math.max(0, grid.getHeight() - (int) ((101 + 6 + 12) * density) + (int) (8 * density));
        } else {
            // Android and Apple hang from the bottom of the screen: two rows fit above the bottom margin and the
            // wallpaper shows above them. More rows scroll in as you go down.
            int rowDp = theme == Theme.ANDROID ? 137 : 144;   // card + the item's padding / the icon and its name
            top = Math.max((int) (12 * density), grid.getHeight() - (int) (2 * rowDp * density) - bottom);
        }
        if (grid.getPaddingTop() != top || grid.getPaddingLeft() != side || grid.getPaddingBottom() != bottom) {
            grid.setPadding(side, top, side, bottom);
        }
    }

    @Override
    public void onTileFocused(int position, View card) {
        lastFocused = position;
        if (adapter.isDocked(position)) {
            onDockFocused(position, card);
            return;
        }
        magnifyDock(-1);
        hideHero();
        hideGlass();
        // The focused row always rests with its bottom edge on the grid's bottom margin, at once (no animated scroll).
        // The same rule for every card of a row means moving sideways never moves the grid, and going back to a row
        // puts it where it was. The grid does no scrolling of its own (see AppGridLayoutManager), so this is the only
        // scroll; the list clamps it at both ends (so the first rows of ANDROID and APPLE stay hanging from the bottom).
        View item = (View) card.getParent();
        grid.post(() -> {
            if (!item.isAttachedToWindow() || lastFocused != position) return;
            int dy = item.getBottom() - (grid.getHeight() - grid.getPaddingBottom());
            if (dy != 0) grid.scrollBy(0, dy);
        });
    }

    // ---- top shelf -------------------------------------------------------------------------------

    private void onDockFocused(int position, View card) {
        hideGlass();
        magnifyDock(position);
        // Bring the dock row back to the bottom of the screen at once. Done after the layout pass so the grid's top
        // padding is final (at launch it is still being set), then the name tooltip goes above the icon.
        grid.post(() -> {
            View item = (View) card.getParent();
            if (item == null || !item.isAttachedToWindow() || lastFocused != position) return;
            int dy = item.getTop() - (int) (6 * density) - grid.getPaddingTop();
            if (Math.abs(dy) > 1) grid.scrollBy(0, dy);
            Rect r = new Rect(0, 0, card.getWidth(), card.getHeight());
            root.offsetDescendantRectToMyCoords(card, r);
            showHero(position, r);
        });
    }

    /** Scale dock icons by distance from the focused one; focused < 0 resets them all. */
    private void magnifyDock(int focused) {
        RecyclerView.LayoutManager lm = grid.getLayoutManager();
        if (lm == null) return;
        for (int i = 0; i < adapter.dockCount(); i++) {
            View item = lm.findViewByPosition(i);
            View card = item != null ? item.findViewById(R.id.card) : null;
            if (card == null) continue;
            int d = focused < 0 ? Integer.MAX_VALUE : Math.abs(i - focused);
            float s = d == 0 ? DOCK_MAGNIFY : d == 1 ? DOCK_NEIGHBOUR : 1f;
            float lift = d == 0 ? DOCK_LIFT_DP : d == 1 ? DOCK_NEIGHBOUR_LIFT_DP : 0f;
            card.setPivotX(card.getWidth() / 2f);
            card.setPivotY(card.getHeight());
            card.animate().scaleX(s).scaleY(s).translationY(-lift * density)
                    .setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
            // The shadow grows from the same point (the icon's bottom centre) so it stays under the icon.
            View shadow = item.findViewById(R.id.shadow);
            shadow.setPivotX(shadow.getWidth() / 2f);
            shadow.setPivotY(card.getBottom() - shadow.getTop());
            shadow.animate().scaleX(s).scaleY(s).translationY(-lift * density)
                    .setDuration(220).setInterpolator(new DecelerateInterpolator()).start();
        }
    }

    // Name tooltip above the magnified icon, dot below it, and a soft glow in the app's colour.
    private void showHero(int position, Rect card) {
        heroName.setText(adapter.labelAt(position));
        heroName.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        float iconTop = card.bottom - card.height() * DOCK_MAGNIFY - DOCK_LIFT_DP * density;
        heroName.setX(card.exactCenterX() - heroName.getMeasuredWidth() / 2f);
        heroName.setY(iconTop - 8 * density - heroName.getMeasuredHeight());
        dockDot.setX(card.exactCenterX() - dockDot.getWidth() / 2f);
        dockDot.setY(card.bottom + 3 * density);
        heroName.animate().alpha(1f).setDuration(160).start();
        dockDot.animate().alpha(1f).setDuration(160).start();
        int color = adapter.ambientColorAt(position);
        if (!heroShown) {
            setAmbient(color);
            ambient.animate().alpha(1f).setDuration(600).start();
            heroShown = true;
        } else if (color != ambientColor) {
            ambient.animate().alpha(0.35f).setDuration(200).withEndAction(() -> {
                setAmbient(color);
                ambient.animate().alpha(1f).setDuration(500).start();
            }).start();
        }
    }

    private void setAmbient(int color) {
        ambientColor = color;
        int c = color & 0x00FFFFFF;
        // Kept faint so the photo keeps its own colours (was 36% / 15%).
        ambientGlow.setColors(new int[]{0x26000000 | c, 0x0D000000 | c, 0x00000000 | c});
    }

    private void hideHero() {
        if (!heroShown) return;
        heroShown = false;
        heroName.animate().alpha(0f).setDuration(160).start();
        dockDot.animate().alpha(0f).setDuration(160).start();
        ambient.animate().alpha(0f).setDuration(300).start();
        magnifyDock(-1);
    }

    private void hideGlass() {
        if (!glassShown) return;
        glassShown = false;
        glass.setAlpha(0f);
    }

    // ---- navigation ------------------------------------------------------------------------------

    @Override
    public boolean onTileVerticalKey(int position, int keyCode) {
        int count = adapter.getItemCount();
        int dock = adapter.dockCount();
        int target;
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (position < dock) return false;             // dock row: let focus move to the top bar
            int col = (position - dock) % COLUMNS;
            if (position - dock < COLUMNS) {
                if (dock == 0) return false;
                // first grid row: up to the dock icon nearest above this column (the dock is centred and narrower)
                int nearest = Math.round(((col + 0.5f) * 2 * DOCK_SLOTS / COLUMNS - dockLead(dock) - 1f) / 2f);
                target = Math.max(0, Math.min(dock - 1, nearest));
            } else {
                target = position - COLUMNS;
            }
        } else {
            if (position < dock) {
                if (count == dock) return true;            // nothing below the dock
                // down from a dock icon: the grid column under it
                int col = (int) ((dockLead(dock) + 2 * position + 1f) * COLUMNS / (2 * DOCK_SLOTS));
                target = Math.min(dock + Math.min(col, COLUMNS - 1), count - 1);
            } else {
                int lastRowStart = dock + ((count - 1 - dock) / COLUMNS) * COLUMNS;
                if (position >= lastRowStart) return true; // already on the last row: stay put
                target = Math.min(position + COLUMNS, count - 1);
            }
        }
        focusTile(target);
        return true;
    }

    private static int lcm(int x, int y) {
        int a = x, b = y;
        while (b != 0) { int t = a % b; a = b; b = t; }
        return x / a * y;
    }

    /** Empty half-icon units on each side of a dock of n icons, so the dock sits centred in DOCK_SLOTS icon cells. */
    static int dockLead(int n) {
        return Math.max(0, DOCK_SLOTS - n);
    }

    // ---- dock editing ----------------------------------------------------------------------------

    @Override
    public void onTileMenu(int position, View card) {
        String pkg = adapter.pkgAt(position);
        boolean docked = adapter.isDocked(position);
        boolean dockTheme = theme == Theme.DOCK;
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        int width = (int) (220 * density);
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);

        // Only dock apps can be reordered (that is what pinning is for); the other apps stay alphabetical.
        if (docked) addInputRow(rows, popup, R.drawable.ic_move, getString(R.string.move), -1, () -> startMove(pkg));
        if (adapter.isPinned(pkg)) {
            addInputRow(rows, popup, R.drawable.ic_unpin,
                    getString(dockTheme ? R.string.unpin_from_dock : R.string.unpin_from_front), -1, () -> {
                adapter.unpin(pkg);
                focusAfterLayout(pkg);
            });
        } else {
            addInputRow(rows, popup, R.drawable.ic_pin,
                    getString(dockTheme ? R.string.pin_to_dock : R.string.pin_to_front), -1, () -> {
                if (adapter.pin(pkg)) focusAfterLayout(pkg);
                else Toast.makeText(this, R.string.dock_full, Toast.LENGTH_SHORT).show();
            });
        }
        addInputRow(rows, popup, R.drawable.ic_info, getString(R.string.app_info), -1, () ->
                open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg))));

        // Float the menu just above the tile, centred on it; the name tooltip steps aside while it's open.
        Rect r = new Rect(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);
        panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        float top = docked ? r.bottom - r.height() * DOCK_MAGNIFY - DOCK_LIFT_DP * density : r.top;
        int x = (int) (r.exactCenterX() - width / 2f);
        int y = (int) (top - 12 * density - panel.getMeasuredHeight());
        if (y < 0) y = (int) (r.bottom + 12 * density);   // no room above (top row): open below the tile
        heroName.animate().alpha(0f).setDuration(120).start();
        popup.setOnDismissListener(() -> {
            if (adapter.isDocked(lastFocused) && heroShown) heroName.animate().alpha(1f).setDuration(160).start();
        });
        if (docked) {   // the dock keeps its soft rise-in; menus over the grid just appear
            panel.setAlpha(0f);
            panel.setTranslationY(8 * density);
        }
        popup.showAtLocation(root, Gravity.NO_GRAVITY, x, y);
        if (docked) {
            panel.animate().alpha(1f).translationY(0f).setDuration(200)
                    .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        }
        rows.getChildAt(0).requestFocus();
    }

    // ---- moving apps -----------------------------------------------------------------------------
    // Move is only for dock apps: from the app's menu (hold OK), Left/Right slide it along the dock; OK or Back finishes.

    private String movingPkg;

    private void startMove(String pkg) {
        movingPkg = pkg;
        Toast.makeText(this, R.string.move_hint, Toast.LENGTH_LONG).show();
        // Closing the menu hands focus to the first view on screen; give it back to the app being moved.
        grid.postDelayed(() -> {
            int pos = adapter.positionOf(pkg);
            RecyclerView.ViewHolder vh = pos >= 0 ? grid.findViewHolderForAdapterPosition(pos) : null;
            if (vh != null) vh.itemView.findViewById(R.id.card).requestFocus();
        }, 150);
    }

    @Override
    public boolean isMoving() {
        return movingPkg != null;
    }

    @Override
    public boolean onMoveKey(int position, int keyCode) {
        String pkg = movingPkg;
        if (pkg == null) return false;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK:
                movingPkg = null;
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (adapter.isDocked(position)) {
                    adapter.move(pkg, keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                    focusAfterLayout(pkg);
                }
                return true;
            default:
                return true;   // swallow everything else while moving
        }
    }

    // ---- config sidebar --------------------------------------------------------------------------
    // The Customize button opens a panel on the right edge: the theme and the wallpaper.

    private void showConfigSidebar() {
        final Dialog dialog = new Dialog(this, R.style.Sidebar);
        View panel = getLayoutInflater().inflate(R.layout.sidebar_config, null);
        LinearLayout rows = panel.findViewById(R.id.rows);

        addConfigHeader(rows, getString(R.string.theme_header));
        addConfigRow(rows, dialog, 0, getString(R.string.theme_dock), theme == Theme.DOCK, true,
                () -> applyTheme(Theme.DOCK));
        addConfigRow(rows, dialog, 0, getString(R.string.theme_android), theme == Theme.ANDROID, true,
                () -> applyTheme(Theme.ANDROID));
        addConfigRow(rows, dialog, 0, getString(R.string.theme_apple), theme == Theme.APPLE, true,
                () -> applyTheme(Theme.APPLE));

        addConfigHeader(rows, getString(R.string.wallpaper_header));
        addConfigRow(rows, dialog, R.drawable.ic_pin, getString(R.string.wallpaper_new), false, true, this::fetchWallpaper);
        if (!wallpapers.files().isEmpty()) {   // stepping through stays open so you can browse
            addConfigRow(rows, dialog, R.drawable.ic_right, getString(R.string.wallpaper_next), false, false,
                    () -> stepWallpaper(1));
            addConfigRow(rows, dialog, R.drawable.ic_left, getString(R.string.wallpaper_prev), false, false,
                    () -> stepWallpaper(-1));
        }
        if (!"auto".equals(wallpaperChoice())) {
            addConfigRow(rows, dialog, R.drawable.ic_unpin, getString(R.string.wallpaper_remove), false, true,
                    this::removeWallpaper);
            addConfigRow(rows, dialog, R.drawable.ic_settings, getString(R.string.wallpaper_auto), false, true,
                    () -> setWallpaperChoice("auto"));
        }

        // The Android side: the system settings and the inputs, so nothing is lost from the old top bar.
        addConfigHeader(rows, getString(R.string.system_header));
        addSystemRow(rows, dialog, R.drawable.ic_settings, R.string.system_settings, new Intent(Settings.ACTION_SETTINGS));
        addSystemRow(rows, dialog, R.drawable.ic_wifi, R.string.system_network, new Intent(Settings.ACTION_WIFI_SETTINGS));
        addSystemRow(rows, dialog, R.drawable.ic_av, R.string.system_display, new Intent(Settings.ACTION_DISPLAY_SETTINGS));
        addSystemRow(rows, dialog, R.drawable.ic_av, R.string.system_sound, new Intent(Settings.ACTION_SOUND_SETTINGS));
        if (hasInputs()) {   // boxes without HDMI/AV inputs or a tuner (like a streaming stick) don't get the row
            addConfigRow(rows, dialog, R.drawable.ic_input, getString(R.string.inputs), false, true, this::showInputs);
        }

        dialog.setContentView(panel);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setLayout((int) (380 * density), ViewGroup.LayoutParams.MATCH_PARENT);
            w.setGravity(Gravity.END);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.45f);
        }
        dialog.show();
        View selected = rows.getChildAt(1 + theme.ordinal());   // child 0 is the THEME header
        if (selected != null) selected.requestFocus();
    }

    private boolean hasInputs() {
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) {
            for (TvInputInfo info : tim.getTvInputList()) if (info.isPassthroughInput()) return true;
        }
        return !liveTvPkg.isEmpty() && getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg) != null;
    }

    /** A row that opens a system settings screen; skipped when this box has no screen for it. */
    private void addSystemRow(LinearLayout rows, Dialog dialog, int iconRes, int titleRes, Intent intent) {
        if (getPackageManager().resolveActivity(intent, 0) == null) return;
        addConfigRow(rows, dialog, iconRes, getString(titleRes), false, true, () -> open(intent));
    }

    private void addConfigHeader(LinearLayout rows, String text) {
        TextView header = new TextView(this);
        header.setText(text);
        header.setTextColor(0x99FFFFFF);
        header.setTextSize(11f);
        header.setLetterSpacing(0.14f);
        header.setTypeface(getResources().getFont(R.font.open_sans));
        header.setPadding((int) (12 * density), (int) (rows.getChildCount() == 0 ? 6 * density : 20 * density),
                0, (int) (6 * density));
        rows.addView(header);
    }

    /** A row in the sidebar; a dot marks the selected one. iconRes 0 means no icon. */
    private void addConfigRow(LinearLayout rows, Dialog dialog, int iconRes, String title, boolean selected,
                              boolean closeAfter, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ImageView icon = row.findViewById(R.id.icon);
        if (iconRes == 0) icon.setVisibility(View.GONE);
        else icon.setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        row.findViewById(R.id.status).setVisibility(View.GONE);
        row.findViewById(R.id.dot).setVisibility(selected ? View.VISIBLE : View.GONE);
        row.setOnClickListener(v -> {
            if (closeAfter) dialog.dismiss();
            action.run();
        });
        rows.addView(row);
    }

    /** After the dock changes, focus the app's new tile once the grid has re-laid out. */
    private void focusAfterLayout(String pkg) {
        hideHero();
        ((GridLayoutManager) grid.getLayoutManager()).getSpanSizeLookup().invalidateSpanIndexCache();
        grid.invalidateItemDecorations();
        grid.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                grid.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                int pos = adapter.positionOf(pkg);
                RecyclerView.ViewHolder vh = pos >= 0 ? grid.findViewHolderForAdapterPosition(pos) : null;
                if (vh == null) {
                    focusTile(Math.max(pos, 0));
                    return;
                }
                View c = vh.itemView.findViewById(R.id.card);
                c.requestFocus();
                onTileFocused(pos, c);
            }
        });
    }

    private void focusTile(int position) {
        if (adapter.getItemCount() == 0) return;
        final int pos = Math.max(0, Math.min(position, adapter.getItemCount() - 1));
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh != null) {
            vh.itemView.findViewById(R.id.card).requestFocus();
            return;
        }
        // Not laid out yet: jump there (no animated scroll), then focus once the grid has laid out.
        grid.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                grid.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                RecyclerView.ViewHolder h = grid.findViewHolderForAdapterPosition(pos);
                if (h != null) h.itemView.findViewById(R.id.card).requestFocus();   // never guess another tile
            }
        });
        grid.scrollToPosition(pos);
    }

    // ---- top bar ---------------------------------------------------------------------------------

    // Morning 5-12, afternoon 12-17, evening 17-22, night 22-5. Indexes GREETINGS and BACKGROUNDS.
    private static final int[] GREETINGS = {
            R.string.good_morning, R.string.good_afternoon, R.string.good_evening, R.string.good_night};
    private static final int[] BACKGROUNDS = {
            R.drawable.bg_morning, R.drawable.bg_afternoon, R.drawable.bg_evening, R.drawable.bg_night};

    private static int dayPart() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 5) return 3;
        if (hour < 12) return 0;
        if (hour < 17) return 1;
        if (hour < 22) return 2;
        return 3;
    }

    private void updateGreeting() {
        String text = getString(GREETINGS[dayPart()]);
        String name = getString(R.string.user_name).trim();
        greeting.setText(name.isEmpty() ? text : text + ", " + name);
    }

    private String wallpaperChoice() {
        return getSharedPreferences(PREFS, MODE_PRIVATE).getString(PREF_WALLPAPER, "auto");
    }

    private void setWallpaperChoice(String choice) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(PREF_WALLPAPER, choice).apply();
        updateBackground();
    }

    /** Shows the chosen downloaded wallpaper, or the time-of-day photo when the choice is "auto" (or the file is gone). */
    private void updateBackground() {
        String choice = wallpaperChoice();
        File file = "auto".equals(choice) ? null : new File(wallpapers.dir(), choice);
        String key = file != null && file.exists() ? "file:" + choice : "res:" + BACKGROUNDS[dayPart()];
        if (key.equals(bgKey)) return;   // already showing it: don't decode the photo again
        Drawable next = null;
        if (key.startsWith("file:")) {
            Bitmap bm = BitmapFactory.decodeFile(file.getPath());
            if (bm != null) {
                next = new BitmapDrawable(getResources(), bm);
            } else {   // unreadable file: fall back to the time-of-day photo
                key = "res:" + BACKGROUNDS[dayPart()];
                if (key.equals(bgKey)) return;
            }
        }
        if (next == null) next = getDrawable(BACKGROUNDS[dayPart()]);
        boolean first = bgKey.isEmpty();
        bgKey = key;
        final Drawable shown = next;
        if (first) {
            bgPhoto.setImageDrawable(shown);
            return;
        }
        // Fade through black so the swap isn't a jump cut.
        bgPhoto.animate().alpha(0f).setDuration(600).withEndAction(() -> {
            bgPhoto.setImageDrawable(shown);
            bgPhoto.animate().alpha(1f).setDuration(900);
        });
    }

    // ---- wallpapers (Menu, or hold OK, on the Settings button) -----------------------------------

    private void fetchWallpaper() {
        Toast.makeText(this, R.string.wallpaper_searching, Toast.LENGTH_SHORT).show();
        wallpapers.fetchNew((file, message) -> {
            if (file != null) setWallpaperChoice(file.getName());
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

    /** Cycles automatic -> each downloaded wallpaper -> automatic. */
    private void stepWallpaper(int delta) {
        List<String> order = new ArrayList<>();
        order.add("auto");
        for (File f : wallpapers.files()) order.add(f.getName());
        int next = Math.floorMod(Math.max(0, order.indexOf(wallpaperChoice())) + delta, order.size());
        setWallpaperChoice(order.get(next));
        Toast.makeText(this, next == 0 ? getString(R.string.wallpaper_auto_toast)
                : wallpapers.credit(new File(wallpapers.dir(), order.get(next))), Toast.LENGTH_LONG).show();
    }

    private void removeWallpaper() {
        wallpapers.delete(new File(wallpapers.dir(), wallpaperChoice()));
        setWallpaperChoice("auto");
    }

    /** Shows the Wi-Fi network name on the button when we can read it, otherwise the connection type. */
    // Signal bars only (no network name, so screenshots stay private); text only when something is wrong.
    private void updateWifiLabel() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        Network net = cm != null ? cm.getActiveNetwork() : null;
        NetworkCapabilities caps = net != null ? cm.getNetworkCapabilities(net) : null;
        if (caps == null) {
            setWifiPill(R.drawable.ic_wifi_off, getString(R.string.offline), COLOR_ERROR);
            return;
        }
        boolean online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            setWifiPill(R.drawable.ic_ethernet, online ? getString(R.string.ethernet) : getString(R.string.no_internet),
                    online ? 0 : COLOR_WARN);
            return;
        }
        WifiManager wm = getApplicationContext().getSystemService(WifiManager.class);
        WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
        int bars = info != null ? WifiManager.calculateSignalLevel(info.getRssi(), WIFI_ICONS.length) : 0;
        if (!online) {
            setWifiPill(WIFI_ICONS[bars], getString(R.string.no_internet), COLOR_WARN);
            return;
        }
        setWifiPill(WIFI_ICONS[bars], getString(R.string.wifi), 0);
    }

    private void setWifiPill(int icon, String label, int alertColor) {
        btnWifi.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
        btnWifi.setContentDescription(label);
        // Healthy: a round icon button like Inputs and Settings. Alert: a pill with the status text.
        boolean iconOnly = alertColor == 0;
        ViewGroup.LayoutParams lp = btnWifi.getLayoutParams();
        lp.width = iconOnly ? (int) (42 * density) : ViewGroup.LayoutParams.WRAP_CONTENT;
        btnWifi.setLayoutParams(lp);
        btnWifi.setPaddingRelative((int) ((iconOnly ? 10 : 16) * density), 0, (int) ((iconOnly ? 10 : 20) * density), 0);
        btnWifi.setCompoundDrawablePadding(iconOnly ? 0 : (int) (9 * density));
        if (iconOnly) {
            btnWifi.setText("");
            return;
        }
        SpannableString text = new SpannableString("\u25CF  " + label);
        text.setSpan(new ForegroundColorSpan(alertColor), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        btnWifi.setText(text);
    }

    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Not available on this TV", Toast.LENGTH_SHORT).show();
        }
    }

    /** Dropdown menu under the Inputs pill: HDMI ports (with connection state), AV and Live TV. */
    private void showInputs() {
        View anchor = findViewById(R.id.btnSettings);
        View panel = getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);

        // Compact, hanging right below the Settings button with its right edge lined up to it.
        int width = (int) (170 * density);
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);

        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        boolean sawNonHdmi = false;
        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
            // HDMI ports first in port order, then AV and the rest.
            Collections.sort(inputs, (a, b) -> {
                boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
                if (ha != hb) return ha ? -1 : 1;
                return String.valueOf(a.loadLabel(this)).compareToIgnoreCase(String.valueOf(b.loadLabel(this)));
            });
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput()) continue;
                CharSequence l = info.loadLabel(this);
                String label = l == null ? info.getId() : l.toString();
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                if (hdmi && !label.toUpperCase().contains("HDMI")) label = "HDMI " + label;
                final String id = info.getId();
                int state = tim.getInputState(id);
                if (!hdmi && rows.getChildCount() > 0 && !sawNonHdmi) addSeparator(rows);
                if (!hdmi) sawNonHdmi = true;
                addInputRow(rows, popup, hdmi ? R.drawable.ic_input : R.drawable.ic_av, label, state,
                        () -> open(new Intent(Intent.ACTION_VIEW).setDataAndType(
                                TvContract.buildChannelUriForPassthroughInput(id), "vnd.android.cursor.item/channel")));
            }
        }
        Intent liveTv = liveTvPkg.isEmpty() ? null : getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
        if (liveTv != null) {
            if (rows.getChildCount() > 0) addSeparator(rows);
            addInputRow(rows, popup, R.drawable.ic_livetv, getString(R.string.live_tv), -1, () -> open(liveTv));
        }
        if (rows.getChildCount() == 0) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show();
            return;
        }

        // Drop in: fade and unfold from the top edge.
        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        popup.showAsDropDown(anchor, anchor.getWidth() - width, (int) (10 * density), Gravity.START);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
        rows.getChildAt(0).requestFocus();
    }

    private void addSeparator(LinearLayout rows) {
        View line = new View(this);
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) density);
        int m = (int) (6 * density), h = (int) (12 * density);
        lp.setMargins(h, m, h, m);
        rows.addView(line, lp);
    }

    private void addInputRow(LinearLayout rows, PopupWindow popup, int iconRes, String title, int state, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        // This TV reports "standby" for every port whether or not something is plugged in, so only
        // definite states are shown.
        if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            status.setText(R.string.connected);
            dot.setVisibility(View.VISIBLE);
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status.setText(R.string.not_connected);
            dot.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.GONE);
            dot.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        rows.addView(row);
    }
}
