package itkach.aard2;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.ActionMode;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.ContextCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;


abstract class BlobDescriptorListFragment extends BaseListFragment implements ActionMode.Callback {
    protected ActionMode actionMode;

    private Drawable icClock;
    private Drawable icList;
    private Drawable icArrowUp;
    private Drawable icArrowDown;

    private BlobDescriptorListAdapter listAdapter;
    private AlertDialog deleteConfirmationDialog = null;

    private final static String PREF_SORT_ORDER = "sortOrder";
    private final static String PREF_SORT_DIRECTION = "sortDir";

    private MenuItem miFilter = null;

    public boolean isFilterExpanded() {
        return miFilter != null && miFilter.isActionViewExpanded();
    }

    public void collapseFilter() {
        if (miFilter != null) {
            miFilter.collapseActionView();
        }
    }

    abstract BlobDescriptorList getDescriptorList();

    abstract String getItemClickAction();

    abstract int getDeleteConfirmationItemCountResId();

    abstract String getPreferencesNS();

    @NonNull
    private SharedPreferences prefs() {
        return requireActivity().getSharedPreferences(getPreferencesNS(), Activity.MODE_PRIVATE);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final AppCompatActivity activity = (AppCompatActivity) requireActivity();

        BlobDescriptorList descriptorList = getDescriptorList();

        SharedPreferences p = this.prefs();

        String sortOrderStr = p.getString(PREF_SORT_ORDER,
                BlobDescriptorList.SortOrder.TIME.name());
        BlobDescriptorList.SortOrder sortOrder = BlobDescriptorList.SortOrder.valueOf(sortOrderStr);

        boolean sortDir = p.getBoolean(PREF_SORT_DIRECTION, false);

        descriptorList.setSort(sortOrder, sortDir);

        listAdapter = new BlobDescriptorListAdapter(descriptorList, getItemClickAction());
        listAdapter.setOnSelectionStartedListener(new BlobDescriptorListAdapter.OnSelectionChangeListener() {
            @Override
            public void selectionStarted() {
                activity.startSupportActionMode(BlobDescriptorListFragment.this);
            }

            @Override
            public void selectionChanged(int selectionCount) {
                if (actionMode != null) {
                    actionMode.setTitle(getString(R.string.specified_number_of_items_selected, selectionCount));
                }
            }

            @Override
            public void selectionCanceled() {
                finishActionMode();
            }
        });

        icClock = ContextCompat.getDrawable(activity, R.drawable.ic_clock_time_nine);
        icList = ContextCompat.getDrawable(activity, R.drawable.ic_format_list_bulleted);
        icArrowUp = ContextCompat.getDrawable(activity, R.drawable.ic_sort_ascending);
        icArrowDown = ContextCompat.getDrawable(activity, R.drawable.sort_descending);

        recyclerView.setAdapter(listAdapter);
    }

    protected void deleteSelectedItems() {
        SparseBooleanArray checkedItems = listAdapter.getCheckedItemPositions();
        for (int i = checkedItems.size() - 1; i > -1; --i) {
            int position = checkedItems.keyAt(i);
            boolean checked = checkedItems.valueAt(i);
            if (checked) {
                getDescriptorList().remove(position);
            }
        }
    }

    public void finishActionMode() {
        if (actionMode != null) {
            actionMode.finish();
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.blob_descriptor_list, menu);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        BlobDescriptorList list = getDescriptorList();

        miFilter = menu.findItem(R.id.action_filter);

        View filterActionView = miFilter.getActionView();
        SearchView searchView = filterActionView
                .findViewById(R.id.search);
        searchView.setQueryHint(miFilter.getTitle());
        searchView.setQuery(list.getFilter(), true);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                BlobDescriptorList list = getDescriptorList();
                if (!newText.equals(list.getFilter())) {
                    getDescriptorList().setFilter(newText);
                }
                return true;
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
            icon = icList;
            textRes = R.string.action_sort_by_title;
        }
        mi.setIcon(icon);
        mi.setTitle(textRes);
        SharedPreferences p = this.prefs();
        SharedPreferences.Editor editor = p.edit();
        editor.putString(PREF_SORT_ORDER, order.name());
        editor.apply();
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
        editor.apply();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem mi) {
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

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        actionMode = mode;
        if (mode != null) {
            mode.getMenuInflater().inflate(R.menu.blob_descriptor_selection, menu);
        }
        listAdapter.setSelectionMode(true);
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
            int count = listAdapter.getCheckedItemCount();
            String countStr = getResources().getQuantityString(getDeleteConfirmationItemCountResId(), count, count);
            String message = getString(R.string.blob_descriptor_confirm_delete, countStr);
            deleteConfirmationDialog = new MaterialAlertDialogBuilder(requireActivity())
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setTitle("")
                    .setMessage(message)
                    .setPositiveButton(R.string.action_yes, (dialog, which) -> {
                        deleteSelectedItems();
                        mode.finish();
                        deleteConfirmationDialog = null;
                    })
                    .setNegativeButton(R.string.action_no, null)
                    .create();
            deleteConfirmationDialog.setOnDismissListener(dialogInterface -> deleteConfirmationDialog = null);
            deleteConfirmationDialog.show();
            return true;
        } else if (itemId == R.id.blob_descriptor_select_all) {
            listAdapter.selectAll();
            return true;
        }
        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode mode) {
        actionMode = null;
        listAdapter.setSelectionMode(false);
    }
}
