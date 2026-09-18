package itkach.aard2;

import android.content.Context;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * The app's search/filter/find input: a leading icon, a text field with a thin
 * underline, and a clear button that appears once there's text. A small,
 * fully-controlled stand-in for android.widget.SearchView, which we used to use
 * for these three fields (Lookup, Filter, find-in-page) but which offered no
 * public way to set its leading icon or tighten its spacing - forcing
 * reach-into-internals hacks. Here icon and spacing are plain layout, so those
 * hacks are gone. The listener mirrors SearchView.OnQueryTextListener so the
 * call sites read the same.
 */
public class SearchField extends LinearLayout {

    public interface OnQueryTextListener {
        void onQueryTextChange(String newText);
        void onQueryTextSubmit(String query);
    }

    private final ImageView icon;
    private final EditText edit;
    private final ImageButton clear;
    private OnQueryTextListener listener;

    public SearchField(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        LayoutInflater.from(context).inflate(R.layout.search_field, this, true);
        icon = findViewById(R.id.search_field_icon);
        edit = findViewById(R.id.search_field_edit);
        clear = findViewById(R.id.search_field_clear);

        clear.setImageDrawable(IconMaker.actionBar(context, IconMaker.IC_CLOSE, 18));
        clear.setOnClickListener(v -> {
            edit.setText("");
            // Clearing means the user wants to type a fresh query, so bring the
            // keyboard back up (for Lookup this also matches "keyboard when there
            // are no results"). It's already up for Filter/find, so this is a no-op
            // there.
            showKeyboard();
        });

        edit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                clear.setVisibility(s.length() > 0 ? VISIBLE : GONE);
                if (listener != null) {
                    listener.onQueryTextChange(s.toString());
                }
            }
        });
        edit.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_ACTION_GO) {
                if (listener != null) {
                    listener.onQueryTextSubmit(edit.getText().toString());
                }
                return true;
            }
            return false;
        });
    }

    // Grab the full available width, the way SearchView does. Hosted as a
    // Toolbar action view (the Filter), the Toolbar measures the field with an
    // AT_MOST spec of the space left beside the other icons; a plain LinearLayout
    // would shrink to its content (a tiny box), so treat AT_MOST as EXACTLY to
    // fill it. In the other hosts (Lookup, find-in-page) the field is already
    // given an EXACTLY/match_parent width, so this is a no-op there.
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.AT_MOST) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(
                    MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY);
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    public void setIcon(Drawable drawable) {
        icon.setImageDrawable(drawable);
    }

    public void setQueryHint(CharSequence hint) {
        edit.setHint(hint);
    }

    /** Set the query text; the text-changed callback fires as it does for a real
     *  edit, and (like SearchView) onQueryTextSubmit fires too when submit=true. */
    public void setQuery(CharSequence query, boolean submit) {
        edit.setText(query);
        edit.setSelection(edit.getText().length());
        if (submit && listener != null) {
            listener.onQueryTextSubmit(query == null ? "" : query.toString());
        }
    }

    public CharSequence getQuery() {
        return edit.getText();
    }

    public void setOnQueryTextListener(OnQueryTextListener l) {
        this.listener = l;
    }

    // Route focus requests to the text field so the caret and keyboard land
    // there (callers say requestFocus()/clearFocus() as they did on the SearchView).
    @Override
    public boolean requestFocus(int direction, Rect previouslyFocusedRect) {
        return edit.requestFocus(direction, previouslyFocusedRect);
    }

    @Override
    public void clearFocus() {
        edit.clearFocus();
        super.clearFocus();
    }

    // Focus the text field and raise the soft keyboard, targeting the inner
    // EditText (the IME's served view). SearchView did this itself when its
    // action view expanded; callers that expand a SearchField do it via this.
    public void showKeyboard() {
        edit.requestFocus();
        // Ask two ways, because neither alone covers every programmatic reveal:
        //  - InputMethodManager.showSoftInput with explicit flags (0, not
        //    SHOW_IMPLICIT, which is advisory and gets dropped when the last touch
        //    wasn't on a text field, e.g. right after a bottom-nav tab tap).
        //  - The window insets controller, which succeeds in early/first-frame
        //    reveals (app start) where the IMM has no input connection yet but
        //    is a no-op in some other cases the IMM handles.
        // Showing an already-shown keyboard is idempotent, so doing both is safe.
        WindowInsetsControllerCompat controller = ViewCompat.getWindowInsetsController(edit);
        if (controller != null) {
            controller.show(WindowInsetsCompat.Type.ime());
        }
        InputMethodManager imm =
                (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(edit, 0);
        }
    }

    // Drop focus and lower the soft keyboard. SearchView did this itself when its
    // action view collapsed; callers that collapse a SearchField do it via this.
    public void hideKeyboard() {
        InputMethodManager imm =
                (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(getWindowToken(), 0);
        }
        edit.clearFocus();
    }
}
