package itkach.aard2;

import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.net.Uri;
import android.util.Log;

import com.ibm.icu.text.Collator;
import com.ibm.icu.text.RuleBasedCollator;
import com.ibm.icu.text.StringSearch;

import java.io.IOException;
import java.text.StringCharacterIterator;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import itkach.slob.Slob;

final class BlobDescriptorList extends AbstractList<BlobDescriptor> {

    private final String TAG = getClass().getName();

    static enum SortOrder {
        TIME, NAME;
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
    private Comparator<BlobDescriptor>      lastAccessComparator;
    private Slob.KeyComparator              keyComparator;
    private int                             maxSize;
    private RuleBasedCollator               filterCollator;

    BlobDescriptorList(Application app, DescriptorStore<BlobDescriptor> store) {
        this(app, store, 100);
    }

    BlobDescriptorList(Application app, DescriptorStore<BlobDescriptor> store, int maxSize) {
        this.app = app;
        this.store = store;
        this.maxSize = maxSize;
        this.list = new ArrayList<BlobDescriptor>();
        this.filteredList = new ArrayList<BlobDescriptor>();
        this.dataSetObservable = new DataSetObservable();
        this.filter = "";
        keyComparator = Slob.COMPARATORS.get(Slob.Strength.QUATERNARY);

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

        lastAccessComparator = new Comparator<BlobDescriptor>() {
            @Override
            public int compare(BlobDescriptor b1, BlobDescriptor b2) {
                return (int) (b2.lastAccess - b1.lastAccess);
            }
        };

        order = SortOrder.TIME;
        ascending = false;
        setSort(order, ascending);

        try {
            filterCollator = (RuleBasedCollator) Collator.getInstance(Locale.ROOT).clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
        filterCollator.setStrength(Collator.PRIMARY);
        filterCollator.setAlternateHandlingShifted(true);

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
        if (filter == null || filter.length() == 0) {
            this.filteredList.addAll(this.list);
        }
        else {
            for (BlobDescriptor bd : this.list) {
                StringSearch stringSearch = new StringSearch(
                        filter, new StringCharacterIterator(bd.key), filterCollator);
                int matchPos = stringSearch.first();
                if (matchPos != StringSearch.DONE) {
                    this.filteredList.add(bd);
                }
            }
        }
        sortOrderChanged();
    }

    private void sortOrderChanged() {
        try {
            Collections.sort(this.filteredList, comparator);
        }
        catch(Exception e) {
            //From http://www.oracle.com/technetwork/java/javase/compatibility-417013.html#source
            /*
            Synopsis: Updated sort behavior for Arrays and Collections may throw an IllegalArgumentException
            Description: The sorting algorithm used by java.util.Arrays.sort and (indirectly) by
                         java.util.Collections.sort has been replaced. The new sort implementation may
                         throw an IllegalArgumentException if it detects a Comparable that violates
                         the Comparable contract. The previous implementation silently ignored such a situation.
                         If the previous behavior is desired, you can use the new system property,
                         java.util.Arrays.useLegacyMergeSort, to restore previous mergesort behavior.
            Nature of Incompatibility: behavioral
            RFE: 6804124
             */
            //Name comparators use ICU collation key comparison. Given Unicode collation complexity
            //it's hard to be sure that collation key comparisons won't trigger an exception. It certainly
            //does at least for some keys in ICU 53.1.
            //Incorrect or no sorting seems preferable than a crashing app.
            //TODO perhaps java.util.Collections.sort shouldn't be used at all

            Log.w(TAG, "Error while sorting descriptors:", e);
            StringBuffer sb = new StringBuffer("Descriptors keys that caused sort failure:");
            for (BlobDescriptor bd : this.filteredList) {
                sb.append("\n");
                sb.append(bd.key);
            }
            Log.w(TAG, sb.toString());
        }

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
        String slobId = slob.getId().toString();
        if (slobId.equals(bd.slobId)) {
            blob = new Slob.Blob(slob, bd.blobId, bd.key, bd.fragment);
        } else {
            Iterator<Slob.Blob> result = slob.find(bd.key,
                    Slob.Strength.QUATERNARY);
            if (result.hasNext()) {
                blob = result.next();
                bd.slobId = slobId;
                bd.blobId = blob.id;
            }
        }
        if (blob != null) {
            bd.lastAccess = System.currentTimeMillis();
            store.save(bd);
        }
        return blob;
    }

    public BlobDescriptor createDescriptor(String contentUrl) {
        Log.d(TAG, "Create descriptor from content url: " + contentUrl);
        Uri uri = Uri.parse(contentUrl);
        BlobDescriptor bd = BlobDescriptor.fromUri(uri);
        if (bd != null) {
            String slobUri = app.getSlobURI(bd.slobId);
            bd.slobUri = slobUri;
        }
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
        if (this.list.size() > this.maxSize) {
            Collections.sort(this.list, lastAccessComparator);
            BlobDescriptor lru = this.list.remove(this.list.size() - 1);
            store.delete(lru.id);
        }
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
            Log.d(TAG, String.format("Item (%s) %s removed? %s", bd.key, bd.id, removed));
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
        Log.d(TAG, "Is bookmarked?" + result);
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
