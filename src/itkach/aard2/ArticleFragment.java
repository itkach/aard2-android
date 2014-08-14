package itkach.aard2;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.ProgressBar;

public class ArticleFragment extends Fragment {

    public static final String ARG_URL = "articleUrl";

    private ArticleWebView view;
    private MenuItem miBookmark;
    private Drawable icBookmark;
    private Drawable icBookmarkO;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        icBookmark = Icons.BOOKMARK.forActionBar();
        icBookmarkO = Icons.BOOKMARK_O.forActionBar();
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.article, menu);
        miBookmark = menu.findItem(R.id.action_bookmark_article);
        String url = view.getUrl();
        Application app = (Application)getActivity().getApplication();
        try {
            displayBookmarked(app.isBookmarked(url));
        }
        catch (Exception ex) {
            miBookmark.setVisible(false);
           Log.d(getTag(), "Not bookmarable: " + url, ex);
        }
    }

    private void displayBookmarked(boolean value) {
        if (value) {
            miBookmark.setChecked(true);
            miBookmark.setIcon(icBookmark);
        } else {
            miBookmark.setChecked(false);
            miBookmark.setIcon(icBookmarkO);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_find_in_page) {
            view.showFindDialog(null, false);
            return true;
        }
        if (itemId == R.id.action_bookmark_article) {
            Application app = (Application)getActivity().getApplication();
            String url = view.getUrl();
            if (url != null) {
                if (item.isChecked()) {
                    app.removeBookmark(url);
                    displayBookmarked(false);
                } else {
                    app.addBookmark(view.getUrl());
                    displayBookmarked(true);
                }
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.article_view, container, false);
        final ProgressBar progressBar = (ProgressBar)layout.findViewById(R.id.webViewPogress);
        view = (ArticleWebView)layout.findViewById(R.id.webView);
        view.restoreState(savedInstanceState);
        Bundle args = getArguments();
        String url = args == null ? null : args.getString(ARG_URL);
        if (url != null) {
            view.loadUrl(url);
        }
        else {
            view.loadData("Not found", "text/plain", "utf-8");
        }
        view.setWebChromeClient(new WebChromeClient() {
            public void onProgressChanged(WebView view, final int newProgress) {
                final Activity acitivity = getActivity();
                if (acitivity != null) {
                    acitivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progressBar.setProgress(newProgress);
                            if (newProgress >= progressBar.getMax()) {
                                progressBar.setVisibility(ViewGroup.GONE);
                            }
                        }
                    });
                }
            }
        });
        return layout;
    }

    @Override
    public void onDestroy() {
        if (view != null) {
            view.loadUrl("about:blank");
            view = null;
        }
        super.onDestroy();
    }
}