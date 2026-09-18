/*
* This file is heavily inspired by the Android Open Source Project
* licensed under the Apache License, Version 2.0
*/

package itkach.aard2;

import android.content.Context;
import android.view.ActionMode;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.InputMethodManager;

class FindActionModeCallback implements ActionMode.Callback,
        SearchField.OnQueryTextListener {

    private final SearchField searchField;
    private final SearchableWebView webview;
    private final InputMethodManager imManager;

    FindActionModeCallback(Context context, SearchableWebView webview) {
        this.webview = webview;
        // Theme the field to match the Toolbar the find bar overlays: the overlay
        // paints the field's text, hint, glass and clear button in colorOnPrimary
        // against the colorPrimary bar (actionModeStyle).
        Context themed = new ContextThemeWrapper(context, R.style.ThemeOverlay_Aard2_Toolbar);
        searchField = (SearchField) LayoutInflater.from(themed).inflate(R.layout.webview_find, null);
        searchField.setIcon(IconMaker.actionBar(themed, IconMaker.IC_SEARCH));
        searchField.setQueryHint(context.getString(R.string.find_hint));
        searchField.setOnQueryTextListener(this);
        imManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    /* Place text in the field so it can be searched for. Setting the query fires
     * onQueryTextChange, which runs the find, so callers needn't also call it. */
    void setText(String text) {
        searchField.setQuery(text, false);
    }

    /*
     * Move the highlight to the next match.
     * @param next If true, find the next match further down in the document.
     *             If false, find the previous match, up in the document.
     */
    private void findNext(boolean next) {
        webview.findNext(next);
    }

    /* Highlight all instances of the current query in the webview. */
    void findAll() {
        webview.findAllAsync(searchField.getQuery().toString());
    }

    void showSoftInput() {
        // Focus the field and raise the keyboard via the field itself (targets its
        // inner EditText, the IME's served view). Posted because this is called
        // right after the ActionMode is created, before its bar is laid out - the
        // same reason the Filter defers its showKeyboard.
        searchField.post(searchField::showKeyboard);
    }

    // ActionMode.Callback implementation
    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.setCustomView(searchField);
        mode.getMenuInflater().inflate(R.menu.webview_find, menu);

        // Match the previous/next arrows to the Toolbar's own action icons: the
        // same IconMaker.actionBar() glyphs (caret-up = previous, caret-down =
        // next), colorOnPrimary against the colorPrimary bar, replacing the
        // fixed-size AOSP ic_find_*_mtrl bitmaps the menu declares. A touch
        // smaller than a standard app-bar icon so they don't read as heavy next
        // to the field.
        Context ctx = webview.getContext();
        menu.findItem(R.id.find_prev).setIcon(IconMaker.actionBar(ctx, IconMaker.IC_ANGLE_UP, 18));
        menu.findItem(R.id.find_next).setIcon(IconMaker.actionBar(ctx, IconMaker.IC_ANGLE_DOWN, 18));

        searchField.requestFocus();
        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        webview.clearMatches();
        imManager.hideSoftInputFromWindow(webview.getWindowToken(), 0);
        webview.setLastFind(searchField.getQuery().toString());
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        imManager.hideSoftInputFromWindow(webview.getWindowToken(), 0);
        int itemId = item.getItemId();
        if (itemId == R.id.find_prev) {
            findNext(false);
        } else if (itemId == R.id.find_next) {
            findNext(true);
        } else {
            return false;
        }
        return true;
    }

    // SearchField.OnQueryTextListener implementation
    @Override
    public void onQueryTextChange(String newText) {
        findAll();
    }

    @Override
    public void onQueryTextSubmit(String query) {
        imManager.hideSoftInputFromWindow(webview.getWindowToken(), 0);
        findNext(true);
    }
}
