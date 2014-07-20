package itkach.aard2;

import itkach.slob.Slob;

import java.io.IOException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.net.Uri;
import android.util.Log;

import com.ibm.icu.text.CollationKey;

final class BlobDescriptorList extends AbstractList<BlobDescriptor> {

    static enum SortOrder {
        TIME, NAME
    }

    private Application                     app;
    private DescriptorStore<BlobDescriptor> store;
    private List<BlobDescriptor>            list;
    private List<BlobDescriptor>            filteredList;
    private String                          filter;
    private SortOrder                       order;
    private boolean                         ascending;
    private final DataSetObservable         dataSetObservable;
    private Comparator<BlobDescriptor>      nameComparatorAsc;
    private Comparator<BlobDescriptor>      nameComparatorDesc;
    private Comparator<BlobDescriptor>      timeComparatorAsc;
    private Comparator<BlobDescriptor>      timeComparatorDesc;
    private Comparator<BlobDescriptor>      comparator;
    private Slob.KeyComparator              keyComparator;

    BlobDescriptorList(Application app, DescriptorStore<BlobDescriptor> store) {
        this.app = app;
        this.store = store;
        this.list = new ArrayList<BlobDescriptor>();
        this.filteredList = new ArrayList<BlobDescriptor>();
        this.dataSetObservable = new DataSetObservable();
        this.filter = "";
        keyComparator = Slob.COMPARATORS.get(Slob.Strength.IDENTICAL);

        nameComparatorAsc = new Comparator<BlobDescriptor>() {
            @Override
            public int compare(BlobDescriptor b1, BlobDescriptor b2) {
                return keyComparator.compare(b1.key, b2.key);
            }
        };

        nameComparatorDesc = new Comparator<BlobDescriptor>() {
            @Override
            public int compare(BlobDescriptor b1, BlobDescriptor b2) {
                return keyComparator.compare(b2.key, b1.key);
            }
        };

        timeComparatorAsc = new Comparator<BlobDescriptor>() {
            @Override
            public int compare(BlobDescriptor b1, BlobDescriptor b2) {
                return (int) (b1.createdAt - b2.createdAt);
            }
        };

        timeComparatorDesc = new Comparator<BlobDescriptor>() {
            @Override
            public int compare(BlobDescriptor b1, BlobDescriptor b2) {
                return (int) (b2.createdAt - b1.createdAt);
            }
        };

        order = SortOrder.TIME;
        ascending = false;
        setSort(order, ascending);
    }

    public void registerDataSetObserver(DataSetObserver observer) {
        this.dataSetObservable.registerObserver(observer);
    }

    public void unregisterDataSetObserver(DataSetObserver observer) {
        this.dataSetObservable.unregisterObserver(observer);
    }

    /**
     * Notifies the attached observers that the underlying data has been changed
     * and any View reflecting the data set should refresh itself.
     */
    public void notifyDataSetChanged() {
        this.filteredList.clear();
        Slob.KeyComparator weakComparator = Slob.COMPARATORS
                .get(Slob.Strength.PRIMARY);
        CollationKey filterKey = weakComparator.getCollationKey(filter);
        byte[] nullTerminatedPattern = filterKey.toByteArray();
        if (nullTerminatedPattern.length == 1) {
            this.filteredList.addAll(this.list);
        }
        else {
            byte[] pattern = new byte[nullTerminatedPattern.length - 1];
            System.arraycopy(nullTerminatedPattern, 0, pattern, 0, pattern.length);
            for (BlobDescriptor bd : this.list) {
                byte[] data = weakComparator.getCollationKey(bd.key).toByteArray();
                //data.length-1 to ignore last byte which is 0
                if (KPM.indexOf(data, 0, data.length - 1, pattern) > -1) {
                    this.filteredList.add(bd);
                }
            }
        }
        sortOrderChanged();
    }

    private void sortOrderChanged() {
        Collections.sort(this.filteredList, comparator);
        this.dataSetObservable.notifyChanged();
    }

    /**
     * Notifies the attached observers that the underlying data is no longer
     * valid or available. Once invoked this adapter is no longer valid and
     * should not report further data set changes.
     */
    public void notifyDataSetInvalidated() {
        this.dataSetObservable.notifyInvalidated();
    }

    void load() {
        this.list.addAll(this.store.load(BlobDescriptor.class));
        notifyDataSetChanged();
    }

    Slob resolveOwner(BlobDescriptor bd) {
        Slob slob = app.getSlob(bd.slobId);
        if (slob == null) {
            slob = app.findSlob(bd.slobUri);
        }
        return slob;
    }

    Slob.Blob resolve(BlobDescriptor bd) {
        Slob slob = resolveOwner(bd);
        Slob.Blob blob = null;
        if (slob == null) {
            return null;
        }
        if (slob.getId().equals(bd.slobId)) {
            try {
                blob = new Slob.Blob(slob, bd.blobId, bd.key, bd.fragment,
                        slob.get(bd.blobId));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            Iterator<Slob.Blob> result = slob.find(bd.key,
                    Slob.Strength.QUATERNARY);
            if (result.hasNext()) {
                blob = result.next();
            }
        }
        return blob;
    }

    public BlobDescriptor createDescriptor(String contentUrl) {
        BlobDescriptor bd = new BlobDescriptor();
        bd.id = UUID.randomUUID().toString();
        bd.createdAt = System.currentTimeMillis();
        Uri uri = Uri.parse(contentUrl);
        bd.key = uri.getLastPathSegment();
        bd.slobId = uri.getQueryParameter("slob");
        bd.blobId = uri.getQueryParameter("blob");
        bd.fragment = uri.getFragment();
        Slob slob = app.getSlob(bd.slobId);
        String slobUri = app.getURI(slob);
        bd.slobUri = slobUri;
        return bd;
    }

    public BlobDescriptor add(String contentUrl) {
        BlobDescriptor bd = createDescriptor(contentUrl);
        int index = this.list.indexOf(bd);
        if (index > -1) {
            return this.list.get(index);
        }
        this.list.add(bd);
        store.save(bd);
        notifyDataSetChanged();
        return bd;
    }

    public BlobDescriptor remove(String contentUrl) {
        int index = this.list.indexOf(createDescriptor(contentUrl));
        if (index > -1) {
            return removeByIndex(index);
        }
        return null;
    }

    public BlobDescriptor remove(int index) {
        //FIXME find exact item by uuid or using sorted<->unsorted mapping
        BlobDescriptor bd = this.filteredList.get(index);
        int realIndex = this.list.indexOf(bd);
        if (realIndex > -1) {
            return removeByIndex(realIndex);
        }
        return null;
    }

    private BlobDescriptor removeByIndex(int index) {
        BlobDescriptor bd = this.list.remove(index);
        if (bd != null) {
            boolean removed = store.delete(bd.id);
            Log.d("remove", String.format("Item (%s) %s removed? %s", bd.key, bd.id, removed));
            if (removed) {
                notifyDataSetChanged();
            }
        }
        return bd;
    }

    public boolean contains(String contentUrl) {
        BlobDescriptor bd = createDescriptor(contentUrl);
        int index = this.list.indexOf(bd);
        boolean result = index > -1;
        Log.i("Is bookmarked?", "" + result);
        return result;
    }

    public void setFilter(String filter) {
        this.filter = filter;
        notifyDataSetChanged();
    }

    public String getFilter() {
        return this.filter;
    }

    @Override
    public BlobDescriptor get(int location) {
        return this.filteredList.get(location);
    }

    @Override
    public int size() {
        return this.filteredList.size();
    }

    public void setSort(boolean ascending) {
        setSort(this.order, ascending);
    }

    public void setSort(SortOrder order) {
        setSort(order, this.ascending);
    }

    public SortOrder getSortOrder() {
        return this.order;
    }

    public boolean isAscending() {
        return this.ascending;
    }

    public void setSort(SortOrder order, boolean ascending) {
        this.order = order;
        this.ascending = ascending;
        Comparator<BlobDescriptor> c = null;
        if (order == SortOrder.NAME) {
            c = ascending ? nameComparatorAsc : nameComparatorDesc;
        }
        if (order == SortOrder.TIME) {
            c = ascending ? timeComparatorAsc : timeComparatorDesc;
        }
        if (c != comparator) {
            comparator = c;
            sortOrderChanged();
        }
    }

}
