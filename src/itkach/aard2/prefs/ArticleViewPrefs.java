package itkach.aard2.prefs;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

public class ArticleViewPrefs extends Prefs {
    private static final String PREF_REMOTE_CONTENT = "remoteContent";
    private static final String PREF_TEXT_ZOOM = "textZoom";
    private static final String PREF_STYLE_PREFIX = "style.";
    private static final String PREF_STYLE_AVAILABLE_PREFIX = "style.available.";

    public static final String PREF_REMOTE_CONTENT_ALWAYS = "always";
    public static final String PREF_REMOTE_CONTENT_WIFI = "wifi";
    public static final String PREF_REMOTE_CONTENT_NEVER = "never";

    private static ArticleViewPrefs instance;

    private static ArticleViewPrefs getInstance() {
        if (instance == null) {
            instance = new ArticleViewPrefs();
        }
        return instance;
    }

    protected ArticleViewPrefs() {
        super("articleView");
    }

    @NonNull
    public static String getRemoteContentPreference() {
        return getInstance().prefs.getString(PREF_REMOTE_CONTENT, PREF_REMOTE_CONTENT_WIFI);
    }

    public static void setRemoteContentPreference(@NonNull String remoteContentPreference) {
        getInstance().prefs.edit().putString(PREF_REMOTE_CONTENT, remoteContentPreference).apply();
    }

    public static int getPreferredZoomLevel() {
        return getInstance().prefs.getInt(PREF_TEXT_ZOOM, 100);
    }

    public static void setPreferredZoomLevel(int preferredZoomLevel) {
        getInstance().prefs.edit().putInt(PREF_TEXT_ZOOM, preferredZoomLevel).apply();
    }

    @NonNull
    public static String getDefaultStyle(@NonNull String slobId, @NonNull String defaultStyleName) {
        return getInstance().prefs.getString(PREF_STYLE_PREFIX + slobId, defaultStyleName);
    }

    public static void setDefaultStyle(@NonNull String slobId, @NonNull String styleName) {
        getInstance().prefs.edit().putString(PREF_STYLE_PREFIX + slobId, styleName).apply();
    }

    @NonNull
    public static TreeSet<String> getAvailableStyles(@NonNull String slobId) {
        return new TreeSet<>(getInstance().prefs.getStringSet(PREF_STYLE_AVAILABLE_PREFIX + slobId,
                Collections.emptySet()));
    }

    public static void setAvailableStyles(@NonNull String slobId, Set<String> availableStyles) {
        getInstance().prefs.edit().putStringSet(PREF_STYLE_AVAILABLE_PREFIX + slobId, availableStyles).apply();
    }
}
