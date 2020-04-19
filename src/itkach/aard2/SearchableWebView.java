/*
 * This file is heavily inspired by the Android Open Source Project
 * licensed under the Apache License, Version 2.0
 */

package itkach.aard2;

import android.content.Context;
import android.util.AttributeSet;
import android.webkit.WebView;

class SearchableWebView extends WebView {

    private String mLastFind = null;

    public void setLastFind(String find) {
        mLastFind = find;
    }

    public SearchableWebView(Context context) {
        this(context, null);
    }

    public SearchableWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Start an ActionMode for finding text in this WebView.  Only works if this
     * WebView is attached to the view system.
     *
     * @param text    If non-null, will be the initial text to search for.
     *                Otherwise, the last String searched for in this WebView will
     *                be used to start.
     * @param showIme If true, show the IME, assuming the user will begin typing.
     *                If false and text is non-null, perform a find all.
     * @return boolean True if the find dialog is shown, false otherwise.
     */
    @Override
    public boolean showFindDialog(String text, boolean showIme) {
        FindActionModeCallback callback = new FindActionModeCallback(getContext(), this);
        if (getParent() == null || startActionMode(callback) == null) {
            // Could not start the action mode, so end Find on page
            return false;
        }

        if (showIme) {
            callback.showSoftInput();
        } else if (text != null) {
            callback.setText(text);
            callback.findAll();
            return true;
        }
        if (text == null) {
            text = mLastFind;
        }
        if (text != null) {
            callback.setText(text);
            callback.findAll();
        }
        return true;
    }
}
