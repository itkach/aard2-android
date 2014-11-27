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
import java.util.HashSet;
import java.util.Set;

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

    private String[]        styleTitles;
    private final String[]  defaultStyles;


    private String          currentSlobId;

    @JavascriptInterface
    public void setStyleTitles(String[] titles) {
        Log.d(TAG, String.format("Got %d style titles", titles.length));
        this.styleTitles = concat(defaultStyles, titles);
        for (String title : titles) {
            Log.d(getClass().getName(), title);
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

        styleSwitcherJs = Application.styleSwitcherJs;

        WebSettings settings = this.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        defaultStyles = new String[]{getResources().getString(R.string.default_style_title)};

        this.addJavascriptInterface(this, "android");

        //Hardware rendering is buggy
        //https://code.google.com/p/android/issues/detail?id=63738
        //this.setLayerType(LAYER_TYPE_SOFTWARE, null);

        this.setWebViewClient(new WebViewClient() {

            byte[] noBytes = new byte[0];

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                BlobDescriptor bd = BlobDescriptor.fromUri(Uri.parse(url));
                currentSlobId = bd == null ? null : bd.slobId;
                Log.d(TAG, "onPageStarted: " + url);
                view.loadUrl("javascript:" + styleSwitcherJs);
            }

            @Override
            public void onLoadResource(WebView view, String url) {
                applyStylePref();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d(TAG, "onPageFinished: " + url);
                view.loadUrl("javascript:" + "android.setStyleTitles($styleSwitcher.getTitles())");
                applyStylePref();
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                Log.d(TAG, "Should intercept? " + url);
                Uri parsed;
                try {
                    parsed = Uri.parse(url);
                }
                catch (Exception e) {
                    Log.d(TAG, "Failed to parse url: " + url, e);
                    return super.shouldInterceptRequest(view, url);
                }
                if (parsed.isRelative()) {
                    Log.d(TAG, "Relative url, not intercepting: " + url);
                    return null;
                }
                String host = parsed.getHost();
                if (host == null || host.toLowerCase().equals("localhost")) {
                    Log.d(TAG, "Local url, not intercepting: " + url);
                    return null;
                }
                if (allowRemoteContent(getContext())) {
                    Log.d(TAG, String.format("Remote content from %s is allowed", url));
                    return null;
                }
                String msg = String.format("Remote content from %s is not allowed", url);
                Log.d(TAG, msg);
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
                      //intent.putExtra("referer", referer);
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

    boolean allowRemoteContent(Context context) {
        SharedPreferences prefs = this.prefs();
        String prefValue = prefs.getString(PREF_REMOTE_CONTENT, PREF_REMOTE_CONTENT_WIFI);
        Log.d(TAG, "Remote content preference: " + prefValue);
        if (prefValue.equals(PREF_REMOTE_CONTENT_ALWAYS)) {
            return true;
        }
        if (prefValue.equals(PREF_REMOTE_CONTENT_NEVER)) {
            return false;
        }
        if (prefValue.equals(PREF_REMOTE_CONTENT_WIFI)) {
            ConnectivityManager cm = (ConnectivityManager) context
                    .getSystemService(Context.CONNECTIVITY_SERVICE);

            NetworkInfo networkInfo = cm.getActiveNetworkInfo();
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
        return styleTitles;
    }

    void setStyle(String styleTitle) {
        saveStylePref(styleTitle);
        this.loadUrl(
         String.format("javascript:(window.$styleSwitcher && window.$styleSwitcher.setStyle('%s'))", styleTitle));
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
        if (slobId == null) {
            return;
        }
        SharedPreferences prefs = prefs();
        String prefName = PREF_STYLE + slobId;
        String currentPref = prefs.getString(prefName, "");
        if (currentPref.equals(styleTitle)) {
            return;
        }
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(prefName, styleTitle);
        boolean success = editor.commit();
        if (!success) {
            Log.w(getClass().getName(), "Failed to save article view style pref");
        }
    }

    void applyStylePref() {
        String slobId = getCurrentSlobId();
        if (slobId == null) {
            return;
        }
        SharedPreferences prefs = prefs();
        String styleTitle = prefs.getString(PREF_STYLE + slobId, "");
        Log.d(TAG, "Set style pref for " + slobId + ": " + styleTitle);
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

}
