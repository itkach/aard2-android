package itkach.aard2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.Build;
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
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;


public class ArticleFragment extends Fragment {

    public static final String ARG_URL = "articleUrl";

    private ArticleWebView  view;
    private MenuItem        miBookmark;
    private MenuItem        miFullscreen;
    private Drawable        icBookmark;
    private Drawable        icBookmarkO;
    private Drawable        icFullscreen;
    private String          url;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Activity activity = getActivity();
        Context context = activity.getActionBar().getThemedContext();
        icBookmark =  IconMaker.actionBar(context, IconMaker.IC_BOOKMARK);
        icBookmarkO = IconMaker.actionBar(context, IconMaker.IC_BOOKMARK_O);
        icFullscreen = IconMaker.actionBar(context, IconMaker.IC_FULLSCREEN);
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
        miFullscreen = menu.findItem(R.id.action_fullscreen);
        if (Build.VERSION.SDK_INT < 19) {
            miFullscreen.setVisible(false);
            miFullscreen.setEnabled(false);
        }
    }

    private void displayBookmarked(boolean value) {
        if (miBookmark == null) {
            return;
        }
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
            view.showFindDialog(null, true);
            return true;
        }
        if (itemId == R.id.action_bookmark_article) {
            Application app = (Application)getActivity().getApplication();
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
            ((ArticleCollectionActivity)getActivity()).toggleFullScreen();
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
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            final String[] styleTitles = view.getAvailableStyles();
            builder.setTitle(R.string.select_style)
                    .setItems(styleTitles, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            String title = styleTitles[which];
                            view.saveStylePref(title);
                            view.applyStylePref();
                        }
                    });
            AlertDialog dialog = builder.create();
            dialog.show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        Bundle args = getArguments();
        this.url = args == null ? null : args.getString(ARG_URL);
        if (url == null) {
            View layout = inflater.inflate(R.layout.empty_view, container, false);
            TextView textView = (TextView)layout.findViewById(R.id.empty_text);
            textView.setText("");
            ImageView icon = (ImageView) layout.findViewById(R.id.empty_icon);
            icon.setImageDrawable(IconMaker.emptyView(getActivity(),
                    IconMaker.IC_BAN));
            this.setHasOptionsMenu(false);
            return layout;
        }

        View layout = inflater.inflate(R.layout.article_view, container, false);
        final ProgressBar progressBar = (ProgressBar) layout.findViewById(R.id.webViewPogress);
        view = (ArticleWebView) layout.findViewById(R.id.webView);
        view.restoreState(savedInstanceState);
        view.loadUrl(url);
        view.setWebChromeClient(new WebChromeClient() {
            public void onProgressChanged(WebView view, final int newProgress) {
                final Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(new Runnable() {
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
    public void onResume() {
        super.onResume();
        applyTextZoomPref();
        applyStylePref();
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        if (this.url == null) {
            miBookmark.setVisible(false);
        }
        else {
            Application app = (Application)getActivity().getApplication();
            try {
                boolean bookmarked =  app.isBookmarked(this.url);
                displayBookmarked(bookmarked);
            } catch (Exception ex) {
                miBookmark.setVisible(false);
            }
        }
        applyTextZoomPref();
        applyStylePref();
        miFullscreen.setIcon(icFullscreen);
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
        miFullscreen = null;
        miBookmark = null;
        super.onDestroy();
    }

}