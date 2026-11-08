package ru.vlad.unitprice;

import android.content.Context;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class Prefs {

    public static final String NAME = "unitprice";
    public static final String KEY_ALL = "all_apps";
    public static final String KEY_OVERLAY = "overlay_enabled";

    public static final Set<String> SUPPORTED = new HashSet<>(Arrays.asList(
            "ru.pyaterochka.app.browser",
            "ru.perekrestok.app",
            "ru.samokat.app",
            "ru.sbcs.store",
            "ru.instamart",
            "ru.bristol.bristol_app",
            "ru.ozon.fresh",
            "ru.ozon.app.android",
            "ru.ozon.app"
    ));

    private Prefs() {
    }

    public static boolean allApps(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(KEY_ALL, true);
    }

    public static void setAllApps(Context c, boolean value) {
        c.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_ALL, value).apply();
    }

    public static boolean overlayEnabled(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE).getBoolean(KEY_OVERLAY, true);
    }

    public static void setOverlayEnabled(Context c, boolean value) {
        c.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY_OVERLAY, value).apply();
    }
}
