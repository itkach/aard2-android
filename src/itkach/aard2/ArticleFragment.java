package itkach.aard2;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.ProgressBar;

import itkach.fdrawable.IconicFontDrawable;

public class ArticleFragment extends Fragment {

    public static final String ARG_URL = "articleUrl";

    private ArticleWebView view;
    private MenuItem miBookmark;
    private IconicFontDrawable icBookmark;
    private IconicFontDrawable icBookmarkO;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        icBookmark = Icons.BOOKMARK.create();
        icBookmarkO = Icons.BOOKMARK_O.create();
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.article, menu);
        miBookmark = menu.findItem(R.id.action_bookmark_article);
        Application app = (Application)getActivity().getApplication();
        displayBookmarked(app.isBookmarked(view.getUrl()));
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
            if (item.isChecked()) {
                app.removeBookmark(view.getUrl());
                displayBookmarked(false);
            } else {
                app.addBookmark(view.getUrl());
                displayBookmarked(true);
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
        Bundle args = getArguments();
        view.restoreState(savedInstanceState);
        String url = args.getString(ARG_URL);
        view.loadUrl(url);
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
        view = null;
        super.onDestroy();
    }
}