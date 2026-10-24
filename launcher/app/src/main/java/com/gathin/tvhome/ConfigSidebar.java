package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The settings sidebar: a panel on the right edge with a few big buttons. Theme and Wallpaper open their own screen
 * inside the panel (Back returns to the buttons), Continue watching is a switch, and Android settings opens the system
 * settings directly. Shared by the Dock and Circles themes ({@link ClassicHome}) and the Tiles home
 * ({@link MainActivity}); each one tells it what to do through {@link Host}.
 */
final class ConfigSidebar {

    static final String THEME_DOCK = "dock", THEME_CIRCLES = "circles", THEME_TILES = "tiles";
    /** The file the Dock and Circles themes always saved into; the theme choice lives here. */
    static final String PREFS = "tvhome";
    private static final String PREF_THEME = "theme";
    static final String SCREEN_MENU = "menu", SCREEN_WALLPAPER = "wallpaper";
    /** Set by a wallpaper change so the rebuilt Home opens the panel again on the wallpaper screen. */
    static boolean reopenWallpaper;

    interface Host {
        /** The theme was picked: save it and rebuild Home. */
        void onTheme(String key);

        /** A wallpaper was picked, added or removed: rebuild Home so it reads the collection again. */
        void onWallpaper();

        /** The Continue watching switch changed. */
        void onReco();

        /** The Inputs dropdown, or null when this home has none. */
        Runnable inputs();

        /** The longer list of launcher options (upstream's own settings page), or null. */
        Runnable moreSettings();
    }

    private final Activity act;
    private final Context ctx;
    private final Host host;
    private final WallpaperStore wallpapers;
    private final SharedPreferences choices;
    private final String liveTvPkg;
    private final float density;

    ConfigSidebar(Activity act, Host host) {
        this.act = act;
        this.ctx = act;
        this.host = host;
        wallpapers = new WallpaperStore(act);
        choices = act.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        liveTvPkg = act.getString(R.string.live_tv_package).trim();
        density = act.getResources().getDisplayMetrics().density;
    }

    /** The theme in use: Dock, Circles or Tiles (upstream's own home, the default). */
    static String theme(Context ctx) {
        String key = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(PREF_THEME, THEME_TILES);
        return THEME_DOCK.equals(key) || THEME_CIRCLES.equals(key) ? key : THEME_TILES;
    }

    static void saveTheme(Context ctx, String key) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(PREF_THEME, key).apply();
    }

    private boolean recoEnabled() {
        return choices.getBoolean(MainActivity.PREF_RECO, true);
    }

    private void bumpRevision() {
        choices.edit().putLong(Config.SETTINGS_REVISION_KEY, choices.getLong(Config.SETTINGS_REVISION_KEY, 0) + 1).apply();
    }

    void show(String firstScreen) {
        final Dialog dialog = new Dialog(act, R.style.ClassicSidebar);
        View panel = act.getLayoutInflater().inflate(R.layout.c_sidebar_config, null);
        final LinearLayout rows = panel.findViewById(R.id.rows);
        final TextView title = panel.findViewById(R.id.configTitle);
        final Runnable[] menu = new Runnable[1];
        final boolean[] inMenu = {true};
        final String current = theme(ctx);

        final Runnable themeScreen = () -> {
            inMenu[0] = false;
            title.setText(R.string.c_menu_theme);
            rows.removeAllViews();
            addRow(rows, dialog, R.drawable.c_ic_left, act.getString(R.string.c_back), false, false, () -> menu[0].run());
            addRow(rows, dialog, 0, act.getString(R.string.c_theme_dock), THEME_DOCK.equals(current), true,
                    () -> host.onTheme(THEME_DOCK));
            addRow(rows, dialog, 0, act.getString(R.string.c_theme_circles), THEME_CIRCLES.equals(current), true,
                    () -> host.onTheme(THEME_CIRCLES));
            addRow(rows, dialog, 0, act.getString(R.string.c_theme_tiles), THEME_TILES.equals(current), true,
                    () -> host.onTheme(THEME_TILES));
            int selected = THEME_DOCK.equals(current) ? 1 : THEME_CIRCLES.equals(current) ? 2 : 3;
            rows.getChildAt(selected).requestFocus();
        };
        final Runnable[] wallpaperScreen = new Runnable[1];
        wallpaperScreen[0] = () -> {
            inMenu[0] = false;
            title.setText(R.string.c_menu_wallpaper);
            rows.removeAllViews();
            addRow(rows, dialog, R.drawable.c_ic_left, act.getString(R.string.c_back), false, false, () -> menu[0].run());
            addRow(rows, dialog, R.drawable.c_ic_wallpaper, act.getString(R.string.c_wallpaper_new), false, true,
                    this::fetchWallpaper);
            if (collection().size() > 1) {   // stepping through reopens this screen so you can keep browsing
                addRow(rows, dialog, R.drawable.c_ic_right, act.getString(R.string.c_wallpaper_next), false, true,
                        () -> stepWallpaper(1));
                addRow(rows, dialog, R.drawable.c_ic_left, act.getString(R.string.c_wallpaper_prev), false, true,
                        () -> stepWallpaper(-1));
            }
            String chosen = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
            if (chosen != null && wallpapers.isDownloaded(chosen)) {
                addRow(rows, dialog, R.drawable.c_ic_unpin, act.getString(R.string.c_wallpaper_remove), false, true,
                        this::removeWallpaper);
            }
            if (chosen != null) {
                addRow(rows, dialog, R.drawable.c_ic_settings, act.getString(R.string.c_wallpaper_auto), false, true,
                        this::useThemeWallpaper);
            }
            rows.getChildAt(1).requestFocus();
        };
        menu[0] = () -> {
            inMenu[0] = true;
            title.setText(R.string.c_settings_title);
            rows.removeAllViews();
            addRow(rows, dialog, R.drawable.c_ic_theme, act.getString(R.string.c_menu_theme), false, false, themeScreen);
            addRow(rows, dialog, R.drawable.c_ic_wallpaper, act.getString(R.string.c_menu_wallpaper), false, false,
                    wallpaperScreen[0]);
            View recoRowView = addRow(rows, dialog, R.drawable.c_ic_theme, act.getString(R.string.c_menu_reco), false,
                    false, () -> {
                        choices.edit().putBoolean(MainActivity.PREF_RECO, !recoEnabled()).apply();
                        bumpRevision();
                        host.onReco();
                        menu[0].run();
                        rows.getChildAt(2).requestFocus();   // stay on the switch
                    });
            TextView recoStatus = recoRowView.findViewById(R.id.status);
            recoStatus.setVisibility(View.VISIBLE);
            boolean access = HistoryStore.listenerEnabled(ctx)
                    || act.checkSelfPermission(RecoStore.PERMISSION) == PackageManager.PERMISSION_GRANTED;
            recoStatus.setText(!recoEnabled() ? R.string.c_reco_off
                    : access ? R.string.c_reco_on : R.string.c_reco_needs_permission);
            Runnable more = host.moreSettings();
            if (more != null) {
                addRow(rows, dialog, R.drawable.c_ic_settings, act.getString(R.string.c_menu_more), false, true, more);
            }
            addRow(rows, dialog, R.drawable.c_ic_settings, act.getString(R.string.c_menu_android), false, true,
                    () -> open(new Intent(Settings.ACTION_SETTINGS)));
            Runnable inputs = host.inputs();
            if (inputs != null && hasInputs()) {   // boxes without HDMI/AV inputs or a tuner don't get this one
                addRow(rows, dialog, R.drawable.c_ic_input, act.getString(R.string.c_inputs), false, true, inputs);
            }
            rows.getChildAt(0).requestFocus();
        };
        if (SCREEN_WALLPAPER.equals(firstScreen)) wallpaperScreen[0].run();
        else menu[0].run();

        // Back goes from a screen to the buttons, and from the buttons it closes the panel.
        dialog.setOnKeyListener((d, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_BACK || inMenu[0]) return false;
            if (event.getAction() == KeyEvent.ACTION_UP) menu[0].run();
            return true;
        });
        dialog.setContentView(panel);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setLayout((int) (380 * density), ViewGroup.LayoutParams.MATCH_PARENT);
            w.setGravity(Gravity.END);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            w.setDimAmount(0.45f);
        }
        dialog.show();
        rows.getChildAt(Math.min(rows.getChildCount() - 1, SCREEN_WALLPAPER.equals(firstScreen) ? 1 : 0)).requestFocus();
    }

    private boolean hasInputs() {
        TvInputManager tim = (TvInputManager) act.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim != null) {
            for (TvInputInfo info : tim.getTvInputList()) if (info.isPassthroughInput()) return true;
        }
        return !liveTvPkg.isEmpty() && act.getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg) != null;
    }

    /** A row in the sidebar; a dot marks the selected one. iconRes 0 means no icon. */
    private View addRow(LinearLayout rows, Dialog dialog, int iconRes, String title, boolean selected,
                        boolean closeAfter, Runnable action) {
        View row = act.getLayoutInflater().inflate(R.layout.c_item_input, rows, false);
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
        return row;
    }

    private void open(Intent i) {
        try {
            act.startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(act, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    // ---- wallpapers: the same collection upstream rotates (wallpapers/ next to the theme), plus the downloaded ones ----

    /** Every picture of the collection, in name order: the theme's own and the ones downloaded from the web. */
    private List<File> collection() {
        File dir = wallpapers.dir();
        return new ArrayList<>(WallpaperRotation.images(dir));
    }

    private void setChoice(String name) {
        SharedPreferences.Editor e = choices.edit();
        if (name == null) e.remove(Config.WALLPAPER_CHOICE_KEY);
        else e.putString(Config.WALLPAPER_CHOICE_KEY, name);
        e.apply();
        bumpRevision();
    }

    private void fetchWallpaper() {
        Toast.makeText(act, R.string.c_wallpaper_searching, Toast.LENGTH_SHORT).show();
        wallpapers.fetchNew((file, message) -> {
            if (file != null) {
                setChoice(file.getName());
                reopenWallpaper = true;
                host.onWallpaper();
            }
            Toast.makeText(act, message, Toast.LENGTH_LONG).show();
        });
    }

    /** Cycles theme default -> each picture of the collection -> theme default. */
    private void stepWallpaper(int delta) {
        List<String> order = new ArrayList<>();
        order.add(null);
        for (File f : collection()) order.add(f.getName());
        String chosen = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int at = Math.max(0, order.indexOf(chosen));
        int next = Math.floorMod(at + delta, order.size());
        setChoice(order.get(next));
        if (next == 0) {
            Toast.makeText(act, R.string.c_wallpaper_auto_toast, Toast.LENGTH_LONG).show();
        } else if (wallpapers.isDownloaded(order.get(next))) {
            Toast.makeText(act, wallpapers.credit(new File(wallpapers.dir(), order.get(next))), Toast.LENGTH_LONG).show();
        }
        reopenWallpaper = true;
        host.onWallpaper();
    }

    private void removeWallpaper() {
        String chosen = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        if (chosen != null) wallpapers.delete(new File(wallpapers.dir(), chosen));
        setChoice(null);
        reopenWallpaper = true;
        host.onWallpaper();
    }

    private void useThemeWallpaper() {
        setChoice(null);
        reopenWallpaper = true;
        host.onWallpaper();
    }
}
