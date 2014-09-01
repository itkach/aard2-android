package itkach.aard2;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ArticleWebView extends WebView {

    String TAG = getClass().getName();

    private static final String PREF_TEXT_ZOOM = "textZoom";

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

        WebSettings settings = this.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        //Hardware rendering is buggy
        //https://code.google.com/p/android/issues/detail?id=63738
        this.setLayerType(LAYER_TYPE_SOFTWARE, null);
        this.setWebViewClient(new WebViewClient() {

            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d(TAG, "Page finished: " + url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view,
                    final String url) {
                Log.d(TAG, String.format("shouldOverrideUrlLoading: %s (current %s, initial %s)",
                        url, view.getUrl(), initialUrl));
                String referer = view.getUrl();
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
                      intent.putExtra("referer", referer);
                      getContext().startActivity(intent);
                      Log.d("Overriding loading of", url);
                      return true;
                }

                Log.d("NOT overriding loading of", url);
                return false;
            };
        });

        applyTextZoomPref();
    }

    private SharedPreferences prefs() {
        return getContext().getSharedPreferences("articleView", Activity.MODE_PRIVATE);
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
