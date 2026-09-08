package itkach.aard2;

import android.content.Context;
import androidx.core.content.ContextCompat;
import android.util.TypedValue;

import com.kazy.fontdrawable.FontDrawable;


class IconMaker {

    // Font Awesome 7 Free splits each icon's outline ("regular") and filled
    // ("solid") look into separate font files sharing the same codepoint,
    // rather than FA4's separate "-o"-suffixed icon+codepoint per outline
    // variant - so a Glyph has to carry which file it comes from, not just
    // which character. Each asset is a subset built with fonttools
    // (pyftsubset) containing only the glyphs actually used below, not the
    // full ~2000-icon Free set (subsetting is why fontawesome-7-solid.otf is
    // ~5KB and fontawesome-7-regular.otf ~2KB, rather than several hundred KB
    // each) - see git history for the exact pyftsubset invocation if these
    // ever need to be regenerated (e.g. to add another icon).
    private static final String FONT_SOLID = "fontawesome-7-solid.otf";
    private static final String FONT_REGULAR = "fontawesome-7-regular.otf";

    static final class Glyph {
        final char code;
        final String font;
        private Glyph(char code, String font) {
            this.code = code;
            this.font = font;
        }
    }

    private static Glyph solid(char code) {
        return new Glyph(code, FONT_SOLID);
    }

    private static Glyph regular(char code) {
        return new Glyph(code, FONT_REGULAR);
    }

    static final Glyph IC_SEARCH = solid('');            // magnifying-glass
    static final Glyph IC_BOOKMARK = solid('');          // bookmark
    static final Glyph IC_BOOKMARK_O = regular('');      // bookmark (outline)
    static final Glyph IC_HISTORY = solid('');           // clock-rotate-left
    static final Glyph IC_DICTIONARY = solid('');        // book
    static final Glyph IC_SETTINGS = solid('');          // gear
    static final Glyph IC_RELOAD = solid('');            // arrows-rotate
    static final Glyph IC_FILTER = solid('');            // filter
    static final Glyph IC_SORT_DESC = solid('');         // arrow-up-wide-short
    static final Glyph IC_SORT_ASC = solid('');          // arrow-down-wide-short
    static final Glyph IC_CLOCK = regular('');           // clock (outline)
    static final Glyph IC_LIST = solid('');              // list
    static final Glyph IC_TRASH = solid('');             // trash (FA7 Free has no outline style for this one)
    static final Glyph IC_LICENSE = solid('');           // building-columns
    static final Glyph IC_EXTERNAL_LINK = solid('');     // arrow-up-right-from-square
    static final Glyph IC_FILE_ARCHIVE = regular('');    // file-zipper (outline)
    static final Glyph IC_ERROR = solid('');             // triangle-exclamation
    static final Glyph IC_COPYRIGHT = solid('');         // copyright
    static final Glyph IC_CHECK_SQUARE = regular('\uf14a'); // square-check (checked box) - codepoint changed from FA4's f046
    static final Glyph IC_SQUARE = regular('\uf0c8');       // square (empty box)
    static final Glyph IC_ADD = solid('+');               // plus - FA7 maps this to the literal ASCII '+', changed from FA4's f067
    static final Glyph IC_ANGLE_UP = solid('');          // angle-up
    static final Glyph IC_ANGLE_DOWN = solid('');        // angle-down
    static final Glyph IC_STAR = solid('');              // star
    static final Glyph IC_STAR_O = regular('');          // star (outline) - same codepoint as IC_STAR, different font file
    static final Glyph IC_FOLDER = solid('');            // folder
    static final Glyph IC_LEVEL_UP = solid('');          // arrow-turn-up
    static final Glyph IC_BAN = solid('');               // ban
    static final Glyph IC_RANDOM = solid('');            // dice
    static final Glyph IC_DRAG_HANDLE = solid('\uf58e'); // grip-vertical (drag-to-reorder handle)

    static FontDrawable make(Context context, Glyph g, int sizeDp, int color) {
        FontDrawable drawable = new FontDrawable.Builder(context, g.code, g.font)
                .setSizeDp(sizeDp)
                .setColor(color)
                .build();
        return drawable;
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

    // Bottom-navigation icon. The selected destination is drawn in the brand
    // colour (colorPrimary), the rest in the muted textColorSecondary (the
    // framework's own "de-emphasized icon" role, which follows the theme and,
    // on Android 12+, the system's per-wallpaper dynamic color). The colour is
    // chosen here rather than via itemIconTint because the FontDrawable this
    // produces doesn't honour a tint list. Size follows the bar's itemIconSize,
    // so the glyph size passed to make() is just its intrinsic bound.
    static FontDrawable tab(Context context, Glyph g, boolean selected) {
        int attr = selected
                ? com.google.android.material.R.attr.colorPrimary
                : android.R.attr.textColorSecondary;
        int fallback = selected ? 0xff0099cc : 0xff888888;
        return make(context, g, 24, resolveThemeColor(context, attr, fallback));
    }

    // Content-area accent icons (favorite star, expand/collapse chevron,
    // trash, add) - tied to the theme's brand color (colorPrimary) so they
    // stay consistent with the rest of the UI instead of a separate
    // hardcoded accent.
    static FontDrawable list(Context context, Glyph g) {
        int color = resolveThemeColor(context, com.google.android.material.R.attr.colorPrimary, 0xff0099cc);
        return make(context, g, 26, color);
    }

    // Secondary row actions (a dictionary row's forget/trash) - deliberately
    // smaller and in the muted secondary text color so they recede behind the
    // dictionary name and its on/off toggle rather than competing with them the
    // way full-size colorPrimary list() icons do.
    static FontDrawable rowAction(Context context, Glyph g) {
        int color = resolveThemeColor(context, android.R.attr.textColorSecondary, 0xff888888);
        return make(context, g, 18, color);
    }

    // The expand/collapse chevron is a passive disclosure affordance, not an
    // action - smaller still and in the faint hint colour so it recedes further
    // than the row's actual actions (trash) instead of reading as a button.
    static FontDrawable chevron(Context context, Glyph g) {
        int color = resolveThemeColor(context, android.R.attr.textColorHint, 0xffaaaaaa);
        return make(context, g, 13, color);
    }

    // These icons are drawn directly in a Toolbar (bookmark toggle, etc.), so
    // they need to contrast with colorPrimary the same way the Toolbar's own
    // title text does - resolving @android:color/secondary_text_dark
    // unconditionally (as this used to) ignored the active theme entirely and
    // read as a washed-out, disabled-looking grey once that stopped
    // coincidentally matching.
    static FontDrawable actionBar(Context context, Glyph g) {
        int color = resolveThemeColor(context, com.google.android.material.R.attr.colorOnPrimary, 0xff000000);
        // 20dp (not the framework action icon's nominal 24dp): Font Awesome
        // glyphs carry almost no built-in padding, unlike Material's icons, so
        // they read larger at equal size - 20dp brings the bookmark glyph into
        // line with the framework overflow "⋮" it sits next to.
        return make(context, g, 20, color);
    }

    // Contextual-action-bar (multi-select) icons. Unlike the main Toolbar, the
    // ActionMode bar uses the app theme's own surface (light in light theme)
    // with a dark foreground - the same colorOnPrimary tint the Toolbar icons
    // use would be white-on-white here. textColorPrimary follows the theme and
    // matches the CAB's own title/close-button colour.
    static FontDrawable actionMode(Context context, Glyph g) {
        int color = resolveThemeColor(context, android.R.attr.textColorPrimary, 0xff000000);
        // Same 20dp as the main Toolbar's actionBar() icons so the two
        // contextual bars (multi-select, find-in-page) match the regular
        // toolbar rather than looking oversized next to it.
        return make(context, g, 20, color);
    }

    static FontDrawable text(Context context, Glyph g) {
        int color = resolveThemeColor(context, android.R.attr.textColorSecondary, 0xff888888);
        return make(context, g, 16, color);
    }

    // colorError is Material's own dynamic-color-aware error role, replacing
    // a fixed android.R.color.holo_red_dark this used to resolve to
    // regardless of theme.
    static FontDrawable errorText(Context context, Glyph g) {
        int color = resolveThemeColor(context, com.google.android.material.R.attr.colorError, 0xffcc0000);
        return make(context, g, 16, color);
    }

    // Placeholder icon for an empty list (e.g. "no bookmarks yet") -
    // textColorHint is the framework's own role for exactly this kind of
    // de-emphasized, content-absent state.
    static FontDrawable emptyView(Context context, Glyph g) {
        int color = resolveThemeColor(context, android.R.attr.textColorHint, 0xffcccccc);
        return make(context, g, 52, color);
    }

}
