package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Locale;

/**
 * CleanTV fork. The header of the Dock and Circles themes: the same layout (home_header.xml), the same greeting and the
 * same network, Inputs and Settings pills as the Tiles home, driven by the same config (greeting_name, show_network_pill,
 * show_inputs_pill, icon_only_buttons, icon_only_inputs, wifi_label_width), so switching theme never changes the header.
 * The Tiles home keeps upstream's own code for it in MainActivity; this follows the same rules.
 */
final class HomeHeader {

    private final Context ctx;
    private final TextView greeting, btnInputs, btnWifi, btnSettings;
    private final String greetingName;
    private final boolean showInputsPill;
    private final boolean wifiIconOnly;
    private final String liveTvPackage;
    private final float density;
    private int wifiIcon;

    HomeHeader(Context ctx, View root, Config cfg, Runnable onInputs, Runnable onSettings) {
        this.ctx = ctx;
        density = ctx.getResources().getDisplayMetrics().density;
        greeting = root.findViewById(R.id.greeting);
        btnInputs = root.findViewById(R.id.btnInputs);
        btnWifi = root.findViewById(R.id.btnWifi);
        btnSettings = root.findViewById(R.id.btnSettings);
        greetingName = cfg.greetingName;
        showInputsPill = cfg.showInputsPill;
        liveTvPackage = cfg.liveTvPackage == null ? "" : cfg.liveTvPackage;
        // The reorder and app library buttons of Tiles have no meaning here: moving an app is done on the app itself.
        root.findViewById(R.id.btnReorder).setVisibility(View.GONE);
        root.findViewById(R.id.btnLibraryTop).setVisibility(View.GONE);

        btnInputs.setOnClickListener(v -> onInputs.run());
        btnSettings.setOnClickListener(v -> onSettings.run());
        btnSettings.setOnLongClickListener(v -> {
            onSettings.run();
            return true;
        });
        btnWifi.setOnClickListener(v -> openWifiSettings());
        btnWifi.setOnLongClickListener(v -> {
            openWifiSettings();
            return true;
        });
        if (!cfg.showNetworkPill) btnWifi.setVisibility(View.GONE);
        wifiIconOnly = cfg.iconOnlyButtons && cfg.wifiLabelWidth <= 0;
        if (cfg.iconOnlyButtons) {
            if (cfg.wifiLabelWidth > 0) btnWifi.setMaxWidth(Math.round(cfg.wifiLabelWidth * density));
            else makeIconOnly(btnWifi);
            makeIconOnly(btnSettings);
        }
        if (cfg.iconOnlyInputs) makeIconOnly(btnInputs);
    }

    /** Greeting, network and Inputs, as of now. Called when Home comes back and every minute. */
    void update() {
        updateGreeting();
        updateNetwork();
        updateInputs();
    }

    /** The first pill that is on screen, where Up from the apps lands. */
    View firstPill() {
        if (btnInputs.getVisibility() == View.VISIBLE) return btnInputs;
        if (btnWifi.getVisibility() == View.VISIBLE) return btnWifi;
        return btnSettings;
    }

    View settingsPill() {
        return btnSettings;
    }

    /** True when the Inputs pill is on screen, i.e. there is something to switch to. */
    boolean hasInputs() {
        return btnInputs.getVisibility() == View.VISIBLE;
    }

    private void openWifiSettings() {
        try {
            ctx.startActivity(SystemSettings.intent(ctx, Settings.ACTION_WIFI_SETTINGS));
        } catch (Exception e) {
            Toast.makeText(ctx, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    /** Turns a labelled top pill into the round icon-only shape: no text, the icon centred by even padding. */
    private void makeIconOnly(TextView pill) {
        pill.setContentDescription(pill.getText());
        pill.setText(null);
        pill.setMaxWidth(Integer.MAX_VALUE);
        int pad = Math.round(10 * density);
        pill.setPaddingRelative(pad, 0, pad, 0);
        pill.setCompoundDrawablePadding(0);
        pill.getLayoutParams().width = Math.round(42 * density);
    }

    private void updateGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        int res;
        // The same day parts as the Tiles home, including its locale rules.
        Locale locale = ctx.getResources().getConfiguration().getLocales().get(0);
        String lang = locale.getLanguage();
        String country = locale.getCountry();
        if ("in".equals(lang) || "id".equals(lang)) {
            if (hour < 10) res = R.string.good_morning;
            else if (hour < 15) res = R.string.good_afternoon;
            else if (hour < 18) res = R.string.good_evening;
            else res = R.string.good_night;
        } else if (("es".equals(lang) && "ES".equals(country)) || "ca".equals(lang)) {
            if (hour < 6) res = R.string.good_night;
            else if (hour < 12) res = R.string.good_morning;
            else if (hour < 21) res = R.string.good_afternoon;
            else res = R.string.good_night;
        } else if ("da".equals(lang)) {
            if (hour < 5) res = R.string.good_night;
            else if (hour < 11) res = R.string.good_morning;
            else if (hour < 12) res = R.string.good_forenoon;
            else if (hour < 18) res = R.string.good_afternoon;
            else if (hour < 22) res = R.string.good_evening;
            else res = R.string.good_night;
        } else {
            if (hour < 5) res = R.string.good_night;
            else if (hour < 12) res = R.string.good_morning;
            else if (hour < 17) res = R.string.good_afternoon;
            else if (hour < 22) res = R.string.good_evening;
            else res = R.string.good_night;
        }
        greeting.setText(greetingName.isEmpty() ? ctx.getString(res)
                : ctx.getString(R.string.greeting_with_name, ctx.getString(res), greetingName));
    }

    /** The network name on the pill when it can be read, otherwise the connection type; Ethernet and Wi-Fi icons. */
    private void updateNetwork() {
        String text = ctx.getString(R.string.wifi);
        boolean online = false;
        int icon = R.drawable.ic_wifi;
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            boolean wifi = false, ethernet = false;
            // All networks, not just the active one: with a VPN up the active network is the VPN itself.
            for (Network net : cm != null ? cm.getAllNetworks() : new Network[0]) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(net);
                if (caps == null || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue;
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
                online = true;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) ethernet = true;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifi = true;
            }
            if (!online) {
                text = ctx.getString(R.string.offline);
            } else if (ethernet) {
                text = ctx.getString(R.string.ethernet);
                icon = R.drawable.ic_ethernet;
            } else if (wifi) {
                WifiManager wm = (WifiManager) ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                String ssid = info != null ? info.getSSID() : null;
                if (ssid != null) {
                    ssid = ssid.replace("\"", "").trim();
                    if (!ssid.isEmpty() && !ssid.contains("unknown") && !ssid.equals("0x")) text = ssid;
                }
            }
        } catch (Exception ignored) {
        }
        if (!android.text.TextUtils.equals(btnWifi.getText(), text)) btnWifi.setText(text);
        if (wifiIconOnly) {
            btnWifi.setContentDescription(text);
            btnWifi.setText(null);
        }
        if (icon != wifiIcon) {
            wifiIcon = icon;
            btnWifi.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
        }
    }

    /** Hides the Inputs pill when it would have nothing to list: streaming boxes have no HDMI-in, AV or tuner. */
    private void updateInputs() {
        if (!showInputsPill) {
            btnInputs.setVisibility(View.GONE);
            return;
        }
        boolean any = !liveTvPackage.isEmpty()
                && ctx.getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPackage) != null;
        TvInputManager tim = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
        if (!any && tim != null) {
            for (TvInputInfo info : tim.getTvInputList()) {
                if (info.isPassthroughInput() && !info.isHidden(ctx)) {
                    any = true;
                    break;
                }
            }
        }
        btnInputs.setVisibility(any ? View.VISIBLE : View.GONE);
    }
}
