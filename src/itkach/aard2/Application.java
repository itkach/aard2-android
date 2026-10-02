package itkach.aard2;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.content.res.Configuration;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.android.material.appbar.AppBarLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Future;

import itkach.slob.Slob;
import itkach.slob.Slob.Blob;
import itkach.slobber.Slobber;
import itkach.slobber.StylePreference;

public class Application extends android.app.Application {

    public static final String LOCALHOST = "127.0.0.1";
    public static final String CONTENT_URL_TEMPLATE = "http://" + LOCALHOST + ":%s%s";

    private Slobber                         slobber;
    private File                            userStyleDir;

    BlobDescriptorList                      bookmarks;
    BlobDescriptorList                      history;
    SlobDescriptorList                      dictionaries;

    private static int                      PREFERRED_PORT = 8013;
    private int                             port = -1;

    BlobList                                lastResult;

    private DescriptorStore<BlobDescriptor> bookmarkStore;
    private DescriptorStore<BlobDescriptor> historyStore;
    private DescriptorStore<SlobDescriptor> dictStore;

    private ObjectMapper                    mapper;

    private String                          lookupQuery = "";

    private List<Activity>                  articleActivities;

    static String jsStyleSwitcher;
    static String jsSetUserStyle;
    static String jsSetCannedStyle;
    static String jsProbeColors;

    private static final String PREF                    = "app";
    // Shared by ArticleWebView (text zoom, remote-content policy, and
    // per-dictionary style prefs) and SettingsFragment (remote-content
    // UI) as well as this class - not specific to any one of them, so it
    // lives here rather than on whichever of those happened to declare
    // it first.
    static final String ARTICLE_VIEW_PREF               = "articleView";
    static final String PREF_STYLE                      = "style.";
    static final String PREF_STYLE_AVAILABLE            = "style.available.";
    // Per (dictionary, resolved style, UI dark/light) cache of the article
    // background/foreground colors the WebView actually paints - measured once by
    // the real engine (see probecolors.js) so the loading placeholder can match.
    static final String STYLE_COLOR_PREF                = "styleColors";
    static final String PREF_RANDOM_FAV_LOOKUP          = "onlyFavDictsForRandomLookup";
    static final String PREF_UI_THEME                   = "UITheme";
    static final String PREF_UI_THEME_LIGHT             = "light";
    static final String PREF_UI_THEME_DARK              = "dark";
    static final String PREF_USE_VOLUME_FOR_NAV         = "useVolumeForNav";
    static final String PREF_AUTO_PASTE                 = "autoPaste";
    static final String PREF_RECORD_HISTORY             = "recordHistory";
    static final String PREF_AUTO_FULLSCREEN_LANDSCAPE  = "autoFullscreenLandscape";

    private static final String TAG = Application.class.getSimpleName();

    @Override
    public void onCreate() {
        super.onCreate();
        articleActivities = Collections.synchronizedList(new ArrayList<Activity>());

        mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false);
        dictStore = new DescriptorStore<SlobDescriptor>(mapper, getDir("dictionaries", MODE_PRIVATE));
        bookmarkStore = new DescriptorStore<BlobDescriptor>(mapper, getDir(
                "bookmarks", MODE_PRIVATE));
        historyStore = new DescriptorStore<BlobDescriptor>(mapper, getDir(
                "history", MODE_PRIVATE));
        slobber = new Slobber();
        // User CSS lives in a flat directory Slobber serves under /user-styles
        // and inlines a <link> to when requested (see ArticleWebView styles).
        userStyleDir = getDir("user-styles", MODE_PRIVATE);
        slobber.setStyleDir(userStyleDir);
        migrateUserStylesToFiles();

        long t0 = System.currentTimeMillis();

        startWebServer();

        Log.d(TAG, String.format("Started web server on port %d in %d ms",
                port, (System.currentTimeMillis() - t0)));
        try {
            InputStream is;
            is = getClass().getClassLoader().getResourceAsStream("styleswitcher.js");
            jsStyleSwitcher = readTextFile(is, 0);
            is = getAssets().open("setuserstyle.js");
            jsSetUserStyle = readTextFile(is, 0);
            is = getAssets().open("setcannedstyle.js");
            jsSetCannedStyle = readTextFile(is, 0);
            is = getAssets().open("probecolors.js");
            jsProbeColors = readTextFile(is, 0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        String initialQuery = prefs().getString("query", "");

        lastResult = new BlobList(this);

        dictionaries = new SlobDescriptorList(this, dictStore);
        bookmarks = new BlobDescriptorList(this, bookmarkStore);
        history = new BlobDescriptorList(this, historyStore);

        dictionaries.registerDataSetObserver(new DataSetObserver() {
            @Override
            synchronized public void onChanged() {
                lastResult.setData(new ArrayList<Slob.Blob>().iterator());
                slobber.setSlobs(null);
                List<Slob> slobs = new ArrayList<Slob>();
                for (SlobDescriptor sd : dictionaries) {
                    String origSlobId = sd.id;
                    Slob s = sd.load(getApplicationContext());
                    if (s != null) {
                        if (!origSlobId.equals(sd.id)) {
                            Log.d(TAG, String.format("%s has been replaced, updating dict store %s -> %s", sd.path, origSlobId, sd.id));
                            //dictionary file has been replaced
                            //(same file name, different slob uuid)
                            //need to update store accordingly
                            dictStore.delete(origSlobId);
                            dictStore.save(sd);
                        }
                        slobs.add(s);
                    }
                }
                slobber.setSlobs(slobs);

                lookup(lookupQuery);
                bookmarks.notifyDataSetChanged();
                history.notifyDataSetChanged();
            }
        });

        dictionaries.load();
        lookup(initialQuery, false);
        // Off the main thread: with history at up to 1000 entries, reading and
        // deserializing it is a noticeable chunk of cold start. The
        // Bookmarks/History fragments show a spinner (see
        // BlobDescriptorList.isLoading) until it finishes.
        bookmarks.loadAsync();
        history.loadAsync();
    }

    static String readTextFile(InputStream is, int maxSize) throws IOException, FileTooBigException {
        InputStreamReader reader = new InputStreamReader(is, "UTF-8");
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
                throw new FileTooBigException();
            }
            sw.write(buf, 0, read);
        }
        reader.close();
        return sw.toString();
    }


    private void startWebServer() {
        int portCandidate = PREFERRED_PORT;
        try {
            slobber.start("127.0.0.1", portCandidate);
            port = portCandidate;
        } catch (IOException e) {
            Log.w(TAG,
                    String.format("Failed to start on preferred port %d",
                            portCandidate), e);
            Set<Integer> seen = new HashSet<Integer>();
            seen.add(PREFERRED_PORT);
            Random rand = new Random();
            int attemptCount = 0;
            while (true) {
                int value = 1 + (int)Math.floor((65535-1025)*rand.nextDouble());
                portCandidate = 1024 + value;
                if (seen.contains(portCandidate)) {
                    continue;
                }
                attemptCount += 1;
                seen.add(portCandidate);
                Exception lastError;
                try {
                    slobber.start("127.0.0.1", portCandidate);
                    port = portCandidate;
                    break;
                } catch (IOException e1) {
                    lastError = e1;
                    Log.w(TAG,
                            String.format("Failed to start on port %d",
                                    portCandidate), e1);
                }
                if (attemptCount >= 20) {
                    throw new RuntimeException("Failed to start web server", lastError);
                }
            }
        }
    }

    SharedPreferences prefs() {
        return this.getSharedPreferences(PREF, Activity.MODE_PRIVATE);
    }

    String getPreferredTheme() {
        return prefs().getString(Application.PREF_UI_THEME,
                Application.PREF_UI_THEME_LIGHT);
    }

    boolean isUIDark() {
        return getPreferredTheme().equals(PREF_UI_THEME_DARK);
    }

    void installTheme(Activity activity) {
        String theme = getPreferredTheme();
        if (theme.equals(PREF_UI_THEME_DARK)) {
            activity.setTheme(R.style.Theme_Aard2_Dark);
        }
        else {
            activity.setTheme(R.style.Theme_Aard2);
        }
    }

    // The app's own light/dark preference (above) is independent of the
    // device's system dark mode setting - a user can pick "dark" for this
    // app while their device is in light mode, or vice versa. Status bar
    // icons (clock, battery, signal), though, are drawn by the system and
    // don't follow that app-level choice - they follow the device's own
    // setting (or get stuck, inconsistently across devices, if nothing
    // ever explicitly declares their appearance, which this app never
    // did). So the status bar's own backdrop has to be chosen the same way
    // the icons actually are - by the device's dark mode, not by
    // getPreferredTheme() - or the two can end up mismatched with no
    // guaranteed contrast.
    boolean isDeviceDark() {
        int nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return nightMode == Configuration.UI_MODE_NIGHT_YES;
    }

    // Sets the system status bar icons' appearance and the legacy
    // Window.setStatusBarColor() to the device-dark-aware backdrop color, and
    // returns that color. Driven by isDeviceDark(), not by this activity's own
    // applied theme, for the reason above.
    //
    // The legacy setStatusBarColor() is set because on a device that isn't
    // actually running edge-to-edge (confirmed on a Samsung running Android 12 -
    // our targetSdkVersion enforces edge-to-edge only on newer OS versions, and
    // that enforcement lives in the OS itself), the system reserves the status
    // bar's space BEFORE dispatching insets to our content, so
    // WindowInsetsCompat.Type.statusBars() is always zero there and AppBarLayout's
    // getTopInset() never draws its scrim - the deprecated API is what paints the
    // bar there. It's a no-op on a real edge-to-edge device (deprecated exactly
    // because the OS ignores it once edge-to-edge is enforced), so setting it
    // unconditionally covers either case.
    @SuppressWarnings("deprecation")
    int applyStatusBarAppearance(Activity activity) {
        boolean deviceDark = isDeviceDark();
        int scrimColor = ContextCompat.getColor(activity,
                deviceDark ? android.R.color.background_dark : android.R.color.background_light);
        activity.getWindow().setStatusBarColor(scrimColor);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(
                activity.getWindow(), activity.getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(!deviceDark);
        return scrimColor;
    }

    // Applies the above and additionally tints appBar's status bar foreground to
    // the returned backdrop color, via AppBarLayout's own statusBarForeground
    // mechanism (see ArticleCollectionActivity for how that foreground stays
    // pinned to the true top of the window regardless of the bar's own scroll
    // offset). Both MainActivity and ArticleCollectionActivity use this overload.
    void applyStatusBarAppearance(Activity activity, AppBarLayout appBar) {
        appBar.setStatusBarForegroundColor(applyStatusBarAppearance(activity));
    }

    void push(Activity activity) {
        this.articleActivities.add(activity);
        Log.d(TAG, "Activity added, stack size " + this.articleActivities.size());
        if (this.articleActivities.size() > 3) {
            Log.d(TAG, "Max stack size exceeded, finishing oldest activity");
            this.articleActivities.get(0).finish();
        }
    }

    void pop(Activity activity) {
        this.articleActivities.remove(activity);
    }


    Slob[] getActiveSlobs() {
        List<Slob> result = new ArrayList(dictionaries.size());
        for (SlobDescriptor sd : dictionaries) {
            if (sd.active) {
                Slob s = slobber.getSlob(sd.id);
                if (s != null) {
                    result.add(s);
                }
            }
        }
        return result.toArray(new Slob[result.size()]);
    };

    Slob[] getFavoriteSlobs() {
        List<Slob> result = new ArrayList(dictionaries.size());
        for (SlobDescriptor sd : dictionaries) {
            if (sd.active && sd.useForRandomLookup) {
                Slob s = slobber.getSlob(sd.id);
                if (s != null) {
                    result.add(s);
                }
            }
        }
        return result.toArray(new Slob[result.size()]);
    };


    // A dictionary's active/random flags changing in place - as opposed to a
    // structural add/remove/replace - doesn't change which slob files are open,
    // only which of the already-loaded ones a lookup considers (getActiveSlobs
    // filters the open set by the live flag). The dictionaries observer reopens
    // every dictionary file on any change, which is needless here and gets
    // slower the more dictionaries are installed, so the active toggle calls
    // this instead: refresh just what the active set feeds - the current lookup -
    // without touching a single file.
    void onActiveDictionariesChanged() {
        lookup(lookupQuery);
    }

    Iterator<Blob> find(String key) {
        return Slob.find(key, getActiveSlobs());
    }

    Iterator<Blob> find(String key, String preferredSlobId) {
        //When following links we want to consider all dictionaries
        //including the ones user turned off
        return find(key, preferredSlobId, false);
    }

    Slob.PeekableIterator<Blob> find(String key, String preferredSlobId, boolean activeOnly) {
        return this.find(key, preferredSlobId, activeOnly, null);
    }

    Slob.PeekableIterator<Blob> find(String key, String preferredSlobId, boolean activeOnly, Slob.Strength upToStrength) {
        long t0 = System.currentTimeMillis();
        Slob[] slobs = activeOnly ? getActiveSlobs() : slobber.getSlobs();
        Slob.PeekableIterator<Blob> result = Slob.find(key, slobs, slobber.findSlob(preferredSlobId), upToStrength);
        Log.d(TAG, String.format("find ran in %dms", System.currentTimeMillis() - t0));
        return result;
    }

    boolean isOnlyFavDictsForRandomLookup() {
        final SharedPreferences prefs = prefs();
        return prefs.getBoolean(Application.PREF_RANDOM_FAV_LOOKUP, false);
    }

    void setOnlyFavDictsForRandomLookup(boolean value) {
        final SharedPreferences prefs = prefs();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(Application.PREF_RANDOM_FAV_LOOKUP, value);
        editor.apply();
    }

    Blob random() {
        Slob[] slobs = isOnlyFavDictsForRandomLookup() ? getFavoriteSlobs() : getActiveSlobs();
        return slobber.findRandom(slobs);
    }

    boolean useVolumeForNav() {
        final SharedPreferences prefs = prefs();
        return prefs.getBoolean(Application.PREF_USE_VOLUME_FOR_NAV, true);
    }

    void setUseVolumeForNav(boolean value) {
        final SharedPreferences prefs = prefs();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(Application.PREF_USE_VOLUME_FOR_NAV, value);
        editor.apply();
    }

    boolean autoPaste() {
        final SharedPreferences prefs = prefs();
        return prefs.getBoolean(Application.PREF_AUTO_PASTE, false);
    }

    void setAutoPaste(boolean value) {
        final SharedPreferences prefs = prefs();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(Application.PREF_AUTO_PASTE, value);
        editor.apply();
    }

    boolean recordHistory() {
        final SharedPreferences prefs = prefs();
        return prefs.getBoolean(Application.PREF_RECORD_HISTORY, true);
    }

    void setRecordHistory(boolean value) {
        final SharedPreferences prefs = prefs();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(Application.PREF_RECORD_HISTORY, value);
        editor.apply();
    }

    // Whether rotating an article to landscape auto-enters full-screen reading
    // mode (and portrait leaves it). On by default - handy on a phone - but
    // toggleable off for devices where it's unwanted, e.g. tablets.
    boolean autoFullscreenLandscape() {
        return prefs().getBoolean(Application.PREF_AUTO_FULLSCREEN_LANDSCAPE, true);
    }

    void setAutoFullscreenLandscape(boolean value) {
        prefs().edit().putBoolean(Application.PREF_AUTO_FULLSCREEN_LANDSCAPE, value).apply();
    }

    // Whether the user dismissed the auto-full-screen-in-landscape while in
    // landscape. Suppresses the landscape rule (so it doesn't re-enter on resume
    // or a non-rotation config change) until the orientation actually changes.
    // Transient and app-scoped, so the dismissal holds across an article's resumes
    // and across following a link into another article, but re-arms on rotation
    // and on a fresh process.
    private boolean autoFullscreenDismissed;

    boolean isAutoFullscreenDismissed() {
        return autoFullscreenDismissed;
    }

    void setAutoFullscreenDismissed(boolean value) {
        autoFullscreenDismissed = value;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // Re-arm auto-full-screen-in-landscape when the device leaves landscape.
        // Done here rather than only in ArticleCollectionActivity because the
        // Application sees every rotation, including ones made with no article in
        // the foreground (e.g. on the main screen).
        if (newConfig.orientation != Configuration.ORIENTATION_LANDSCAPE) {
            autoFullscreenDismissed = false;
        }
    }


    String getUrl(Blob blob) {
        String path = Slobber.mkContentURL(blob);
        String styleTitle = resolveStyleTitle(getSlobURI(blob.owner.getId().toString()));
        // "Default" is never sent as a real param: the server's natural,
        // unmodified rendering already *is* that (see Slobber's
        // StylePreference, which does nothing when the param is absent),
        // so sending it would only cost a wasted HTML parse on every
        // single article load for an identical result.
        if (!styleTitle.equals(getString(R.string.default_style_title))) {
            path = StylePreference.withStyleParam(path, styleTitle);
        }
        return String.format(CONTENT_URL_TEMPLATE, port, path);
    }

    // ---- User styles: flat .css files under userStyleDir, which Slobber serves
    //      under /user-styles and injects a <link> to when ?style=<name> asks
    //      for one. The file name (extension included) is the style identifier
    //      everywhere; clients strip ".css" only for display. ----

    // The user style file names (with the .css extension), unsorted.
    List<String> userStyleNames() {
        List<String> names = new ArrayList<String>();
        File[] files = userStyleDir == null ? null : userStyleDir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isFile() && f.getName().endsWith(".css")) {
                    names.add(f.getName());
                }
            }
        }
        return names;
    }

    boolean isUserStyle(String name) {
        return name != null && name.endsWith(".css") && userStyleDir != null
                && new File(userStyleDir, name).isFile();
    }

    void saveUserStyle(String name, String css) throws IOException {
        FileOutputStream out = new FileOutputStream(
                new File(userStyleDir, userStyleFileName(name)));
        try {
            out.write(css.getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }

    // Cap on a user style's base filename in UTF-8 bytes (before ".css"), under the
    // common 255-byte per-name filesystem limit so a name can't make the write
    // fail. Bytes, not chars: a filename is byte-limited, and one char can be
    // several UTF-8 bytes.
    private static final int USER_STYLE_NAME_MAX_BYTES = 240;

    // The on-disk filename for a user style, from an arbitrary name: path
    // separators replaced, byte length capped, and always ending in a lowercase
    // ".css" - so the write can't fail on the name, and the case-sensitive readers
    // (userStyleNames, isUserStyle, the picker labels) match regardless of how the
    // extension was cased. Applied both on upload (via saveUserStyle) and by the
    // migration below, so the two agree on the resulting file.
    private static String userStyleFileName(String name) {
        String base = name.replaceAll("[/\\\\]", "_");
        if (base.toLowerCase(Locale.ROOT).endsWith(".css")) {
            base = base.substring(0, base.length() - 4);
        }
        // Trim whole characters off the end until it fits, so a multi-byte
        // character is never split into invalid bytes.
        while (base.getBytes(StandardCharsets.UTF_8).length > USER_STYLE_NAME_MAX_BYTES) {
            base = base.substring(0, base.length() - 1);
        }
        return base + ".css";
    }

    void deleteUserStyle(String name) {
        new File(userStyleDir, name).delete();
    }

    // One-time move of user styles from the old SharedPreferences store (style
    // name -> CSS text, with newlines escaped as "\n") into the flat <name>.css
    // files Slobber now serves. Runs on every start but is a no-op once the old
    // prefs have been cleared.
    private void migrateUserStylesToFiles() {
        SharedPreferences prefs = getSharedPreferences("userStyles", MODE_PRIVATE);
        Map<String, ?> stored = prefs.getAll();
        if (stored.isEmpty()) {
            return;
        }
        // Old selection title -> new .css filename, used below to fix up the
        // per-dictionary selections that named these styles. Only entries whose
        // CSS is on disk (already there, or written here) go in - and only those
        // are dropped from the old store below, so an entry that fails to write
        // keeps its CSS for the next start to retry rather than losing it.
        Map<String, String> renamed = new HashMap<>();
        SharedPreferences.Editor migrated = prefs.edit();
        for (Map.Entry<String, ?> entry : stored.entrySet()) {
            if (!(entry.getValue() instanceof String)) {
                continue;
            }
            String name = userStyleFileName(entry.getKey());
            if (!new File(userStyleDir, name).exists()) {
                String css = ((String) entry.getValue()).replace("\\n", "\n");
                try {
                    saveUserStyle(name, css);
                } catch (IOException e) {
                    Log.w(TAG, "Failed to migrate user style " + entry.getKey(), e);
                    continue;
                }
            }
            renamed.put(entry.getKey(), name);
            migrated.remove(entry.getKey());
        }
        // Rewrite each dictionary's selected style (style.<uri>, but not the
        // style.available.<uri> sets) that named a migrated user style, so it
        // resolves to the renamed <name>.css instead of silently falling back to
        // the default look.
        SharedPreferences articleView = getSharedPreferences(ARTICLE_VIEW_PREF, MODE_PRIVATE);
        SharedPreferences.Editor edit = articleView.edit();
        boolean changed = false;
        for (Map.Entry<String, ?> entry : articleView.getAll().entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(PREF_STYLE) || key.startsWith(PREF_STYLE_AVAILABLE)) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof String && renamed.containsKey(value)) {
                edit.putString(key, renamed.get((String) value));
                changed = true;
            }
        }
        if (changed) {
            edit.apply();
        }
        // Drop only the entries whose CSS is now on disk; any that failed to write
        // stay so the next start retries them.
        migrated.apply();
    }

    /**
     * Resolves a dictionary's effective style title from its raw stored
     * preference (storedStyleTitle - either a concrete title the user
     * picked, or the "Auto" sentinel) and the set of style titles it's
     * known to declare: "Auto" resolves to the first declared title that
     * looks dark while the dark UI theme is active, else to
     * defaultStyleTitle. A pure function of its arguments (no
     * SharedPreferences/WebView access), shared by getPreferredStyle()
     * (which already has this data cached as instance state) and
     * resolveStyleTitle() below (which needs the same resolution before
     * any WebView for the dictionary exists, so it reads the same
     * underlying SharedPreferences data directly instead).
     */
    static String resolveStyle(String storedStyleTitle, String autoStyleTitle,
                                String defaultStyleTitle, boolean isUIDark,
                                Set<String> availableTitles) {
        if (!storedStyleTitle.equals(autoStyleTitle)) {
            return storedStyleTitle;
        }
        if (isUIDark) {
            for (String title : availableTitles) {
                if (isDarkStyleTitle(title)) {
                    return title;
                }
            }
        }
        return defaultStyleTitle;
    }

    /**
     * Whether title looks like it names a dark/night style, going purely
     * by whether it contains "night" or "dark" (case-insensitively) -
     * dictionaries don't declare this explicitly, so this loose
     * convention is the only signal available.
     */
    static boolean isDarkStyleTitle(String title) {
        String lower = title.toLowerCase(Locale.ROOT);
        return lower.contains("night") || lower.contains("dark");
    }

    /**
     * The single entry point for "what style should apply to this
     * dictionary" from a bare slob uri - i.e. usable both before any
     * WebView for it exists (see getUrl(Blob) above, which needs the
     * answer to bake into the very first request URL, instead of only
     * correcting it after a default-styled page has already started
     * rendering) and from a live one (ArticleWebView.getPreferredStyle()
     * delegates here instead of keeping its own parallel resolution).
     * Always returns a concrete title, never null - "Default" is a real
     * answer, not an absence of one; callers that specifically need to
     * know whether that's the *literal* answer (to decide whether it's
     * worth adding as a URL param at all) compare against
     * R.string.default_style_title themselves.
     */
    String resolveStyleTitle(String slobUri) {
        SharedPreferences prefs = getSharedPreferences(ARTICLE_VIEW_PREF, Activity.MODE_PRIVATE);
        String autoStyleTitle = getString(R.string.auto_style_title);
        String defaultStyleTitle = getString(R.string.default_style_title);
        if (slobUri == null) {
            return defaultStyleTitle;
        }
        String storedStyleTitle = prefs.getString(PREF_STYLE + slobUri, autoStyleTitle);
        Set<String> availableTitles = prefs.getStringSet(
                PREF_STYLE_AVAILABLE + slobUri, Collections.<String>emptySet());
        return resolveStyle(storedStyleTitle, autoStyleTitle,
                defaultStyleTitle, isUIDark(), availableTitles);
    }

    // ---- Article color cache: the background/foreground the WebView actually
    //      paints for a dictionary+style, measured once by the real engine (see
    //      probecolors.js) and reused as the loading placeholder so it stops
    //      flashing a color guessed from the style's name. ----

    // '|' delimits the parts: it can't occur in a slob's uri tag and is
    // vanishingly unlikely in a style name; a stray collision would only pick a
    // slightly-off placeholder color the next probe corrects anyway. (The key
    // becomes a SharedPreferences XML attribute name, so a NUL/control-char
    // separator is out - it would corrupt the file or normalize to a space.)
    private String styleColorKey(String slobUri, String styleTitle) {
        return slobUri + '|' + styleTitle + '|' + (isUIDark() ? 'd' : 'l');
    }

    // Cached {background, foreground} for this dictionary+style, or null if never
    // measured. The UI light/dark state is part of the key: a style that adapts
    // via @media prefers-color-scheme paints differently in each.
    int[] getStyleColors(String slobUri, String styleTitle) {
        if (slobUri == null) {
            return null;
        }
        String value = getSharedPreferences(STYLE_COLOR_PREF, MODE_PRIVATE)
                .getString(styleColorKey(slobUri, styleTitle), null);
        if (value == null) {
            return null;
        }
        int sep = value.indexOf(',');
        try {
            return new int[]{
                    Integer.parseInt(value.substring(0, sep)),
                    Integer.parseInt(value.substring(sep + 1))};
        } catch (Exception e) {
            return null;
        }
    }

    private void saveStyleColors(String slobUri, String styleTitle, int bg, int fg) {
        if (slobUri == null) {
            return;
        }
        getSharedPreferences(STYLE_COLOR_PREF, MODE_PRIVATE).edit()
                .putString(styleColorKey(slobUri, styleTitle), bg + "," + fg)
                .apply();
    }

    // Parses a probecolors.js result and stores the resolved placeholder colors.
    void cacheProbedColors(String slobUri, String styleTitle, String json) {
        if (slobUri == null || json == null) {
            return;
        }
        double[] c;
        try {
            c = mapper.readValue(json, double[].class);
        } catch (Exception e) {
            Log.w(TAG, "Failed to parse probed colors: " + json, e);
            return;
        }
        if (c.length < 11) {
            return;
        }
        int bg = resolveBg(c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7]);
        int fg = Color.rgb((int) c[8], (int) c[9], (int) c[10]);
        saveStyleColors(slobUri, styleTitle, bg, fg);
    }

    // body wins when opaque, html is the fallback. A transparent or translucent
    // background reveals whatever is behind it - which, for HTML in a WebView, is
    // the browser's default white, the surface the content was authored against
    // (dictionary markup with dark text and no background of its own assumes a
    // white page). So a partly transparent color composites over white and a
    // fully transparent one (getComputedStyle's rgba(0,0,0,0) for unset, whose
    // RGB is a meaningless black) resolves to white - NOT the app's own theme
    // background, which for a dark UI would put dark text on a dark page.
    private static int resolveBg(double br, double bg, double bb, double ba,
                                 double hr, double hg, double hb, double ha) {
        if (ba >= 1) return Color.rgb((int) br, (int) bg, (int) bb);
        if (ha >= 1) return Color.rgb((int) hr, (int) hg, (int) hb);
        if (ba > 0) return composite(br, bg, bb, ba, Color.WHITE);
        if (ha > 0) return composite(hr, hg, hb, ha, Color.WHITE);
        return Color.WHITE;
    }

    private static int composite(double r, double g, double b, double a, int base) {
        return Color.rgb(
                (int) Math.round(r * a + Color.red(base) * (1 - a)),
                (int) Math.round(g * a + Color.green(base) * (1 - a)),
                (int) Math.round(b * a + Color.blue(base) * (1 - a)));
    }

    Slob getSlob(String slobId) {
        return slobber.getSlob(slobId);
    }

    synchronized boolean addDictionary(Uri uri) {
        SlobDescriptor newDesc = SlobDescriptor.fromUri(getApplicationContext(), uri.toString());
        if (newDesc.id != null) {
            for (SlobDescriptor d: dictionaries) {
                if (d.id != null && d.id.equals(newDesc.id)) {
                    return true;
                }
            }
        }
        newDesc.order = dictionaries.nextOrder();
        dictionaries.add(newDesc);
        return false;
    }


    Slob findSlob(String slobOrUri) {
        return slobber.findSlob(slobOrUri);
    }

    String getSlobURI(String slobId) {
        return  slobber.getSlobURI(slobId);
    }


    void addBookmark(String contentURL) {
        bookmarks.add(contentURL);
    }

    void removeBookmark(String contentURL) {
        bookmarks.remove(contentURL);
    }

    boolean isBookmarked(String contentURL) {
        return bookmarks.contains(contentURL);
    }

    private void setLookupResult(String query, Iterator<Slob.Blob> data) {
        this.lastResult.setData(data);
        lookupQuery = query;
        SharedPreferences.Editor edit = prefs().edit();
        edit.putString("query", query);
        edit.apply();
    }

    String getLookupQuery() {
        return lookupQuery;
    }

    private Future<?> currentLookupTask;
    // Bumped at the start of every lookup so a result arriving from a superseded
    // background find is discarded. Only touched on the main thread - lookup()
    // and the delivered result both run there - so it needs no synchronization.
    private int lookupGeneration;

    void lookup(String query) {
        this.lookup(query, true);
    }

    void lookup(final String query, boolean async) {
        // Invalidate any in-flight find on every path (empty, sync and async),
        // not just async: a running task can't be stopped by cancel(false) and
        // posts its result unconditionally, so a superseded result is discarded
        // by the generation mismatch check below.
        final int generation = ++lookupGeneration;
        if (currentLookupTask != null) {
            currentLookupTask.cancel(false);
            notifyLookupCanceled(query);
            currentLookupTask = null;
        }
        notifyLookupStarted(query);
        if (query == null || query.equals("")) {
            setLookupResult("", new ArrayList<Slob.Blob>().iterator());
            notifyLookupFinished(query);
            return;
        }

        if (async) {
            currentLookupTask = Util.runAsync(() -> find(query), result -> {
                if (generation != lookupGeneration) {
                    return;
                }
                setLookupResult(query, result);
                notifyLookupFinished(query);
                currentLookupTask = null;
            });
        }
        else {
            setLookupResult(query, find(query));
            notifyLookupFinished(query);
        }
    }

    private void notifyLookupStarted(String query) {
        for (LookupListener l : lookupListeners) {
            l.onLookupStarted(query);
        }
    }

    private void notifyLookupFinished(String query) {
        for (LookupListener l : lookupListeners) {
            l.onLookupFinished(query);
        }
    }

    private void notifyLookupCanceled (String query) {
        for (LookupListener l : lookupListeners) {
            l.onLookupCanceled(query);
        }
    }

    private List<LookupListener> lookupListeners = new ArrayList<LookupListener>();

    void addLookupListener(LookupListener listener){
        lookupListeners.add(listener);
    }

    void removeLookupListener(LookupListener listener){
        lookupListeners.remove(listener);
    }


    static class FileTooBigException extends IOException {
    }
}
