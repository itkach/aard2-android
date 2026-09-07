package itkach.aard2;

import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;


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
        setRetainInstance(true);
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

    // Coordinates the three states (loading spinner / empty view / list) in
    // one place, since RecyclerView has neither setListShown nor setEmptyView
    // of its own and the two would otherwise fight over the list's visibility.
    @Override
    public void setListShown(boolean shown) {
        listShown = shown;
        setProgressVisible(!shown);
        updateEmptyViewVisibility();
    }

    private void updateEmptyViewVisibility() {
        RecyclerView recyclerView = getRecyclerView();
        if (recyclerView == null) {
            return;
        }
        if (!listShown) {
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

}
