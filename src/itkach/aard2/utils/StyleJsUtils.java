package itkach.aard2.utils;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

import itkach.aard2.Application;

public final class StyleJsUtils {
    private static String styleSwitcherJs;
    private static String userStylesJs;
    private static String clearUserStyleJs;
    private static String setCannedStyleJs;

    @NonNull
    public static String getStyleSwitcherJs() {
        if (styleSwitcherJs == null) {
            try (InputStream is = Objects.requireNonNull(StyleJsUtils.class.getClassLoader())
                    .getResourceAsStream("styleswitcher.js")) {
                styleSwitcherJs = Utils.readStream(is, 0);
            } catch (IOException e) {
                styleSwitcherJs = "";
            }
        }
        return styleSwitcherJs;
    }

    @NonNull
    public static String getUserStyleJs() {
        if (userStylesJs == null) {
            try (InputStream is = Application.get().getAssets().open("userstyle.js")) {
                userStylesJs = Utils.readStream(is, 0);
            } catch (IOException e) {
                userStylesJs = "";
            }
        }
        return userStylesJs;
    }

    @NonNull
    public static String getClearUserStyleJs() {
        if (clearUserStyleJs == null) {
            try (InputStream is = Application.get().getAssets().open("clearuserstyle.js")) {
                clearUserStyleJs = Utils.readStream(is, 0);
            } catch (IOException e) {
                clearUserStyleJs = "";
            }
        }
        return clearUserStyleJs;
    }

    @NonNull
    public static String getSetCannedStyleJs() {
        if (setCannedStyleJs == null) {
            try (InputStream is = Application.get().getAssets().open("setcannedstyle.js")) {
                setCannedStyleJs = Utils.readStream(is, 0);
            } catch (IOException e) {
                setCannedStyleJs = "";
            }
        }
        return setCannedStyleJs;
    }
}
