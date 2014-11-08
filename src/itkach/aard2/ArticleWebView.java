package itkach.aard2;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ArticleWebView extends WebView {

    private final StyleTitlesReceiver styleReceiver;

    private final String styleSwitcherJs;
    String TAG = getClass().getName();

    static final String PREF = "articleView";
    private static final String PREF_TEXT_ZOOM = "textZoom";
    private static final String PREF_STYLE = "style.";
    static final String PREF_REMOTE_CONTENT = "remoteContent";
    static final String PREF_REMOTE_CONTENT_ALWAYS = "always";
    static final String PREF_REMOTE_CONTENT_WIFI = "wifi";
    static final String PREF_REMOTE_CONTENT_NEVER = "never";

    String initialUrl;
    Set<String> externalSchemes = new HashSet<String>(){
        {
            add("https");
            add("ftp");
            add("sftp");
            add("mailto");
            add("geo");
        }
    };

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

        styleReceiver = new StyleTitlesReceiver(
                getResources().getString(R.string.default_style_title));

        this.addJavascriptInterface(styleReceiver, "android");

        //Hardware rendering is buggy
        //https://code.google.com/p/android/issues/detail?id=63738
        //this.setLayerType(LAYER_TYPE_SOFTWARE, null);
        this.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d(TAG, "Page finished: " + url);
                view.loadUrl("javascript:"+styleSwitcherJs);
                view.loadUrl("javascript:android.setTitles($styleSwitcher.getTitles())");
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
                byte[] msgBytes;
                try {
                    msgBytes = msg.getBytes("UTF-8");
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
                InputStream inputStream = new ByteArrayInputStream(msgBytes);
                return new WebResourceResponse("text/plain", "UTF-8", inputStream);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view,
                    final String url) {
                Log.d(TAG, String.format("shouldOverrideUrlLoading: %s (current %s, initial %s)",
                        url, view.getUrl(), initialUrl));
                //String referer = view.getUrl();
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
        return styleReceiver.titles;
    }

    void setStyle(String styleTitle) {
        saveStylePref(styleTitle);
        this.loadUrl(
         String.format("javascript:$styleSwitcher.setStyle('%s')", styleTitle));
    }

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences(PREF, Activity.MODE_PRIVATE);
    }

    @Override
    public void loadUrl(String url, Map<String, String> additionalHttpHeaders) {
        initialUrl = url;
        super.loadUrl(url, additionalHttpHeaders);
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
        String url = this.getUrl();
        if (url == null) {
            return null;
        }
        Uri uri = Uri.parse(url);
        List<String> pathSegments = uri.getPathSegments();
        if (pathSegments.size() < 2) {
            return null;
        }
        return pathSegments.get(1);
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
