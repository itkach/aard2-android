package itkach.aard2.prefs;

import androidx.annotation.NonNull;

public class AppPrefs extends Prefs {
    private static final String PREF_UI_THEME = "UITheme";
    private static final String PREF_QUERY = "query";
    private static final String PREF_RANDOM_FAV_LOOKUP = "onlyFavDictsForRandomLookup";
    private static final String PREF_USE_VOLUME_FOR_NAV = "useVolumeForNav";
    private static final String PREF_AUTO_PASTE = "autoPaste";

    public static final String PREF_UI_THEME_AUTO = "auto";
    public static final String PREF_UI_THEME_LIGHT = "light";
    public static final String PREF_UI_THEME_DARK = "dark";

    private static AppPrefs instance;
    private static AppPrefs getInstance() {
        if (instance == null) {
            instance = new AppPrefs();
        }
        return instance;
    }

    protected AppPrefs() {
        super("app");
    }

    @NonNull
    public static String getPreferredTheme() {
        return getInstance().prefs.getString(PREF_UI_THEME, PREF_UI_THEME_LIGHT);
    }

    public static void setPreferredTheme(@NonNull String preferredTheme) {
        getInstance().prefs.edit().putString(PREF_UI_THEME, preferredTheme).apply();
    }

    @NonNull
    public static String getLastQuery() {
        return getInstance().prefs.getString(PREF_QUERY, "");
    }

    public static void setLastQuery(@NonNull String lastQuery) {
        getInstance().prefs.edit().putString(PREF_QUERY, lastQuery).apply();
    }

    public static boolean useOnlyFavoritesForRandomLookups() {
        return getInstance().prefs.getBoolean(PREF_RANDOM_FAV_LOOKUP, false);
    }

    public static void setUseOnlyFavoritesForRandomLookups(boolean useOnlyFavoritesForRandomLookups) {
        getInstance().prefs.edit().putBoolean(PREF_RANDOM_FAV_LOOKUP, useOnlyFavoritesForRandomLookups).apply();
    }

    public static boolean useVolumeKeysForNavigation() {
        return getInstance().prefs.getBoolean(PREF_USE_VOLUME_FOR_NAV, true);
    }

    public static void setUseVolumeKeysForNavigation(boolean useVolumeKeysForNavigation) {
        getInstance().prefs.edit().putBoolean(PREF_USE_VOLUME_FOR_NAV, useVolumeKeysForNavigation).apply();
    }

    public static boolean autoPasteInLookup() {
        return getInstance().prefs.getBoolean(PREF_AUTO_PASTE, false);
    }

    public static void setAutoPasteInLookup(boolean autoPasteInLookup) {
        getInstance().prefs.edit().putBoolean(PREF_AUTO_PASTE, autoPasteInLookup).apply();
    }
}
