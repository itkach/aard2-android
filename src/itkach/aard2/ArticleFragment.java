package itkach.aard2;

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
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;


public class ArticleFragment extends Fragment {

    public static final String ARG_URL = "articleUrl";

    private ArticleWebView view;
    private MenuItem miBookmark;
    private String url;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        //Looks like this may be called multiple times with the same menu
        //for some reason when activity is restored, so need to clear
        //to avoid duplicates
        menu.clear();
        inflater.inflate(R.menu.article, menu);
        miBookmark = menu.findItem(R.id.action_bookmark_article);
    }

    private void displayBookmarked(boolean value) {
        if (miBookmark == null) {
            return;
        }
        if (value) {
            miBookmark.setChecked(true);
            miBookmark.setIcon(R.drawable.ic_bookmark);
        } else {
            miBookmark.setChecked(false);
            miBookmark.setIcon(R.drawable.ic_bookmark_border);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_find_in_page) {
            view.showFindDialog(null, true);
            return true;
        }
        if (itemId == R.id.action_bookmark_article) {
            Application app = (Application) requireActivity().getApplication();
            if (this.url != null) {
                if (item.isChecked()) {
                    app.removeBookmark(this.url);
                    displayBookmarked(false);
                } else {
                    app.addBookmark(this.url);
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
            view.textZoomIn();
            return true;
        }
        if (itemId == R.id.action_zoom_out) {
            view.textZoomOut();
            return true;
        }
        if (itemId == R.id.action_zoom_reset) {
            view.resetTextZoom();
            return true;
        }
        if (itemId == R.id.action_load_remote_content) {
            view.forceLoadRemoteContent = true;
            view.reload();
            return true;
        }
        if (itemId == R.id.action_select_style) {
            final String[] styleTitles = view.getAvailableStyles();
            new MaterialAlertDialogBuilder(requireActivity())
                    .setTitle(R.string.select_style)
                    .setItems(styleTitles, (dialog, which) -> {
                        String title = styleTitles[which];
                        view.saveStylePref(title);
                        view.applyStylePref();
                    })
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        Bundle args = getArguments();
        this.url = args == null ? null : args.getString(ARG_URL);
        if (url == null) {
            View layout = inflater.inflate(R.layout.empty_view, container, false);
            ImageView icon = layout.findViewById(R.id.empty_icon);
            icon.setImageResource(R.drawable.ic_block);
            this.setHasOptionsMenu(false);
            return layout;
        }

        View layout = inflater.inflate(R.layout.article_view, container, false);
        final ProgressBar progressBar = layout.findViewById(R.id.webViewPogress);
        view = layout.findViewById(R.id.webView);
        view.restoreState(savedInstanceState);
        view.loadUrl(url);
        view.setWebChromeClient(new WebChromeClient() {
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
        if (this.url == null) {
            miBookmark.setVisible(false);
        } else {
            Application app = (Application) requireActivity().getApplication();
            try {
                boolean bookmarked = app.isBookmarked(this.url);
                displayBookmarked(bookmarked);
            } catch (Exception ex) {
                miBookmark.setVisible(false);
            }
        }
        applyTextZoomPref();
        applyStylePref();
    }

    void applyTextZoomPref() {
        if (view != null) {
            view.applyTextZoomPref();
        }
    }

    void applyStylePref() {
        if (view != null) {
            view.applyStylePref();
        }
    }

    public ArticleWebView getWebView() {
        return view;
    }

    @Override
    public void onDestroy() {
        if (view != null) {
            view.destroy();
            view = null;
        }
        miBookmark = null;
        super.onDestroy();
    }

}