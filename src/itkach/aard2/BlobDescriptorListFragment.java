package itkach.aard2;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.SearchView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;


abstract class BlobDescriptorListFragment extends BaseListFragment {

    private Drawable icClock;
    private Drawable icList;
    private Drawable icArrowUp;
    private Drawable icArrowDown;

    private BlobDescriptorListAdapter       listAdapter;
    private AlertDialog                     deleteConfirmationDialog = null;

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

    protected void setSelectionMode(boolean selectionMode) {
        listAdapter.setSelectionMode(selectionMode);
    }

    protected int getSelectionMenuId() {
        return R.menu.blob_descriptor_selection;
    }

    abstract int getDeleteConfirmationItemCountResId();

    abstract String getPreferencesNS();

    private SharedPreferences prefs() {
        return getActivity().getSharedPreferences(getPreferencesNS(), Activity.MODE_PRIVATE);
    }


    protected boolean onSelectionActionItemClicked(final ActionMode mode, MenuItem item) {
        ListView listView = getListView();
        switch (item.getItemId()) {
            case R.id.blob_descriptor_delete:
                int count = listView.getCheckedItemCount();
                String countStr = getResources().getQuantityString(getDeleteConfirmationItemCountResId(), count, count);
                String message = getString(R.string.blob_descriptor_confirm_delete, countStr);
                deleteConfirmationDialog = new AlertDialog.Builder(getActivity())
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle("")
                        .setMessage(message)
                        .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                            deleteSelectedItems();
                            mode.finish();
                            deleteConfirmationDialog = null;
                        })
                        .setNegativeButton(android.R.string.no, null).create();
                deleteConfirmationDialog.setOnDismissListener(dialogInterface -> deleteConfirmationDialog = null);
                deleteConfirmationDialog.show();
                return true;
            case R.id.blob_descriptor_select_all:
                int itemCount = listView.getCount();
                for (int i = itemCount - 1; i > -1; --i) {
                    listView.setItemChecked(i, true);
                }
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        BlobDescriptorList descriptorList = getDescriptorList();

        SharedPreferences p = this.prefs();

        String sortOrderStr = p.getString(PREF_SORT_ORDER,
                                          BlobDescriptorList.SortOrder.TIME.name());
        BlobDescriptorList.SortOrder sortOrder = BlobDescriptorList.SortOrder.valueOf(sortOrderStr);

        boolean sortDir = p.getBoolean(PREF_SORT_DIRECTION, false);

        descriptorList.setSort(sortOrder, sortDir);

        listAdapter = new BlobDescriptorListAdapter(descriptorList);

        final FragmentActivity activity = requireActivity();
        icClock =  ContextCompat.getDrawable(activity, R.drawable.ic_clock_time_nine);
        icList = ContextCompat.getDrawable(activity, R.drawable.ic_format_list_bulleted);
        icArrowUp = ContextCompat.getDrawable(activity, R.drawable.ic_sort_ascending);
        icArrowDown = ContextCompat.getDrawable(activity, R.drawable.sort_descending);

        final ListView listView = getListView();
        listView.setOnItemClickListener((parent, view1, position, id) -> {
            Intent intent = new Intent(activity, ArticleCollectionActivity.class);
            intent.setAction(getItemClickAction());
            intent.putExtra("position", position);
            startActivity(intent);
        });

        setListAdapter(listAdapter);
    }

    protected void deleteSelectedItems() {
        SparseBooleanArray checkedItems = getListView().getCheckedItemPositions();
        for (int i = checkedItems.size() - 1; i > -1; --i) {
            int position = checkedItems.keyAt(i);
            boolean checked = checkedItems.get(position);
            if (checked) {
                getDescriptorList().remove(position);
            }
         }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.blob_descriptor_list, menu);
    }

    @Override
    public void onPrepareOptionsMenu(final Menu menu) {

        BlobDescriptorList list = getDescriptorList();

        miFilter = menu.findItem(R.id.action_filter);

        View filterActionView = miFilter.getActionView();
        SearchView searchView = filterActionView
                .findViewById(R.id.fldFilter);
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
