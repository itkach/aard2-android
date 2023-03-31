package itkach.aard2.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import itkach.aard2.prefs.AppPrefs;

public class Utils {
    private static final String TAG = Utils.class.getSimpleName();

    public static <T extends Comparable<? super T>> void sort(List<T> list) {
        try {
            Collections.sort(list);
        } catch (Exception e) {
            Log.w(TAG, "Error while sorting:", e);
        }
    }

    public static <T> void sort(List<T> list, Comparator<? super T> comparator) {
        try {
            Collections.sort(list, comparator);
        } catch (Exception e) {
            // From http://www.oracle.com/technetwork/java/javase/compatibility-417013.html#source
            /*
            Synopsis: Updated sort behavior for Arrays and Collections may throw an IllegalArgumentException
            Description: The sorting algorithm used by java.util.Arrays.sort and (indirectly) by
                         java.util.Collections.sort has been replaced. The new sort implementation may
                         throw an IllegalArgumentException if it detects a Comparable that violates
                         the Comparable contract. The previous implementation silently ignored such a situation.
                         If the previous behavior is desired, you can use the new system property,
                         java.util.Arrays.useLegacyMergeSort, to restore previous mergesort behavior.
            Nature of Incompatibility: behavioral
            RFE: 6804124
             */
            // Name comparators use ICU collation key comparison. Given Unicode collation complexity
            // it's hard to be sure that collation key comparisons won't trigger an exception. It certainly
            // does at least for some keys in ICU 53.1.
            // Incorrect or no sorting seems preferable than a crashing app.
            // TODO: perhaps java.util.Collections.sort shouldn't be used at all
            Log.w(TAG, "Error while sorting:", e);
        }
    }

    @Nullable
    public static String wikipediaToSlobUri(@Nullable Uri uri) {
        if (uri == null) {
            return null;
        }
        String host = uri.getHost();
        if (TextUtils.isEmpty(host)) {
            return null;
        }
        String normalizedHost = host;
        String[] parts = host.split("\\.");
        // If mobile host like en.m.wikipedia.opr get rid of m
        if (parts.length == 4) {
            normalizedHost = String.format("%s.%s.%s", parts[0], parts[2], parts[3]);
        }
        return "http://" + normalizedHost;
    }

    public static void updateNightMode() {
        int nightMode;
        switch (AppPrefs.getPreferredTheme()) {
            case AppPrefs.PREF_UI_THEME_DARK:
                nightMode = AppCompatDelegate.MODE_NIGHT_YES;
                break;
            case AppPrefs.PREF_UI_THEME_LIGHT:
                nightMode = AppCompatDelegate.MODE_NIGHT_NO;
                break;
            case AppPrefs.PREF_UI_THEME_AUTO:
            default:
                nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        AppCompatDelegate.setDefaultNightMode(nightMode);
    }

    public static boolean isNightMode(@NonNull Context context) {
        int nightModeFlags = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES;
    }

    @NonNull
    public static String readStream(@NonNull InputStream is, int maxSize) throws IOException {
        InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
        StringWriter sw = new StringWriter();
        char[] buf = new char[16384];
        int count = 0;
        while (true) {
            int read = reader.read(buf);
            if (read == -1) {
                break;
            }
            count += read;
            if (maxSize > 0 && count > maxSize) {
                throw new IOException("Too big file");
            }
            sw.write(buf, 0, read);
        }
        reader.close();
        return sw.toString();
    }
}
