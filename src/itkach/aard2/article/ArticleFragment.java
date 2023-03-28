package itkach.aard2.article;

import android.app.Activity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import itkach.aard2.Application;
import itkach.aard2.R;
import itkach.aard2.widget.ArticleWebView;


public class ArticleFragment extends Fragment {
    public static final String ARG_URL = "articleUrl";

    private ArticleWebView webView;
    private MenuItem bookmarkMenu;
    private String url;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        menu.clear();
        inflater.inflate(R.menu.article, menu);
        bookmarkMenu = menu.findItem(R.id.action_bookmark_article);
    }

    private void displayBookmarked(boolean value) {
        if (bookmarkMenu == null) {
            return;
        }
        if (value) {
            bookmarkMenu.setChecked(true);
            bookmarkMenu.setIcon(R.drawable.ic_bookmark);
        } else {
            bookmarkMenu.setChecked(false);
            bookmarkMenu.setIcon(R.drawable.ic_bookmark_border);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_find_in_page) {
            webView.showFindDialog(null, true);
            return true;
        }
        if (itemId == R.id.action_bookmark_article) {
            Application app = (Application) requireActivity().getApplication();
            if (url != null) {
                if (item.isChecked()) {
                    app.removeBookmark(url);
                    displayBookmarked(false);
                } else {
                    app.addBookmark(url);
                    displayBookmarked(true);
                }
            }
            return true;
        }
        if (itemId == R.id.action_fullscreen) {
            ((ArticleCollectionActivity) requireActivity()).toggleFullScreen();
            return true;
        }
        if (itemId == R.id.action_zoom_in) {
            webView.textZoomIn();
            return true;
        }
        if (itemId == R.id.action_zoom_out) {
            webView.textZoomOut();
            return true;
        }
        if (itemId == R.id.action_zoom_reset) {
            webView.resetTextZoom();
            return true;
        }
        if (itemId == R.id.action_load_remote_content) {
            webView.setForceLoadRemoteContent(true);
            webView.reload();
            return true;
        }
        if (itemId == R.id.action_select_style) {
            final String[] styleTitles = webView.getAvailableStyles();
            new MaterialAlertDialogBuilder(requireActivity())
                    .setTitle(R.string.select_style)
                    .setItems(styleTitles, (dialog, which) -> {
                        String title = styleTitles[which];
                        webView.saveStylePref(title);
                        webView.applyStylePref();
                    })
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        url = args == null ? null : args.getString(ARG_URL);
        if (url == null) {
            View layout = inflater.inflate(R.layout.empty_view, container, false);
            ImageView icon = layout.findViewById(R.id.empty_icon);
            icon.setImageResource(R.drawable.ic_block);
            setHasOptionsMenu(false);
            return layout;
        }

        View layout = inflater.inflate(R.layout.article_view, container, false);
        LinearProgressIndicator progressBar = layout.findViewById(R.id.progress_horizontal);
        webView = layout.findViewById(R.id.webView);
        webView.restoreState(savedInstanceState);
        webView.loadUrl(url);
        webView.setWebChromeClient(new WebChromeClient() {
            public void onProgressChanged(WebView view, final int newProgress) {
                final Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        progressBar.setProgress(newProgress);
                        if (newProgress >= progressBar.getMax()) {
                            progressBar.setVisibility(ViewGroup.GONE);
                        }
                    });
                }
            }
        });

        return layout;
    }


    @Override
    public void onResume() {
        super.onResume();
        applyTextZoomPref();
        applyStylePref();
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        if (url == null) {
            bookmarkMenu.setVisible(false);
        } else {
            Application app = (Application) requireActivity().getApplication();
            try {
                boolean bookmarked = app.isBookmarked(url);
                displayBookmarked(bookmarked);
            } catch (Exception ex) {
                bookmarkMenu.setVisible(false);
            }
        }
        applyTextZoomPref();
        applyStylePref();
    }

    void applyTextZoomPref() {
        if (webView != null) {
            webView.applyTextZoomPref();
        }
    }

    void applyStylePref() {
        if (webView != null) {
            webView.applyStylePref();
        }
    }

    public ArticleWebView getWebView() {
        return webView;
    }

    @Override
    public void onDestroy() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        bookmarkMenu = null;
        super.onDestroy();
    }

}