package itkach.aard2;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class ArticleWebView extends WebView {

    public static final String LOCALHOST = Application.LOCALHOST;
    private final String styleSwitcherJs;

    String TAG = getClass().getSimpleName();

    static final String PREF = "articleView";
    private static final String PREF_TEXT_ZOOM = "textZoom";
    private static final String PREF_STYLE = "style.";
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

    private String[]            styleTitles;
    private final String[]      defaultStyles;

    private String              currentSlobId;
    private String              currentSlobUri;
    private ConnectivityManager connectivityManager;

    private Timer               timer;
    private TimerTask           applyStylePref;

    boolean forceLoadRemoteContent;

    @JavascriptInterface
    public void setStyleTitles(String[] titles) {
        Log.d(TAG, String.format("Got %d style titles", titles.length));
        this.styleTitles = concat(defaultStyles, titles);
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            for (String title : titles) {
                Log.d(TAG, title);
            }
        }
    }

    static String[] concat(String[] A, String[] B) {
        int aLen = A.length;
        int bLen = B.length;
        String[] C= new String[aLen+bLen];
        System.arraycopy(A, 0, C, 0, aLen);
        System.arraycopy(B, 0, C, aLen, bLen);
        return C;
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

        defaultStyles = new String[]{getResources().getString(R.string.default_style_title)};

        this.addJavascriptInterface(this, "$SLOB");

        timer = new Timer();

        final Runnable applyStyleRunnable = new Runnable() {
            @Override
            public void run() {
                applyStylePref();
            }
        };

        applyStylePref = new TimerTask() {
            @Override
            public void run() {
                android.os.Handler handler = getHandler();
                if (handler != null) {
                    handler.post(applyStyleRunnable);
                }
            }
        };

        this.setWebViewClient(new WebViewClient() {

            byte[] noBytes = new byte[0];

            Map<String, Long> times = new HashMap<String, Long>();

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                Log.d(TAG, "onPageStarted: " + url);
                times.put(url, System.currentTimeMillis());
                view.loadUrl("javascript:" + styleSwitcherJs);
                try {
                    timer.schedule(applyStylePref, 30, 100);
                } catch (IllegalStateException ex) {
                    Log.w(TAG, "Failed to schedule applyStylePref", ex);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Long time = times.remove(url);
                if (time != null && Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Page finished: " + url + " in " + (System.currentTimeMillis() - time));
                }
                applyStylePref.cancel();
                view.loadUrl("javascript:" + "$SLOB.setStyleTitles($styleSwitcher.getTitles())");
                applyStylePref();
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                Uri parsed;
                try {
                    parsed = Uri.parse(url);
                }
                catch (Exception e) {
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

                if (externalSchemes.contains(scheme) ||
                        (scheme.equals("http") && !host.equals(LOCALHOST))) {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
                    getContext().startActivity(browserIntent);
                    return true;
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

    String[] getAvailableStyles() {
        final SharedPreferences prefs = getContext().getSharedPreferences(
                "userStyles", Activity.MODE_PRIVATE);
        Map<String, ?> data = prefs.getAll();
        List<String> names = new ArrayList<String>(data.keySet());
        Collections.sort(names);
        return concat(styleTitles, names.toArray(new String[names.size()]));
    }

    void setStyle(String styleTitle) {
        saveStylePref(styleTitle);
        String js;
        final SharedPreferences prefs = getContext().getSharedPreferences(
                "userStyles", Activity.MODE_PRIVATE);
        if (prefs.contains(styleTitle)){
            String css = prefs.getString(styleTitle, "");
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
        return getContext().getSharedPreferences(PREF, Activity.MODE_PRIVATE);
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

    void saveStylePref(String styleTitle) {
        String currentPref = getPreferredStyle();
        if (currentPref.equals(styleTitle)) {
            return;
        }
        if (currentSlobUri == null) {
            Log.w(TAG, "Can't save article view style pref - slob uri is null");
            return;
        }
        SharedPreferences prefs = prefs();
        String prefName = PREF_STYLE + currentSlobUri;
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
        SharedPreferences prefs = prefs();
        return prefs.getString(PREF_STYLE + currentSlobUri, "");
    }

    @JavascriptInterface
    public String exportStyleSwitcherAs() {
        return "$styleSwitcher";
    }

    @JavascriptInterface
    public void onStyleSet(String title) {
        Log.d(TAG, "Style set! " + title);
        applyStylePref.cancel();
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
        updateBackgrounColor();
    }

    private void updateBackgrounColor() {
        String preferredStyle = getPreferredStyle();
        if (preferredStyle != null) {
            preferredStyle = preferredStyle.toLowerCase();
            // webview's default background may "show through" before page
            // load started and/or before page's style applies (and even after that if
            // style doesn't explicitly set background).
            // this is a hack to preemptively set "right" background and prevent
            // extra flash
            //
            // TODO Hack it even more - allow style title to include background color spec
            // so that this can work with "strategically" named user css
            if (preferredStyle.contains("night") || preferredStyle.contains("dark")) {
                setBackgroundColor(Color.BLACK);
                return;
            }
        }
        setBackgroundColor(Color.WHITE);
    }

    private void setCurrentSlobIdFromUrl(String url) {
        if (!url.startsWith("javascript:")) {
            Uri uri = Uri.parse(url);
            BlobDescriptor bd = BlobDescriptor.fromUri(uri);
            if (bd != null) {
                currentSlobId = bd.slobId;
                Application app = (Application)((Activity)getContext()).getApplication();
                currentSlobUri = app.getSlobURI(currentSlobId);
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

    @Override
    public void destroy() {
        super.destroy();
        timer.cancel();
    }
}
