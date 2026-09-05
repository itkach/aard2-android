package itkach.aard2;

import android.content.Context;
import androidx.core.content.ContextCompat;
import android.util.TypedValue;

import com.kazy.fontdrawable.FontDrawable;


class IconMaker {

    static final String CUSTOM_FONT_PATH = "fontawesome-4.2.0.ttf";

    static final char IC_SEARCH = '\uf002';
    static final char IC_BOOKMARK = '\uf02e';
    static final char IC_BOOKMARK_O = '\uf097';
    static final char IC_HISTORY = '\uf1da';
    static final char IC_DICTIONARY = '\uf02d';
    static final char IC_SETTINGS = '\uf013';
    static final char IC_RELOAD = '\uf021';
    static final char IC_FILTER = '\uf0b0';
    static final char IC_SORT_DESC = '\uf161';
    static final char IC_SORT_ASC = '\uf160';
    static final char IC_CLOCK = '\uf017';
    static final char IC_LIST = '\uf03a';
    static final char IC_TRASH = '\uf1f8';
    static final char IC_LICENSE = '\uf19c';
    static final char IC_EXTERNAL_LINK = '\uf08e';
    static final char IC_FILE_ARCHIVE = '\uf1c6';
    static final char IC_ERROR = '\uf071';
    static final char IC_COPYRIGHT = '\uf1f9';
    static final char IC_SELECT_ALL = '\uf046';
    static final char IC_ADD = '\uf067';
    static final char IC_ANGLE_UP = '\uf106';
    static final char IC_ANGLE_DOWN = '\uf107';
    static final char IC_STAR = '\uf005';
    static final char IC_STAR_O = '\uf006';
    static final char IC_FOLDER = '\uf07b';
    static final char IC_LEVEL_UP = '\uf148';
    static final char IC_BAN = '\uf05e';
    static final char IC_FULLSCREEN = '\uf065';
    static final char IC_RANDOM = '\uf074';


    static FontDrawable make(Context context, char c, int sizeDp, int color) {
        FontDrawable drawable = new FontDrawable.Builder(context, c, CUSTOM_FONT_PATH)
                .setSizeDp(sizeDp)
                .setColor(color)
                .build();
        return drawable;
    }

    static FontDrawable makeWithColorRes(Context context, char c, int sizeDp, int colorRes) {
        return make(context, c, sizeDp, context.getResources().getColor(colorRes));
    }

    // Resolves a theme attribute (framework or library) to an actual color,
    // so icon color follows the active theme (light/dark, or whatever brand
    // color it's set to) instead of a color resource baked in ahead of time.
    static int resolveThemeColor(Context context, int attrId, int fallbackColor) {
        TypedValue typedValue = new TypedValue();
        boolean wasResolved = context.getTheme().resolveAttribute(attrId, typedValue, true);
        if (wasResolved) {
            return ContextCompat.getColor(context, typedValue.resourceId);
        }
        return fallbackColor;
    }

    static FontDrawable tab(Context context, char c) {
        return makeWithColorRes(context, c, 21, R.color.tab_icon);
    }

    // Content-area accent icons (favorite star, expand/collapse chevron,
    // trash, add) - tied to the theme's brand color (colorPrimary) so they
    // stay consistent with the rest of the UI instead of a separate
    // hardcoded accent.
    static FontDrawable list(Context context, char c) {
        int color = resolveThemeColor(context, com.google.android.material.R.attr.colorPrimary, 0xff0099cc);
        return make(context, c, 26, color);
    }

    // These icons are drawn directly in a Toolbar (bookmark toggle, CAB
    // select-all/delete), so they need to contrast with colorPrimary the
    // same way the Toolbar's own title text does - resolving
    // @android:color/secondary_text_dark unconditionally (as this used to)
    // ignored the active theme entirely and read as a washed-out,
    // disabled-looking grey once that stopped coincidentally matching.
    static FontDrawable actionBar(Context context, char c) {
        int color = resolveThemeColor(context, com.google.android.material.R.attr.colorOnPrimary, 0xff000000);
        return make(context, c, 26, color);
    }

    static FontDrawable text(Context context, char c) {
        int color = resolveThemeColor(context, android.R.attr.textColorSecondary, 0xff888888);
        return make(context, c, 16, color);
    }

    static FontDrawable errorText(Context context, char c) {
        return makeWithColorRes(context, c, 16, android.R.color.holo_red_dark);
    }

    static FontDrawable emptyView(Context context, char c) {
        return makeWithColorRes(context, c, 52, R.color.empty_view_icon);
    }

}
