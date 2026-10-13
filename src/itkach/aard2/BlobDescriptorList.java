package itkach.aard2;

import android.database.DataSetObservable;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.ibm.icu.text.Collator;
import com.ibm.icu.text.RuleBasedCollator;
import com.ibm.icu.text.StringSearch;

import java.text.StringCharacterIterator;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import itkach.slob.Slob;

final class BlobDescriptorList extends AbstractList<BlobDescriptor> {

    private final String TAG = getClass().getSimpleName();

    static enum SortOrder {
        TIME, NAME;
    }

    // The dictionaries entries point into.
    interface Dictionaries {
        // The loaded dictionary file with this id, or null.
        Slob getSlob(String slobId);
        // The first loaded dictionary file with this uri, in list order, or
        // null.
        Slob findSlobByURI(String uri);
        // Ids of the files in the dictionary list, including ones that failed
        // to load.
        Set<String> getListedIds();
    }

    private Dictionaries                    dictionaries;

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
    private Handler                         handler;
    // True from loadAsync() until its results are merged in on the main thread
    // and entries are moved, and while a later move pass runs (see
    // moveToLoadedDictionaries). Read on the main thread only (set false there
    // too), so no synchronization.
    private boolean                         loading;
    // Set when dictionaries change while loading, so a move pass runs (again)
    // once that's done. Main thread only.
    private boolean                         moveAgain;
    // Actions waiting for loading to finish (see whenLoaded). Main thread only,
    // like loading itself.
    private final List<Runnable>            loadedCallbacks = new ArrayList<>();

    BlobDescriptorList(Dictionaries dictionaries, DescriptorStore<BlobDescriptor> store) {
        this(dictionaries, store, 1000);
    }

    BlobDescriptorList(Dictionaries dictionaries, DescriptorStore<BlobDescriptor> store, int maxSize) {
        this.dictionaries = dictionaries;
        this.store = store;
        this.maxSize = maxSize;
        this.list = new ArrayList<BlobDescriptor>();
        this.filteredList = new ArrayList<BlobDescriptor>();
        this.dataSetObservable = new DataSetObservable();
        this.filter = "";
        keyComparator = Slob.Strength.QUATERNARY.comparator;

        nameComparatorAsc = new Comparator<BlobDescriptor>() {
            @Override
            public int compare(BlobDescriptor b1, BlobDescriptor b2) {
            return keyComparator.compare(b1.key, b2.key);
            }
        };

        nameComparatorDesc = Collections.reverseOrder(nameComparatorAsc);

        timeComparatorAsc = new Comparator<BlobDescriptor>() {
            @Override
            public int compare(BlobDescriptor b1, BlobDescriptor b2) {
            return Util.compare(b1.createdAt, b2.createdAt);
            }
        };

        timeComparatorDesc = Collections.reverseOrder(timeComparatorAsc);

        lastAccessComparator = new Comparator<BlobDescriptor>() {
            @Override
            public int compare(BlobDescriptor b1, BlobDescriptor b2) {
                return  Util.compare(b2.lastAccess, b1.lastAccess);
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
        filterCollator.setAlternateHandlingShifted(false);
        handler = new Handler(Looper.getMainLooper());
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
        Util.sort(this.filteredList, comparator);
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

    boolean isLoading() {
        return loading;
    }

    // Run action once this list's entries are available: immediately if the
    // async load and any move pass have already finished (or were never
    // started), otherwise right after. Lets a caller that needs a populated,
    // up-to-date list - opening a bookmarks/history article, resolving the
    // bookmark icon - avoid acting on the still-empty list during a cold start,
    // or on entries about to be moved. Main thread only.
    void whenLoaded(Runnable action) {
        if (loading) {
            loadedCallbacks.add(action);
        } else {
            action.run();
        }
    }

    // Load descriptors off the main thread. Reading and deserializing them from
    // disk can be slow (history fills to maxSize = 1000 entries), enough to drag
    // out cold start if done on it. isLoading() stays true from here until the
    // results are merged in on the main thread and entries are moved (see
    // moveToLoadedDictionaries); observers are notified at both ends, so a list
    // fragment can show its spinner meanwhile.
    // notifyDataSetChanged rebuilds and re-sorts the filtered view, so the merged
    // items land in the fragment's current sort order.
    void loadAsync() {
        loading = true;
        notifyDataSetChanged();
        Util.runAsync(() -> store.load(BlobDescriptor.class), loaded -> {
            // Merge rather than addAll. An entry the user created while this was
            // loading - e.g. opening or bookmarking an article reached straight
            // from a link before the load finished - is already in the list, and
            // add() couldn't dedupe against it (it wasn't loaded yet), so it also
            // wrote its own store file. Keep that fresh copy and drop the stale
            // duplicate now read from disk. equals()/hashCode() are by article
            // identity, not the per-entry store id, so the set matches across the
            // two files (and catches any pre-existing duplicate files too).
            // Membership is via a HashSet: with up to maxSize entries a contains()
            // scan per loaded item would be quadratic. All of this runs on the
            // main thread, as do all list mutations, so the list is never touched
            // concurrently - the background task only reads the store.
            Set<BlobDescriptor> seen = new HashSet<>(list);
            for (BlobDescriptor bd : loaded) {
                if (seen.add(bd)) {
                    list.add(bd);
                } else {
                    store.delete(bd.id);
                }
            }
            // The disk load plus any entries added while it was in flight can put
            // the list over the cap; trim back rather than leaving it to grow by
            // one on every such cold start.
            enforceMaxSize();
            startMoving();
        });
    }

    private void finishLoading() {
        loading = false;
        notifyDataSetChanged();
        // Drain a copy: a callback may itself call whenLoaded, and with
        // loading now false that reentrant one runs immediately rather than
        // re-queuing, so this list won't be mutated mid-iteration.
        List<Runnable> callbacks = new ArrayList<>(loadedCallbacks);
        loadedCallbacks.clear();
        for (Runnable action : callbacks) {
            action.run();
        }
    }

    // An entry to move to another dictionary file, and what looking its key up
    // there found.
    private static final class Move {
        final BlobDescriptor bd;
        final Slob to;
        Slob.Blob found;
        boolean failed;

        Move(BlobDescriptor bd, Slob to) {
            this.bd = bd;
            this.to = to;
        }
    }

    // Move entries whose dictionary file has been closed or replaced to a loaded
    // dictionary with the same uri - a newer version of it: the key is looked up
    // there and the entry saved pointing at the article found. Entries whose key
    // isn't found keep pointing at the old file, and record that, so the lookup
    // isn't repeated; a lookup that fails (a read error) isn't recorded, so it's
    // retried on the next change or startup. An entry whose file is still in
    // the dictionary list but fails to load (e.g. on storage that's currently
    // unavailable) isn't moved, so it isn't bound to another file for good.
    // Called whenever the set of loaded dictionaries changes; the lookups run in
    // the background, with the list counting as loading meanwhile, and a change
    // during a load or pass makes it run again once that's done.
    void moveToLoadedDictionaries() {
        if (loading) {
            moveAgain = true;
            return;
        }
        startMoving();
    }

    private void startMoving() {
        moveAgain = false;
        Set<String> listed = dictionaries.getListedIds();
        List<Move> moves = new ArrayList<>();
        for (BlobDescriptor bd : this.list) {
            if (listed.contains(bd.slobId) || bd.slobUri == null) {
                continue;
            }
            Slob slob = dictionaries.findSlobByURI(bd.slobUri);
            if (slob != null && !slob.getId().toString().equals(bd.notFoundIn)) {
                moves.add(new Move(bd, slob));
            }
        }
        if (moves.isEmpty()) {
            if (loading) {
                finishLoading();
            }
            return;
        }
        if (!loading) {
            loading = true;
            notifyDataSetChanged();
        }
        Util.runAsync(() -> lookUp(moves), found -> {
            if (found != null) {
                applyMoves(found);
            }
            if (moveAgain) {
                startMoving();
            } else {
                finishLoading();
            }
        });
    }

    private List<Move> lookUp(List<Move> moves) {
        for (Move m : moves) {
            try {
                m.found = findBlob(m.to, m.bd.key);
            }
            catch (Exception ex) {
                m.failed = true;
                Log.w(TAG, String.format("Failed to look up %s in %s (%s)",
                        m.bd.key, m.to.getId(), m.to.fileURI), ex);
            }
        }
        return moves;
    }

    // The article for key: the one with exactly that key if there is one, else
    // the first match. Matches are keys equal to it at quaternary strength (not
    // a prefix search), so there are only ever a few.
    private static Slob.Blob findBlob(Slob slob, String key) {
        Iterator<Slob.Blob> result = slob.find(key, Slob.Strength.QUATERNARY);
        Slob.Blob first = null;
        while (result.hasNext()) {
            Slob.Blob blob = result.next();
            if (blob.key.equals(key)) {
                return blob;
            }
            if (first == null) {
                first = blob;
            }
        }
        return first;
    }

    private void applyMoves(List<Move> moves) {
        Set<BlobDescriptor> current = Collections.newSetFromMap(new IdentityHashMap<>());
        current.addAll(this.list);
        Set<BlobDescriptor> moved = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Move m : moves) {
            BlobDescriptor bd = m.bd;
            String toId = m.to.getId().toString();
            // Skip entries removed while the lookups ran, and targets closed
            // meanwhile: that change runs the pass again.
            if (m.failed || !current.contains(bd) || dictionaries.getSlob(toId) == null) {
                continue;
            }
            if (m.found == null) {
                bd.notFoundIn = toId;
                store.save(bd);
                continue;
            }
            bd.slobId = toId;
            bd.slobUri = m.to.getURI();
            bd.blobId = m.found.id;
            bd.key = m.found.key;
            bd.notFoundIn = null;
            moved.add(bd);
        }
        if (moved.isEmpty()) {
            return;
        }
        // A moved entry can now be equal to one already in the list (the same
        // article bookmarked again in the new version) or to another moved one.
        // Keep the one already there, with the earlier creation time (shown, and
        // sorted on) and the later access time (decides what's evicted first).
        Map<BlobDescriptor, BlobDescriptor> kept = new HashMap<>();
        for (BlobDescriptor bd : this.list) {
            if (!moved.contains(bd)) {
                kept.putIfAbsent(bd, bd);
            }
        }
        Set<BlobDescriptor> dropped = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BlobDescriptor bd : moved) {
            BlobDescriptor existing = kept.putIfAbsent(bd, bd);
            if (existing == null) {
                store.save(bd);
                continue;
            }
            existing.createdAt = Math.min(existing.createdAt, bd.createdAt);
            existing.lastAccess = Math.max(existing.lastAccess, bd.lastAccess);
            store.save(existing);
            store.delete(bd.id);
            dropped.add(bd);
        }
        this.list.removeIf(dropped::contains);
        Log.d(TAG, String.format("Moved %d entries, dropped %d duplicates",
                moved.size(), dropped.size()));
    }

    private void doUpdateLastAccess(BlobDescriptor bd) {
        // A pager can still show an entry that has since been removed; saving
        // it would bring it back.
        if (!isListed(bd)) {
            return;
        }
        long t = System.currentTimeMillis();
        long dt = t - bd.lastAccess;
        if (dt < 2000) {
            return;
        }
        bd.lastAccess = t;
        store.save(bd);
    }

    private boolean isListed(BlobDescriptor bd) {
        return indexOfListed(bd) > -1;
    }

    // Index of this very entry, not of one equal to it: the filtered view can
    // briefly hold an entry a move pass has merged into another, equal one.
    private int indexOfListed(BlobDescriptor bd) {
        for (int i = 0; i < this.list.size(); i++) {
            if (this.list.get(i) == bd) {
                return i;
            }
        }
        return -1;
    }

    void updateLastAccess(final BlobDescriptor bd) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            doUpdateLastAccess(bd);
        }
        else {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    doUpdateLastAccess(bd);
                }
            });
        }
    }

    // The entry's dictionary, or null if its file isn't loaded.
    Slob resolveOwner(BlobDescriptor bd) {
        return dictionaries.getSlob(bd.slobId);
    }

    // The entry's article, or null if its dictionary file isn't loaded. Entries
    // are moved to newer versions of their dictionary before the list counts as
    // loaded (see moveToLoadedDictionaries), so there's nothing to look up here.
    Slob.Blob resolve(BlobDescriptor bd) {
        Slob slob = resolveOwner(bd);
        if (slob == null) {
            return null;
        }
        updateLastAccess(bd);
        return new Slob.Blob(slob, bd.blobId, bd.key, bd.fragment);
    }

    public BlobDescriptor createDescriptor(String contentUrl) {
        Log.d(TAG, "Create descriptor from content url: " + contentUrl);
        Uri uri = Uri.parse(contentUrl);
        BlobDescriptor bd = BlobDescriptor.fromUri(uri);
        if (bd != null) {
            String slobUri = dictionaries.getSlob(bd.slobId).getURI();
            Log.d(TAG, "Found slob uri for: " + bd.slobId + " " + slobUri);
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
        enforceMaxSize();
        notifyDataSetChanged();
        return bd;
    }

    // Evict least-recently-accessed entries until the list is back within maxSize.
    // add() overshoots by one at a time; a merge (loadAsync) can overshoot by many
    // when entries were added while the disk load was in flight - so loop, sorting
    // once, rather than dropping a single entry.
    private void enforceMaxSize() {
        if (this.list.size() <= this.maxSize) {
            return;
        }
        Util.sort(this.list, lastAccessComparator);
        while (this.list.size() > this.maxSize) {
            BlobDescriptor lru = this.list.remove(this.list.size() - 1);
            store.delete(lru.id);
        }
    }

    public BlobDescriptor remove(String contentUrl) {
        int index = this.list.indexOf(createDescriptor(contentUrl));
        if (index > -1) {
            return removeByIndex(index);
        }
        return null;
    }

    public BlobDescriptor remove(int index) {
        int realIndex = indexOfListed(this.filteredList.get(index));
        if (realIndex > -1) {
            return removeByIndex(realIndex);
        }
        return null;
    }

    // Re-insert a descriptor removed via remove(int) - the undo of a swipe-remove.
    // Keeps the original object (and its timestamp), so it re-sorts back into its
    // natural place rather than jumping to the top like a fresh add would.
    public void restore(BlobDescriptor bd) {
        this.list.add(bd);
        store.save(bd);
        notifyDataSetChanged();
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

    // Delete every entry, from both the in-memory list and the backing store.
    // Used when history recording is turned off.
    public void clear() {
        for (BlobDescriptor bd : this.list) {
            store.delete(bd.id);
        }
        this.list.clear();
        notifyDataSetChanged();
    }

    @Override
    public boolean isEmpty() {
        // The true count, independent of any active filter (AbstractList's
        // default isEmpty() reflects the filtered view - size() below).
        return this.list.isEmpty();
    }

    public boolean contains(String contentUrl) {
        return this.list.contains(createDescriptor(contentUrl));
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
