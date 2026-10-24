package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.ContextWrapper;
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
import android.view.LayoutInflater;
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
import androidx.recyclerview.widget.LinearLayoutManager;
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
final class ClassicHome extends ContextWrapper implements ClassicAdapter.TileListener, RecoAdapter.Listener,
        ConfigSidebar.Host {

    /**
     * The Dock and Circles themes, kept as they were before the launcher moved to upstream's base. MainActivity (the
     * only Home) hands its life cycle to this class whenever the saved theme is not Tiles; a ContextWrapper around the
     * activity so the code written for an Activity keeps working.
     */
    private final MainActivity host;

    ClassicHome(MainActivity host) {
        super(host);
        this.host = host;
    }

    private <T extends View> T findViewById(int id) {
        return host.findViewById(id);
    }

    private android.view.Window getWindow() {
        return host.getWindow();
    }

    private LayoutInflater getLayoutInflater() {
        return host.getLayoutInflater();
    }

    private View getCurrentFocus() {
        return host.getCurrentFocus();
    }

    private boolean isDestroyed() {
        return host.isDestroyed();
    }

    /** Tiles per row and dock slots (res/values/config.xml, columns). Set in onCreate. */
    static int COLUMNS = 4;
    /** Most apps the dock can hold (res/values/config.xml, dock_max). Set in onCreate. */
    static int DOCK_MAX = 8;
    /** Dock cells across the screen; the dock sits centred in them (res/values/config.xml, dock_slots). */
    static int DOCK_SLOTS = 9;
    private static final long MINUTE_MS = 60_000L;
    private static final int TILES_COLUMNS = ClassicAdapter.TILES_COLUMNS;   // only reachable through the old Tiles layout
    private String liveTvPkg;

    private ClassicAdapter adapter;
    private RecyclerView grid;
    private FrameLayout root;
    private ClassicGlassView glass;
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
    private ClassicTheme theme = ClassicTheme.DOCK;
    private ClassicDockDecoration dockDecoration;
    private ClassicShelfDecoration shelfDecoration;
    private View circlesHero, yourAppsLabel;
    private TextView heroTitle;
    private TextView btnWifi;
    private float glassTargetX, glassTargetY;
    private boolean glassShown;
    private int lastFocused = 0;
    private RecoStore recoStore;
    private RecoAdapter recoAdapter;
    private RecyclerView recoRow;
    private View recoBlock;
    private int recoLast = 0;
    /** Background: upstream's wallpaper engine (the theme's pictures, time of day, rotation and the downloaded ones). */
    private WallpaperPlayer wallpaperPlayer;
    private float density;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private static final int[] WIFI_ICONS = {R.drawable.c_ic_wifi_1, R.drawable.c_ic_wifi_2, R.drawable.c_ic_wifi};
    // Refresh the Wi-Fi pill as soon as the connection changes, not on the next minute tick.
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override public void onAvailable(@NonNull Network n) { handler.post(ClassicHome.this::updateWifiLabel); }
        @Override public void onLost(@NonNull Network n) { handler.post(ClassicHome.this::updateWifiLabel); }
        @Override public void onCapabilitiesChanged(@NonNull Network n, @NonNull NetworkCapabilities c) {
            handler.post(ClassicHome.this::updateWifiLabel);
        }
        @Override public void onLinkPropertiesChanged(@NonNull Network n, @NonNull LinkProperties p) {
            handler.post(ClassicHome.this::updateWifiLabel);
        }
    };
    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            updateGreeting();
            updateWifiLabel();
            handler.postDelayed(this, MINUTE_MS);
        }
    };

    void onCreate() {
        host.setContentView(R.layout.c_activity_main);
        density = getResources().getDisplayMetrics().density;
        COLUMNS = Math.max(1, getResources().getInteger(R.integer.c_columns));
        DOCK_MAX = Math.min(12, Math.max(COLUMNS, getResources().getInteger(R.integer.c_dock_max)));
        DOCK_SLOTS = Math.min(30, Math.max(DOCK_MAX, getResources().getInteger(R.integer.c_dock_slots)));
        liveTvPkg = getString(R.string.live_tv_package).trim();

        root = findViewById(R.id.root);
        wallpaperPlayer = new WallpaperPlayer(this, root, Config.load(this));
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
        btnWifi = findViewById(R.id.btnWifi);
        circlesHero = findViewById(R.id.circlesHero);
        heroTitle = findViewById(R.id.heroTitle);
        yourAppsLabel = findViewById(R.id.yourAppsLabel);

        grid = findViewById(R.id.grid);
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);

        adapter = new ClassicAdapter(this, this, DOCK_MAX);
        dockDecoration = new ClassicDockDecoration(adapter, DOCK_SLOTS, density);
        shelfDecoration = new ClassicShelfDecoration(adapter, density);
        theme = ClassicTheme.from(ConfigSidebar.theme(this));
        adapter.setTheme(theme);
        grid.setAdapter(adapter);
        configureGrid();
        applyChrome();

        recoStore = new RecoStore(this);
        recoBlock = findViewById(R.id.recoBlock);
        recoRow = findViewById(R.id.recoRow);
        recoRow.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recoRow.setItemAnimator(null);
        recoAdapter = new RecoAdapter(recoStore, this);
        recoRow.setAdapter(recoAdapter);

        // Keep the grid's padding right for the theme (in DOCK it also pushes the dock row to the bottom).
        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> applyGridPadding());

        // The top bar has two buttons: the connection (Wi-Fi, Ethernet or offline, as it is right now) and Settings,
        // which opens the launcher's own panel (theme, wallpaper) with the Android settings inside it.
        View btnSettings = findViewById(R.id.btnSettings);
        btnWifi.setOnClickListener(v -> open(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        btnSettings.setOnClickListener(v -> showSidebar(ConfigSidebar.SCREEN_MENU));

        // Keep focus on the top bar at its ends and at the top: Android's focus finder would otherwise
        // jump to a tile far below when there is nothing in that direction.
        View.OnKeyListener pillKeys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && recoShown()) {   // the recommendations sit between the header and the apps
                focusReco();
                return true;
            }
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

    void onResume() {
        wallpaperPlayer.start();
        glassShown = false;
        glass.setAlpha(0f);
        heroShown = false;
        heroName.setAlpha(0f);
        dockDot.setAlpha(0f);
        ambient.setAlpha(0f);
        adapter.reload();
        refreshReco();
        updateGreeting();
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

    void onPause() {
        wallpaperPlayer.stop();
        handler.removeCallbacks(minuteTick);
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) cm.unregisterNetworkCallback(networkCallback);
    }

    void onDestroy() {
        wallpaperPlayer.close();
    }

    void onBackPressed() {
        // Home screen: Back goes nowhere.
    }

    // ---- themes and the grid ---------------------------------------------------------------------


    /** Layout manager, spans and decoration for the current theme. */
    private void configureGrid() {
        if (dockDecoration != null) grid.removeItemDecoration(dockDecoration);
        if (shelfDecoration != null) grid.removeItemDecoration(shelfDecoration);
        GridLayoutManager glm;
        if (theme == ClassicTheme.DOCK) {
            // The row is 2 * DOCK_SLOTS units wide (half-icon units, so the dock stays exactly centred for any number
            // of icons). Each dock icon takes 2 units; the first and last also take the empty units on their side
            // (the decoration pushes the icon back into its own 2 units), which centres the dock and fills the row so
            // the cards start on the next one. Banner cards are COLUMNS per row.
            final int spans = lcm(COLUMNS, 2 * DOCK_SLOTS);
            glm = new ClassicGridLayoutManager(this, spans);
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
        } else if (theme == ClassicTheme.CIRCLES) {
            glm = new ClassicGridLayoutManager(this, 1, GridLayoutManager.HORIZONTAL);   // one row of round icons
        } else {
            // TILES: the first row is the plate with the pinned apps. When fewer are pinned than the plate has room for,
            // the last one takes the empty cells (the decoration pushes its tile back to its own cell).
            glm = new ClassicGridLayoutManager(this, TILES_COLUMNS);
            glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    int n = adapter.shelfCount();
                    return n > 0 && n < TILES_COLUMNS && position == n - 1 ? TILES_COLUMNS - n + 1 : 1;
                }
            });
            grid.addItemDecoration(shelfDecoration);
        }
        grid.setLayoutManager(glm);
        applyGridPadding();
    }

    /** What the CIRCLES theme adds above its row: the focused app's name, big. The header (with the date) never changes. */
    private void applyChrome() {
        boolean circles = theme == ClassicTheme.CIRCLES;
        circlesHero.setVisibility(circles ? View.VISIBLE : View.GONE);
        yourAppsLabel.setVisibility(circles ? View.VISIBLE : View.GONE);
        if (circles) showCirclesHero(-1);
    }

    /** The big title over the wallpaper: the focused app, or a greeting when nothing is focused. */
    private void showCirclesHero(int position) {
        String name = position >= 0 ? adapter.labelAt(position) : "";
        heroTitle.setText(name.isEmpty() ? getString(GREETINGS[dayPart()]) : name);
    }

    /**
     * Side margins per theme, and the top padding: in DOCK it pushes the dock row to the bottom of the screen (the
     * cards below hide until you press Down); in CIRCLES and TILES the grid hangs from the bottom so the wallpaper shows
     * above it. The header (greeting, clock, date, connection, settings) is the same in every theme.
     */
    private void applyGridPadding() {
        int side = (int) ((theme == ClassicTheme.DOCK ? 264 : theme == ClassicTheme.TILES ? 20 : 0) * density);   // the screen already has a 56dp margin
        int bottom = (int) ((theme == ClassicTheme.DOCK ? 40 : theme == ClassicTheme.TILES ? 24 : 36) * density);
        int top;
        if (theme == ClassicTheme.DOCK) {
            top = Math.max(0, grid.getHeight() - (int) ((101 + 6 + 12) * density) + (int) (8 * density));
        } else if (theme == ClassicTheme.CIRCLES) {
            top = 0;   // the grid itself is only as tall as the row and sits at the bottom (see below)
        } else {
            // tvOS: the first row (inside the shelf) sits low, the second one peeks in below it.
            top = Math.max((int) (12 * density), grid.getHeight() - (int) ((175 + 44) * density));   // the whole plate, the next row just peeking in
        }
        if (grid.getPaddingTop() != top || grid.getPaddingLeft() != side || grid.getPaddingBottom() != bottom) {
            grid.setPadding(side, top, side, bottom);
        }
        // CIRCLES: a one-row horizontal grid is laid out from its own top, so rather than a huge top padding the grid
        // is made exactly as tall as the row and anchored to the bottom of its parent. The other themes fill the parent.
        FrameLayout.LayoutParams glp = (FrameLayout.LayoutParams) grid.getLayoutParams();
        int wantHeight = theme == ClassicTheme.CIRCLES ? (int) (104 * density) + bottom : ViewGroup.LayoutParams.MATCH_PARENT;
        if (glp.height != wantHeight || glp.gravity != Gravity.BOTTOM) {
            glp.height = wantHeight;
            glp.gravity = Gravity.BOTTOM;
            grid.setLayoutParams(glp);
        }
    }

    @Override
    public void onTileFocused(int position, View card) {
        lastFocused = position;
        updateRecoVisibility();
        if (adapter.isDocked(position)) {
            onDockFocused(position, card);
            return;
        }
        magnifyDock(-1);
        hideHero();
        hideGlass();
        if (theme == ClassicTheme.CIRCLES) showCirclesHero(position);
        // Scrolling is done here, at once (no animation), and only here: the grid does no scrolling of its own (see
        // ClassicGridLayoutManager). The rule is the same for every card of a row, so moving sideways never moves the grid
        // and going back to a row puts it where it was. The list clamps the result at both ends.
        View item = (View) card.getParent();
        grid.post(() -> {
            if (!item.isAttachedToWindow() || lastFocused != position) return;
            if (theme == ClassicTheme.CIRCLES) {   // the single row scrolls sideways: keep the focused icon in view
                int left = grid.getPaddingLeft(), right = grid.getWidth() - grid.getPaddingRight();
                int dx = item.getRight() > right ? item.getRight() - right : item.getLeft() < left ? item.getLeft() - left : 0;
                if (dx != 0) grid.scrollBy(dx, 0);
                return;
            }
            int dy;
            if (theme == ClassicTheme.TILES) {   // both of the first rows are on screen: scroll only for rows further down
                int limit = grid.getHeight() - grid.getPaddingBottom();
                dy = item.getBottom() > limit ? item.getBottom() - limit
                        : item.getTop() < grid.getPaddingTop() ? item.getTop() - grid.getPaddingTop() : 0;
            } else {   // DOCK: the focused row's bottom on the bottom margin
                dy = item.getBottom() - (grid.getHeight() - grid.getPaddingBottom());
            }
            if (dy != 0) grid.scrollBy(0, dy);
            grid.invalidate();   // the TILES plate follows the focused row
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
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && recoShown() && isFirstRow(position)) {
            focusReco();   // Up from the first row goes to the recommendations, and from there to the header
            grid.playSoundEffect(android.view.SoundEffectConstants.NAVIGATION_UP);
            return true;
        }
        if (theme == ClassicTheme.CIRCLES) return keyCode == KeyEvent.KEYCODE_DPAD_DOWN;   // one row: Down stays, Up reaches the header
        if (theme != ClassicTheme.DOCK) return false;   // TILES is a plain grid: Android's focus search does it
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
        // Focus moved by code, so Android does not play its navigation sound: play it here.
        grid.playSoundEffect(keyCode == KeyEvent.KEYCODE_DPAD_UP ? android.view.SoundEffectConstants.NAVIGATION_UP
                : android.view.SoundEffectConstants.NAVIGATION_DOWN);
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
        boolean pinsToFront = theme == ClassicTheme.CIRCLES;   // CIRCLES has no dock: pinning just moves the app to the start
        View panel = getLayoutInflater().inflate(R.layout.c_popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        int width = (int) (220 * density);
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);

        // Only pinned apps can be reordered (that is what pinning is for); the other apps stay alphabetical.
        if (adapter.isPinnedAt(position) || theme == ClassicTheme.TILES || theme == ClassicTheme.CIRCLES) addInputRow(rows, popup, R.drawable.c_ic_move, getString(R.string.c_move), -1, () -> startMove(pkg));
        if (theme == ClassicTheme.CIRCLES) {
            // one long row: no pinning, only Move
        } else if (adapter.isPinned(pkg)) {
            addInputRow(rows, popup, R.drawable.c_ic_unpin,
                    getString(pinsToFront ? R.string.c_unpin_from_front : R.string.c_unpin_from_dock), -1, () -> {
                adapter.unpin(pkg);
                focusAfterLayout(pkg);
            });
        } else {
            addInputRow(rows, popup, R.drawable.c_ic_pin,
                    getString(pinsToFront ? R.string.c_pin_to_front : R.string.c_pin_to_dock), -1, () -> {
                if (adapter.pin(pkg)) focusAfterLayout(pkg);
                else Toast.makeText(this, R.string.c_dock_full, Toast.LENGTH_SHORT).show();
            });
        }
        addInputRow(rows, popup, R.drawable.c_ic_info, getString(R.string.c_app_info), -1, () ->
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
    // Move: from an app's menu (hold OK). Pinned apps slide along the dock / plate / start of the row with Left and Right;
    // in the TILES theme the apps outside the plate also move with all four arrows. OK or Back finishes.

    private String movingPkg;

    private void startMove(String pkg) {
        movingPkg = pkg;
        Toast.makeText(this, R.string.c_move_hint, Toast.LENGTH_LONG).show();
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
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN: {
                boolean sideways = keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
                int sign = keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_UP ? -1 : 1;
                if (adapter.isPinnedAt(position)) {   // pinned apps slide along the dock / plate / start of the row
                    if (!sideways) return true;
                    adapter.move(pkg, sign);
                } else if (theme == ClassicTheme.TILES) {   // the apps outside the plate can go anywhere among themselves
                    adapter.moveInRest(pkg, sideways ? sign : sign * TILES_COLUMNS);
                } else if (theme == ClassicTheme.CIRCLES) {   // one row: every app moves along it
                    if (!sideways) return true;
                    adapter.moveInRest(pkg, sign);
                } else {
                    return true;
                }
                focusAfterLayout(pkg);
                return true;
            }
            default:
                return true;   // swallow everything else while moving
        }
    }

    // ---- settings sidebar (ConfigSidebar) ----------------------------------------------------------

    private void showSidebar(String screen) {
        new ConfigSidebar(host, this).show(screen);
    }

    @Override
    public void onTheme(String key) {
        ConfigSidebar.saveTheme(this, key);
        host.recreate();   // MainActivity picks the home for the new theme
    }

    @Override
    public void onWallpaper() {
        host.recreate();   // read the wallpaper collection and the choice again
    }

    @Override
    public void onReco() {
        refreshReco();
    }

    @Override
    public Runnable inputs() {
        return this::showInputs;
    }

    @Override
    public Runnable moreSettings() {
        return null;   // upstream's longer settings page belongs to the Tiles home
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

    // ---- recommendations -------------------------------------------------------------------------
    // A row of "Continue watching" cards between the header and the apps (see RecoStore). Up from the first row of apps
    // reaches it, Up from it reaches the header and Down returns to the app we came from. It can be switched off in
    // Settings, and it hides itself when there is nothing to show or the app list has scrolled down.

    private boolean recoEnabled() {
        return getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE).getBoolean(MainActivity.PREF_RECO, true);
    }

    /** Reads the list again (off the main thread) every time the home screen comes back. */
    private void refreshReco() {
        if (!recoEnabled()) {
            recoAdapter.setItems(Collections.emptyList());
            updateRecoVisibility();
            return;
        }
        recoStore.load(items -> {
            if (isDestroyed() || !recoEnabled()) return;
            recoAdapter.setItems(items);
            updateRecoVisibility();
        });
    }

    private boolean recoShown() {
        return recoBlock.getVisibility() == View.VISIBLE;
    }

    /** Gone when off or empty; hidden (so it cannot take focus) while the focus is below the first rows of apps. */
    private void updateRecoVisibility() {
        boolean any = recoEnabled() && recoAdapter.getItemCount() > 0;
        boolean show = any && isRecoRow(lastFocused);
        recoBlock.setVisibility(show ? View.VISIBLE : any ? View.INVISIBLE : View.GONE);
        View focused = getCurrentFocus();
        if (!show && focused != null && focused.getId() == R.id.recoCard) focusTile(lastFocused);
    }

    /** The rows of apps shown together with the recommendations (the lower ones scroll through their space). */
    private boolean isRecoRow(int position) {
        if (theme == ClassicTheme.CIRCLES) return true;
        if (theme == ClassicTheme.TILES) return position < 2 * TILES_COLUMNS;   // the plate and the row peeking in below it
        return isFirstRow(position);
    }

    /** The row Up leaves from to reach the recommendations: the dock, the plate or the single row of circles. */
    private boolean isFirstRow(int position) {
        if (theme == ClassicTheme.CIRCLES) return true;
        if (theme == ClassicTheme.TILES) return position < TILES_COLUMNS;
        int dock = adapter.dockCount();
        return position < (dock > 0 ? dock : COLUMNS);
    }

    private void focusReco() {
        final int idx = Math.max(0, Math.min(recoLast, recoAdapter.getItemCount() - 1));
        recoRow.scrollToPosition(idx);
        recoRow.post(() -> {
            RecyclerView.ViewHolder vh = recoRow.findViewHolderForAdapterPosition(idx);
            if (vh != null) vh.itemView.findViewById(R.id.recoCard).requestFocus();
        });
    }

    @Override
    public void onRecoFocused() {
        hideGlass();
        hideHero();
    }

    @Override
    public void onRecoOpen(RecoStore.Item item) {
        try {
            startActivity(item.intent);
        } catch (Exception e) {   // the app was removed, or the entry points at something it no longer has
            Toast.makeText(this, getString(R.string.c_cant_open, item.title), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onRecoKey(int position, int keyCode) {
        recoLast = position;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return position == 0;   // the ends do not fall through to the header buttons
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return position == recoAdapter.getItemCount() - 1;
            case KeyEvent.KEYCODE_DPAD_UP:
                btnWifi.requestFocus();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                focusTile(lastFocused);
                recoRow.playSoundEffect(android.view.SoundEffectConstants.NAVIGATION_DOWN);
                return true;
            default:
                return false;
        }
    }

    // ---- top bar ---------------------------------------------------------------------------------

    // Morning 5-12, afternoon 12-17, evening 17-22, night 22-5. Indexes GREETINGS.
    private static final int[] GREETINGS = {
            R.string.c_good_morning, R.string.c_good_afternoon, R.string.c_good_evening, R.string.c_good_night};

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
        String name = getString(R.string.greeting_name).trim();
        greeting.setText(name.isEmpty() ? text : text + ", " + name);
    }

    /**
     * The connection button is just an icon: the Ethernet plug when wired, the Wi-Fi symbol (with signal bars) when on
     * Wi-Fi, and the same symbols with a slash across them when there is no internet or no network at all.
     */
    private void updateWifiLabel() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        Network net = cm != null ? cm.getActiveNetwork() : null;
        NetworkCapabilities caps = net != null ? cm.getNetworkCapabilities(net) : null;
        if (caps == null) {
            setConnectionIcon(R.drawable.c_ic_wifi_off, getString(R.string.c_offline));
            return;
        }
        boolean online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            setConnectionIcon(online ? R.drawable.c_ic_ethernet : R.drawable.c_ic_ethernet_off,
                    getString(online ? R.string.c_ethernet : R.string.c_no_internet));
            return;
        }
        if (!online) {
            setConnectionIcon(R.drawable.c_ic_wifi_off, getString(R.string.c_no_internet));
            return;
        }
        WifiManager wm = getApplicationContext().getSystemService(WifiManager.class);
        WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
        int bars = info != null ? WifiManager.calculateSignalLevel(info.getRssi(), WIFI_ICONS.length) : 0;
        setConnectionIcon(WIFI_ICONS[bars], getString(R.string.c_wifi));
    }

    private void setConnectionIcon(int icon, String description) {
        btnWifi.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
        btnWifi.setContentDescription(description);
    }
    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.c_not_available, Toast.LENGTH_SHORT).show();
        }
    }

    /** Dropdown menu under the Inputs pill: HDMI ports (with connection state), AV and Live TV. */
    private void showInputs() {
        View anchor = findViewById(R.id.btnSettings);
        View panel = getLayoutInflater().inflate(R.layout.c_popup_inputs, root, false);
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
                addInputRow(rows, popup, hdmi ? R.drawable.c_ic_input : R.drawable.c_ic_av, label, state,
                        () -> open(new Intent(Intent.ACTION_VIEW).setDataAndType(
                                TvContract.buildChannelUriForPassthroughInput(id), "vnd.android.cursor.item/channel")));
            }
        }
        Intent liveTv = liveTvPkg.isEmpty() ? null : getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
        if (liveTv != null) {
            if (rows.getChildCount() > 0) addSeparator(rows);
            addInputRow(rows, popup, R.drawable.c_ic_livetv, getString(R.string.c_live_tv), -1, () -> open(liveTv));
        }
        if (rows.getChildCount() == 0) {
            Toast.makeText(this, R.string.c_no_inputs, Toast.LENGTH_SHORT).show();
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
        View row = getLayoutInflater().inflate(R.layout.c_item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        // This TV reports "standby" for every port whether or not something is plugged in, so only
        // definite states are shown.
        if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            status.setText(R.string.c_connected);
            dot.setVisibility(View.VISIBLE);
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status.setText(R.string.c_not_connected);
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
