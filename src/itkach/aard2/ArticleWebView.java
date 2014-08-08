package itkach.aard2;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
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

//                if (!url.equals(initialUrl) && url.startsWith("http://localhost:8013")) {
//                    Intent intent = new Intent(getContext(), StandaloneArticleActivity.class);
//                    intent.setData(Uri.parse(url));
//                    getContext().startActivity(intent);
//                    return true;
//                }
                Log.d("NOT overriding loading of", url);
                return false;
            };
        });
    }

    @Override
    public void loadUrl(String url, Map<String, String> additionalHttpHeaders) {
        initialUrl = url;
        super.loadUrl(url, additionalHttpHeaders);
    }

}
