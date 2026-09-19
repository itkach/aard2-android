package itkach.aard2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.selection.ItemDetailsLookup;
import androidx.recyclerview.selection.ItemKeyProvider;
import androidx.recyclerview.selection.MutableSelection;
import androidx.recyclerview.selection.SelectionPredicates;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


abstract class BlobDescriptorListFragment extends BaseListFragment {

    private Drawable icFilter;
    private Drawable icClock;
    private Drawable icName;
    private Drawable icArrowUp;
    private Drawable icArrowDown;

    private BlobDescriptorListAdapter       listAdapter;
    private AlertDialog                     deleteConfirmationDialog = null;

    private SelectionTracker<Long>          selectionTracker;
    private ActionMode                      actionMode;
    private MenuItem                        miSelectAll;
    // Set while the ActionMode is being torn down. Clearing the selection and
    // refreshing rows during teardown makes the tracker re-fire
    // onSelectionChanged; without this guard that would start a fresh (empty)
    // ActionMode on top of the one just closing - a stuck, buttonless bar.
    private boolean                         tearingDownSelection;

    private final static String PREF_SORT_ORDER = "sortOrder";
    private final static String PREF_SORT_DIRECTION = "sortDir";

    private MenuItem miFilter = null;

    // The filter is applied to the list only while the field is open. Collapsing
    // unapplies it - so a collapsed field always means an unfiltered list - but
    // remembers the text (filterText); reopening re-applies it. This mirrors
    // find-in-page, which remembers its last query and re-runs it on reopen.
    private String filterText = "";
    private boolean filterExpanded = false;

    // "Back collapses an open filter" lives here (not MainActivity.onBackPressed)
    // so it participates in predictive back: the callback advertises whether it
    // will consume back, which it does only while this section is visible and its
    // filter is expanded - otherwise back falls through to the default (exit).
    private OnBackPressedCallback filterBackCallback;

    public boolean isFilterExpanded() {
        return miFilter != null && miFilter.isActionViewExpanded();
    }

    public void collapseFilter() {
        if (miFilter != null) {
            miFilter.collapseActionView();
        }
    }

    // Enabled only when this section is the visible one AND its filter is open;
    // a hidden section can still hold an expanded filter, and its back must not
    // steal the visible section's back press.
    private void syncFilterBackEnabled() {
        if (filterBackCallback != null) {
            filterBackCallback.setEnabled(!isHidden() && isFilterExpanded());
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        syncFilterBackEnabled();
    }

    abstract BlobDescriptorList getDescriptorList();

    // Show the spinner while the descriptor list is still loading from disk (see
    // BlobDescriptorList.loadAsync). Its load-finished notifyDataSetChanged reaches
    // the adapter observer, which re-runs updateEmptyViewVisibility to reveal the
    // list.
    @Override
    protected boolean isListLoading() {
        return getDescriptorList().isLoading();
    }

    abstract String getItemClickAction();

    abstract int getDeleteConfirmationItemCountResId();

    abstract String getPreferencesNS();

    private SharedPreferences prefs() {
        return getActivity().getSharedPreferences(getPreferencesNS(), Activity.MODE_PRIVATE);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        filterBackCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                collapseFilter();
            }
        };
        requireActivity().getOnBackPressedDispatcher()
                .addCallback(getViewLifecycleOwner(), filterBackCallback);

        BlobDescriptorList descriptorList = getDescriptorList();

        SharedPreferences p = this.prefs();
        String sortOrderStr = p.getString(PREF_SORT_ORDER,
                                          BlobDescriptorList.SortOrder.TIME.name());
        BlobDescriptorList.SortOrder sortOrder = BlobDescriptorList.SortOrder.valueOf(sortOrderStr);
        boolean sortDir = p.getBoolean(PREF_SORT_DIRECTION, false);
        descriptorList.setSort(sortOrder, sortDir);

        listAdapter = new BlobDescriptorListAdapter(descriptorList);

        final FragmentActivity activity = getActivity();
        icFilter = IconMaker.actionBar(activity, IconMaker.IC_FILTER);
        icClock =  IconMaker.actionBar(activity, IconMaker.IC_CLOCK);
        icName = IconMaker.actionBar(activity, IconMaker.IC_SORT_NAME);
        icArrowUp = IconMaker.actionBar(activity, IconMaker.IC_SORT_ASC);
        icArrowDown = IconMaker.actionBar(activity, IconMaker.IC_SORT_DESC);

        listAdapter.setOnItemClickListener(position -> {
            Intent intent = new Intent(activity, ArticleCollectionActivity.class);
            intent.setAction(getItemClickAction());
            intent.putExtra("position", position);
            startActivity(intent);
        });
        setListAdapter(listAdapter);

        RecyclerView recyclerView = getRecyclerView();
        selectionTracker = new SelectionTracker.Builder<>(
                "blob-descriptor-selection",
                recyclerView,
                new PositionKeyProvider(),
                new DescriptorDetailsLookup(recyclerView),
                StorageStrategy.createLongStorage())
                .withSelectionPredicate(SelectionPredicates.createSelectAnything())
                .build();
        listAdapter.setSelectionTracker(selectionTracker);
        selectionTracker.addObserver(new SelectionObserver());

        // Swipe a row left or right to remove it (with Undo), the same gesture as
        // the dictionaries list. Selection mode (long-press) stays for bulk
        // delete; swipe is suppressed while a selection is active so the two
        // gestures don't fight.
        ItemTouchHelper swipeHelper = new ItemTouchHelper(
                new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
                    @Override
                    public int getSwipeDirs(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh) {
                        if (selectionTracker != null && selectionTracker.hasSelection()) {
                            return 0;
                        }
                        return super.getSwipeDirs(rv, vh);
                    }

                    @Override
                    public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder vh,
                                          @NonNull RecyclerView.ViewHolder target) {
                        return false;
                    }

                    @Override
                    public void onSwiped(@NonNull RecyclerView.ViewHolder vh, int direction) {
                        removeItem(vh.getBindingAdapterPosition());
                    }

                    @Override
                    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView rv,
                                            @NonNull RecyclerView.ViewHolder vh, float dX, float dY,
                                            int actionState, boolean isCurrentlyActive) {
                        drawSwipeBackground(c, vh, dX, actionState);
                        super.onChildDraw(c, rv, vh, dX, dY, actionState, isCurrentlyActive);
                    }
                });
        swipeHelper.attachToRecyclerView(recyclerView);
    }

    // Removal here is a real delete, so the swipe reveals the trashcan (matching
    // the bulk-delete action), not the dictionaries list's "close" X.
    @Override
    protected IconMaker.Glyph swipeIconGlyph() {
        return IconMaker.IC_TRASH;
    }

    // Remove the swiped item and offer Undo. remove() returns the exact
    // descriptor, so restore() puts it back with its original timestamp.
    private void removeItem(int position) {
        if (position == RecyclerView.NO_POSITION) {
            return;
        }
        final BlobDescriptorList list = getDescriptorList();
        final BlobDescriptor removed = list.remove(position);
        if (removed == null) {
            return;
        }
        undoSnackbar(getString(R.string.blob_descriptor_removed, removed.key),
                () -> list.restore(removed)).show();
    }

    // Drives the contextual ActionMode off the selection state, replacing
    // ListView's built-in CHOICE_MODE_MULTIPLE_MODAL. The bar opens on the
    // first (long-press) selection and stays open - like the old ListView
    // CAB - until the user backs out or deletes; emptying the selection by
    // deselecting the last row does NOT close it.
    private class SelectionObserver extends SelectionTracker.SelectionObserver<Long> {
        @Override
        public void onSelectionChanged() {
            if (tearingDownSelection) {
                return;
            }
            if (selectionTracker.hasSelection() && actionMode == null) {
                actionMode = getActivity().startActionMode(new SelectionActionModeCallback());
                // Show the row checkboxes. Posted, not synchronous: this fires
                // mid-gesture during the tracker's own long-press handling, and
                // refreshing rows inline would reset their binding positions
                // and crash the tracker.
                getRecyclerView().post(() -> listAdapter.setSelectionModeActive(true));
            }
            if (actionMode != null) {
                actionMode.setTitle(String.valueOf(selectionTracker.getSelection().size()));
                updateSelectAllIcon();
            }
        }
    }

    // The select-all toggle's icon is a Gmail-style checkbox reflecting whether
    // EVERYTHING is selected: a checked box when all items are (tap deselects
    // all), an empty box otherwise - including when only some are selected (tap
    // selects all).
    private void updateSelectAllIcon() {
        if (miSelectAll == null) {
            return;
        }
        int count = listAdapter.getItemCount();
        boolean allSelected = count > 0 && selectionTracker.getSelection().size() == count;
        miSelectAll.setIcon(IconMaker.actionMode(getActivity(),
                allSelected ? IconMaker.IC_CHECK_SQUARE : IconMaker.IC_SQUARE));
    }

    private class SelectionActionModeCallback implements ActionMode.Callback {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater inflater = mode.getMenuInflater();
            inflater.inflate(R.menu.blob_descriptor_selection, menu);
            MenuItem miDelete = menu.findItem(R.id.blob_descriptor_delete);
            if (miDelete != null) {
                miDelete.setIcon(IconMaker.actionMode(getActivity(), IconMaker.IC_TRASH));
            }
            miSelectAll = menu.findItem(R.id.blob_descriptor_select_all);
            updateSelectAllIcon();
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            int itemId = item.getItemId();
            if (itemId == R.id.blob_descriptor_delete) {
                confirmDelete();
                return true;
            } else if (itemId == R.id.blob_descriptor_select_all) {
                int count = listAdapter.getItemCount();
                if (selectionTracker.getSelection().size() == count) {
                    // Everything already selected - toggle to deselect all.
                    selectionTracker.clearSelection();
                } else {
                    List<Long> all = new ArrayList<>();
                    for (long i = 0; i < count; i++) {
                        all.add(i);
                    }
                    selectionTracker.setItemsSelected(all, true);
                }
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            actionMode = null;
            miSelectAll = null;
            tearingDownSelection = true;
            selectionTracker.clearSelection();
            listAdapter.setSelectionModeActive(false);
            tearingDownSelection = false;
        }
    }

    private void confirmDelete() {
        int count = selectionTracker.getSelection().size();
        String countStr = getResources().getQuantityString(getDeleteConfirmationItemCountResId(), count, count);
        String message = getString(R.string.blob_descriptor_confirm_delete, countStr);
        deleteConfirmationDialog = new AlertDialog.Builder(getActivity())
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle("")
                .setMessage(message)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        deleteSelectedItems();
                        if (actionMode != null) {
                            actionMode.finish();
                        }
                        deleteConfirmationDialog = null;
                    }
                })
                .setNegativeButton(android.R.string.no, null).create();
        deleteConfirmationDialog.setOnDismissListener(new DialogInterface.OnDismissListener(){
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                deleteConfirmationDialog = null;
            }
        });
        deleteConfirmationDialog.show();
    }

    private void deleteSelectedItems() {
        MutableSelection<Long> selection = new MutableSelection<>();
        selectionTracker.copySelection(selection);
        List<Integer> positions = new ArrayList<>();
        for (Long key : selection) {
            positions.add(key.intValue());
        }
        // Remove from the end so earlier indices stay valid.
        Collections.sort(positions, Collections.reverseOrder());
        BlobDescriptorList list = getDescriptorList();
        for (int position : positions) {
            list.remove(position);
        }
    }

    @Override
    boolean finishActionMode() {
        if (actionMode != null) {
            actionMode.finish();
            return true;
        }
        return false;
    }

    // Position-keyed selection (matching the old getCheckedItemPositions
    // behaviour): key == position, so the trivial two-way mapping is always
    // available.
    private static class PositionKeyProvider extends ItemKeyProvider<Long> {
        PositionKeyProvider() {
            super(SCOPE_MAPPED);
        }

        @Override
        public Long getKey(int position) {
            return (long) position;
        }

        @Override
        public int getPosition(@NonNull Long key) {
            return key.intValue();
        }
    }

    private static class DescriptorDetailsLookup extends ItemDetailsLookup<Long> {
        private final RecyclerView recyclerView;

        DescriptorDetailsLookup(RecyclerView recyclerView) {
            this.recyclerView = recyclerView;
        }

        @Override
        public ItemDetails<Long> getItemDetails(@NonNull MotionEvent e) {
            View view = recyclerView.findChildViewUnder(e.getX(), e.getY());
            if (view != null) {
                RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(view);
                if (holder instanceof BlobDescriptorListAdapter.ViewHolder) {
                    return ((BlobDescriptorListAdapter.ViewHolder) holder).getItemDetails();
                }
            }
            return null;
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.blob_descriptor_list, menu);
    }

    @Override
    public void onPrepareOptionsMenu(final Menu menu) {
        BlobDescriptorList list = getDescriptorList();

        miFilter = menu.findItem(R.id.action_filter);
        miFilter.setIcon(icFilter);
        // Keep filterExpanded in sync with the actual state when the menu is
        // (re)prepared - e.g. after a config change that recreates this view.
        filterExpanded = miFilter.isActionViewExpanded();
        // The expand/collapse transition tells us the new state directly (unlike
        // isActionViewExpanded(), which still reads the old state inside these
        // callbacks). onHiddenChanged/syncFilterBackEnabled handle the rest.
        miFilter.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                filterExpanded = true;
                filterBackCallback.setEnabled(!isHidden());
                // Re-apply the remembered query (collapse unapplied it). The field
                // still shows filterText; only the list was left unfiltered.
                getDescriptorList().setFilter(filterText);
                // SearchView focused itself and raised the keyboard on expand; our
                // SearchField is a plain view, so do it here (posted, since the
                // action view isn't attached/measured yet at this point).
                SearchField field = item.getActionView().findViewById(R.id.fldFilter);
                field.post(field::showKeyboard);
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                filterExpanded = false;
                filterBackCallback.setEnabled(false);
                // Unapply the filter on collapse so a collapsed field always means
                // an unfiltered list - but keep filterText so reopening re-applies
                // it, the way find-in-page remembers its last query.
                getDescriptorList().setFilter("");
                // SearchView lowered its keyboard on collapse; SearchField won't
                // unless told to.
                ((SearchField) item.getActionView().findViewById(R.id.fldFilter))
                        .hideKeyboard();
                return true;
            }
        });
        syncFilterBackEnabled();

        SearchField filterField = (SearchField) miFilter.getActionView()
                .findViewById(R.id.fldFilter);
        // The funnel matches the action button the field expands from (and reads
        // as "filter", not "search") - just an icon now, no reaching into a
        // SearchView's internals.
        filterField.setIcon(IconMaker.actionBar(getActivity(), IconMaker.IC_FILTER));
        filterField.setQueryHint(miFilter.getTitle());
        // Restore the remembered text (not list.getFilter(), which is "" whenever
        // the field is collapsed). The change listener applies it only if the
        // field is currently open.
        filterField.setQuery(filterText, false);
        filterField.setOnQueryTextListener(new SearchField.OnQueryTextListener() {
            @Override
            public void onQueryTextSubmit(String query) {}

            @Override
            public void onQueryTextChange(String newText) {
                filterText = newText;
                // Apply live only while the field is open; a change fired while
                // collapsed (e.g. the restore above) just updates the memory.
                if (filterExpanded && !newText.equals(getDescriptorList().getFilter())) {
                    getDescriptorList().setFilter(newText);
                }
            }
        });
        setSortOrder(menu.findItem(R.id.action_sort_order), list.getSortOrder());
        setAscending(menu.findItem(R.id.action_sort_asc), list.isAscending());

        super.onPrepareOptionsMenu(menu);
    }

    private void setSortOrder(MenuItem mi, BlobDescriptorList.SortOrder order) {
        Drawable icon;
        int textRes;
        if (order == BlobDescriptorList.SortOrder.TIME) {
            icon = icClock;
            textRes = R.string.action_sort_by_time;
        } else {
            icon = icName;
            textRes = R.string.action_sort_by_title;
        }
        mi.setIcon(icon);
        mi.setTitle(textRes);
        SharedPreferences p = this.prefs();
        SharedPreferences.Editor editor = p.edit();
        editor.putString(PREF_SORT_ORDER, order.name());
        editor.commit();
    }

    private void setAscending(MenuItem mi, boolean ascending) {
        Drawable icon;
        int textRes;
        if (ascending) {
            icon = icArrowUp;
            textRes = R.string.action_ascending;
        } else {
            icon = icArrowDown;
            textRes = R.string.action_descending;
        }
        mi.setIcon(icon);
        mi.setTitle(textRes);
        SharedPreferences p = this.prefs();
        SharedPreferences.Editor editor = p.edit();
        editor.putBoolean(PREF_SORT_DIRECTION, ascending);
        editor.commit();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem mi) {
        BlobDescriptorList list = getDescriptorList();
        int itemId = mi.getItemId();
        if (itemId == R.id.action_sort_asc) {
            list.setSort(!list.isAscending());
            setAscending(mi, list.isAscending());
            return true;
        }
        if (itemId == R.id.action_sort_order) {
            if (list.getSortOrder() == BlobDescriptorList.SortOrder.TIME) {
                list.setSort(BlobDescriptorList.SortOrder.NAME);
            } else {
                list.setSort(BlobDescriptorList.SortOrder.TIME);
            }
            setSortOrder(mi, list.getSortOrder());
            return true;
        }
        return super.onOptionsItemSelected(mi);
    }


    @Override
    public void onPause() {
        super.onPause();
        if (deleteConfirmationDialog != null) {
            deleteConfirmationDialog.dismiss();
        }
    }
}
