package itkach.aard2;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class ArticleWebView extends SearchableWebView {

    public static final String LOCALHOST = Application.LOCALHOST;
    private final String styleSwitcherJs;
    private final String defaultStyleTitle;
    private final String autoStyleTitle;

    String TAG = getClass().getSimpleName();

    private static final String PREF_TEXT_ZOOM = "textZoom";
    static final String PREF_REMOTE_CONTENT = "remoteContent";
    static final String PREF_REMOTE_CONTENT_ALWAYS = "always";
    static final String PREF_REMOTE_CONTENT_WIFI = "wifi";
    static final String PREF_REMOTE_CONTENT_NEVER = "never";

    Set<String> externalSchemes = new HashSet<String>(){
        {
            add("https");
            add("ftp");
            add("sftp");
            add("mailto");
            add("geo");
        }
    };

    boolean isExternal(Uri uri) {
        String scheme = uri.getScheme();
        String host = uri.getHost();

        return scheme != null && (
                externalSchemes.contains(scheme) ||
                    (scheme.equals("http") && !host.equals(LOCALHOST)));
    }

    private SortedSet<String>   styleTitles  = new TreeSet<String>();

    private String              currentSlobId;
    private String              currentSlobUri;
    private ConnectivityManager connectivityManager;

    boolean forceLoadRemoteContent;

    @JavascriptInterface
    public void setStyleTitles(String[] titles) {
        Log.d(TAG, String.format("Got %d style titles", titles.length));
        if (titles.length == 0) {
            return;
        }
        SortedSet newStyleTitlesSet = new TreeSet<String>(Arrays.asList(titles));
        if (!this.styleTitles.equals(newStyleTitlesSet)) {
            this.styleTitles = newStyleTitlesSet;
            saveAvailableStylesPref(this.styleTitles);
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            for (String title : titles) {
                Log.d(TAG, title);
            }
        }
    }

    public ArticleWebView(Context context) {
        this(context, null);
    }

    public ArticleWebView(Context context, AttributeSet attrs) {
        super(context, attrs);

        connectivityManager = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);

        styleSwitcherJs = Application.jsStyleSwitcher;

        WebSettings settings = this.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        Resources r = getResources();
        defaultStyleTitle = r.getString(R.string.default_style_title);
        autoStyleTitle = r.getString(R.string.auto_style_title);

        this.addJavascriptInterface(this, "$SLOB");

        this.setWebViewClient(new WebViewClient() {

            byte[] noBytes = new byte[0];

            Map<String, List<Long>> times = new HashMap<String, List<Long>>();

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                Log.d(TAG, "onPageStarted: " + url);
                if (url.startsWith("about:")) {
                    return;
                }
                if (times.containsKey(url)) {
                    Log.d(TAG, "onPageStarted: already ready seen " + url);
                    times.get(url).add(System.currentTimeMillis());
                    return;
                }
                else {
                    List<Long> tsList = new ArrayList<Long>();
                    tsList.add(System.currentTimeMillis());
                    times.put(url, tsList);
                    // Only injects $styleSwitcher itself (needed so
                    // onPageFinished below can ask it what styles this
                    // page declares) - does NOT re-apply a style. The
                    // canned style is already correctly active from the
                    // very first byte the server sent (see Slobber's
                    // StylePreference/Application.getUrl(Blob)); calling
                    // $styleSwitcher.setStyle() again here would toggle
                    // stylesheets' disabled state pointlessly, forcing an
                    // avoidable reflow shortly after the page already
                    // rendered correctly - which is visible as a jump in
                    // the surrounding native UI (the action bar, or - in
                    // fullscreen mode - a brief flash of the system status
                    // bar as the reflow momentarily interrupts immersive
                    // mode), not just inside the WebView's own content.
                    view.loadUrl("javascript:" + styleSwitcherJs);
                }

            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d(TAG, "onPageFinished: " + url);
                if (url.startsWith("about:")) {
                    return;
                }
                if (times.containsKey(url)) {
                    List<Long> tsList = times.get(url);
                    long ts = tsList.remove(tsList.size() - 1);
                    Log.d(TAG, "onPageFinished: finished: " + url + " in " + (System.currentTimeMillis() - ts));
                    if (tsList.isEmpty()) {
                        Log.d(TAG, "onPageFinished: really done with " + url);
                        times.remove(url);
                    }
                }
                else {
                    Log.w(TAG, "onPageFinished: Unexpected page finished event for " + url);
                }
                // Discovers this page's declared styles (for the style
                // picker dialog).
                view.loadUrl("javascript:" + styleSwitcherJs +
                        ";$SLOB.setStyleTitles($styleSwitcher.getTitles())");
                // A canned style is already correctly active from the very
                // first byte the server sent (see Slobber's StylePreference
                // / Application.getUrl(Blob)), so re-applying one here
                // would just force an avoidable reflow - see onPageStarted
                // above. A saved *user* style is different: it's plain
                // client-side CSS injection the server has no way to
                // apply (Slobber deliberately knows nothing about this
                // Android-only feature), so it still needs to be
                // (re-)injected on every fresh load, same as before.
                String preferredStyle = getPreferredStyle();
                if (isUserStyle(preferredStyle)) {
                    setStyle(preferredStyle);
                }
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                Uri parsed;
                try {
                    parsed = Uri.parse(url);
                } catch (Exception e) {
                    Log.d(TAG, "Failed to parse url: " + url, e);
                    return super.shouldInterceptRequest(view, url);
                }
                if (parsed.isRelative()) {
                    return null;
                }
                String host = parsed.getHost();
                if (host == null || host.toLowerCase().equals(LOCALHOST)) {
                    return null;
                }
                if (allowRemoteContent()) {
                    return null;
                }
                return new WebResourceResponse("text/plain", "UTF-8",
                        new ByteArrayInputStream(noBytes));
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view,
                                                    final String url) {
                Log.d(TAG, String.format("shouldOverrideUrlLoading: %s (current %s)",
                        url, view.getUrl()));

                Uri uri = Uri.parse(url);
                String scheme = uri.getScheme();
                String host = uri.getHost();

                if (isExternal(uri)) {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
                    getContext().startActivity(browserIntent);
                    return true;
                }

                String fragment = uri.getFragment();
                if (fragment != null ) {
                    Uri current = Uri.parse(view.getUrl());
                    Log.d(TAG, "shouldOverrideUrlLoading URL with fragment: " + url);
                    if (scheme.equals(current.getScheme()) &&
                            host.equals(current.getHost()) &&
                            uri.getPort() == current.getPort() &&
                            uri.getPath().equals(current.getPath())) {
                        Log.d(TAG, "NOT overriding loading of same page link " + url);
                        return false;
                    }
                }

                if (scheme.equals("http") && host.equals(LOCALHOST) && uri.getQueryParameter("blob") == null) {
                    Intent intent = new Intent(getContext(), ArticleCollectionActivity.class);
                    intent.setData(uri);
                    getContext().startActivity(intent);
                    Log.d(TAG, "Overriding loading of " + url);
                    return true;
                }
                Log.d(TAG, "NOT overriding loading of " + url);
                return false;
            }
        });

        this.setOnLongClickListener(new OnLongClickListener(){

            @Override
            public boolean onLongClick(View view) {
                WebView.HitTestResult hitTestResult = getHitTestResult();
                int resultType= hitTestResult.getType();
                Log.d(TAG, String.format(
                        "Long tap on element %s (%s)",
                        resultType,
                        hitTestResult.getExtra()));
                if (resultType == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                        resultType == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                    String url = hitTestResult.getExtra();
                    Uri uri = Uri.parse(url);
                    if (isExternal(uri)) {
                        Intent share = new Intent(Intent.ACTION_SEND);
                        share.setType("text/plain");
                        share.addFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET);
                        share.putExtra(Intent.EXTRA_TEXT, url);
                        getContext().startActivity(Intent.createChooser(share, "Share Link"));
                        return true;
                    }
                }
                return false;
            }
        });

        applyTextZoomPref();
    }

    boolean allowRemoteContent() {
        if (forceLoadRemoteContent) {
            return true;
        }
        SharedPreferences prefs = this.prefs();
        String prefValue = prefs.getString(PREF_REMOTE_CONTENT, PREF_REMOTE_CONTENT_WIFI);
        if (prefValue.equals(PREF_REMOTE_CONTENT_ALWAYS)) {
            return true;
        }
        if (prefValue.equals(PREF_REMOTE_CONTENT_NEVER)) {
            return false;
        }
        if (prefValue.equals(PREF_REMOTE_CONTENT_WIFI)) {
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
            if (networkInfo != null) {
                int networkType = networkInfo.getType();
                if (networkType == ConnectivityManager.TYPE_WIFI ||
                        networkType == ConnectivityManager.TYPE_ETHERNET) {
                    return true;
                }
            }
        }
        return false;
    }

    private SharedPreferences userStylesPrefs() {
        return getContext().getSharedPreferences("userStyles", Activity.MODE_PRIVATE);
    }

    // Whether styleTitle names a user-authored custom style rather than one
    // of a dictionary's own declared ("canned") styles or the Default/Auto
    // sentinels.
    private boolean isUserStyle(String styleTitle) {
        return userStylesPrefs().contains(styleTitle);
    }

    String[] getAvailableStyles() {
        Map<String, ?> data = userStylesPrefs().getAll();
        List<String> names = new ArrayList<String>(data.keySet());
        Util.sort(names);
        names.addAll(styleTitles);
        names.add(defaultStyleTitle);
        names.add(autoStyleTitle);
        return names.toArray(new String[names.size()]);
    }

    private void setStyle(String styleTitle) {
        String js;
        if (isUserStyle(styleTitle)) {
            String css = userStylesPrefs().getString(styleTitle, "");
            String elementId = getCurrentSlobId();
            js = String.format(
                    "javascript:" + Application.jsUserStyle, elementId, css);
        }
        else {
            js = String.format(
                    "javascript:" + Application.jsClearUserStyle + Application.jsSetCannedStyle,
                    getCurrentSlobId(), styleTitle);
        }
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, js);
        }
        this.loadUrl(js);
    }

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences(Application.ARTICLE_VIEW_PREF, Activity.MODE_PRIVATE);
    }

    void applyTextZoomPref() {
        SharedPreferences prefs = prefs();
        int textZoom = prefs.getInt(PREF_TEXT_ZOOM, 100);
        WebSettings settings = getSettings();
        settings.setTextZoom(textZoom);
    }

    private void saveTextZoomPref() {
        SharedPreferences prefs = prefs();
        int textZoom = getSettings().getTextZoom();
        SharedPreferences.Editor e = prefs.edit();
        e.putInt(PREF_TEXT_ZOOM, textZoom);
        boolean success = e.commit();
        if (!success) {
            Log.w(TAG, "Failed to save article view text zoom pref");
        }
    }

    private String getCurrentSlobId() {
        return currentSlobId;
    }

    private void saveAvailableStylesPref(Set<String> styleTitles) {
        SharedPreferences prefs = prefs();
        SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet(Application.PREF_STYLE_AVAILABLE + currentSlobUri, styleTitles);
        boolean success = editor.commit();
        if (!success) {
            Log.w(TAG, "Failed to save article view available styles pref");
        }
    }

    private void loadAvailableStylesPref() {
        if (currentSlobUri == null) {
            Log.w(TAG, "Can't load article view available styles pref - slob uri is null");
            return;
        }
        SharedPreferences prefs = prefs();
        Log.d(TAG, "Available styles before pref load: " + styleTitles.size());
        styleTitles = new TreeSet(
                prefs.getStringSet(Application.PREF_STYLE_AVAILABLE + currentSlobUri,
                        Collections.EMPTY_SET));
        Log.d(TAG, "Loaded available styles: " + styleTitles.size());
    }

    void saveStylePref(String styleTitle) {
        if (currentSlobUri == null) {
            Log.w(TAG, "Can't save article view style pref - slob uri is null");
            return;
        }
        SharedPreferences prefs = prefs();
        String prefName = Application.PREF_STYLE + currentSlobUri;
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(prefName, styleTitle);
        boolean success = editor.commit();
        if (!success) {
            Log.w(TAG, "Failed to save article view style pref");
        }
    }

    @JavascriptInterface
    public String getPreferredStyle() {
        if (currentSlobUri == null) {
            return "";
        }
        // Application.resolveStyleTitle() re-reads the same
        // SharedPreferences data this instance's styleTitles/currentSlobUri
        // are themselves sourced from (setStyleTitles() persists them
        // synchronously - see saveAvailableStylesPref()) - so delegating
        // here instead of resolving locally can't observe stale data.
        String result = getApplication().resolveStyleTitle(currentSlobUri);
        Log.d(TAG, "getPreferredStyle() will return " + result);
        return result;
    }

    @JavascriptInterface
    public String exportStyleSwitcherAs() {
        return "$styleSwitcher";
    }

    @JavascriptInterface
    public void onStyleSet(String title) {
        Log.d(TAG, "Style set! " + title);
    }

    void applyStylePref() {
        String styleTitle = getPreferredStyle();
        this.setStyle(styleTitle);
    }

    boolean textZoomIn() {
        WebSettings settings = getSettings();
        int newZoom = settings.getTextZoom() + 20;
        if (newZoom <= 200) {
            settings.setTextZoom(newZoom);
            saveTextZoomPref();
            return true;
        }
        else {
            return false;
        }
    }

    boolean textZoomOut() {
        WebSettings settings = getSettings();
        int newZoom = settings.getTextZoom() - 20;
        if (newZoom >= 40) {
            settings.setTextZoom(newZoom);
            saveTextZoomPref();
            return true;
        }
        else {
            return false;
        }
    }

    void resetTextZoom() {
        getSettings().setTextZoom(100);
        saveTextZoomPref();
    }


    @Override
    public void loadUrl(String url, Map<String, String> additionalHttpHeaders) {
        beforeLoadUrl(url);
        super.loadUrl(url, additionalHttpHeaders);
    }

    @Override
    public void loadUrl(String url) {
        beforeLoadUrl(url);
        super.loadUrl(url);
    }

    private void beforeLoadUrl(String url) {
        setCurrentSlobIdFromUrl(url);
        if (!url.startsWith("javascript:")) {
            updateBackgrounColor();
        }
    }

    private void updateBackgrounColor() {
        int color = Color.WHITE;
        String preferredStyle = getPreferredStyle();
        // webview's default background may "show through" before page
        // load started and/or before page's style applies (and even after that if
        // style doesn't explicitly set background).
        // this is a hack to preemptively set "right" background and prevent
        // extra flash
        //
        // TODO Hack it even more - allow style title to include background color spec
        // so that this can work with "strategically" named user css
        if (Application.isDarkStyleTitle(preferredStyle)) {
            color = Color.BLACK;
        }
        setBackgroundColor(color);
    }

    private Application getApplication() {
        return (Application)((Activity)getContext()).getApplication();
    }

    private void setCurrentSlobIdFromUrl(String url) {
        if (!url.startsWith("javascript:")) {
            Uri uri = Uri.parse(url);
            BlobDescriptor bd = BlobDescriptor.fromUri(uri);
            if (bd != null) {
                currentSlobId = bd.slobId;
                currentSlobUri = getApplication().getSlobURI(currentSlobId);
                loadAvailableStylesPref();
            }
            else {
                currentSlobId = null;
                currentSlobUri = null;
            }
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, String.format("currentSlobId set from url %s to %s, uri %s",
                        url, currentSlobId, currentSlobUri));
            }
        }
    }

}
