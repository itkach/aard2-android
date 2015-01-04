package itkach.aard2;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class ArticleWebView extends WebView {

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
    private ConnectivityManager connectivityManager;
    private Timer timer;

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

        this.addJavascriptInterface(this, "android");

        //Hardware rendering is buggy
        //https://code.google.com/p/android/issues/detail?id=63738
        //this.setLayerType(LAYER_TYPE_SOFTWARE, null);

        timer = new Timer();

        this.setWebViewClient(new WebViewClient() {

            byte[] noBytes = new byte[0];

            Runnable applyStyleRunnable = new Runnable() {
                @Override
                public void run() {
                    applyStylePref();
                }
            };

            TimerTask applyStylePref = new TimerTask() {
                @Override
                public void run() {
                    android.os.Handler handler = getHandler();
                    if (handler != null) {
                        handler.post(applyStyleRunnable);
                    }
                }
            };

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                BlobDescriptor bd = BlobDescriptor.fromUri(Uri.parse(url));
                currentSlobId = bd == null ? null : bd.slobId;
                Log.d(TAG, "onPageStarted: " + url);
                view.loadUrl("javascript:" + styleSwitcherJs);
                try {
                    timer.schedule(applyStylePref, 60, 250);
                } catch (IllegalStateException ex) {
                    Log.w(TAG, "Failed to schedule applyStylePref", ex);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d(TAG, "onPageFinished: " + url);
                applyStylePref.cancel();
                view.loadUrl("javascript:" + "android.setStyleTitles($styleSwitcher.getTitles())");
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
                if (host == null || host.toLowerCase().equals("localhost")) {
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
                        (scheme.equals("http") && !host.equals("localhost"))) {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
                    getContext().startActivity(browserIntent);
                    return true;
                }

                if (scheme.equals("http") && host.equals("localhost") && uri.getQueryParameter("blob") == null) {
                      Intent intent = new Intent(getContext(), ArticleCollectionActivity.class);
                      intent.setData(uri);
                      getContext().startActivity(intent);
                      Log.d("Overriding loading of", url);
                      return true;
                }

                Log.d("NOT overriding loading of", url);
                return false;
            }
        });

        applyTextZoomPref();
    }

    boolean allowRemoteContent() {
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
            Log.w(getClass().getName(), "Failed to save article view text zoom pref");
        }
    }

    private String getCurrentSlobId() {
        return currentSlobId;
    }

    void saveStylePref(String styleTitle) {
        String slobId = getCurrentSlobId();
        String currentPref = getStylePref(slobId);
        if (currentPref.equals(styleTitle)) {
            return;
        }
        SharedPreferences prefs = prefs();
        String prefName = PREF_STYLE + slobId;
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(prefName, styleTitle);
        boolean success = editor.commit();
        if (!success) {
            Log.w(getClass().getName(), "Failed to save article view style pref");
        }
    }

    String getStylePref(String slobId) {
        if (slobId == null) {
            return "";
        }
        SharedPreferences prefs = prefs();
        return prefs.getString(PREF_STYLE + slobId, "");
    }

    void applyStylePref() {
        String styleTitle = getStylePref(getCurrentSlobId());
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
    public void destroy() {
        super.destroy();
        timer.cancel();
    }
}
