package itkach.aard2;

import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.snackbar.Snackbar;


public abstract class BaseListFragment extends SimpleListFragment {

    protected View emptyView;
    // Whether the list (vs the loading spinner) should be showing; when false
    // neither the list nor the empty view is shown, only the spinner.
    private boolean listShown = true;

    // The adapter emptyObserver is currently registered on, so registration can
    // be paired with an unregister. This fragment is retained across
    // configuration changes (setRetainInstance) and some adapters are
    // application-scoped (e.g. Lookup's app.lastResult), so without unregistering
    // when the view goes away, the recreate after a theme switch would re-register
    // the same observer on the same adapter - which throws "already registered".
    private RecyclerView.Adapter<? extends RecyclerView.ViewHolder> observedAdapter;

    // Toggles the empty view in/out as the adapter's contents change -
    // RecyclerView, unlike ListView, has no setEmptyView(), so we watch the
    // adapter ourselves.
    private final RecyclerView.AdapterDataObserver emptyObserver =
            new RecyclerView.AdapterDataObserver() {
                @Override
                public void onChanged() {
                    updateEmptyViewVisibility();
                }

                @Override
                public void onItemRangeInserted(int positionStart, int itemCount) {
                    updateEmptyViewVisibility();
                }

                @Override
                public void onItemRangeRemoved(int positionStart, int itemCount) {
                    updateEmptyViewVisibility();
                }
            };

    abstract IconMaker.Glyph getEmptyIcon();

    abstract CharSequence getEmptyText();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // No setRetainInstance(true): these fragments are hosted in a
        // ViewPager2 FragmentStateAdapter, which manages (and forbids retaining)
        // its fragments' instance state itself. App-scoped data (lookup results,
        // bookmarks, etc.) lives in Application and survives recreation anyway.
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        emptyView = inflater.inflate(R.layout.empty_view, container, false);
        TextView emptyText = ((TextView)emptyView.findViewById(R.id.empty_text));
        emptyText.setMovementMethod(LinkMovementMethod.getInstance());
        emptyText.setText(getEmptyText());
        ImageView emptyIcon = (ImageView)(emptyView.findViewById(R.id.empty_icon));
        emptyIcon.setImageDrawable(IconMaker.emptyView(getActivity(), getEmptyIcon()));
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Overlaid behind the RecyclerView in the same FrameLayout, shown only
        // when the list is empty.
        ((ViewGroup) getRecyclerView().getParent()).addView(emptyView, 0);
        emptyView.setVisibility(View.GONE);
    }

    @Override
    public void setListAdapter(RecyclerView.Adapter<? extends RecyclerView.ViewHolder> adapter) {
        super.setListAdapter(adapter);
        if (observedAdapter != adapter) {
            if (observedAdapter != null) {
                observedAdapter.unregisterAdapterDataObserver(emptyObserver);
                observedAdapter = null;
            }
            if (adapter != null) {
                adapter.registerAdapterDataObserver(emptyObserver);
                observedAdapter = adapter;
            }
        }
        updateEmptyViewVisibility();
    }

    @Override
    public void onDestroyView() {
        if (observedAdapter != null) {
            observedAdapter.unregisterAdapterDataObserver(emptyObserver);
            observedAdapter = null;
        }
        super.onDestroyView();
    }

    // Lists whose data loads asynchronously (bookmarks/history) override this so
    // the spinner keeps showing until the load finishes, even though the list is
    // nominally "shown". Default: not loading.
    protected boolean isListLoading() {
        return false;
    }

    // Coordinates the three states (loading spinner / empty view / list) in
    // one place, since RecyclerView has neither setListShown nor setEmptyView
    // of its own and the two would otherwise fight over the list's visibility.
    @Override
    public void setListShown(boolean shown) {
        listShown = shown;
        updateEmptyViewVisibility();
    }

    private void updateEmptyViewVisibility() {
        RecyclerView recyclerView = getRecyclerView();
        if (recyclerView == null) {
            return;
        }
        boolean spinner = !listShown || isListLoading();
        setProgressVisible(spinner);
        if (spinner) {
            // Loading: only the spinner shows.
            recyclerView.setVisibility(View.GONE);
            if (emptyView != null) {
                emptyView.setVisibility(View.GONE);
            }
            return;
        }
        RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
        boolean empty = adapter == null || adapter.getItemCount() == 0;
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (emptyView != null) {
            emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        }
    }

    // Overridden by the screen that has a selection ActionMode (bookmarks /
    // history); a no-op here so MainActivity can call it on any list fragment.
    boolean finishActionMode() {
        return false;
    }

    protected int themeColor(int attr) {
        TypedValue tv = new TypedValue();
        getActivity().getTheme().resolveAttribute(attr, tv, true);
        return tv.data;
    }

    // A "…, [UNDO]" snackbar for the swipe-to-remove lists. The action text colour
    // is set explicitly: Material's Snackbar otherwise leaves it a default accent
    // (a purple that ignores the app's dynamic theme). colorPrimaryInverse is the
    // right role - the snackbar's own background is the inverse surface.
    protected Snackbar undoSnackbar(CharSequence text, Runnable undo) {
        Snackbar snackbar = Snackbar.make(getRecyclerView(), text, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_undo, v -> undo.run());
        snackbar.setActionTextColor(
                themeColor(com.google.android.material.R.attr.colorPrimaryInverse));
        return snackbar;
    }

    // Swipe-to-remove background, shared by the lists that support it
    // (dictionaries, bookmarks, history): a plain surface revealing an icon on the
    // side the row slides away from. Lazily built on first swipe.
    private ColorDrawable swipeBackground;
    private Drawable swipeCloseIcon;
    private int swipeIconMargin;

    // The glyph the swipe reveals. Default is a close (X) - "close, not delete",
    // as for dictionaries. Bookmarks/history override it with the trashcan to
    // match their bulk-delete action, since there removal is a real delete.
    protected IconMaker.Glyph swipeIconGlyph() {
        return IconMaker.IC_CLOSE;
    }

    protected void drawSwipeBackground(@NonNull Canvas c, @NonNull RecyclerView.ViewHolder vh,
                                       float dX, int actionState) {
        if (actionState != ItemTouchHelper.ACTION_STATE_SWIPE || dX == 0) {
            return;
        }
        if (swipeBackground == null) {
            swipeBackground = new ColorDrawable(
                    themeColor(com.google.android.material.R.attr.colorSurfaceVariant));
            swipeCloseIcon = IconMaker.make(getActivity(), swipeIconGlyph(), 22,
                    themeColor(com.google.android.material.R.attr.colorOnSurfaceVariant));
            swipeIconMargin = Math.round(20 * getResources().getDisplayMetrics().density);
        }
        View item = vh.itemView;
        swipeBackground.setBounds(item.getLeft(), item.getTop(), item.getRight(), item.getBottom());
        swipeBackground.draw(c);
        int iw = swipeCloseIcon.getIntrinsicWidth();
        int ih = swipeCloseIcon.getIntrinsicHeight();
        int top = item.getTop() + (item.getHeight() - ih) / 2;
        if (dX > 0) {
            swipeCloseIcon.setBounds(item.getLeft() + swipeIconMargin, top,
                    item.getLeft() + swipeIconMargin + iw, top + ih);
        } else {
            swipeCloseIcon.setBounds(item.getRight() - swipeIconMargin - iw, top,
                    item.getRight() - swipeIconMargin, top + ih);
        }
        swipeCloseIcon.draw(c);
    }

}
