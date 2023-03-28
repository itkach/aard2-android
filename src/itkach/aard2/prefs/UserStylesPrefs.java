package itkach.aard2.prefs;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public class UserStylesPrefs extends Prefs {
    private static UserStylesPrefs instance;
    private static UserStylesPrefs getInstance() {
        if (instance == null) {
            instance = new UserStylesPrefs();
        }
        return instance;
    }

    protected UserStylesPrefs() {
        super("userStyles");
    }

    @NonNull
    public static List<String> listStyleNames() {
        return new ArrayList<>(getInstance().prefs.getAll().keySet());
    }

    public static boolean hasStyle(@NonNull String styleName) {
        return getInstance().prefs.contains(styleName);
    }

    @NonNull
    public static String getStylesheet(@NonNull String styleName) {
        return getInstance().prefs.getString(styleName, "");
    }

    public static void removeStyle(@NonNull String styleName) {
        getInstance().prefs.edit().remove(styleName).apply();
    }

    public static boolean addStyle(@NonNull String styleName, @NonNull String styleSheet) {
        return getInstance().prefs.edit().putString(styleName, styleSheet).commit();
    }
}
