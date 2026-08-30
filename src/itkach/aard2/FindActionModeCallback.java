/*
* This file is heavily inspired by the Android Open Source Project
* licensed under the Apache License, Version 2.0
*/

package itkach.aard2;

import android.content.Context;
import android.os.Build;
import android.text.Editable;
import android.text.Selection;
import android.text.Spannable;
import android.text.TextWatcher;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

class FindActionModeCallback implements ActionMode.Callback, TextWatcher,
        View.OnLongClickListener, View.OnClickListener {

    private View searchView;
    private EditText editText;
    private SearchableWebView webview;
    private InputMethodManager imManager;

    FindActionModeCallback(Context context, SearchableWebView webview) {
        this.webview = webview;
        searchView = LayoutInflater.from(context).inflate(R.layout.webview_find, null);

        editText = searchView.findViewById(R.id.edit);
        editText.setOnLongClickListener(this);
        editText.setOnClickListener(this);
        editText.addTextChangedListener(this);

        imManager = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    /* Place text in the text field so it can be searched for. */
    void setText(String text) {
        editText.setText(text);
        Spannable span = (Spannable) editText.getText();
        int length = span.length();
        // Ideally, we would like to set the selection to the whole field,
        // but this brings up the Text selection CAB, which dismisses this
        // one.
        Selection.setSelection(span, length, length);
        // Necessary each time we set the text, so that this will watch
        // changes to it.
        span.setSpan(this, 0, length, Spannable.SPAN_INCLUSIVE_INCLUSIVE);
    }

    /*
     * Move the highlight to the next match.
     * @param next If true, find the next match further down in the document.
     *             If false, find the previous match, up in the document.
     */
    private void findNext(boolean next) {
        webview.findNext(next);
    }

    /*
     * Highlight all the instances of the string from editText in webview.
     */
    void findAll() {
        String find = editText.getText().toString();

        if (Build.VERSION.SDK_INT < 16)
            webview.findAll(find);
        else
            webview.findAllAsync(find);
    }

    void showSoftInput() {
        // imManager.showSoftInputMethod doesn't work
        imManager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
    }

    // OnLongClickListener implementation
    @Override
    public boolean onLongClick(View v) {
        // Override long click so that select ActionMode is not opened, which
        // would exit find ActionMode.
        return true;
    }

    // OnClickListener implementation
    @Override
    public void onClick(View v) {
        findNext(true);
    }

    // ActionMode.Callback implementation
    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        mode.setCustomView(searchView);
        mode.getMenuInflater().inflate(R.menu.webview_find, menu);

        Editable edit = editText.getText();
        Selection.setSelection(edit, edit.length());
        editText.requestFocus();
        return true;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        webview.clearMatches();
        imManager.hideSoftInputFromWindow(webview.getWindowToken(), 0);
        webview.setLastFind(editText.getText().toString());
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

    // TextWatcher implementation
    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        // Does nothing.  Needed to implement TextWatcher.
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        findAll();
    }

    @Override
    public void afterTextChanged(Editable s) {
        // Does nothing.  Needed to implement TextWatcher.
    }
}